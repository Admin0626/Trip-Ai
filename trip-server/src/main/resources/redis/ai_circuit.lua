-- All three keys share a hash tag. Redis TIME avoids application clock skew.
-- Validate before writing: script errors do not roll back earlier writes.
redis.replicate_commands()
local action, ticket, generation, outcome = ARGV[1], ARGV[2], ARGV[3], ARGV[4]
local window, cooldown, minimum, threshold, lease, ttl = tonumber(ARGV[5]), tonumber(ARGV[6]), tonumber(ARGV[7]), tonumber(ARGV[8]), tonumber(ARGV[9]), tonumber(ARGV[10])
local function bad() return redis.error_reply('Invalid circuit state') end
if action~='acquire' and action~='complete' and action~='abandon' and action~='status' then return bad() end
if not window or not cooldown or not minimum or not threshold or not lease or not ttl then return bad() end
for i=1,3 do
  local kind=redis.call('TYPE',KEYS[i]).ok
  if kind~='none' and kind~=(i==1 and 'hash' or 'zset') then return bad() end
end
local clock=redis.call('TIME'); local now=tonumber(clock[1])*1000+math.floor(tonumber(clock[2])/1000)
local phase=redis.call('HGET',KEYS[1],'phase')
local gen=redis.call('HGET',KEYS[1],'generation')
local retry=tonumber(redis.call('HGET',KEYS[1],'retryAt') or '0')
local untilTime=tonumber(redis.call('HGET',KEYS[1],'leaseUntil') or '0')
local probe=redis.call('HGET',KEYS[1],'probe') or ''
if phase then
  if (phase~='CLOSED' and phase~='OPEN' and phase~='HALF_OPEN') or not gen or gen=='' or not retry or not untilTime or retry<0 or untilTime<0 then return bad() end
  if phase=='OPEN' and retry<=0 then return bad() end
  if phase=='HALF_OPEN' and (untilTime<=0 or probe=='') then return bad() end
elseif redis.call('EXISTS',KEYS[1])==1 or redis.call('EXISTS',KEYS[2],KEYS[3])>0 then return bad() end
if redis.call('ZCARD',KEYS[3])>redis.call('ZCARD',KEYS[2]) then return bad() end
local changed=false
local function state(p,g,r,l,t)
  phase,gen,retry,untilTime,probe=p,g,r,l,t
  redis.call('HSET',KEYS[1],'phase',p,'generation',g,'retryAt',r,'leaseUntil',l,'probe',t)
  changed=true
end
if phase then
  redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',now-window)
  redis.call('ZREMRANGEBYSCORE',KEYS[3],'-inf',now-window)
end
-- An abandoned/crashed probe cannot hold the circuit forever or finish late.
if phase=='HALF_OPEN' and untilTime<=now then state('OPEN','expired:'..ticket,now+cooldown,0,'') end
local allowed=false
if action=='acquire' then
  if not phase then state('CLOSED','initial:'..ticket,0,0,'') end
  if phase=='CLOSED' then allowed=true; changed=true
  elseif phase=='OPEN' and retry<=now then state('HALF_OPEN','probe:'..ticket,0,now+lease,ticket); allowed=true end
elseif action=='complete' and gen==generation then
  if phase=='HALF_OPEN' and probe==ticket then
    if outcome=='1' then
      redis.call('DEL',KEYS[2],KEYS[3]); redis.call('ZADD',KEYS[2],now,ticket)
      state('CLOSED','recovered:'..ticket,0,0,'')
    else
      redis.call('ZADD',KEYS[2],now,ticket); redis.call('ZADD',KEYS[3],now,ticket)
      state('OPEN','failed:'..ticket,now+cooldown,0,'')
    end
  elseif phase=='CLOSED' then
    redis.call('ZADD',KEYS[2],now,ticket)
    if outcome~='1' then redis.call('ZADD',KEYS[3],now,ticket) end
    local total=redis.call('ZCARD',KEYS[2]); local failed=redis.call('ZCARD',KEYS[3])
    changed=true
    if total>=minimum and failed*100>total*threshold then state('OPEN','opened:'..ticket,now+cooldown,0,'') end
  end
elseif action=='abandon' and phase=='HALF_OPEN' and gen==generation and probe==ticket then
  -- Quota rejected before outbound: release the probe without a fake failure.
  state('OPEN','abandoned:'..ticket,now,0,'')
end
if changed then for i=1,3 do redis.call('PEXPIRE',KEYS[i],ttl) end end
local total=redis.call('ZCARD',KEYS[2]); local failed=redis.call('ZCARD',KEYS[3])
local deadline=phase=='HALF_OPEN' and untilTime or retry
return cjson.encode({phase=phase or 'CLOSED',allowed=allowed,generation=gen or '',ticket=ticket,
  total=total,failed=failed,retryAfterSeconds=math.max(0,math.ceil((deadline-now)/1000)),
  retryAt=deadline>0 and deadline or 0})
