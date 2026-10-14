package com.certaaccess.evidencerfp.evidence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@Profile("!production")
public class LexicalEvidenceRetriever implements EvidenceRetriever {
    @Override
    public List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit) {
        var questionTerms = EvidenceTextTerms.tokens(question.question());
        return availableChunks.stream()
                .map(chunk -> new ScoredChunk(chunk,
                        EvidenceTextTerms.overlap(questionTerms, EvidenceTextTerms.tokens(chunk.content()))))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                        .thenComparing(candidate -> candidate.chunk().documentName())
                        .thenComparing(candidate -> candidate.chunk().locator()))
                .limit(Math.max(1, limit))
                .map(ScoredChunk::chunk)
                .toList();
    }

    private record ScoredChunk(EvidenceChunk chunk, int score) {
    }
}
