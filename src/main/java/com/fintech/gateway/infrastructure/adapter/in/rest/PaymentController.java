package com.fintech.gateway.infrastructure.adapter.in.rest;

import com.fintech.gateway.application.dto.AuditEntryResponse;
import com.fintech.gateway.application.dto.PaymentResponse;
import com.fintech.gateway.application.dto.SubmitPaymentCommand;
import com.fintech.gateway.domain.port.in.GetPaymentUseCase;
import com.fintech.gateway.domain.port.in.SubmitPaymentUseCase;
import com.fintech.gateway.infrastructure.adapter.in.rest.dto.SubmitPaymentRequest;
import com.fintech.gateway.infrastructure.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for payment operations.
 *
 * <p>Provides three endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/payments} — Submit a new payment (async, returns 202)</li>
 *   <li>{@code GET /api/v1/payments/{id}} — Query payment status</li>
 *   <li>{@code GET /api/v1/payments/{id}/audit} — Query payment audit trail</li>
 * </ul>
 *
 * <p><strong>Security:</strong>
 * <ul>
 *   <li>All endpoints require JWT authentication</li>
 *   <li>Submit requires {@code ROLE_PAYMENT_SUBMIT}</li>
 *   <li>Query endpoints require {@code ROLE_PAYMENT_VIEW}</li>
 *   <li>Tenant isolation is enforced via {@link TenantContext}</li>
 * </ul>
 *
 * <p><strong>Idempotency:</strong> The {@code Idempotency-Key} header is required
 * for payment submission. If a duplicate key is detected, the original response
 * is returned with HTTP 200 instead of 202.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment submission, status inquiry, and audit trail")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final SubmitPaymentUseCase submitPaymentUseCase;
    private final GetPaymentUseCase getPaymentUseCase;

    /**
     * Constructs the payment controller.
     *
     * @param submitPaymentUseCase use case for payment submission
     * @param getPaymentUseCase    use case for payment queries
     */
    public PaymentController(SubmitPaymentUseCase submitPaymentUseCase,
                             GetPaymentUseCase getPaymentUseCase) {
        this.submitPaymentUseCase = submitPaymentUseCase;
        this.getPaymentUseCase = getPaymentUseCase;
    }

    /**
     * Submits a new payment for asynchronous processing.
     *
     * <p>The payment is immediately acknowledged with HTTP 202 and a tracking ID.
     * Processing happens asynchronously via the event-driven pipeline
     * (fraud check → bank processing).
     *
     * <p>If the same {@code Idempotency-Key} is used again (by the same tenant),
     * the original response is returned with HTTP 200 — no duplicate payment
     * is created.
     *
     * @param request        the payment details (validated via Bean Validation)
     * @param idempotencyKey client-provided UUID for deduplication (required header)
     * @return 202 Accepted for new payments, 200 OK for idempotent duplicates
     */
    @PostMapping
    @PreAuthorize("hasRole('PAYMENT_SUBMIT')")
    @Operation(
            summary = "Submit a new payment",
            description = """
                    Accepts a payment initiation request and acknowledges immediately (async processing).
                    Returns a tracking ID. Processing happens asynchronously via Kafka pipeline
                    (fraud assessment → bank processing).
                    
                    **Idempotency:** If the same `Idempotency-Key` is reused by the same tenant,
                    the original response is returned with HTTP 200 instead of 202."""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Payment accepted for async processing"),
            @ApiResponse(responseCode = "200", description = "Idempotent duplicate — same payment returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request body (validation failure)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Token lacks PAYMENT_SUBMIT role")
    })
    public ResponseEntity<PaymentResponse> submitPayment(
            @Valid @RequestBody SubmitPaymentRequest request,
            @Parameter(description = "Client-provided UUID for idempotency", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String tenantId = TenantContext.getTenantId();
        log.info("Payment submission: tenantId={}, idempotencyKey={}", tenantId, idempotencyKey);

        SubmitPaymentCommand command = new SubmitPaymentCommand(
                tenantId,
                idempotencyKey,
                request.amount(),
                request.currency(),
                request.creditorAccount(),
                request.debtorAccount(),
                request.paymentMethod()
        );

        PaymentResponse response = submitPaymentUseCase.submit(command);

        if (response.isDuplicate()) {
            log.info("Idempotent duplicate returned: paymentId={}", response.paymentId());
            return ResponseEntity.ok(response);
        }

        log.info("Payment accepted: paymentId={}, tenantId={}", response.paymentId(), tenantId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Queries the current status of a payment.
     *
     * <p>Account numbers in the response are masked for PCI compliance.
     * If the payment belongs to a different tenant, a 404 is returned
     * (no information leakage about other tenants' payments).
     *
     * @param paymentId the unique payment identifier
     * @return 200 OK with payment status, or 404 if not found
     */
    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('PAYMENT_VIEW')")
    @Operation(
            summary = "Get payment status",
            description = "Returns the current status of a payment. Account numbers are masked for PCI compliance. Returns 404 if the payment belongs to another tenant."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Token lacks PAYMENT_VIEW role"),
            @ApiResponse(responseCode = "404", description = "Payment not found (or belongs to another tenant)")
    })
    public ResponseEntity<PaymentResponse> getPayment(
            @Parameter(description = "Payment UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID paymentId) {
        String tenantId = TenantContext.getTenantId();
        log.debug("Payment status query: paymentId={}, tenantId={}", paymentId, tenantId);

        PaymentResponse response = getPaymentUseCase.getPayment(paymentId, tenantId);
        return ResponseEntity.ok(response);
    }

    /**
     * Queries the audit trail for a payment.
     *
     * <p>Returns the complete chronological history of state transitions,
     * from initial submission to final resolution. Each entry records
     * the previous status, new status, event type, and timestamp.
     *
     * @param paymentId the unique payment identifier
     * @return 200 OK with ordered audit trail, or 404 if payment not found
     */
    @GetMapping("/{paymentId}/audit")
    @PreAuthorize("hasRole('PAYMENT_VIEW')")
    @Operation(
            summary = "Get payment audit trail",
            description = "Returns the complete immutable audit trail — every state transition from ingestion to final resolution, in chronological order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit trail returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Token lacks PAYMENT_VIEW role"),
            @ApiResponse(responseCode = "404", description = "Payment not found (or belongs to another tenant)")
    })
    public ResponseEntity<List<AuditEntryResponse>> getAuditTrail(
            @Parameter(description = "Payment UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID paymentId) {
        String tenantId = TenantContext.getTenantId();
        log.debug("Audit trail query: paymentId={}, tenantId={}", paymentId, tenantId);

        List<AuditEntryResponse> trail = getPaymentUseCase.getAuditTrail(paymentId, tenantId);
        return ResponseEntity.ok(trail);
    }
}

