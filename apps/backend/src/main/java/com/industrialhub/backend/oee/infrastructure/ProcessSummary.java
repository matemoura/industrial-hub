package com.industrialhub.backend.oee.infrastructure;

import java.math.BigDecimal;

public interface ProcessSummary {
    String getDescription();
    BigDecimal getTotalHours();
    Long getWorkerCount();
    Long getOccurrences();
}
