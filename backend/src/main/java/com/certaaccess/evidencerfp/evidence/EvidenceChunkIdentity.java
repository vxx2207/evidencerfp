package com.certaaccess.evidencerfp.evidence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Deterministische IDs und Inhalts-Hashes für reproduzierbare Retrieval-Läufe. */
public final class EvidenceChunkIdentity {
    private EvidenceChunkIdentity() { }

    public static UUID stableId(String documentSha256, String locator, String content) {
        byte[] digest = digest(normalize(documentSha256) + "\0" + normalize(locator) + "\0" + normalize(content));
        // RFC-4122-kompatible, aus SHA-256 abgeleitete UUID (Version 5 / Variant 2).
        digest[6] = (byte) ((digest[6] & 0x0f) | 0x50);
        digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
        ByteBuffer buffer = ByteBuffer.wrap(digest);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static String sha256(String content) {
        return HexFormat.of().formatHex(digest(normalize(content)));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist nicht verfügbar.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }
}
