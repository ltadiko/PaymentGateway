package com.fintech.gateway.infrastructure.adapter.in.rest;

import com.fintech.gateway.domain.exception.DuplicatePaymentException;
import com.fintech.gateway.domain.exception.InvalidStateTransitionException;
import com.fintech.gateway.domain.exception.PaymentNotFoundException;
import com.fintech.gateway.domain.model.PaymentStatus;
import com.fintech.gateway.domain.model.TransitionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link GlobalExceptionHandler}.
 *
 * <p>Uses a {@link TestExceptionController} that deliberately throws each domain
 * exception, verifying that the global handler maps them to the correct HTTP status
 * and consistent {@code ApiErrorResponse} JSON structure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
class GlobalExceptionHandlerTest {

    public static final String BEARER = "Bearer ";
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fintech.gateway.infrastructure.security.JwtTokenProvider jwtTokenProvider;

    private String validToken() {
        return jwtTokenProvider.generateToken(
                "test-user", "tenant-001", List.of("PAYMENT_SUBMIT", "PAYMENT_VIEW"));
    }

    /**
     * Test controller that throws specific exceptions for each test case.
     */
    @TestConfiguration
    static class TestConfig {

        @RestController
        static class TestExceptionController {

            @GetMapping("/test/payment-not-found")
            public void throwPaymentNotFound() {
                throw new PaymentNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            }

            @GetMapping("/test/duplicate-payment")
            public void throwDuplicatePayment() {
                throw new DuplicatePaymentException("idem-key-123");
            }

            @GetMapping("/test/invalid-transition")
            public void throwInvalidTransition() {
                throw new InvalidStateTransitionException(
                        new PaymentStatus.Completed("BNK-REF"),
                        new TransitionEvent.StartFraudCheck()
                );
            }

            @GetMapping("/test/unexpected-error")
            public void throwUnexpected() {
                throw new RuntimeException("Something went terribly wrong");
            }
        }
    }

    @Test
    @DisplayName("PaymentNotFoundException → 404 with standard error format")
    void shouldReturn404ForPaymentNotFound() throws Exception {
        mockMvc.perform(get("/test/payment-not-found")
                        .header("Authorization", BEARER + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Payment not found: 00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.path").value("/test/payment-not-found"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("DuplicatePaymentException → 409 with standard error format")
    void shouldReturn409ForDuplicatePayment() throws Exception {
        mockMvc.perform(get("/test/duplicate-payment")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Duplicate payment submission for idempotency key: idem-key-123"))
                .andExpect(jsonPath("$.path").value("/test/duplicate-payment"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("InvalidStateTransitionException → 422 with standard error format")
    void shouldReturn422ForInvalidTransition() throws Exception {
        mockMvc.perform(get("/test/invalid-transition")
                        .header("Authorization", "Bearer " + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Content"))
                .andExpect(jsonPath("$.message").value("Invalid state transition: cannot apply StartFraudCheck to status COMPLETED"))
                .andExpect(jsonPath("$.path").value("/test/invalid-transition"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @DisplayName("Unexpected RuntimeException → 500 with generic message (no stack trace leak)")
    void shouldReturn500ForUnexpectedError() throws Exception {
        mockMvc.perform(get("/test/unexpected-error")
                        .header(HttpHeaders.AUTHORIZATION, BEARER + validToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                // Must NOT contain the actual exception message ("Something went terribly wrong")
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/test/unexpected-error"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}

