package com.industrialhub.backend.common.domain;

public enum AttachmentEntityType {
    WORK_ORDER, NON_CONFORMANCE, SPARE_PART;

    public String toStoragePrefix() {
        return name().toLowerCase().replace("_", "-");
    }
}
