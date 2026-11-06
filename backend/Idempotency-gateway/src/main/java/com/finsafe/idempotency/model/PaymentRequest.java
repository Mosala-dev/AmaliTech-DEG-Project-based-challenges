package com.finsafe.idempotency.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO code")
        String currency) {

    /**
     * Canonical form used for fingerprinting, so that "100", "100.00" and
     * different JSON whitespace/key order all count as the same request.
     */
    public String canonical() {
        return amount.stripTrailingZeros().toPlainString() + "|" + currency.toUpperCase();
    }
}
