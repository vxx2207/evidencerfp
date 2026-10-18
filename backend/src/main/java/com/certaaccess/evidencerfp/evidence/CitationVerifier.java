package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.Citation;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class CitationVerifier {
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?(?:\\s*%)?");
    private static final Pattern PROTECTED_ENTITY = Pattern.compile(
            "\\b(?:[A-ZÄÖÜ]{2,}[A-ZÄÖÜ0-9.-]*|[A-Za-zÄÖÜäöüß]+-\\d+[A-Za-z0-9.-]*|"
                    + "[A-Za-z]+\\d+[A-Za-z0-9.-]*|"
                    + "[A-ZÄÖÜ][a-zäöüß]+(?:[A-ZÄÖÜ][A-Za-zÄÖÜäöüß0-9]*)+)\\b");
    private static final Pattern NEGATION = Pattern.compile(
            "(?i)\\b(?:nicht|kein(?:e|en|er|es|em)?|ohne|nie|deaktiviert|ausgeschlossen|verhindert|untersagt|unzulässig)\\b");

    public boolean verify(Collection<Citation> citations, Collection<EvidenceChunk> retrievedChunks) {
        if (citations == null || citations.isEmpty() || retrievedChunks == null || retrievedChunks.isEmpty()) {
            return false;
        }
        Map<UUID, EvidenceChunk> allowed = retrievedChunks.stream()
                .collect(Collectors.toMap(EvidenceChunk::id, Function.identity()));
        return citations.stream().allMatch(citation -> verifyOne(citation, allowed));
    }

    /**
     * Prüft zusätzlich zur exakten Zitation, ob sicherheitsrelevante Claim-Bestandteile vom
     * zitierten Wortlaut getragen werden. Zahlen, technische Entitäten und Negation dürfen nicht
     * ergänzt oder in ihrer Polarität umgedreht werden.
     */
    public boolean verifyClaim(String claim, Collection<Citation> citations,
                               Collection<EvidenceChunk> retrievedChunks) {
        if (claim == null || claim.isBlank() || !verify(citations, retrievedChunks)) return false;
        String evidence = citations.stream().map(Citation::quote).collect(Collectors.joining(" "));
        if (!containsAll(normalizedValues(NUMBER, claim), normalizedValues(NUMBER, evidence))) return false;
        if (!containsAll(normalizedValues(PROTECTED_ENTITY, claim),
                normalizedValues(PROTECTED_ENTITY, evidence))) return false;

        boolean claimNegated = NEGATION.matcher(claim).find();
        boolean evidenceNegated = NEGATION.matcher(evidence).find();
        if (claimNegated != evidenceNegated) return false;

        Set<String> claimTerms = EvidenceTextTerms.tokens(claim);
        Set<String> evidenceTerms = EvidenceTextTerms.tokens(evidence);
        return EvidenceTextTerms.overlap(claimTerms, evidenceTerms) > 0;
    }

    private boolean verifyOne(Citation citation, Map<UUID, EvidenceChunk> allowed) {
        if (citation == null || citation.chunkId() == null || citation.quote() == null
                || citation.quote().isBlank()) {
            return false;
        }
        EvidenceChunk source = allowed.get(citation.chunkId());
        if (source == null) {
            return false;
        }
        String normalizedSource = normalize(source.content());
        String normalizedQuote = normalize(citation.quote());
        return normalizedQuote.length() >= 12 && normalizedSource.contains(normalizedQuote);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private Set<String> normalizedValues(Pattern pattern, String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        while (matcher.find()) {
            result.add(matcher.group().replaceAll("\\s+", "")
                    .replace(',', '.').toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private boolean containsAll(Set<String> required, Set<String> available) {
        return available.containsAll(required);
    }
}
