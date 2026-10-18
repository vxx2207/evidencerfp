package com.certaaccess.evidencerfp.analysis.application;

public final class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super("Der Idempotency-Key wurde bereits für einen anderen Upload verwendet.");
    }
}
