package com.finsafe.idempotency.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key already used for a different request body.");
    }
}
