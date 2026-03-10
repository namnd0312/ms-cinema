package com.namnd.cinema.service.impl;

import com.namnd.cinema.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceImpl implements RedisService {

    private static final Logger logger = LoggerFactory.getLogger(RedisServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisServiceImpl(StringRedisTemplate stringRedisTemplate,
                            RedisTemplate<String, Object> redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
    }

    // ─── Key-Value Operations ───

    @Override
    public void set(String key, String value) {
        try {
            stringRedisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            logger.error("Redis SET failed for key: {}", key, e);
        }
    }

    @Override
    public void set(String key, String value, long timeout, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            logger.error("Redis SET with TTL failed for key: {}", key, e);
        }
    }

    @Override
    public String get(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Redis GET failed for key: {}", key, e);
            return null;
        }
    }

    @Override
    public Boolean delete(String key) {
        try {
            return stringRedisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Redis DELETE failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long delete(Collection<String> keys) {
        try {
            return stringRedisTemplate.delete(keys);
        } catch (Exception e) {
            logger.error("Redis batch DELETE failed", e);
            return 0L;
        }
    }

    @Override
    public Boolean hasKey(String key) {
        try {
            return stringRedisTemplate.hasKey(key);
        } catch (DataAccessResourceFailureException e) {
            // Rethrow connectivity failures so callers with fail-closed logic can handle them
            logger.error("Redis HASKEY connectivity failure for key: {}", key, e);
            throw e;
        } catch (Exception e) {
            logger.error("Redis HASKEY failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return stringRedisTemplate.expire(key, timeout, unit);
        } catch (Exception e) {
            logger.error("Redis EXPIRE failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long getExpire(String key) {
        try {
            return stringRedisTemplate.getExpire(key);
        } catch (Exception e) {
            logger.error("Redis GETEXPIRE failed for key: {}", key, e);
            return 0L;
        }
    }

    // ─── Hash Operations ───

    @Override
    public void hSet(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
        } catch (Exception e) {
            logger.error("Redis HSET failed for key: {}, hashKey: {}", key, hashKey, e);
        }
    }

    @Override
    public Object hGet(String key, String hashKey) {
        try {
            return redisTemplate.opsForHash().get(key, hashKey);
        } catch (Exception e) {
            logger.error("Redis HGET failed for key: {}, hashKey: {}", key, hashKey, e);
            return null;
        }
    }

    @Override
    public Map<Object, Object> hGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            logger.error("Redis HGETALL failed for key: {}", key, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public Long hDelete(String key, Object... hashKeys) {
        try {
            return redisTemplate.opsForHash().delete(key, hashKeys);
        } catch (Exception e) {
            logger.error("Redis HDEL failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public Boolean hHasKey(String key, String hashKey) {
        try {
            return redisTemplate.opsForHash().hasKey(key, hashKey);
        } catch (Exception e) {
            logger.error("Redis HHASKEY failed for key: {}, hashKey: {}", key, hashKey, e);
            return false;
        }
    }

    // ─── List Operations ───

    @Override
    public Long lPush(String key, String value) {
        try {
            return stringRedisTemplate.opsForList().leftPush(key, value);
        } catch (Exception e) {
            logger.error("Redis LPUSH failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public Long rPush(String key, String value) {
        try {
            return stringRedisTemplate.opsForList().rightPush(key, value);
        } catch (Exception e) {
            logger.error("Redis RPUSH failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public List<String> lRange(String key, long start, long end) {
        try {
            return stringRedisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            logger.error("Redis LRANGE failed for key: {}", key, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Long lLen(String key) {
        try {
            return stringRedisTemplate.opsForList().size(key);
        } catch (Exception e) {
            logger.error("Redis LLEN failed for key: {}", key, e);
            return 0L;
        }
    }

    // ─── Set Operations ───

    @Override
    public Long sAdd(String key, String... values) {
        try {
            return stringRedisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            logger.error("Redis SADD failed for key: {}", key, e);
            return 0L;
        }
    }

    @Override
    public Set<String> sMembers(String key) {
        try {
            return stringRedisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            logger.error("Redis SMEMBERS failed for key: {}", key, e);
            return Collections.emptySet();
        }
    }

    @Override
    public Boolean sIsMember(String key, String value) {
        try {
            return stringRedisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            logger.error("Redis SISMEMBER failed for key: {}", key, e);
            return false;
        }
    }

    @Override
    public Long sRemove(String key, String... values) {
        try {
            Object[] objects = values;
            return stringRedisTemplate.opsForSet().remove(key, objects);
        } catch (Exception e) {
            logger.error("Redis SREM failed for key: {}", key, e);
            return 0L;
        }
    }

    // ─── Pub/Sub ───

    @Override
    public void publish(String channel, String message) {
        try {
            stringRedisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            logger.error("Redis PUBLISH failed for channel: {}", channel, e);
        }
    }

    // ─── Distributed Lock ───

    @Override
    public boolean tryLock(String key, long timeout, TimeUnit unit) {
        try {
            Boolean result = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "locked", timeout, unit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.error("Redis TRYLOCK failed for key: {}", key, e);
            return false;
        }
    }

    // LIMITATION: no ownership check — if lock TTL expires and another thread acquires it,
    // this deletes the new holder's lock. Add UUID-value + Lua script when concurrent lock use case arises.
    @Override
    public void unlock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Redis UNLOCK failed for key: {}", key, e);
        }
    }
}
