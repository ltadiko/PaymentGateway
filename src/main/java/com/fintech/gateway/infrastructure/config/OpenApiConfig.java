package com.fintech.gateway.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 configuration for the Payment Gateway.
 *
 * <p>Configures Swagger UI ({@code /swagger-ui.html}) and the OpenAPI spec
 * ({@code /v3/api-docs}) with:
 * <ul>
 *   <li>API metadata (title, version, description)</li>
 *   <li>JWT Bearer token security scheme</li>
 *   <li>Logical grouping via tags</li>
 * </ul>
 *
 * <p>Swagger UI is publicly accessible (no JWT required) so that developers
 * can explore and test the API interactively.
 *
 * @see <a href="https://springdoc.org">springdoc-openapi documentation</a>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Configures the OpenAPI specification with metadata and security.
     *
     * @return the OpenAPI configuration
     */
    @Bean
    public OpenAPI paymentGatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Gateway API")
                        .version("1.0.0")
                        .description("""
                                Secure Event-Driven Payment Gateway — Core Backend Engine.
                                
                                ## Overview
                                High-throughput payment ingestion with asynchronous processing via Kafka.
                                Payments move through a fraud assessment and bank processing pipeline.
                                
                                ## Authentication
                                All endpoints (except `/api/v1/auth/token`) require a JWT Bearer token.
                                Use the **Auth** endpoint to obtain a token, then click **Authorize** above
                                and paste it.
                                
                                ## Test Hooks
                                | Amount | Fraud | Bank | Final Status |
                                |--------|-------|------|--------------|
                                | `11.11` | Approved | Always succeeds | `COMPLETED` |
                                | `99.99` | Approved | Always fails | `FAILED` |
                                | `≥ 10,000` | Rejected | Not called | `FRAUD_REJECTED` |
                                | Other | Approved | 70% success | `COMPLETED` or `FAILED` |
                                """)
                        .contact(new Contact()
                                .name("Payment Gateway Team"))
                        .license(new License()
                                .name("Internal")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token obtained from POST /api/v1/auth/token")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .tags(List.of(
                        new Tag().name("Auth").description("JWT token issuance (mock — dev only)"),
                        new Tag().name("Payments").description("Payment submission, status inquiry, and audit trail"),
                        new Tag().name("Fraud").description("Mock fraud assessment service (internal)")
                ));
    }
}

