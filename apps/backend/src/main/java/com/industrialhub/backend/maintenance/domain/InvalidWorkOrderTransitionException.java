package com.industrialhub.backend.maintenance.domain;

import java.util.List;

public class InvalidWorkOrderTransitionException extends RuntimeException {

    private final List<WorkOrderStatus> allowedNext;

    public InvalidWorkOrderTransitionException(WorkOrderStatus from, WorkOrderStatus to, List<WorkOrderStatus> allowedNext) {
        super("Invalid transition from " + from + " to " + to);
        this.allowedNext = allowedNext;
    }

    public List<WorkOrderStatus> getAllowedNext() {
        return allowedNext;
    }
}
