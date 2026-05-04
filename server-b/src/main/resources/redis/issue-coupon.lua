local existing = redis.call('GET', KEYS[1])
if existing then
  return existing
end

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
  redis.call('SET', KEYS[1], 'DUPLICATE')
  redis.call('EXPIRE', KEYS[1], ARGV[2])
  redis.call('EXPIRE', KEYS[2], ARGV[3])
  redis.call('EXPIRE', KEYS[3], ARGV[3])
  return 'DUPLICATE'
end

local stock = tonumber(redis.call('GET', KEYS[3]) or '0')
if stock <= 0 then
  redis.call('SET', KEYS[1], 'SOLD_OUT')
  redis.call('EXPIRE', KEYS[1], ARGV[2])
  redis.call('EXPIRE', KEYS[3], ARGV[3])
  return 'SOLD_OUT'
end

redis.call('DECR', KEYS[3])
redis.call('SADD', KEYS[2], ARGV[1])
redis.call('SET', KEYS[1], 'SUCCESS')
redis.call('EXPIRE', KEYS[1], ARGV[2])
redis.call('EXPIRE', KEYS[2], ARGV[3])
redis.call('EXPIRE', KEYS[3], ARGV[3])
return 'SUCCESS'
