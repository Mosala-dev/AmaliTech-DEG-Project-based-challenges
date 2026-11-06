package com.finsafe.idempotency.model;

public record PaymentResponse(String status, String transactionId, String amount, String currency) {
}
