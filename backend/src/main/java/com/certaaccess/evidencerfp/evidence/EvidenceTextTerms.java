package com.certaaccess.evidencerfp.evidence;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Kleine, deterministische Normalisierung für den deutschen Offline-Retriever. */
public final class EvidenceTextTerms {
    private static final Set<String> STOP_WORDS = Set.of(
            "aber", "alle", "auch", "dass", "diese", "dieser", "dieses", "eine", "einer", "einem",
            "einen", "eines", "gibt", "haben", "ihre", "kann", "können", "oder", "sind", "über",
            "unter", "wird", "werden", "wurde", "welche", "welcher", "welches", "wieviel", "durch",
            "für", "with", "from", "does", "this", "that", "what", "when", "where", "your",
            "system", "lösung", "certaaccess", "bereits");
    private static final String[] SUFFIXES = {"innen", "ern", "em", "en", "er", "es", "e", "n", "s"};

    private EvidenceTextTerms() { }

    public static Set<String> tokens(String text) {
        Set<String> result = new HashSet<>();
        Arrays.stream(text.toLowerCase(Locale.GERMAN).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 4)
                .filter(token -> !STOP_WORDS.contains(token))
                .map(EvidenceTextTerms::stem)
                .filter(token -> token.length() >= 4)
                .forEach(result::add);
        return result;
    }

    public static int overlap(Set<String> first, Set<String> second) {
        int score = 0;
        for (String term : first) if (second.contains(term)) score++;
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
}
