package com.x2x.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted on-disk wallet storage for the desktop app ({@code ~/.2x2-wallet/wallet.dat}).
 * AES-256-GCM with a key derived from the user PIN via PBKDF2-HMAC-SHA256.
 */
public final class DesktopStorage {

    private static final int PBKDF2_ITERS = 120_000;
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int KEY_LEN_BITS = 256;

    private final Path dir;
    private final Path file;

    private String mnemonic;
    private String passphrase = "";
    private int receiveIndex;
    private int changeIndex;
    private byte[] pinSalt;
    private byte[] pinHash;

    public DesktopStorage() {
        this(Path.of(System.getProperty("user.home"), ".2x2-wallet"));
    }

    public DesktopStorage(Path dir) {
        this.dir = dir;
        this.file = dir.resolve("wallet.dat");
    }

    public boolean hasWallet() {
        return Files.isRegularFile(file);
    }

    public void createNew(String mnemonic, String passphrase, String pin) throws Exception {
        this.mnemonic = mnemonic;
        this.passphrase = passphrase == null ? "" : passphrase;
        this.receiveIndex = 0;
        this.changeIndex = 0;
        setPin(pin);
        save(pin);
    }

    public void unlock(String pin) throws Exception {
        if (!Files.isRegularFile(file)) throw new IOException("wallet file missing");
        byte[] raw = Files.readAllBytes(file);
        Properties meta = readMeta(raw);
        pinSalt = Base64.getDecoder().decode(meta.getProperty("pinSalt"));
        pinHash = Base64.getDecoder().decode(meta.getProperty("pinHash"));
        if (!verifyPin(pin)) throw new GeneralSecurityException("incorrect PIN");

        byte[] salt = Base64.getDecoder().decode(meta.getProperty("encSalt"));
        byte[] iv = Base64.getDecoder().decode(meta.getProperty("encIv"));
        byte[] cipherText = Base64.getDecoder().decode(meta.getProperty("payload"));
        byte[] plain = decrypt(deriveKey(pin, salt), iv, cipherText);
        Properties data = new Properties();
        data.load(new java.io.ByteArrayInputStream(plain));
        mnemonic = data.getProperty("mnemonic");
        passphrase = data.getProperty("passphrase", "");
        receiveIndex = Integer.parseInt(data.getProperty("receiveIndex", "0"));
        changeIndex = Integer.parseInt(data.getProperty("changeIndex", "0"));
    }

    public void save(String pin) throws Exception {
        Files.createDirectories(dir);
        if (pinSalt == null || pinHash == null) setPin(pin);

        Properties data = new Properties();
        data.setProperty("mnemonic", mnemonic);
        data.setProperty("passphrase", passphrase == null ? "" : passphrase);
        data.setProperty("receiveIndex", Integer.toString(receiveIndex));
        data.setProperty("changeIndex", Integer.toString(changeIndex));
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        data.store(bos, "2x2-wallet");
        byte[] plain = bos.toByteArray();

        byte[] encSalt = random(SALT_LEN);
        byte[] iv = random(IV_LEN);
        byte[] payload = encrypt(deriveKey(pin, encSalt), iv, plain);

        Properties meta = new Properties();
        meta.setProperty("version", "1");
        meta.setProperty("pinSalt", Base64.getEncoder().encodeToString(pinSalt));
        meta.setProperty("pinHash", Base64.getEncoder().encodeToString(pinHash));
        meta.setProperty("encSalt", Base64.getEncoder().encodeToString(encSalt));
        meta.setProperty("encIv", Base64.getEncoder().encodeToString(iv));
        meta.setProperty("payload", Base64.getEncoder().encodeToString(payload));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        meta.store(out, "2x2-wallet-encrypted");
        Files.write(file, out.toByteArray());
    }

    public void delete() throws IOException {
        Files.deleteIfExists(file);
        mnemonic = null;
        passphrase = "";
        receiveIndex = 0;
        changeIndex = 0;
        pinSalt = null;
        pinHash = null;
    }

    public String getMnemonic() { return mnemonic; }
    public String getPassphrase() { return passphrase == null ? "" : passphrase; }
    public int getReceiveIndex() { return receiveIndex; }
    public int getChangeIndex() { return changeIndex; }

    public void setReceiveIndex(int v) { receiveIndex = Math.max(0, v); }
    public void setChangeIndex(int v) { changeIndex = Math.max(0, v); }

    public boolean verifyPin(String pin) {
        if (pinSalt == null || pinHash == null) return false;
        return MessageDigest.isEqual(pinHash, hashPin(pin, pinSalt));
    }

    private void setPin(String pin) {
        if (pin == null || !pin.matches("\\d{6}")) {
            throw new IllegalArgumentException("PIN must be exactly 6 digits");
        }
        pinSalt = random(SALT_LEN);
        pinHash = hashPin(pin, pinSalt);
    }

    private static Properties readMeta(byte[] raw) throws IOException {
        Properties p = new Properties();
        p.load(new java.io.ByteArrayInputStream(raw));
        return p;
    }

    private static byte[] hashPin(String pin, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(pin.getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] deriveKey(String pin, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERS, KEY_LEN_BITS);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return key;
    }

    private static byte[] encrypt(byte[] key, byte[] iv, byte[] plain) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(plain);
    }

    private static byte[] decrypt(byte[] key, byte[] iv, byte[] cipherText)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c.doFinal(cipherText);
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        new SecureRandom().nextBytes(b);
        return b;
    }
}
