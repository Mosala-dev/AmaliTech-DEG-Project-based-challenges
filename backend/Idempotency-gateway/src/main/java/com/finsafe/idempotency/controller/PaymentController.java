package com.finsafe.idempotency.controller;

import com.finsafe.idempotency.model.PaymentRequest;
import com.finsafe.idempotency.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PaymentController {

    private final IdempotencyService idempotencyService;

    public PaymentController(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/process-payment")
    public ResponseEntity<?> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        String key = idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 255) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Idempotency-Key must be 1-255 non-blank characters."));
        }

        IdempotencyService.Outcome outcome = idempotencyService.handle(key, request);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(outcome.response().httpStatus());
        if (outcome.replayed()) {
            builder.header("X-Cache-Hit", "true");
        }
        return builder.body(outcome.response().body());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
