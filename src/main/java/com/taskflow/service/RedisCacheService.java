package com.taskflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@ConditionalOnBean(name = "redisTemplate")
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private static final String TOOL_CACHE_PREFIX = "taskflow:tool:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheToolResult(String toolName, String paramsHash,
                                 Object result, Duration ttl) {
        String key = TOOL_CACHE_PREFIX + toolName + ":" + paramsHash;
        redisTemplate.opsForValue().set(key, result, ttl);
        log.debug("Cached tool result: {}", key);
    }

    public Optional<Object> getCachedToolResult(String toolName, String paramsHash) {
        String key = TOOL_CACHE_PREFIX + toolName + ":" + paramsHash;
        Object result = redisTemplate.opsForValue().get(key);
        if (result != null) {
            log.debug("Cache hit: {}", key);
        }
        return Optional.ofNullable(result);
    }

    public void invalidateTool(String toolName) {
        String pattern = TOOL_CACHE_PREFIX + toolName + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            Long deleted = redisTemplate.delete(keys);
            log.debug("Invalidated {} cache entries for tool: {}", deleted, toolName);
        }
    }

    public void clearAll() {
        var keys = redisTemplate.keys(TOOL_CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
