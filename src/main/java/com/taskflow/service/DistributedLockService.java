package com.taskflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@ConditionalOnBean(name = "redisTemplate")
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private static final String LOCK_PREFIX = "taskflow:lock:";
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String tryLock(String resourceKey) {
        return tryLock(resourceKey, DEFAULT_LOCK_TTL);
    }

    public String tryLock(String resourceKey, Duration ttl) {
        String lockKey = LOCK_PREFIX + resourceKey;
        String lockValue = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, ttl);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock acquired: {}", resourceKey);
            return lockValue;
        }
        return null;
    }

    public boolean release(String resourceKey, String lockValue) {
        String lockKey = LOCK_PREFIX + resourceKey;
        Object current = redisTemplate.opsForValue().get(lockKey);
        if (lockValue.equals(current)) {
            Boolean deleted = redisTemplate.delete(lockKey);
            log.debug("Lock released: {}", resourceKey);
            return Boolean.TRUE.equals(deleted);
        }
        return false;
    }

    public boolean isLocked(String resourceKey) {
        String lockKey = LOCK_PREFIX + resourceKey;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
}
