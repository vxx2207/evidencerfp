package com.certaaccess.evidencerfp.analysis.application;

/** Technischer KI-Fehler, der niemals als fachliches NOT_FOUND maskiert werden darf. */
public class AnswerGenerationException extends RuntimeException {
    private final String code;

    public AnswerGenerationException(String code, String safeMessage) {
        super(safeMessage);
        this.code = code;
    }

    public AnswerGenerationException(String code, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
