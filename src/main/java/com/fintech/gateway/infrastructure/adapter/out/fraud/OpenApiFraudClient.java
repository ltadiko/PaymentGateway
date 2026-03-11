package com.fintech.gateway.infrastructure.adapter.out.fraud;

import com.fintech.gateway.domain.port.out.FraudAssessmentPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * HTTP-based implementation of {@link FraudAssessmentPort} using the OpenAPI specification.
 *
 * <p>Calls the fraud assessment service via REST, following the contract defined in
 * {@code src/main/resources/openapi/fraud-assessment-api.yaml}. In this assignment,
 * the endpoint is mocked by {@link MockFraudController} running inside the same
 * application ({@code fraud.api.base-url = http://localhost:${server.port}}).
 *
 * <p>In production, this client would point to a separate microservice URL
 * and include circuit breaker, timeout, and retry policies.
 *
 * <p>This adapter is marked {@code @Primary} to take precedence over the
 * simple {@link com.fintech.gateway.infrastructure.adapter.out.mock.MockFraudAssessmentAdapter}
 * which doesn't exercise the HTTP contract.
 *
 * <p><strong>Note on URL resolution:</strong> The base URL is resolved lazily
 * using {@link Environment} to support {@code @SpringBootTest(RANDOM_PORT)} where
 * {@code local.server.port} is only available after the embedded server starts.
 *
 * @see MockFraudController
 * @see FraudAssessmentPort
 */
@Component
@Primary
public class OpenApiFraudClient implements FraudAssessmentPort {

    private static final Logger log = LoggerFactory.getLogger(OpenApiFraudClient.class);

    private final Environment environment;
    private final String configuredBaseUrl;
    private volatile RestClient restClient;

    /**
     * Constructs the OpenAPI fraud client.
     *
     * @param baseUrl     the configured base URL of the fraud assessment service
     * @param environment the Spring environment for resolving dynamic properties
     */
    public OpenApiFraudClient(
            @Value("${app.fraud.api.base-url:http://localhost:8080}") String baseUrl,
            Environment environment) {
        this.configuredBaseUrl = baseUrl;
        this.environment = environment;
        log.info("OpenAPI Fraud Client configured: baseUrl={}", baseUrl);
    }

    /**
     * Lazily initialises the RestClient, resolving the local server port at call time.
     *
     * <p>This is needed for {@code @SpringBootTest(RANDOM_PORT)} where the port
     * is assigned after bean creation.
     *
     * @return the RestClient pointed at the correct URL
     */
    private RestClient getRestClient() {
        if (restClient == null) {
            synchronized (this) {
                if (restClient == null) {
                    String resolvedUrl = resolveBaseUrl();
                    restClient = RestClient.builder()
                            .baseUrl(resolvedUrl)
                            .build();
                    log.info("OpenAPI Fraud Client RestClient created: resolvedUrl={}", resolvedUrl);
                }
            }
        }
        return restClient;
    }

    /**
     * Resolves the base URL, substituting the dynamic local.server.port if available.
     */
    private String resolveBaseUrl() {
        String localPort = environment.getProperty("local.server.port");
        if (localPort != null && configuredBaseUrl.contains(":8080")) {
            return configuredBaseUrl.replace(":8080", ":" + localPort);
        }
        return configuredBaseUrl;
    }

    /**
     * Assesses a payment for fraud risk by calling the external service via HTTP.
     *
     * @param request the assessment request containing payment details
     * @return the assessment result with score and decision
     * @throws RuntimeException if the fraud service is unreachable or returns an error
     */
    @Override
    public FraudAssessmentResult assess(FraudAssessmentRequest request) {
        log.info("Calling fraud API: paymentId={}, amount={} {}",
                request.paymentId(), request.amount(), request.currency());

        MockFraudController.FraudApiRequest apiRequest = new MockFraudController.FraudApiRequest(
                request.paymentId(),
                request.amount(),
                request.currency(),
                request.merchantId(),
                request.paymentMethod()
        );

        MockFraudController.FraudApiResponse apiResponse = getRestClient()
                .post()
                .uri("/api/v1/fraud/assess")
                .contentType(MediaType.APPLICATION_JSON)
                .body(apiRequest)
                .retrieve()
                .body(MockFraudController.FraudApiResponse.class);

        if (apiResponse == null) {
            throw new RuntimeException("Fraud API returned null response for paymentId=" + request.paymentId());
        }

        log.info("Fraud API responded: paymentId={}, approved={}, score={}",
                apiResponse.paymentId(), apiResponse.approved(), apiResponse.fraudScore());

        return new FraudAssessmentResult(
                apiResponse.paymentId(),
                apiResponse.approved(),
                apiResponse.fraudScore(),
                apiResponse.reason(),
                apiResponse.assessedAt() != null ? apiResponse.assessedAt() : Instant.now()
        );
    }
}
