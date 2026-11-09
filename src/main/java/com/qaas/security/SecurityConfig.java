package com.qaas.security;

import com.qaas.config.AppProperties;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
@EnableConfigurationProperties(AppProperties.class)
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
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
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
                // Project-scoped resources authorize per project (ownership / membership) in
                // ProjectService and ProjectSettingsService, not by global role. A global role
                // matcher here would be both too weak (any VIEWER could reach another project's
                // data) and too strong (a project's own OWNER usually holds global role VIEWER,
                // since only the first registered user is a global OWNER — so a role matcher
                // locked legitimate owners out of their own settings).
                .requestMatchers("/api/projects/**").authenticated()
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
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        List<String> allowedOrigins = properties.cors() == null ? null : properties.cors().allowedOrigins();
        if (allowedOrigins == null || allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank()
                || origin.contains("*"))) {
            throw new IllegalStateException("qaas.cors.allowed-origins must contain exact, non-wildcard origins");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}