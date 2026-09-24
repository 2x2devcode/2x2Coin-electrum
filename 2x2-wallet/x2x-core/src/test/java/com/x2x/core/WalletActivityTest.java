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

/** Activity must include change-address UTXOs after the deposit is spent. */
public class WalletActivityTest {

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

    private void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    public void listActivityIncludesChangeUtxoWhenReceiveEmpty() throws Exception {
        Wallet probe = Wallet.fromMnemonic(
                "abandon abandon abandon abandon abandon abandon abandon abandon "
                        + "abandon abandon abandon about",
                "",
                new ApiClient(base, base));
        String receive0 = probe.receiveAddress(0);
        String change0 = probe.changeAddress(0);

        server.createContext("/", ex -> {
            hits.incrementAndGet();
            String path = ex.getRequestURI().getPath();
            if (path.contains(receive0) && path.endsWith("/txs")) {
                respond(ex, 200, "{\"transactions\":[]}");
            } else if (path.contains(receive0) && path.endsWith("/utxos")) {
                respond(ex, 200, "{\"utxos\":[]}");
            } else if (path.contains(change0) && path.endsWith("/txs")) {
                respond(ex, 200, "{\"transactions\":[]}");
            } else if (path.contains(change0) && path.endsWith("/utxos")) {
                respond(ex, 200,
                        "{\"utxos\":[{\"txid\":\"ccddee\",\"vout\":1,\"amountSatoshis\":699990000}]}");
            } else {
                respond(ex, 404, "{\"error\":\"no\"}");
            }
        });

        ApiClient api = new ApiClient(base, base);
        api.setPinningEnabled(false);
        api.setMinRequestIntervalMs(0);
        Wallet w = Wallet.fromMnemonic(
                "abandon abandon abandon abandon abandon abandon abandon abandon "
                        + "abandon abandon abandon about",
                "",
                api);

        List<ApiClient.TxInfo> act = w.listActivity(0, 0);
        assertEquals(1, act.size());
        assertEquals("ccddee", act.get(0).txid);
        assertEquals("6.99990000", act.get(0).amount);
        assertTrue(hits.get() >= 2);
    }
}
