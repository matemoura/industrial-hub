package com.industrialhub.backend.production.domain;

public class OrderAlreadyAllocatedException extends RuntimeException {
    public OrderAlreadyAllocatedException(String dynamicsOrderNumber, String loadNumber) {
        super("OP %s já alocada na carga %s".formatted(dynamicsOrderNumber, loadNumber));
    }
}
