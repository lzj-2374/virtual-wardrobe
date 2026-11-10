package com.liuzhangjie.wardrobe.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 * <p>
 * 注意 jjwt 版本：本类用的是 <b>0.12.6</b> 的 API，和网上大量 0.9.x 教程完全不同。
 * 0.9.x 的 {@code setClaims / setExpiration / setSigningKey / signWith(SignatureAlgorithm, byte[])}
 * 在 0.12.x 里已经全部移除，照抄会直接编译不过。
 */
@Slf4j
@Component
public class JwtUtil {

    /** 签名密钥，来自 application.yml 的 jwt.secret */
    @Value("${jwt.secret}")
    private String secret;

    /** token 有效期，单位秒，来自 jwt.expire */
    @Value("${jwt.expire}")
    private Long expire;

    /**
     * 把字符串密钥转成 jjwt 需要的 SecretKey。
     * <p>
     * HS 系列算法对密钥长度有硬性要求（至少 256 位 = 32 字节），
     * 密钥太短会抛 WeakKeyException。当前 yml 里配的是 65 个字符，
     * jjwt 会自动选用强度更高的 HS512。
     */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。
     *
     * @param userId   用户主键，放在标准声明 subject 里
     * @param username 用户名，放在自定义声明里（方便日志排查时一眼看出是谁）
     * @param role     0 普通用户 / 1 管理员，2.3 的过滤器用它判断能不能进管理端接口
     */
    public String createToken(Long userId, String username, Integer role) {
        Date now = new Date();
        // expire 是秒，getTime() 是毫秒，乘 1000 时写成 1000L 保证是 long 运算。
        // 如果写成 1000，遇到较大的 expire 会 int 溢出成负数，token 一签发就是过期的。
        Date expiration = new Date(now.getTime() + expire * 1000L);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key())
                .compact();
    }

    /**
     * 解析并校验 token。
     * <p>
     * 故意设计成"失败返回 null"而不是往外抛异常：
     * 2.3 的过滤器里只需要判断解析结果有没有值，不用写三个 catch。
     *
     * @return 校验通过返回 Claims；过期、签名不对、格式错误、传空都返回 null
     */
    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException 覆盖了三种典型情况：
            //   ExpiredJwtException    过期
            //   SignatureException     签名对不上（被改过，或用了别的密钥签的）
            //   MalformedJwtException  根本不是 JWT 格式的字符串
            // IllegalArgumentException 是传了空串或 null 时抛的
            log.debug("token 校验失败：{}", e.getMessage());
            return null;
        }
    }

    public Long getExpire() {
        return expire;
    }

}
