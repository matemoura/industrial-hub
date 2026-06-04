package com.industrialhub.backend.qms.domain;

public enum NcDocumentLinkType {
    PROCEDURE_AT_OCCURRENCE,   // documento vigente quando a NC ocorreu
    CORRECTIVE_REFERENCE,      // documento referenciado na ação corretiva
    OTHER                      // outros vínculos documentais
}
