package com.certaaccess.evidencerfp.evidence;

/** Sicher klassifizierter technischer Fehler eines Embedding-Providers. */
public class EvidenceProviderException extends RuntimeException {
    private final String code;

    public EvidenceProviderException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
