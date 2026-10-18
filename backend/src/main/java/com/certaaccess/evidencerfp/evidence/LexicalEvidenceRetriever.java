package com.certaaccess.evidencerfp.evidence;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile("!production")
public class LexicalEvidenceRetriever implements EvidenceRetriever {
    @Override
    public List<EvidenceChunk> retrieve(RfpQuestion question, List<EvidenceChunk> availableChunks, int limit) {
        var questionTerms = EvidenceTextTerms.tokens(question.question());
        Map<EvidenceChunk, Set<String>> termsByChunk = new HashMap<>();
        Map<EvidenceChunk, Set<String>> locatorTermsByChunk = new HashMap<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (EvidenceChunk chunk : availableChunks) {
            Set<String> terms = EvidenceTextTerms.tokens(chunk.content());
            Set<String> locatorTerms = EvidenceTextTerms.tokens(chunk.locator());
            termsByChunk.put(chunk, terms);
            locatorTermsByChunk.put(chunk, locatorTerms);
            Set<String> searchableTerms = new java.util.HashSet<>(terms);
            searchableTerms.addAll(locatorTerms);
            searchableTerms.forEach(term -> documentFrequency.merge(term, 1, Integer::sum));
        }
        int corpusSize = Math.max(1, availableChunks.size());
        return availableChunks.stream()
                .map(chunk -> new ScoredChunk(chunk,
                        score(questionTerms, termsByChunk.get(chunk), locatorTermsByChunk.get(chunk),
                                documentFrequency, corpusSize)))
                .filter(candidate -> candidate.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed()
                        .thenComparing(candidate -> candidate.chunk().documentName())
                        .thenComparing(candidate -> candidate.chunk().locator()))
                .limit(Math.max(1, limit))
                .map(ScoredChunk::chunk)
                .toList();
    }

    private double score(Set<String> questionTerms, Set<String> chunkTerms, Set<String> locatorTerms,
                         Map<String, Integer> documentFrequency, int corpusSize) {
        double score = 0;
        for (String term : questionTerms) {
            boolean inContent = chunkTerms.contains(term);
            boolean inLocator = locatorTerms.contains(term);
            if (!inContent && !inLocator) continue;
            int frequency = documentFrequency.getOrDefault(term, corpusSize);
            double inverseDocumentFrequency = 1.0 + Math.log((corpusSize + 1.0) / (frequency + 1.0));
            if (inContent) score += inverseDocumentFrequency;
            if (inLocator) score += inverseDocumentFrequency * 2.5;
        }
        return score;
    }

    private record ScoredChunk(EvidenceChunk chunk, double score) {
    }
}
