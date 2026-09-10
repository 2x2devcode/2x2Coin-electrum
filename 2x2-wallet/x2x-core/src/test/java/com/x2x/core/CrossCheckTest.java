package com.x2x.core;

import org.junit.Test;

/** Prints reference values used to cross-check the JS web wallet. */
public class CrossCheckTest {
    @Test
    public void printReferenceAddresses() {
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon "
                + "abandon abandon abandon about";
        Wallet w = Wallet.fromMnemonic(mnemonic, "", new ApiClient());
        System.out.println("[xcheck] mnemonic=" + mnemonic);
        System.out.println("[xcheck] m/44'/0'/0'/0/0 = " + w.key(0, 0).address);
        System.out.println("[xcheck] m/44'/0'/0'/0/1 = " + w.key(0, 1).address);
        System.out.println("[xcheck] m/44'/0'/0'/1/0 = " + w.key(1, 0).address);
    }
}
