package com.certaaccess.evidencerfp.analysis.application;

import com.certaaccess.evidencerfp.analysis.domain.QuestionResult;
import com.certaaccess.evidencerfp.evidence.EvidenceChunk;
import com.certaaccess.evidencerfp.evidence.RfpQuestion;

import java.util.List;

/** Port für beleggebundene Antwortgeneratoren (deterministisch oder Spring AI/Ollama). */
public interface AnswerGenerator {
    QuestionResult generate(RfpQuestion question, List<EvidenceChunk> retrievedChunks);
}
