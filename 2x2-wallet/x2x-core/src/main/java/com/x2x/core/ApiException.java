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
    public static final String MSG_RATE_LIMITED = "Too many requests. Please wait and try again.";
    public static final String MSG_NETWORK = "Network unavailable. Please try again.";
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
            if (m != null && m.toLowerCase().contains("pin")) {
                return MSG_PIN;
            }
            cur = cur.getCause();
        }
        return MSG_NETWORK;
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
            return new ApiException(Kind.NETWORK, code, MSG_NETWORK);
        }
        if (code >= 400) {
            return new ApiException(Kind.INVALID_REQUEST, code, MSG_INVALID_REQUEST);
        }
        return new ApiException(Kind.UNKNOWN, code, MSG_NETWORK);
    }
}
