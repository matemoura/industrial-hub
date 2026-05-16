package com.industrialhub.backend.qms.domain;

import java.util.UUID;

public class RcaNotFoundException extends RuntimeException {

    public RcaNotFoundException(UUID ncId) {
        super("Análise de causa raiz não encontrada para a NC: " + ncId);
    }
}
