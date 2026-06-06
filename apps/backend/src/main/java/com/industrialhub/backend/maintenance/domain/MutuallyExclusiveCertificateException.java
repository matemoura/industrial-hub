package com.industrialhub.backend.maintenance.domain;

public class MutuallyExclusiveCertificateException extends RuntimeException {

    public MutuallyExclusiveCertificateException() {
        super("Informe certificateDocumentId ou faça upload, não ambos.");
    }
}
