package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * HTTP status / retry policy for {@link ApiClient} (no Android SDK, no OkHttp).
 */
public class ApiClientRetryTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger hits = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        hits.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    private ApiClient client() {
        ApiClient c = new ApiClient(base, base);
        c.setPinningEnabled(false);
        c.setRateLimitBackoffMs(0);
        c.setShortRetryBackoffMs(0);
        c.setTimeoutMs(3_000);
        return c;
    }

    private void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    public void broadcast400DoesNotRetry() throws Exception {
        server.createContext("/api/tx/broadcast", ex -> {
            hits.incrementAndGet();
            respond(ex, 400, "{\"error\":\"TX decode failed\"}");
        });
        ApiClient api = client();
        try {
            api.broadcast("00");
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.INVALID_TX, e.getKind());
            assertEquals(ApiException.MSG_INVALID_TX, e.getUserMessage());
            assertEquals(400, e.getHttpStatus());
        }
        assertEquals(1, hits.get());
        assertEquals(1, api.getRequestCount());
    }

    @Test
    public void broadcast429RetriesOnceThenFails() throws Exception {
        server.createContext("/api/tx/broadcast", ex -> {
            hits.incrementAndGet();
            respond(ex, 429, "{\"error\":\"rate limited\"}");
        });
        ApiClient api = client();
        try {
            api.broadcast("deadbeef");
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.RATE_LIMITED, e.getKind());
            assertEquals(ApiException.MSG_RATE_LIMITED, e.getUserMessage());
        }
        // initial + 1 rate-limit retry
        assertEquals(2, hits.get());
        assertEquals(2, api.getRequestCount());
    }

    @Test
    public void getStatus502RetriesThenSucceeds() throws Exception {
        server.createContext("/api/status", ex -> {
            int n = hits.incrementAndGet();
            if (n < 3) {
                respond(ex, 502, "{\"error\":\"upstream unavailable\"}");
            } else {
                respond(ex, 200, "{\"online\":true,\"chain\":\"main\",\"blocks\":1,\"headers\":1,\"peers\":1}");
            }
        });
        ApiClient api = client();
        ApiClient.Status s = api.getStatus();
        assertTrue(s.online);
        assertEquals(3, hits.get());
    }

    @Test
    public void getStatus502ExhaustsWithFriendlyNetworkMessage() throws Exception {
        server.createContext("/api/status", ex -> {
            hits.incrementAndGet();
            respond(ex, 502, "{\"error\":\"upstream unavailable\"}");
        });
        ApiClient api = client();
        try {
            api.getStatus();
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.NETWORK, e.getKind());
            assertEquals(ApiException.MSG_NETWORK, e.getUserMessage());
            // Must not leak JSON body
            assertTrue(!e.getUserMessage().contains("upstream"));
            assertTrue(!e.getUserMessage().contains("{"));
        }
        assertEquals(3, hits.get());
    }

    @Test
    public void broadcast404DoesNotRetry() throws Exception {
        server.createContext("/api/tx/broadcast", ex -> {
            hits.incrementAndGet();
            respond(ex, 404, "{\"error\":\"missing\"}");
        });
        ApiClient api = client();
        try {
            api.broadcast("00");
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.NOT_FOUND, e.getKind());
        }
        assertEquals(1, hits.get());
    }

    @Test
    public void userMessageHelperNeverReturnsJson() {
        ApiException e = new ApiException(ApiException.Kind.NETWORK, 502, ApiException.MSG_NETWORK);
        assertEquals(ApiException.MSG_NETWORK, ApiException.userMessage(e));
        assertEquals(ApiException.MSG_NETWORK, ApiException.userMessage(new IOException("{\"error\":\"x\"}")));
    }
}
