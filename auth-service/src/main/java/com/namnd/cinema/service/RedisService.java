package com.namnd.cinema.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface RedisService {

    // Key-Value operations
    void set(String key, String value);
    void set(String key, String value, long timeout, TimeUnit unit);
    String get(String key);
    Boolean delete(String key);
    Long delete(Collection<String> keys);
    Boolean hasKey(String key);
    Boolean expire(String key, long timeout, TimeUnit unit);
    Long getExpire(String key);

    // Hash operations
    void hSet(String key, String hashKey, Object value);
    Object hGet(String key, String hashKey);
    Map<Object, Object> hGetAll(String key);
    Long hDelete(String key, Object... hashKeys);
    Boolean hHasKey(String key, String hashKey);

    // List operations
    Long lPush(String key, String value);
    Long rPush(String key, String value);
    List<String> lRange(String key, long start, long end);
    Long lLen(String key);

    // Set operations
    Long sAdd(String key, String... values);
    Set<String> sMembers(String key);
    Boolean sIsMember(String key, String value);
    Long sRemove(String key, String... values);

    // Pub/Sub
    void publish(String channel, String message);

    // Distributed Lock
    boolean tryLock(String key, long timeout, TimeUnit unit);
    void unlock(String key);
}
