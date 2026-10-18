package com.certaaccess.evidencerfp.analysis.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;

/** Fail-closed Struktur- und Größenprüfung vor der Bindung einer Modellausgabe. */
@Component
public class StrictModelAnswerJsonValidator {
    private static final int MAX_JSON_CHARACTERS = 600_000;
    private static final Set<String> ROOT_FIELDS = Set.of("claims", "internalClarification");
    private static final Set<String> CLAIM_FIELDS = Set.of("text", "citations");
    private static final Set<String> CITATION_FIELDS = Set.of("chunkId", "quote");

    private final ObjectMapper objectMapper;

    public StrictModelAnswerJsonValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String json) {
        if (json == null || json.length() > MAX_JSON_CHARACTERS) throw invalid();
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.isObject() || !root.propertyNames().equals(ROOT_FIELDS)) {
            throw invalid();
        }
        JsonNode claims = root.get("claims");
        JsonNode clarification = root.get("internalClarification");
        if (claims == null || !claims.isArray() || claims.size() > 20
                || clarification == null
                || !(clarification.isNull() || clarification.isString())
                || clarification.isString() && clarification.asString().length() > 4_000) {
            throw invalid();
        }
        for (JsonNode claim : claims) {
            if (!claim.isObject() || !claim.propertyNames().equals(CLAIM_FIELDS)) throw invalid();
            JsonNode text = claim.get("text");
            JsonNode citations = claim.get("citations");
            if (text == null || !text.isString() || text.asString().isBlank()
                    || text.asString().length() > 2_000 || citations == null || !citations.isArray()
                    || citations.isEmpty() || citations.size() > 6) {
                throw invalid();
            }
            for (JsonNode citation : citations) {
                if (!citation.isObject() || !citation.propertyNames().equals(CITATION_FIELDS)) throw invalid();
                JsonNode chunkId = citation.get("chunkId");
                JsonNode quote = citation.get("quote");
                if (chunkId == null || !chunkId.isString() || quote == null || !quote.isString()
                        || quote.asString().isBlank() || quote.asString().length() > 4_000) {
                    throw invalid();
                }
                try {
                    UUID.fromString(chunkId.asString());
                } catch (IllegalArgumentException exception) {
                    throw invalid();
                }
            }
        }
    }

    private IllegalArgumentException invalid() {
        return new IllegalArgumentException("Die Modellausgabe entspricht nicht dem erlaubten Claim-Schema.");
    }
}
