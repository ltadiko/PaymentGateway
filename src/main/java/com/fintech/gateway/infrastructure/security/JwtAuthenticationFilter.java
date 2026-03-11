package com.fintech.gateway.infrastructure.security;

import com.fintech.gateway.application.dto.ApiErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that intercepts every HTTP request.
 *
 * <p>Extracts the bearer token from the {@code Authorization} header, validates
 * it using {@link JwtTokenProvider}, and populates the Spring Security context
 * with the authenticated principal and authorities.
 *
 * <p>Also sets the {@link TenantContext} thread-local for downstream tenant-scoped
 * operations. The tenant context is always cleared in a {@code finally} block
 * to prevent leakage across pooled threads.
 *
 * <p>If no token is present, the filter chain continues without authentication.
 * Spring Security will then reject the request if the endpoint requires auth.
 *
 * @see JwtTokenProvider
 * @see TenantContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the filter with the JWT token provider and object mapper.
     *
     * @param jwtTokenProvider the provider for token validation and claim extraction
     * @param objectMapper     Jackson object mapper for serialising error responses
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes each request: extracts JWT, validates, sets security context and tenant.
     *
     * @param request     the HTTP request
     * @param response    the HTTP response
     * @param filterChain the filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);

            if (token != null) {
                Claims claims = jwtTokenProvider.validateAndExtract(token);
                String subject = claims.getSubject();
                String tenantId = jwtTokenProvider.getTenantId(claims);
                List<String> roles = jwtTokenProvider.getRoles(claims);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
                TenantContext.setTenantId(tenantId);

                log.debug("Authenticated user '{}' for tenant '{}' with roles {}", subject, tenantId, roles);
            }

            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            ApiErrorResponse error = ApiErrorResponse.of(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized",
                    "Invalid or expired token",
                    request.getRequestURI()
            );
            response.getWriter().write(objectMapper.writeValueAsString(error));
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Extracts the bearer token from the Authorization header.
     *
     * @param request the HTTP request
     * @return the token string, or {@code null} if no bearer token is present
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

