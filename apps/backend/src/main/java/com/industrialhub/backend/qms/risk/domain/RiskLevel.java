package com.industrialhub.backend.qms.risk.domain;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public static RiskLevel fromRpn(int rpn) {
        if (rpn <= 30) return LOW;
        if (rpn <= 100) return MEDIUM;
        if (rpn <= 200) return HIGH;
        return CRITICAL;
    }
}
