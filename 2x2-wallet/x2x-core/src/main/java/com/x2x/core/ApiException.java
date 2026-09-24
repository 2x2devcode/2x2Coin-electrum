package com.x2x.core;

import java.io.IOException;

/**
 * Typed HTTP/API failure with a stable, user-facing message (never raw JSON bodies).
 * All messages are English.
 */
public final class ApiException extends IOException {

    public enum Kind {
        INVALID_REQUEST,
        INVALID_TX,
        NOT_FOUND,
        RATE_LIMITED,
        NETWORK,
        PIN_MISMATCH,
        UNKNOWN
    }

    public static final String MSG_INVALID_TX = "Invalid transaction";
    public static final String MSG_TIME_TOO_NEW =
            "Transaction time rejected by the network (clock skew). Wait a minute and try Send again.";
    public static final String MSG_RATE_LIMITED = "Too many requests. Please wait and try again.";
    public static final String MSG_NETWORK = "Network unavailable. Please try again.";
    public static final String MSG_BROADCAST =
            "Could not broadcast: the network node is temporarily unavailable. Wait a minute and try again — your coins were not sent.";
    public static final String MSG_INSUFFICIENT =
            "Insufficient funds for this amount plus the network fee.";
    public static final String MSG_NOT_FOUND = "Resource not found";
    public static final String MSG_INVALID_REQUEST = "Invalid request";
    public static final String MSG_PIN =
            "TLS certificate pin mismatch. Update the wallet; on Windows delete %LOCALAPPDATA%\\2x2-Wallet and reopen.";

    private final Kind kind;
    private final int httpStatus;
    private final String userMessage;

    public ApiException(Kind kind, int httpStatus, String userMessage) {
        super(userMessage);
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.userMessage = userMessage != null ? userMessage : MSG_NETWORK;
    }

    public Kind getKind() { return kind; }
    public int getHttpStatus() { return httpStatus; }
    public String getUserMessage() { return userMessage; }

    /** Prefer this in UI Toasts/dialogs instead of {@code Throwable#getMessage()}. */
    public static String userMessage(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof ApiException) {
                return ((ApiException) cur).getUserMessage();
            }
            String m = cur.getMessage();
            if (m != null) {
                String lower = m.toLowerCase();
                if (lower.contains("pin")) return MSG_PIN;
                if (lower.contains("insufficient funds")) return MSG_INSUFFICIENT;
            }
            cur = cur.getCause();
        }
        return MSG_NETWORK;
    }

    /** Daemon / gateway said the tx is already known (mempool or chain) — treat as success. */
    public static boolean isAlreadyAccepted(String body) {
        if (body == null || body.isEmpty()) return false;
        String lower = body.toLowerCase();
        return lower.contains("already in block chain")
                || lower.contains("already in blockchain")
                || lower.contains("already have")
                || lower.contains("already in mempool")
                || lower.contains("txn-already-known")
                || lower.contains("txn-already-in-mempool")
                || lower.contains("error code: -27")
                || lower.contains("\"code\":-27");
    }

    /** Peercoin {@code time-too-new} / FutureDrift reject. */
    public static boolean isTimeTooNew(String bodyOrMessage) {
        if (bodyOrMessage == null || bodyOrMessage.isEmpty()) return false;
        String lower = bodyOrMessage.toLowerCase();
        return lower.contains("time-too-new")
                || lower.contains("error code: -26")
                || MSG_TIME_TOO_NEW.equals(bodyOrMessage);
    }

    public static boolean isTimeTooNew(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof ApiException) {
                ApiException ae = (ApiException) cur;
                if (isTimeTooNew(ae.getUserMessage())) return true;
            }
            if (isTimeTooNew(cur.getMessage())) return true;
            cur = cur.getCause();
        }
        return false;
    }

    /** Map a broadcast HTTP body to a typed exception (never leaks raw JSON to the UI). */
    public static ApiException fromBroadcastBody(int code, String body) {
        if (isTimeTooNew(body)) {
            return new ApiException(Kind.INVALID_TX, code, MSG_TIME_TOO_NEW);
        }
        if (code == 400 || code == 413) {
            return new ApiException(Kind.INVALID_TX, code, MSG_INVALID_TX);
        }
        return fromHttpStatus(code, true);
    }

    public static boolean isRateLimited(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof ApiException
                    && ((ApiException) cur).getKind() == Kind.RATE_LIMITED) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    public static ApiException fromHttpStatus(int code, boolean broadcast) {
        if (code == 429) {
            return new ApiException(Kind.RATE_LIMITED, code, MSG_RATE_LIMITED);
        }
        if (code == 404) {
            return new ApiException(Kind.NOT_FOUND, code, MSG_NOT_FOUND);
        }
        if (code == 400 || code == 413) {
            if (broadcast || code == 400) {
                return new ApiException(
                        broadcast ? Kind.INVALID_TX : Kind.INVALID_REQUEST,
                        code,
                        broadcast ? MSG_INVALID_TX : MSG_INVALID_REQUEST);
            }
        }
        if (code >= 500 || code == 408) {
            return new ApiException(Kind.NETWORK, code, broadcast ? MSG_BROADCAST : MSG_NETWORK);
        }
        if (code >= 400) {
            return new ApiException(Kind.INVALID_REQUEST, code, MSG_INVALID_REQUEST);
        }
        return new ApiException(Kind.UNKNOWN, code, MSG_NETWORK);
    }
}
