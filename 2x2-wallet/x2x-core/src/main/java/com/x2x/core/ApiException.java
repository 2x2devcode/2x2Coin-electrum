package com.x2x.core;

import java.io.IOException;

/**
 * Typed HTTP/API failure with a stable, user-facing message (never raw JSON bodies).
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

    public static final String MSG_INVALID_TX = "Transação inválida";
    public static final String MSG_RATE_LIMITED = "Demasiados pedidos. Aguarde e tente de novo.";
    public static final String MSG_NETWORK = "Rede indisponível. Tente de novo.";
    public static final String MSG_NOT_FOUND = "Recurso não encontrado";
    public static final String MSG_INVALID_REQUEST = "Pedido inválido";
    public static final String MSG_PIN =
            "Falha de segurança TLS (pin). Atualize a carteira; no Windows apague %LOCALAPPDATA%\\2x2-Wallet e volte a abrir.";

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
