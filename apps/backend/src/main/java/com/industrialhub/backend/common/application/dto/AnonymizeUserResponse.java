package com.industrialhub.backend.common.application.dto;

import java.util.Map;

public record AnonymizeUserResponse(
    boolean anonymized,
    Map<String, Integer> affectedEntities
) {}
