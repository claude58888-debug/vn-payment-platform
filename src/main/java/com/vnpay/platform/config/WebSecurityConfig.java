package com.vnpay.platform.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public API endpoints - HMAC signed
                        .requestMatchers("/api/**").permitAll()
                        // Internal endpoints - should be network-restricted in production
                        .requestMatchers("/internal/**").permitAll()
                        // Health check
                        .requestMatchers("/actuator/**").permitAll()
                        // Login page and static resources
                        .requestMatchers("/login", "/static/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        // Dashboard requires ADMIN role
                        .requestMatchers("/dashboard/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/dashboard")) {
                                response.sendRedirect("/login");
                            } else {
                                response.setStatus(401);
                                response.setContentType("application/json");
                                response.getWriter().write("{\"code\":401,\"message\":\"Unauthorized\"}");
                            }
                        })
                );

        return http.build();
    }
}
