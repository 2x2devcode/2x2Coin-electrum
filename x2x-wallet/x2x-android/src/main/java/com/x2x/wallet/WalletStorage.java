package com.x2x.wallet;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/** Encrypted persistence for mnemonic, PIN hash, and HD address indices. */
public final class WalletStorage {
    private static final String FILE = "x2x_secure_prefs";
    private static final String KEY_MNEMONIC = "mnemonic";
    private static final String KEY_PASSPHRASE = "bip39_passphrase";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_RECEIVE_INDEX = "receive_index";
    private static final String KEY_CHANGE_INDEX = "change_index";
    private static final String KEY_SEED_CONFIRMED = "seed_confirmed";

    private final SharedPreferences prefs;

    public WalletStorage(Context ctx) {
        try {
            MasterKey key = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            prefs = EncryptedSharedPreferences.create(
                    ctx, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            throw new RuntimeException("secure storage init failed", e);
        }
    }

    public boolean hasWallet() { return prefs.contains(KEY_MNEMONIC); }

    public void saveMnemonic(String mnemonic) {
        prefs.edit().putString(KEY_MNEMONIC, mnemonic).apply();
    }

    public String getMnemonic() { return prefs.getString(KEY_MNEMONIC, null); }

    /** Optional BIP39 passphrase (empty string when unused). */
    public void savePassphrase(String passphrase) {
        prefs.edit().putString(KEY_PASSPHRASE, passphrase == null ? "" : passphrase).apply();
    }

    public String getPassphrase() {
        String p = prefs.getString(KEY_PASSPHRASE, "");
        return p == null ? "" : p;
    }

    public boolean hasPin() { return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT); }

    public void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = toHex(salt);
        prefs.edit()
                .putString(KEY_PIN_SALT, saltHex)
                .putString(KEY_PIN_HASH, hashPin(pin, saltHex))
                .apply();
    }

    public boolean verifyPin(String pin) {
        String salt = prefs.getString(KEY_PIN_SALT, null);
        String expect = prefs.getString(KEY_PIN_HASH, null);
        if (salt == null || expect == null) return false;
        return MessageDigest.isEqual(
                expect.getBytes(StandardCharsets.UTF_8),
                hashPin(pin, salt).getBytes(StandardCharsets.UTF_8));
    }

    public int getReceiveIndex() { return prefs.getInt(KEY_RECEIVE_INDEX, 0); }

    public void setReceiveIndex(int index) {
        prefs.edit().putInt(KEY_RECEIVE_INDEX, Math.max(0, index)).apply();
    }

    public int getChangeIndex() { return prefs.getInt(KEY_CHANGE_INDEX, 0); }

    public void setChangeIndex(int index) {
        prefs.edit().putInt(KEY_CHANGE_INDEX, Math.max(0, index)).apply();
    }

    public boolean isSeedConfirmed() { return prefs.getBoolean(KEY_SEED_CONFIRMED, false); }

    public void setSeedConfirmed(boolean confirmed) {
        prefs.edit().putBoolean(KEY_SEED_CONFIRMED, confirmed).apply();
    }

    public void clear() { prefs.edit().clear().apply(); }

    private static String hashPin(String pin, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(saltHex.getBytes(StandardCharsets.UTF_8));
            md.update(pin.getBytes(StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
