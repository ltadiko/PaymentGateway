package com.fintech.gateway.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fintech.gateway.application.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring Security configuration for the Payment Gateway.
 *
 * <p>Key security decisions:
 * <ul>
 *   <li><strong>Stateless sessions:</strong> No server-side session; every request is
 *       authenticated via JWT bearer token.</li>
 *   <li><strong>CSRF disabled:</strong> Safe for stateless REST APIs — no cookies are used.</li>
 *   <li><strong>JWT filter:</strong> Inserted before {@code UsernamePasswordAuthenticationFilter}
 *       to authenticate every request via the {@code Authorization} header.</li>
 *   <li><strong>Method security:</strong> Enabled via {@code @EnableMethodSecurity} for
 *       fine-grained {@code @PreAuthorize} checks on controller methods.</li>
 * </ul>
 *
 * <p>Public endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/auth/token} — mock token issuer (dev-only)</li>
 *   <li>{@code GET /h2-console/**} — H2 database console (dev-only)</li>
 * </ul>
 *
 * @see JwtAuthenticationFilter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the security configuration.
     *
     * @param jwtAuthenticationFilter the JWT authentication filter
     * @param objectMapper            Jackson object mapper for serialising error responses
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * Configures the HTTP security filter chain.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        // Mock fraud endpoint (internal, called by Kafka consumer)
                        .requestMatchers("/api/v1/fraud/**").permitAll()
                        // Swagger UI & OpenAPI spec
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs").permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Return 401 (not 403) for unauthenticated requests
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            ApiErrorResponse error = ApiErrorResponse.of(
                                    HttpServletResponse.SC_UNAUTHORIZED,
                                    "Unauthorized",
                                    "Authentication required",
                                    request.getRequestURI()
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(error));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            ApiErrorResponse error = ApiErrorResponse.of(
                                    HttpServletResponse.SC_FORBIDDEN,
                                    "Forbidden",
                                    "Insufficient permissions",
                                    request.getRequestURI()
                            );
                            response.getWriter().write(objectMapper.writeValueAsString(error));
                        })
                )
                // Allow H2 console frames
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .build();
    }
}

