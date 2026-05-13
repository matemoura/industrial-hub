package com.industrialhub.backend.qms.domain;

import java.util.List;

public class InvalidNcTransitionException extends RuntimeException {

    private final List<NcStatus> allowedNext;

    public InvalidNcTransitionException(NcStatus from, NcStatus to, List<NcStatus> allowedNext) {
        super("Invalid transition from " + from + " to " + to);
        this.allowedNext = allowedNext;
    }

    public List<NcStatus> getAllowedNext() {
        return allowedNext;
    }
}
