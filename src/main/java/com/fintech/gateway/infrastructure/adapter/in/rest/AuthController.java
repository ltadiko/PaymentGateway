package com.fintech.gateway.infrastructure.adapter.in.rest;

import com.fintech.gateway.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Mock authentication controller for issuing JWT tokens.
 *
 * <p><strong>DEV-ONLY:</strong> In production, tokens would be issued by an
 * external Identity Provider (Keycloak, Auth0, etc.). This controller exists
 * solely to enable local testing without an external IdP dependency.
 *
 * <p>Usage:
 * <pre>{@code
 * POST /api/v1/auth/token
 * {
 *   "subject": "merchant-abc",
 *   "tenantId": "tenant-001",
 *   "roles": ["PAYMENT_SUBMIT", "PAYMENT_VIEW"]
 * }
 * }</pre>
 *
 * @see JwtTokenProvider
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "JWT token issuance (mock — dev only)")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Constructs the auth controller.
     *
     * @param jwtTokenProvider the JWT token provider
     */
    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Issues a new JWT token for the given credentials.
     *
     * <p>This endpoint is publicly accessible ({@code permitAll}) and does not
     * require an existing token. In production, this would be replaced by an
     * OAuth2 authorization flow.
     *
     * @param request the token request containing subject, tenantId, and roles
     * @return a JSON response with the JWT token and its expiration time
     */
    @PostMapping("/token")
    @SecurityRequirements // No auth required — public endpoint
    @Operation(
            summary = "Issue a JWT token",
            description = """
                    DEV-ONLY: Issues a mock JWT token. In production, tokens would come from an external IdP (Keycloak/Auth0).
                    
                    Use this token in the `Authorization: Bearer <token>` header for all other endpoints.
                    
                    **Available roles:** `PAYMENT_SUBMIT`, `PAYMENT_VIEW`"""
    )
    @ApiResponse(responseCode = "200", description = "Token issued successfully")
    public ResponseEntity<Map<String, Object>> issueToken(@RequestBody TokenRequest request) {
        log.info("Token issuance requested: subject={}, tenantId={}, roles={}",
                request.subject(), request.tenantId(), request.roles());

        String token = jwtTokenProvider.generateToken(
                request.subject(),
                request.tenantId(),
                request.roles()
        );

        log.info("Token issued successfully: subject={}, tenantId={}", request.subject(), request.tenantId());

        return ResponseEntity.ok(Map.of(
                "token", token,
                "expiresIn", 3600
        ));
    }

    /**
     * Request body for token issuance.
     *
     * @param subject  the token subject (e.g., merchant identifier)
     * @param tenantId the tenant identifier for multi-tenancy
     * @param roles    the list of roles to embed in the token
     */
    public record TokenRequest(
            String subject,
            String tenantId,
            List<String> roles
    ) {}
}

