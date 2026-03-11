package com.fintech.gateway.infrastructure.adapter.out.fraud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link MockFraudController}.
 *
 * <p>Verifies the in-app fraud mock endpoint follows the OpenAPI contract
 * and returns deterministic results based on payment amount.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
class MockFraudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Low amount (< 5000) → approved with score 15")
    void shouldApproveLowAmount() throws Exception {
        mockMvc.perform(post("/api/v1/fraud/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
                                  "amount": 100.00,
                                  "currency": "USD",
                                  "merchantId": "tenant-001",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.fraudScore").value(15))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.paymentId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.assessedAt").exists());
    }

    @Test
    @DisplayName("Medium amount (5000-10000) → approved with score 55")
    void shouldApproveMediumAmountWithHigherScore() throws Exception {
        mockMvc.perform(post("/api/v1/fraud/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentId": "550e8400-e29b-41d4-a716-446655440001",
                                  "amount": 6000.00,
                                  "currency": "EUR",
                                  "merchantId": "tenant-002",
                                  "paymentMethod": "CARD"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.fraudScore").value(55))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @DisplayName("High amount (>= 10000) → rejected with score 85")
    void shouldRejectHighAmount() throws Exception {
        mockMvc.perform(post("/api/v1/fraud/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentId": "550e8400-e29b-41d4-a716-446655440002",
                                  "amount": 15000.00,
                                  "currency": "GBP",
                                  "merchantId": "tenant-003",
                                  "paymentMethod": "WALLET"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.fraudScore").value(85))
                .andExpect(jsonPath("$.reason").value("High risk transaction amount"));
    }

    @Test
    @DisplayName("Boundary: exactly 10000 → rejected")
    void shouldRejectExactThreshold() throws Exception {
        mockMvc.perform(post("/api/v1/fraud/assess")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentId": "550e8400-e29b-41d4-a716-446655440003",
                                  "amount": 10000.00,
                                  "currency": "USD",
                                  "merchantId": "tenant-001",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.fraudScore").value(85));
    }
}

