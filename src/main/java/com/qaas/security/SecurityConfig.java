package com.qaas.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)   // removes GET /login redirect
            .httpBasic(AbstractHttpConfigurer::disable)
            .cors(cors -> {})
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                .requestMatchers("/api/users/**").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**", "/api/pages/**", "/api/tests/**",
                        "/api/executions/**", "/api/bugs/**", "/api/reports/**",
                        "/api/ui-elements/**", "/api/screenshots/**",
                        "/api/api-endpoints/**", "/api/deep-findings/**",
                        "/api/recordings/**").hasAnyRole("OWNER", "TESTER", "VIEWER")
                .requestMatchers(HttpMethod.DELETE, "/api/recordings/**").hasAnyRole("OWNER", "TESTER")
                .requestMatchers(HttpMethod.POST, "/api/recordings/**").hasAnyRole("OWNER", "TESTER")
                .requestMatchers(HttpMethod.PATCH, "/api/bugs/**").hasAnyRole("OWNER", "TESTER")
                .requestMatchers(HttpMethod.GET, "/api/projects/**").hasAnyRole("OWNER", "TESTER", "VIEWER")
                .requestMatchers(HttpMethod.PUT, "/api/projects/**").hasAnyRole("OWNER", "TESTER")
                .requestMatchers("/api/projects/**").hasAnyRole("OWNER", "TESTER", "VIEWER")
                .requestMatchers("/api/analysis/**", "/api/reports/**").hasAnyRole("OWNER", "TESTER")
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // allowedOriginPatterns supports wildcards and works with allowCredentials(true)
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}