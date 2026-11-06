package com.finsafe.idempotency.service;

import com.finsafe.idempotency.model.PaymentRequest;
import com.finsafe.idempotency.model.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Simulates a slow downstream payment provider. */
@Service
public class PaymentProcessor {

    private final long delayMs;

    public PaymentProcessor(@Value("${payment.processing-delay-ms:2000}") long delayMs) {
        this.delayMs = delayMs;
    }

    public PaymentResponse charge(PaymentRequest request) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment interrupted", e);
        }
        String amount = request.amount().stripTrailingZeros().toPlainString();
        String currency = request.currency().toUpperCase();
        return new PaymentResponse(
                "Charged " + amount + " " + currency,
                UUID.randomUUID().toString(),
                amount,
                currency);
    }
}
