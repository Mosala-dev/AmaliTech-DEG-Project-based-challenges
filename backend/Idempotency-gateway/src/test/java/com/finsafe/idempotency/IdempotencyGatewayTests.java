package com.finsafe.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "payment.processing-delay-ms=500")
class IdempotencyGatewayTests {

    @Autowired
    MockMvc mvc;

    private MvcResult pay(String key, String body) throws Exception {
        var req = post("/process-payment").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) req.header("Idempotency-Key", key);
        return mvc.perform(req).andReturn();
    }

    @Test
    void firstRequestIsProcessed() throws Exception {
        MvcResult r = pay(UUID.randomUUID().toString(), "{\"amount\":100,\"currency\":\"GHS\"}");
        assertEquals(201, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("Charged 100 GHS"));
        assertNull(r.getResponse().getHeader("X-Cache-Hit"));
    }

    @Test
    void duplicateIsReplayedInstantlyWithSameResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"amount\":100,\"currency\":\"GHS\"}";
        MvcResult first = pay(key, body);

        long start = System.currentTimeMillis();
        MvcResult second = pay(key, body);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 200, "replay should not re-run processing, took " + elapsed + "ms");
        assertEquals(first.getResponse().getStatus(), second.getResponse().getStatus());
        assertEquals(first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
        assertEquals("true", second.getResponse().getHeader("X-Cache-Hit"));
    }

    @Test
    void sameKeyDifferentBodyIsRejected() throws Exception {
        String key = UUID.randomUUID().toString();
        pay(key, "{\"amount\":100,\"currency\":\"GHS\"}");
        MvcResult r = pay(key, "{\"amount\":500,\"currency\":\"GHS\"}");
        assertEquals(422, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString()
                .contains("Idempotency key already used for a different request body."));
    }

    @Test
    void missingKeyIsBadRequest() throws Exception {
        assertEquals(400, pay(null, "{\"amount\":100,\"currency\":\"GHS\"}").getResponse().getStatus());
    }

    @Test
    void invalidBodyIsBadRequest() throws Exception {
        assertEquals(400, pay("k-" + UUID.randomUUID(), "{\"amount\":-5,\"currency\":\"GHS\"}").getResponse().getStatus());
    }

    @Test
    void concurrentDuplicatesWaitAndProcessOnlyOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"amount\":250,\"currency\":\"USD\"}";
        int n = 5;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<MvcResult>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return pay(key, body);
            }));
        }
        go.countDown();

        String firstBody = null;
        int replays = 0;
        for (Future<MvcResult> f : futures) {
            MvcResult r = f.get(10, TimeUnit.SECONDS);
            assertEquals(201, r.getResponse().getStatus());
            String b = r.getResponse().getContentAsString();
            if (firstBody == null) firstBody = b;
            assertEquals(firstBody, b, "all callers must see the same transaction");
            if ("true".equals(r.getResponse().getHeader("X-Cache-Hit"))) replays++;
        }
        pool.shutdown();
        assertEquals(n - 1, replays, "exactly one request should have actually processed");
    }
}
