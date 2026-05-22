package com.industrialhub.backend.common.domain;

public class DataRetentionCooldownException extends RuntimeException {

    private final long secondsRemaining;

    public DataRetentionCooldownException(long secondsRemaining) {
        super("Retenção de dados em cooldown. Aguarde " + secondsRemaining + "s antes de executar novamente.");
        this.secondsRemaining = secondsRemaining;
    }

    public long getSecondsRemaining() {
        return secondsRemaining;
    }
}
