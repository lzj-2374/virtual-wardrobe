package com.liuzhangjie.wardrobe.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liuzhangjie.wardrobe.common.Result;
import com.liuzhangjie.wardrobe.common.ResultCode;
import com.liuzhangjie.wardrobe.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security 的规则手册。
 * <p>
 * 引入 spring-boot-starter-security 之后，Spring 默认给出一套规则：
 * 所有路径都必须登录、用一个自动生成的密码走 HTTP Basic 认证。
 * 只要本类里定义了自己的 SecurityFilterChain，那套默认规则就会被整个替换掉。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * 密码加密工具。
     * <p>
     * 这个 Bean 严格说和"拦截"没关系，它只是 Spring Security 附带的 BCrypt 实现。
     * 写成 @Bean 是为了让容器管理它，这样 UserServiceImpl 里可以直接注入，
     * 而不是在业务代码里到处 new BCryptPasswordEncoder()。
     * <p>
     * 必须显式声明成 BCryptPasswordEncoder。不声明的话 Spring 用的是
     * DelegatingPasswordEncoder，encode 出来会带 {bcrypt} 前缀（68 位），
     * 和数据库里 password VARCHAR(100) 的预期、以及校验时的匹配方式都对不上。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 请求过滤链：决定"哪些路径不用查、哪些必须查"。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. 关掉 CSRF。
                //    Security 默认要求每个 POST 带上隐藏的 csrf token 才放行，
                //    前后端分离时前端根本不带，不关的话 POST /user/register 一律 403。
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 不建 session。
                //    用 JWT，凭证由客户端每次请求自带（Authorization 头），
                //    服务端不需要靠 session 记住"谁登录过"。
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. 放行规则。
                //    注意这里的路径<b>不含 context-path</b>：
                //    实际接口是 /api/user/login，但 context-path=/api 已经在进入过滤链之前被剥掉了。
                .authorizeHttpRequests(auth -> auth
                        // 浏览器跨域预检请求不带 Authorization 头，必须放行，否则前端一调就报跨域
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/user/login",          // 登录（本身就不可能带 token）
                                "/user/register",       // 注册
                                "/doc.html",            // Knife4j 文档页
                                "/webjars/**",          // 文档页的静态资源
                                "/v3/api-docs/**",      // OpenAPI 描述文件
                                "/swagger-ui/**",       // Swagger UI
                                "/health",              // 探活
                                "/error"                // 兜底：否则接口一旦抛异常，错误页本身会被拦成 401，看不到真实报错
                        ).permitAll()
                        // 管理端专用：必须写在 anyRequest() 上面。
                        // 规则从上往下匹配、先命中先赢，写在 anyRequest() 后面永远轮不到，等于白写。
                        // 用 hasRole("ADMIN") 而非 "ROLE_ADMIN"：这方法内部会自动补 ROLE_ 前缀，
                        // 而 JwtAuthenticationFilter 塞进来的权限串正是 "ROLE_ADMIN"，刚好对上。
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())

                // 4. 两个出口，缺一不可。
                //    未登录：默认返回 HTTP 401 且 body 为空，但前端 axios 拦截器读的是响应体里的 code，
                //            HTTP 状态一律当成功处理，结果"没登录"被当成"空数据"，永远不会跳登录页。
                //    没权限：默认返回 HTTP 403 且 body 为空，前端表现是"页面一片空白、没有任何提示"，
                //            比 401 还难查，所以同样要接管、同样吐 JSON。
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))

                // 5. 把自定义的 JWT 过滤器插到 Security 认证过滤器之前。
                //    它负责在鉴权规则执行前，先把 token 翻译成 SecurityContext 里的认证对象。
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 统一未登录出口：HTTP 状态码给 200，靠 body 里的 code=401 让前端跳登录页。
     */
    private void writeUnauthorized(HttpServletRequest request,
                                   HttpServletResponse response,
                                   AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage())));
    }

    /**
     * 统一"没权限"出口：登录了，但角色不够（普通用户调 /admin/**）。
     * <p>
     * 为什么不直接让它返回默认的 403：
     * 默认的 403 是 HTTP 403 + 空 body，前端拦截器只看 body 里的 code，
     * 拿到空 body 会当成"成功但没数据"，页面就这么白着，排查时完全看不出是被拦了。
     * 这里同样把 HTTP 状态压成 200，把真实原因放进 body 的 code=403。
     * <p>
     * 注意它和 authenticationEntryPoint 的分工：
     * 没带 token / token 失效 → 走 writeUnauthorized（401，前端跳登录页）
     * token 有效但角色不对    → 走 writeForbidden（403，前端提示"没有权限"，不该跳登录页）
     * 这两种情况混为一谈的话，普通用户点管理端入口会被反复踢回登录页，体验很怪。
     */
    private void writeForbidden(HttpServletRequest request,
                                HttpServletResponse response,
                                AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage())));
    }

}
