package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.properties.JwtProperties;
import com.sky.result.Result;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/admin/refresh")
@Slf4j
public class RefreshTokenController {

    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping
    public Result<Map<String, String>> refresh(@RequestHeader("refreshToken") String refreshToken) {
        try {
            // 1. 解析 refreshToken
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminRefreshSecretKey(), refreshToken);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());

            // 2. 检查 Redis 中是否存在该 refreshToken（未被吊销）
            String redisKey = "refresh_token:" + refreshToken;
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(redisKey))) {
                log.warn("无效或已吊销的 refreshToken: {}", refreshToken);
                return Result.error("Refresh token invalid or expired");
            }

            // 3. 验证是否与当前用户的活跃 refreshToken 一致（防止旧 token 滥用）
            String currentRefresh = stringRedisTemplate.opsForValue().get("user_refresh:" + empId);
            if (!refreshToken.equals(currentRefresh)) {
                log.warn("用户 {} 使用了旧的 refreshToken", empId);
                return Result.error("Refresh token has been replaced");
            }

            // 4. 生成新的 accessToken
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put(JwtClaimsConstant.EMP_ID, empId);
            String newAccessToken = JwtUtil.createJWT(
                    jwtProperties.getAdminSecretKey(),
                    jwtProperties.getAdminTtl(),
                    newClaims);

            // 5. 滑动刷新 refreshToken 的过期时间（可选，续期）
            stringRedisTemplate.expire(redisKey, jwtProperties.getAdminRefreshTtl(), TimeUnit.MILLISECONDS);
            stringRedisTemplate.expire("user_refresh:" + empId, jwtProperties.getAdminRefreshTtl(), TimeUnit.MILLISECONDS);

            Map<String, String> result = new HashMap<>();
            result.put("accessToken", newAccessToken);
            return Result.success(result);
        } catch (Exception e) {
            log.error("刷新 token 失败", e);
            return Result.error("Invalid refresh token");
        }
    }
}
