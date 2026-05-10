package com.industrialhub.backend.oee.infrastructure;

import java.math.BigDecimal;

public interface IndirectActivitySummary {
    String getDescription();
    Long getOccurrences();
    BigDecimal getTotalHours();
}
