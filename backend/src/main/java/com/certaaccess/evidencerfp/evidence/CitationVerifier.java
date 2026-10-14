package com.certaaccess.evidencerfp.evidence;

import com.certaaccess.evidencerfp.analysis.domain.Citation;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CitationVerifier {

    public boolean verify(Collection<Citation> citations, Collection<EvidenceChunk> retrievedChunks) {
        if (citations == null || citations.isEmpty() || retrievedChunks == null || retrievedChunks.isEmpty()) {
            return false;
        }
        Map<UUID, EvidenceChunk> allowed = retrievedChunks.stream()
                .collect(Collectors.toMap(EvidenceChunk::id, Function.identity()));
        return citations.stream().allMatch(citation -> verifyOne(citation, allowed));
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
}
