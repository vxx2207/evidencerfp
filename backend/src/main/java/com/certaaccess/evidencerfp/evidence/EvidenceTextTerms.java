package com.certaaccess.evidencerfp.evidence;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kleine, deterministische Normalisierung für den deutschen Offline-Retriever. */
public final class EvidenceTextTerms {
    private static final Set<String> STOP_WORDS = Set.of(
            "aber", "alle", "auch", "dass", "diese", "dieser", "dieses", "eine", "einer", "einem",
            "einen", "eines", "gibt", "haben", "ihre", "kann", "können", "oder", "sind", "über",
            "unter", "wird", "werden", "wurde", "welche", "welcher", "welches", "wieviel", "durch",
            "für", "with", "from", "does", "this", "that", "what", "when", "where", "your",
            "system", "lösung", "plattform", "certaaccess", "bereits",
            "aktiviert", "produktive", "produktiv", "unterstützt", "unterstützen");
    private static final String[] SUFFIXES = {"innen", "ern", "em", "en", "er", "es", "e", "n", "s"};
    private static final Set<String> SECURITY_SHORT_TERMS = Set.of(
            "iam", "jit", "mfa", "rpo", "rto", "sla", "sso", "tls");
    private static final Set<String> INTENT_TERMS = Set.of(
            "aufbewahrung", "export", "genehmigung", "jit", "kryptoschutz", "passwort",
            "recovery", "rotation", "verfügbarkeit");
    private static final Pattern VERSION = Pattern.compile(
            "(?i)\\bversion\\s*(\\d+)[.,](\\d+)\\b");
    private static final Pattern QUANTITY_WITH_UNIT = Pattern.compile(
            "(?i)\\b(\\d{2,})\\s*(tag(?:e|en)?|minute(?:n)?|stunde(?:n)?|prozent)\\b");

    private EvidenceTextTerms() { }

    public static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        String normalized = text == null ? "" : text.toLowerCase(Locale.GERMAN);
        Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 4 || token.matches("\\d{2,}")
                        || SECURITY_SHORT_TERMS.contains(token))
                .filter(token -> !STOP_WORDS.contains(token))
                .map(EvidenceTextTerms::stem)
                .forEach(token -> {
                    if (!STOP_WORDS.contains(token)) result.add(token);
                    String canonical = canonical(token);
                    if (!STOP_WORDS.contains(canonical)) result.add(canonical);
                });
        addStructuredTerms(normalized, result);
        return result;
    }

    public static int overlap(Set<String> first, Set<String> second) {
        int score = 0;
        for (String term : first) if (second.contains(term)) score++;
        return score;
    }

    /**
     * Bewertet Inhalt und Fundstellen-Metadaten gemeinsam. Abschnittsbezeichner sind bei
     * Security-Dokumenten häufig die präziseste lokale Semantik (z. B. {@code jit-access}
     * oder {@code siem-export}) und erhalten deshalb ein begrenztes Zusatzgewicht.
     */
    public static int relevance(Set<String> queryTerms, EvidenceChunk chunk) {
        Set<String> contentTerms = tokens(chunk.content());
        Set<String> locatorTerms = tokens(chunk.locator());
        int score = 0;
        for (String term : queryTerms) {
            int intentWeight = INTENT_TERMS.contains(term) ? 3 : 1;
            if (contentTerms.contains(term)) score += 2 * intentWeight;
            if (locatorTerms.contains(term)) score += 2 * intentWeight;
        }
        return score;
    }

    private static String stem(String token) {
        for (String suffix : SUFFIXES) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= 5) {
                return token.substring(0, token.length() - suffix.length());
            }
        }
        return token;
    }

    /**
     * Kleine domänenspezifische Synonymnormalisierung. Sie ist absichtlich transparent und
     * deterministisch: Das Offline-Golden-Set soll semantische Paraphrasen prüfen, ohne dafür
     * einen externen Embedding-Provider zu benötigen.
     */
    private static String canonical(String term) {
        if (startsWithAny(term, "verschlüssel", "geschützt", "schützen", "schützt", "protect", "encrypt")) {
            return "kryptoschutz";
        }
        if (startsWithAny(term, "ruhend", "ruhezustand", "rest")) return "ruhedaten";
        if (startsWithAny(term, "verfügbarkeit", "serviceverfüg", "servicezusag") || "sla".equals(term)) {
            return "verfügbarkeit";
        }
        if (startsWithAny(term, "synthet", "fiktiv", "demo")) return "synthetisch";
        if (startsWithAny(term, "standarddau", "standardmäßig", "laufzeit")) return "standarddauer";
        if (startsWithAny(term, "deaktiv", "joiner", "mover", "leaver")) return "lifecycle";
        if (term.endsWith("aktivierung")) return "rollenaktivierung";
        if (startsWithAny(term, "genehmig", "freigab", "vier", "four")) return "genehmigung";
        if (startsWithAny(term, "kritisch", "critical")) return "kritisch";
        if (startsWithAny(term, "administr", "privileg")) return "privilegiert";
        if (startsWithAny(term, "wiederherstell", "wiederhergest", "recovery")) return "recovery";
        if (startsWithAny(term, "zugriff", "access")) return "zugriff";
        if (startsWithAny(term, "aufbewahr", "retention")) return "aufbewahrung";
        if (startsWithAny(term, "transportweg", "exportier", "ausgegeben")) return "export";
        if (startsWithAny(term, "protection")) return "kryptoschutz";
        if (startsWithAny(term, "behandelt", "verarbeitet", "beendet", "entfernt")) return "lifecycleaktion";
        if (startsWithAny(term, "zugangsdat", "secret", "kennwort")) return "zugangsgeheimnis";
        if (startsWithAny(term, "passwort", "passwört", "password")) return "passwort";
        if (startsWithAny(term, "rotier", "rotation")) return "rotation";
        if (startsWithAny(term, "gespeich", "speicher")) return "speicherung";
        if (startsWithAny(term, "provisionier", "deprovisionier")) return "provisionierung";
        return term;
    }

    private static void addStructuredTerms(String normalized, Set<String> result) {
        Matcher version = VERSION.matcher(normalized);
        while (version.find()) result.add("version_" + version.group(1) + "_" + version.group(2));

        Matcher quantity = QUANTITY_WITH_UNIT.matcher(normalized);
        while (quantity.find()) {
            result.add(quantity.group(1) + "_" + canonicalUnit(quantity.group(2)));
        }

        if (normalized.matches("(?s).*\\bjust[ -]in[ -]time\\b.*")) result.add("jit");
        if (normalized.matches("(?s).*\\bvier[ -]augen\\b.*")) result.add("genehmigung");
        if (normalized.matches("(?s).*\\bservice[ -]account(?:s)?\\b.*")) result.add("serviceaccount");
        if (normalized.matches("(?s).*\\bphishing[ -]resistent\\w*\\b.*")) result.add("phishingresistent");
        if (normalized.matches("(?s).*\\baudit[ -](?:log|logs|ereignis|ereignisse)\\b.*")) result.add("audit");
    }

    private static String canonicalUnit(String unit) {
        if (unit.startsWith("tag")) return "tage";
        if (unit.startsWith("minute")) return "minuten";
        if (unit.startsWith("stunde")) return "stunden";
        return "prozent";
    }

    private static boolean startsWithAny(String value, String... prefixes) {
        return Arrays.stream(prefixes).anyMatch(value::startsWith);
    }
}
