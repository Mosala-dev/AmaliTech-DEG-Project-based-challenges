package com.finsafe.idempotency.service;

import com.finsafe.idempotency.exception.IdempotencyConflictException;
import com.finsafe.idempotency.model.IdempotencyRecord;
import com.finsafe.idempotency.model.PaymentRequest;
import com.finsafe.idempotency.model.PaymentResponse;
import com.finsafe.idempotency.model.StoredResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ConcurrentHashMap<String, IdempotencyRecord> store = new ConcurrentHashMap<>();
    private final PaymentProcessor processor;
    private final Duration ttl;

    public IdempotencyService(PaymentProcessor processor,
                              @Value("${idempotency.ttl-seconds:86400}") long ttlSeconds) {
        this.processor = processor;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** Result of handling a request: the stored response and whether it was replayed. */
    public record Outcome(StoredResponse response, boolean replayed) {
    }

    public Outcome handle(String key, PaymentRequest request) {
        String fingerprint = fingerprint(request);
        IdempotencyRecord mine = new IdempotencyRecord(fingerprint);

        // Atomic claim: exactly one thread wins for a given key, even under a race.
        IdempotencyRecord existing = store.putIfAbsent(key, mine);

        if (existing == null) {
            return new Outcome(process(key, mine, request), false);
        }

        // Key seen before: body must be identical.
        if (!existing.fingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException();
        }

        // Same key + same body: block until the first request finishes (instant if already done).
        return new Outcome(await(existing), true);
    }

    private StoredResponse process(String key, IdempotencyRecord record, PaymentRequest request) {
        try {
            PaymentResponse body = processor.charge(request);
            StoredResponse stored = new StoredResponse(201, body);
            record.result().complete(stored);
            return stored;
        } catch (RuntimeException e) {
            // Failed payments are not cached: free the key so the client can retry,
            // and wake any waiting duplicates with the same failure.
            store.remove(key, record);
            record.result().completeExceptionally(e);
            throw e;
        }
    }

    private StoredResponse await(IdempotencyRecord record) {
        try {
            return record.result().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for in-flight request", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("In-flight request failed", e.getCause());
        }
    }

    /** Developer's Choice: evict keys older than the TTL so memory cannot grow forever. */
    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval-ms:60000}")
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(ttl);
        int before = store.size();
        store.entrySet().removeIf(e ->
                e.getValue().result().isDone() && e.getValue().createdAt().isBefore(cutoff));
        int removed = before - store.size();
        if (removed > 0) {
            log.info("Evicted {} expired idempotency keys", removed);
        }
    }

    private static String fingerprint(PaymentRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(request.canonical().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
