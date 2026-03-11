package com.fintech.gateway.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantContext}.
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Set and get tenant ID")
    void shouldSetAndGetTenantId() {
        TenantContext.setTenantId("tenant-001");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-001");
    }

    @Test
    @DisplayName("Returns null when not set")
    void shouldReturnNullWhenNotSet() {
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("Clear removes tenant ID")
    void shouldClearTenantId() {
        TenantContext.setTenantId("tenant-001");
        TenantContext.clear();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    @DisplayName("Overwrite replaces previous value")
    void shouldOverwritePreviousValue() {
        TenantContext.setTenantId("tenant-001");
        TenantContext.setTenantId("tenant-002");
        assertThat(TenantContext.getTenantId()).isEqualTo("tenant-002");
    }
}

