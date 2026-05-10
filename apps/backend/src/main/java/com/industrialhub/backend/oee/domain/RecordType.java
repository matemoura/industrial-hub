package com.industrialhub.backend.oee.domain;

public enum RecordType {
    PROCESSO("Processo"),
    ATIVIDADE_INDIRETA("Atividade indireta"),
    REGISTRO_ENTRADA("Registro de entrada"),
    REGISTRO_SAIDA("Registro de saída"),
    INTERVALO("Intervalo");

    private final String dynamicsLabel;

    RecordType(String dynamicsLabel) {
        this.dynamicsLabel = dynamicsLabel;
    }

    public static RecordType fromDynamicsLabel(String label) {
        if (label == null) return null;
        for (RecordType type : values()) {
            if (type.dynamicsLabel.equalsIgnoreCase(label.trim())) {
                return type;
            }
        }
        return null;
    }

    public boolean isProductive() {
        return this == PROCESSO;
    }

    public boolean isIndirect() {
        return this == ATIVIDADE_INDIRETA;
    }
}
