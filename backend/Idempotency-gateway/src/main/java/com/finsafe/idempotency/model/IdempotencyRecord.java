package com.finsafe.idempotency.model;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * One entry per Idempotency-Key. The future is completed when the first request
 * finishes; concurrent duplicates simply wait on it.
 */
public class IdempotencyRecord {
    private final String fingerprint;
    private final CompletableFuture<StoredResponse> result = new CompletableFuture<>();
    private final Instant createdAt = Instant.now();

    public IdempotencyRecord(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String fingerprint() { return fingerprint; }
    public CompletableFuture<StoredResponse> result() { return result; }
    public Instant createdAt() { return createdAt; }
}
