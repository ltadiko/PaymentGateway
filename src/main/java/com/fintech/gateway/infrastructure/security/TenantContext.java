package com.fintech.gateway.infrastructure.security;

/**
 * Thread-local holder for the current tenant's identifier.
 *
 * <p>Set by {@link JwtAuthenticationFilter} after JWT validation, and cleared
 * after request processing completes. This enables tenant-scoped operations
 * throughout the request lifecycle without passing the tenant ID explicitly.
 *
 * <p><strong>Important:</strong> {@link #clear()} must be called in a
 * {@code finally} block after every request to prevent tenant ID leakage
 * across pooled threads.
 *
 * <p>Usage in service layer:
 * <pre>{@code
 *   String tenantId = TenantContext.getTenantId();
 *   paymentRepository.findByIdAndTenantId(id, tenantId);
 * }</pre>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — no instantiation
    }

    /**
     * Sets the tenant ID for the current thread/request.
     *
     * @param tenantId the tenant identifier from the JWT token
     */
    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Retrieves the tenant ID for the current thread/request.
     *
     * @return the current tenant ID, or {@code null} if not set
     */
    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    /**
     * Clears the tenant ID from the current thread.
     *
     * <p>Must be called in a {@code finally} block to prevent tenant leakage
     * across pooled servlet threads.
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

