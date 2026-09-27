-- Check all dimensions before incrementing any. KEYS share one Redis cluster hash tag.
local used = {}
for i = 1, 3 do
  local raw = redis.call('GET', KEYS[i]) or '0'
  -- INCR rejects forms such as 1.0 or 01 even though tonumber accepts them.
  if raw ~= '0' and not string.match(raw, '^[1-9]%d*$') then return redis.error_reply('INVALID_QUOTA_COUNTER') end
  used[i] = tonumber(raw)
  if not used[i] or used[i] < 0 or used[i] % 1 ~= 0 then return redis.error_reply('INVALID_QUOTA_COUNTER') end
end
for i = 1, 3 do
  if used[i] >= tonumber(ARGV[i]) then return {0, i, used[1], used[2], used[3]} end
end
for i = 1, 3 do
  used[i] = redis.call('INCR', KEYS[i])
  redis.call('EXPIREAT', KEYS[i], ARGV[i + 3])
end
return {1, 0, used[1], used[2], used[3]}
