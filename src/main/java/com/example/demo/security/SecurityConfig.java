package com.example.demo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf()
                .disable()

                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                .and()

                .authorizeRequests()

                // 登录与居民注册公开
                .antMatchers(
                        "/api/auth/login",
                        "/api/auth/register"
                )
                .permitAll()

                // HTML本身必须允许浏览器打开，真正数据由API鉴权保护
                .antMatchers(
                        "/",
                        "/login.html",
                        "/index.html",
                        "/admin.html",
                        "/technical.html",
                        "/favicon.ico",
                        "/error"
                )
                .permitAll()

                // 公共静态资源
                .antMatchers(
                        "/css/**",
                        "/js/**",
                        "/images/**"
                )
                .permitAll()

                // 管理员专用接口
                .antMatchers("/api/admin/**")
                .hasRole("ADMIN")

                // 开发测试接口不允许普通居民调用
                .antMatchers("/api/test/**")
                .hasRole("ADMIN")

                // 其他数据接口必须登录
                .anyRequest()
                .authenticated()

                .and()

                .exceptionHandling()
                .authenticationEntryPoint(
                        (request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"未登录或登录已过期\"}"
                            );
                        }
                )
                .accessDeniedHandler(
                        (request, response, exception) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"没有权限执行该操作\"}"
                            );
                        }
                )

                .and()
                .formLogin()
                .disable()

                .httpBasic()
                .disable();

        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}
