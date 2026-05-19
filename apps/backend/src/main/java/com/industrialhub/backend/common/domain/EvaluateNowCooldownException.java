package com.industrialhub.backend.common.domain;

public class EvaluateNowCooldownException extends RuntimeException {

    private final long secondsRemaining;

    public EvaluateNowCooldownException(long secondsRemaining) {
        super("Avaliação manual disponível em " + secondsRemaining + " segundos");
        this.secondsRemaining = secondsRemaining;
    }

    public long getSecondsRemaining() {
        return secondsRemaining;
    }
}
