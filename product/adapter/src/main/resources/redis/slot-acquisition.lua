-- Atomic slot acquisition script for fair first-come-first-served allocation
--
-- Keys:
--   KEYS[1]: product:{product_id}:stock (String - current stock count)
--   KEYS[2]: product:{product_id}:queue (Sorted Set - fairness queue with timestamp scores)
--   KEYS[3]: user:{user_id}:product:{product_id} (String - duplicate prevention flag)
--
-- Arguments:
--   ARGV[1]: user_id (String)
--   ARGV[2]: timestamp (Number - epoch milliseconds for arrival-time ordering)
--
-- Returns:
--   - {err = 'DUPLICATE_REQUEST'} if user already has active slot
--   - {err = 'SOLD_OUT'} if no stock available
--   - {ok = 'SLOT_ACQUIRED'} on successful acquisition

local product_stock_key = KEYS[1]
local product_queue_key = KEYS[2]
local duplicate_key = KEYS[3]
local user_id = ARGV[1]
local timestamp = tonumber(ARGV[2])

-- Check for duplicate request (user already has active slot for this product)
if redis.call('EXISTS', duplicate_key) == 1 then
    return {err = 'DUPLICATE_REQUEST'}
end

-- Check stock availability
local stock = tonumber(redis.call('GET', product_stock_key))
if stock == nil or stock <= 0 then
    return {err = 'SOLD_OUT'}
end

-- Atomic allocation operations:
-- 1. Decrement stock counter
redis.call('DECR', product_stock_key)

-- 2. Add user to fairness queue with arrival timestamp as score
redis.call('ZADD', product_queue_key, timestamp, user_id)

-- 3. Set duplicate prevention flag with 30-minute expiry (1800 seconds)
redis.call('SET', duplicate_key, '1', 'EX', 1800)

return {ok = 'SLOT_ACQUIRED'}
