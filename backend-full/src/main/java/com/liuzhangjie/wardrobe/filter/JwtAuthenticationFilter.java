package com.liuzhangjie.wardrobe.filter;

import com.liuzhangjie.wardrobe.utils.JwtUtil;
import com.liuzhangjie.wardrobe.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：把请求头里的 token 翻译成"当前是哪个用户"。
 * <p>
 * 它只做一件事——<b>能认出来就认，认不出来就当作没登录，继续往下走</b>。
 * "这个接口到底要不要登录"由 SecurityConfig 判断，不在这一层拦，
 * 否则 /user/login 这种本身就不可能带 token 的接口会被自己拦住。
 * <p>
 * 为什么继承 OncePerRequestFilter 而不是直接 implements Filter：
 * 一次请求在 Servlet 内部可能经历 forward / include 多次重新进入过滤链，
 * 原生 Filter 会被重复执行。OncePerRequestFilter 用一个 request attribute 打标记，
 * 保证同一个请求里 doFilterInternal 只跑一次。
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Redis 白名单的 key 前缀。
     * 必须和 UserServiceImpl.login() 里写入时用的字符串<b>完全一致</b>，
     * 差一个字符就永远查不到，表现是所有接口都提示未登录。
     */
    private static final String TOKEN_KEY_PREFIX = "wardrobe:login:token:";

    private final JwtUtil jwtUtil;

    private final StringRedisTemplate stringRedisTemplate;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, StringRedisTemplate stringRedisTemplate) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            authenticate(request);
            filterChain.doFilter(request, response);
        } finally {
            // 必须在这里清。线程是复用的，不清理会把当前用户带给下一个请求，属于越权级 bug。
            // 放在 finally 是为了保证后面的 Controller 抛异常时也一定执行到。
            UserContext.remove();
        }
    }

    /**
     * 认人。三个关口任意一个不过，就什么都不塞，安静地让请求继续往下走。
     */
    private void authenticate(HttpServletRequest request) {
        // 第 1 关：请求头里有没有 token
        String token = resolveToken(request);
        if (token == null) {
            return;
        }

        // 第 2 关：签名对不对、有没有过期。parseToken 内部已经 catch 了所有异常，失败返回 null
        Claims claims = jwtUtil.parseToken(token);
        if (claims == null) {
            log.debug("token 校验失败，按未登录处理：{}", request.getRequestURI());
            return;
        }

        // 第 3 关：在不在 Redis 白名单里。
        // JWT 一旦签发就无法主动作废，用户点"退出登录"、管理员封号，
        // 都是靠删掉这条 Redis 记录来实现的，所以这里必须查。
        String cacheKey = TOKEN_KEY_PREFIX + token;
        String cachedUserId;
        try {
            cachedUserId = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            // Redis 挂了不应该连带整个系统 500，按未登录处理即可，但日志要留痕
            log.error("查询 token 白名单失败，本次请求按未登录处理", e);
            return;
        }
        if (cachedUserId == null) {
            log.debug("token 已不在白名单（已退出或已过期）：{}", request.getRequestURI());
            return;
        }

        // 三关全过，正式开始"认人"
        Long userId = Long.valueOf(claims.getSubject());
        Integer role = claims.get("role", Integer.class);

        // ① 给业务层用：Controller / Service 里 UserContext.getUserId() 直接取
        UserContext.set(userId);

        // ② 给 Spring Security 用：SecurityConfig 里的 .authenticated() 认的就是这里塞的认证对象。
        //    不塞这一段，authenticated() 永远判不过，所有接口都会 401。
        String authority = (role != null && role == 1) ? "ROLE_ADMIN" : "ROLE_USER";
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null,
                        List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 从 Authorization 头里剥出 token。
     * 前端发过来长这样：Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.xxxxx.yyyyy
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

}
