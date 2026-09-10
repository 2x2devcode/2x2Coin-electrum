package com.x2x.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * REST client for the 2x2 wallet server.
 *
 * <p>Uses {@link HttpURLConnection} so the module stays pure-JVM (and Android-safe).
 * HTTPS calls use SPKI certificate pinning by default ({@link NetworkParameters#API_TLS_PINS}).
 */
public final class ApiClient {

    private final String apiBase;
    private final String explorerBase;
    private final List<String> apiBases;
    private final List<String> explorerBases;
    private int timeoutMs = 30_000;
    private SSLSocketFactory sslSocketFactory;
    private Set<String> apiPins = NetworkParameters.API_TLS_PINS;
    private Set<String> explorerPins = NetworkParameters.EXPLORER_TLS_PINS;
    private boolean pinningEnabled = true;

    public ApiClient() {
        this(NetworkParameters.OFFICIAL_API_BASE_URLS, NetworkParameters.EXPLORER_BASE_URLS);
    }

    public ApiClient(String apiBase, String explorerBase) {
        this(List.of(apiBase), List.of(explorerBase));
    }

    public ApiClient(List<String> apiBases, List<String> explorerBases) {
        if (apiBases == null || apiBases.isEmpty()) {
            throw new IllegalArgumentException("apiBases required");
        }
        if (explorerBases == null || explorerBases.isEmpty()) {
            throw new IllegalArgumentException("explorerBases required");
        }
        this.apiBases = List.copyOf(apiBases);
        this.explorerBases = List.copyOf(explorerBases);
        this.apiBase = this.apiBases.get(0);
        this.explorerBase = this.explorerBases.get(0);
        this.sslSocketFactory = TlsPinning.socketFactory(apiPins);
    }

    public void setTimeoutMs(int t) { this.timeoutMs = t; }

    /** Override the SSL socket factory (tests / custom trust). */
    public void setSslSocketFactory(SSLSocketFactory f) { this.sslSocketFactory = f; }

    /** Disable pinning (debug only). Prefer updating pins when certificates rotate. */
    public void setPinningEnabled(boolean enabled) {
        this.pinningEnabled = enabled;
        if (enabled) this.sslSocketFactory = TlsPinning.socketFactory(apiPins);
    }

    public String apiBase() { return apiBase; }

    // ---- responses ----

    public static final class Status {
        public boolean online; public String chain; public long blocks; public long headers;
        public double progress; public int peers;
    }

    public static final class Balance {
        public String address; public long confirmedSat; public boolean scanning;
        public long chainTip; public long indexedHeight;
    }

    public static final class Utxo {
        public String txid; public long vout; public long valueSat; public byte[] scriptPubKey;
        public long height;
    }

    public static final class BroadcastResult {
        public boolean ok; public String txid; public String error;
    }

    public static final class TxInfo {
        public String txid; public String amount; public long confirmations;
    }

    // ---- endpoints ----

    public Status getStatus() throws IOException {
        JsonObject o = getJson("/api/status").getAsJsonObject();
        Status s = new Status();
        s.online = optBool(o, "online", false);
        s.chain = optString(o, "chain", null);
        s.blocks = optLong(o, "blocks", 0);
        s.headers = optLong(o, "headers", 0);
        s.progress = o.has("progress") && !o.get("progress").isJsonNull()
                ? o.get("progress").getAsDouble() : 0;
        s.peers = (int) optLong(o, "peers", 0);
        return s;
    }

    /** Current fee in satoshis per kB (falls back to the network default). */
    public long getFeePerKb() throws IOException {
        JsonObject o = getJson("/api/fee").getAsJsonObject();
        if (o.has("feePerKbSatoshis") && !o.get("feePerKbSatoshis").isJsonNull())
            return o.get("feePerKbSatoshis").getAsLong();
        return NetworkParameters.DEFAULT_FEE_PER_KB;
    }

    public Balance getBalance(String address) throws IOException {
        JsonObject o = getJson("/api/address/" + address + "/balance").getAsJsonObject();
        Balance b = new Balance();
        b.address = optString(o, "address", address);
        b.confirmedSat = coinsToSat(optString(o, "balance", "0"));
        b.scanning = optBool(o, "scanning", false);
        b.chainTip = optLong(o, "chainTip", 0);
        b.indexedHeight = optLong(o, "indexedHeight", -1);
        return b;
    }

    public List<Utxo> getUtxos(String address) throws IOException {
        JsonObject o = getJson("/api/address/" + address + "/utxos").getAsJsonObject();
        List<Utxo> out = new ArrayList<>();
        JsonArray arr = o.has("utxos") ? o.getAsJsonArray("utxos") : new JsonArray();
        for (JsonElement e : arr) {
            JsonObject u = e.getAsJsonObject();
            Utxo x = new Utxo();
            x.txid = firstString(u, "txid", "tx_hash", "hash");
            x.vout = firstLong(u, "vout", "n", "outputIndex", "output_n");
            x.valueSat = parseValueToSat(u, "valueSat", "satoshis", "value", "amount");
            x.height = firstLong(u, "height", "block_height", "confirmations");
            String spk = firstStringOrNull(u, "scriptPubKey", "script", "scriptpubkey");
            // For P2PKH we can always reconstruct the script from our own address.
            x.scriptPubKey = spk != null ? Hex.decode(spk) : Address.p2pkhScript(address);
            out.add(x);
        }
        return out;
    }

    public List<TxInfo> getTxs(String address) throws IOException {
        JsonObject o = getJson("/api/address/" + address + "/txs").getAsJsonObject();
        List<TxInfo> out = new ArrayList<>();
        JsonArray arr = o.has("transactions") ? o.getAsJsonArray("transactions") : new JsonArray();
        for (JsonElement e : arr) {
            JsonObject t = e.getAsJsonObject();
            TxInfo x = new TxInfo();
            x.txid = firstStringOrNull(t, "txid", "tx_hash", "hash");
            x.amount = firstStringOrNull(t, "amount", "value");
            x.confirmations = firstLong(t, "confirmations", "height");
            out.add(x);
        }
        return out;
    }

    /** POST a signed raw transaction (hex). Field name confirmed against the live server: "rawTx". */
    public BroadcastResult broadcast(String rawTxHex) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("rawTx", rawTxHex);
        JsonElement resEl = postJson("/api/tx/broadcast", body.toString());
        JsonObject res = resEl.getAsJsonObject();
        BroadcastResult r = new BroadcastResult();
        if (res.has("error") && !res.get("error").isJsonNull()) {
            r.ok = false; r.error = res.get("error").getAsString();
        } else {
            r.ok = true;
            r.txid = firstStringOrNull(res, "txid", "txId", "result", "hash");
        }
        return r;
    }

    // ---- http ----

    private JsonElement getJson(String path) throws IOException {
        return JsonParser.parseString(requestWithFailover("GET", path, null));
    }

    private JsonElement postJson(String path, String body) throws IOException {
        return JsonParser.parseString(requestWithFailover("POST", path, body));
    }

    private String requestWithFailover(String method, String path, String body) throws IOException {
        IOException last = null;
        for (String base : apiBases) {
            try {
                return request(method, base + path, body, apiPins);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no API base URLs configured");
    }

    private String request(String method, String urlStr, String body, Set<String> pins)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        if (c instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) c;
            if (sslSocketFactory != null) https.setSSLSocketFactory(sslSocketFactory);
        }
        c.setRequestMethod(method);
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "x2x-wallet/1.2");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        if (pinningEnabled && c instanceof HttpsURLConnection) {
            TlsPinning.verifyConnection((HttpsURLConnection) c, pins);
        }
        InputStream is = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = is == null ? "" : readAll(is);
        // The server returns JSON error bodies (e.g. {"error":"...TX decode failed"}) with
        // 4xx/5xx codes. Surface those to the caller; only throw when there is no body.
        if (code >= 400 && text.isEmpty())
            throw new IOException("HTTP " + code + " with empty body from " + urlStr);
        return text;
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) != -1) b.write(buf, 0, n);
        return b.toString(StandardCharsets.UTF_8.name());
    }

    // ---- json helpers ----

    private static long coinsToSat(String decimal) {
        if (decimal == null || decimal.isEmpty()) return 0;
        return new BigDecimal(decimal).movePointRight(NetworkParameters.DECIMALS)
                .toBigInteger().longValueExact();
    }

    private static long parseValueToSat(JsonObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k) && !o.get(k).isJsonNull()) {
                String s = o.get(k).getAsString();
                // heuristic: a decimal point means coins, otherwise already satoshis
                return s.contains(".") ? coinsToSat(s) : new BigInteger(s).longValueExact();
            }
        }
        return 0;
    }

    private static boolean optBool(JsonObject o, String k, boolean d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : d;
    }
    private static long optLong(JsonObject o, String k, long d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : d;
    }
    private static String optString(JsonObject o, String k, String d) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d;
    }
    private static String firstString(JsonObject o, String... keys) {
        String v = firstStringOrNull(o, keys);
        if (v == null) throw new IllegalStateException("missing any of expected keys");
        return v;
    }
    private static String firstStringOrNull(JsonObject o, String... keys) {
        for (String k : keys) if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsString();
        return null;
    }
    private static long firstLong(JsonObject o, String... keys) {
        for (String k : keys) if (o.has(k) && !o.get(k).isJsonNull()) return o.get(k).getAsLong();
        return 0;
    }
}
