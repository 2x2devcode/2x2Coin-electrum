package com.x2x.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A 2x2coin transaction.
 *
 * <p>2x2 uses the Peercoin-style wire order with an extra {@code nTime} field:
 * <pre>nVersion(int32) | nTime(uint32) | vin[] | vout[] | nLockTime(uint32)</pre>
 * The {@code nTime} field participates in both the txid and the signature hash,
 * so a standard Bitcoin serializer would produce invalid signatures here.
 */
public final class Transaction {

    public static final long SEQUENCE_FINAL = 0xFFFFFFFFL;
    public static final int SIGHASH_ALL = 0x01;

    public int version = NetworkParameters.TX_CURRENT_VERSION;
    public long nTime;                 // unix seconds
    public long lockTime = 0;
    public final List<Input> inputs = new ArrayList<>();
    public final List<Output> outputs = new ArrayList<>();

    public Transaction() {
        this.nTime = System.currentTimeMillis() / 1000L;
    }

    // ---- model ----

    public static final class OutPoint {
        public final String txid;  // big-endian display hex
        public final long index;
        public OutPoint(String txid, long index) { this.txid = txid; this.index = index; }
    }

    public static final class Input {
        public final OutPoint outPoint;
        public byte[] scriptSig = new byte[0];
        public long sequence = SEQUENCE_FINAL;
        // signing context (not serialized):
        public byte[] connectedScript; // prev scriptPubKey
        public long value;             // satoshis
        public Input(OutPoint o) { this.outPoint = o; }
    }

    public static final class Output {
        public long value;             // satoshis
        public byte[] scriptPubKey;
        public Output(long value, byte[] scriptPubKey) {
            this.value = value; this.scriptPubKey = scriptPubKey;
        }
    }

    public Input addInput(String prevTxid, long prevIndex, byte[] prevScriptPubKey, long value) {
        Input in = new Input(new OutPoint(prevTxid, prevIndex));
        in.connectedScript = prevScriptPubKey;
        in.value = value;
        inputs.add(in);
        return in;
    }

    public Output addOutput(long value, byte[] scriptPubKey) {
        Output o = new Output(value, scriptPubKey);
        outputs.add(o);
        return o;
    }

    public Output addOutputToAddress(long value, String address) {
        return addOutput(value, Address.p2pkhScript(address));
    }

    // ---- serialization ----

    /** Full network serialization (with scriptSigs), used for broadcast + txid. */
    public byte[] serialize() {
        return serialize(-1, null);
    }

    /**
     * Serializes the transaction. When {@code sigIndex >= 0} this produces the legacy
     * signature preimage: every input's scriptSig is emptied except input {@code sigIndex},
     * whose scriptSig is replaced by {@code subScript} (the connected scriptPubKey).
     */
    private byte[] serialize(int sigIndex, byte[] subScript) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        writeInt32LE(o, version);
        writeUint32LE(o, nTime);                 // <-- 2x2 nTime
        writeVarInt(o, inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            Input in = inputs.get(i);
            o.write(reversed(Hex.decode(in.outPoint.txid)), 0, 32);
            writeUint32LE(o, in.outPoint.index);
            byte[] script;
            if (sigIndex < 0) script = in.scriptSig;
            else if (i == sigIndex) script = subScript;
            else script = new byte[0];
            writeVarInt(o, script.length);
            o.write(script, 0, script.length);
            writeUint32LE(o, in.sequence);
        }
        writeVarInt(o, outputs.size());
        for (Output out : outputs) {
            writeInt64LE(o, out.value);
            writeVarInt(o, out.scriptPubKey.length);
            o.write(out.scriptPubKey, 0, out.scriptPubKey.length);
        }
        writeUint32LE(o, lockTime);
        return o.toByteArray();
    }

    /** Transaction id: reverse(double-SHA256(serialize())), big-endian display hex. */
    public String txid() {
        return Hex.encode(reversed(Hashes.sha256d(serialize())));
    }

    /** Legacy sighash for input {@code index} over the connected scriptPubKey. */
    public byte[] sigHash(int index, byte[] subScript, int hashType) {
        byte[] pre = serialize(index, subScript);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(pre, 0, pre.length);
        writeUint32LE(o, hashType);              // nHashType as 4-byte LE
        return Hashes.sha256d(o.toByteArray());
    }

    // ---- signing ----

    /** Sign a single P2PKH input with the given key pair. */
    public void signInput(int index, byte[] privKey, byte[] pubKey) {
        Input in = inputs.get(index);
        if (in.connectedScript == null)
            throw new IllegalStateException("input " + index + " missing connected script");
        byte[] hash = sigHash(index, in.connectedScript, SIGHASH_ALL);
        byte[] der = Secp256k1.signDer(hash, Secp256k1.toBigInteger(privKey));
        byte[] sig = Arrays.copyOf(der, der.length + 1);
        sig[der.length] = (byte) SIGHASH_ALL;    // append sighash type
        // scriptSig = <push sig> <push pubkey>
        ByteArrayOutputStream ss = new ByteArrayOutputStream();
        pushData(ss, sig);
        pushData(ss, pubKey);
        in.scriptSig = ss.toByteArray();
    }

    // ---- low-level writers ----

    static void writeInt32LE(ByteArrayOutputStream o, long v) {
        o.write((int) (v & 0xff)); o.write((int) ((v >> 8) & 0xff));
        o.write((int) ((v >> 16) & 0xff)); o.write((int) ((v >> 24) & 0xff));
    }
    static void writeUint32LE(ByteArrayOutputStream o, long v) { writeInt32LE(o, v); }
    static void writeInt64LE(ByteArrayOutputStream o, long v) {
        for (int i = 0; i < 8; i++) { o.write((int) (v & 0xff)); v >>= 8; }
    }
    static void writeVarInt(ByteArrayOutputStream o, long v) {
        if (v < 0xfd) { o.write((int) v); }
        else if (v <= 0xffff) { o.write(0xfd); o.write((int) (v & 0xff)); o.write((int) ((v >> 8) & 0xff)); }
        else if (v <= 0xffffffffL) { o.write(0xfe); writeUint32LE(o, v); }
        else { o.write(0xff); writeInt64LE(o, v); }
    }
    static void pushData(ByteArrayOutputStream o, byte[] data) {
        int n = data.length;
        if (n < 0x4c) { o.write(n); }
        else if (n <= 0xff) { o.write(0x4c); o.write(n); }
        else { o.write(0x4d); o.write(n & 0xff); o.write((n >> 8) & 0xff); }
        o.write(data, 0, n);
    }
    static byte[] reversed(byte[] b) {
        byte[] r = new byte[b.length];
        for (int i = 0; i < b.length; i++) r[i] = b[b.length - 1 - i];
        return r;
    }

    public String toHex() { return Hex.encode(serialize()); }
}
