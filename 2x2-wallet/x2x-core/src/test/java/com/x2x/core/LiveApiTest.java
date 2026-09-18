package com.x2x.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.List;

import org.junit.Test;

/**
 * Hits the real 2x2 server. Skipped unless run with {@code -Dlive=true} so the normal
 * unit suite stays offline and deterministic.
 */
public class LiveApiTest {

    private boolean live() { return Boolean.parseBoolean(System.getProperty("live", "false")); }

    @Test
    public void statusFeeAndBalanceAgainstLiveServer() throws Exception {
        assumeTrue("live disabled", live());
        ApiClient api = new ApiClient();

        ApiClient.Status s = api.getStatus();
        System.out.println("[live] status: online=" + s.online + " chain=" + s.chain
                + " blocks=" + s.blocks + " peers=" + s.peers);
        assertTrue("server should be online", s.online);
        assertEquals("main", s.chain);
        assertTrue("chain should have blocks", s.blocks > 100000);

        long fee = api.getFeePerKb();
        System.out.println("[live] feePerKb=" + fee);
        assertTrue(fee > 0);

        String sample = "2NHBXKyRY4ZBvyfyuZ2fZvqaGyo89vMGFW";
        ApiClient.Balance b = api.getBalance(sample);
        System.out.println("[live] balance(" + sample + ")=" + b.confirmedSat
                + " sat scanning=" + b.scanning + " tip=" + b.chainTip);

        List<ApiClient.Utxo> utxos = api.getUtxos(sample);
        System.out.println("[live] utxos=" + utxos.size());

        // Funded mainnet deposit: /balance shows 10 while wallet used to sum UTXOs as 0
        // because the API field is amountSatoshis (not valueSat).
        String funded = "2aEv33T2jg7iczvGtoVvvX2ERz1ZJDk7m2";
        List<ApiClient.Utxo> fundedUtxos = api.getUtxos(funded);
        long sum = 0;
        for (ApiClient.Utxo u : fundedUtxos) sum += u.valueSat;
        System.out.println("[live] funded utxos=" + fundedUtxos.size() + " sumSat=" + sum);
        assertTrue("funded address should expose at least one UTXO", fundedUtxos.size() >= 1);
        assertTrue("amountSatoshis must be parsed into valueSat", sum > 0);
    }

    @Test
    public void broadcastRejectsGarbageButReachesDaemon() throws Exception {
        assumeTrue("live disabled", live());
        ApiClient api = new ApiClient();
        try {
            api.broadcast("00");
            org.junit.Assert.fail("garbage broadcast should fail");
        } catch (ApiException e) {
            System.out.println("[live] broadcast(garbage): kind=" + e.getKind()
                    + " status=" + e.getHttpStatus() + " msg=" + e.getUserMessage());
            // Prefer INVALID_TX (400). If the daemon/upstream is down the API may
            // return 502 — still a typed failure, never a silent success.
            assertTrue(e.getKind() == ApiException.Kind.INVALID_TX
                    || e.getKind() == ApiException.Kind.NETWORK);
            assertTrue(!e.getUserMessage().contains("{"));
        }
    }
}
