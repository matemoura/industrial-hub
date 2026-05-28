package com.industrialhub.backend.production.domain;

/** Lançada quando nenhum MRP run real foi executado e purchase-needs é solicitado */
public class NoMrpRunException extends RuntimeException {
    public NoMrpRunException() {
        super("Nenhum MRP executado ainda. Execute o MRP para ver as necessidades de compra.");
    }
}
