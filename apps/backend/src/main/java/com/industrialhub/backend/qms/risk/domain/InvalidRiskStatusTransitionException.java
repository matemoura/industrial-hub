package com.industrialhub.backend.qms.risk.domain;

public class InvalidRiskStatusTransitionException extends RuntimeException {
    public InvalidRiskStatusTransitionException(String message) {
        super(message);
    }

    public InvalidRiskStatusTransitionException(RiskStatus from, RiskStatus to) {
        super("Transição inválida de status: " + from + " → " + to);
    }
}
