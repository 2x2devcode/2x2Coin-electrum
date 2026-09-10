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
    }

    @Test
    public void broadcastRejectsGarbageButReachesDaemon() throws Exception {
        assumeTrue("live disabled", live());
        ApiClient api = new ApiClient();
        ApiClient.BroadcastResult r = api.broadcast("00");
        System.out.println("[live] broadcast(garbage): ok=" + r.ok + " error=" + r.error);
        // The daemon must have parsed our request (field name correct) and rejected the tx.
        assertTrue(!r.ok && r.error != null && r.error.toLowerCase().contains("decode"));
    }
}
