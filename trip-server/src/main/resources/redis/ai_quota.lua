-- User-supplied models have only an hourly rate limit; daily/global counters are ignored.
local raw = redis.call('GET', KEYS[1]) or '0'
-- INCR rejects forms such as 1.0 or 01 even though tonumber accepts them.
if raw ~= '0' and not string.match(raw, '^[1-9]%d*$') then return redis.error_reply('INVALID_QUOTA_COUNTER') end
local used = tonumber(raw)
if not used or used < 0 or used % 1 ~= 0 then return redis.error_reply('INVALID_QUOTA_COUNTER') end
if used >= tonumber(ARGV[1]) then return {0, used} end
used = redis.call('INCR', KEYS[1])
redis.call('EXPIREAT', KEYS[1], ARGV[2])
return {1, used}
