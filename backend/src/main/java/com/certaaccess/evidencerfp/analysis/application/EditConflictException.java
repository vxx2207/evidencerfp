package com.certaaccess.evidencerfp.analysis.application;

public final class EditConflictException extends RuntimeException {
    public EditConflictException() {
        super("Die Frage wurde zwischenzeitlich geändert. Bitte laden Sie den aktuellen Stand neu.");
    }
}
