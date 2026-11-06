package com.finsafe.idempotency.model;

/** The exact status + body that was returned for the first request with a given key. */
public record StoredResponse(int httpStatus, PaymentResponse body) {
}
