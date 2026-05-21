package com.industrialhub.backend.common.domain;

public class SlaRuleDuplicateException extends RuntimeException {

    public SlaRuleDuplicateException() {
        super("Regra SLA já existe para esta combinação");
    }
}
