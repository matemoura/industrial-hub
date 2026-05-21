package com.industrialhub.backend.common.domain;

public class EscalationCooldownException extends RuntimeException {

    private final long secondsRemaining;

    public EscalationCooldownException(long secondsRemaining) {
        super("Escalação manual em cooldown. Aguarde " + secondsRemaining + "s antes de tentar novamente.");
        this.secondsRemaining = secondsRemaining;
    }

    public long getSecondsRemaining() {
        return secondsRemaining;
    }
}
