package com.bn.aliagent.service;

import com.bn.aliagent.util.TokenGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Token 服务
 *
 * 管理 Token 的生成、查询和删除
 * Redis 采用双向索引：
 *   - "token:{userId}"  -> token值   （用于按 userId 登出）
 *   - "token_index:{token}" -> userId （用于按 token O(1) 查询用户）
 * 两者 TTL 统一为 24 小时
 */
@Service
public class TokenService {

    private static final String TOKEN_KEY_PREFIX = "token:";
    private static final String TOKEN_INDEX_PREFIX = "token_index:";
    private static final long TOKEN_TTL_SECONDS = 86400; // 24小时

    private final StringRedisTemplate redisTemplate;

    public TokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成 Token 并写入 Redis（双向索引）
     *
     * @param userId 用户ID
     * @return 生成的 Token 字符串
     */
    public String generateToken(String userId) {
        String token = TokenGenerator.generate();
        String tokenKey = TOKEN_KEY_PREFIX + userId;
        String tokenIndexKey = TOKEN_INDEX_PREFIX + token;

        redisTemplate.opsForValue().set(tokenKey, token, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(tokenIndexKey, userId, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    /**
     * 根据 Token 获取对应的用户ID
     *
     * @param token Token 字符串
     * @return userId，未找到或已过期返回 null
     */
    public String getUserIdByToken(String token) {
        String tokenIndexKey = TOKEN_INDEX_PREFIX + token;
        return redisTemplate.opsForValue().get(tokenIndexKey);
    }

    /**
     * 删除用户的 Token（退出登录）
     * 同时清理双向索引
     *
     * @param userId 用户ID
     */
    public void deleteToken(String userId) {
        String tokenKey = TOKEN_KEY_PREFIX + userId;
        String token = redisTemplate.opsForValue().get(tokenKey);
        if (token != null) {
            redisTemplate.delete(TOKEN_INDEX_PREFIX + token);
        }
        redisTemplate.delete(tokenKey);
    }

    /**
     * 验证 Token 是否有效
     *
     * @param token Token 字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        return getUserIdByToken(token) != null;
    }
}
