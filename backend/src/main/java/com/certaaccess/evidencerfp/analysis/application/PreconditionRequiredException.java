package com.certaaccess.evidencerfp.analysis.application;

public final class PreconditionRequiredException extends RuntimeException {
    public PreconditionRequiredException() {
        super("Für Review-Änderungen ist die aktuelle Fragenrevision im If-Match-Header erforderlich.");
    }
}
