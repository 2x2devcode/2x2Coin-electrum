package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Ensures spendable UTXO collection does not storm the API. */
public class WalletUtxoScanTest {

    private HttpServer server;
    private String base;
    private final AtomicInteger utxoHits = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        utxoHits.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
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
    public void collectSpendableStopsOnceNeedCovered() throws Exception {
        // Any address path: first UTXO response funds the wallet; further probes should be few.
        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            if (path.endsWith("/utxos")) {
                int n = utxoHits.incrementAndGet();
                if (n == 1) {
                    respond(ex, 200, "{\"utxos\":[{\"txid\":\"aa\",\"vout\":0,\"amountSatoshis\":500000000}]}");
                } else {
                    respond(ex, 200, "{\"utxos\":[]}");
                }
            } else if (path.endsWith("/fee")) {
                respond(ex, 200, "{\"feePerKbSatoshis\":10000}");
            } else {
                respond(ex, 404, "{\"error\":\"no\"}");
            }
        });

        ApiClient api = new ApiClient(base, base);
        api.setPinningEnabled(false);
        api.setMinRequestIntervalMs(0);
        api.setRateLimitBackoffMs(0);
        Wallet w = Wallet.fromMnemonic(
                "abandon abandon abandon abandon abandon abandon abandon abandon "
                        + "abandon abandon abandon about",
                "",
                api);

        List<TxBuilder.Spendable> out = w.collectSpendable(0, 0, 100_000L);
        assertEquals(1, out.size());
        assertEquals(500_000_000L, out.get(0).valueSat);
        // Known tip + small gap on receive, then may touch change — but must stay << old 40+.
        assertTrue("utxo probes=" + utxoHits.get(), utxoHits.get() <= 8);
    }
}
