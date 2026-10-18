package com.certaaccess.evidencerfp.analysis.application;

final class JobOwnershipLostException extends RuntimeException {
    JobOwnershipLostException() {
        super("Die Lease des Analysejobs ist nicht mehr gültig.");
    }
}
