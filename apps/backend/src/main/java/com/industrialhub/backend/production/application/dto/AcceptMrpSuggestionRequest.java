package com.industrialhub.backend.production.application.dto;

/** Body opcional para aceitar sugestão MRP — adjustedQty é facultativo */
public record AcceptMrpSuggestionRequest(Integer adjustedQty) {}
