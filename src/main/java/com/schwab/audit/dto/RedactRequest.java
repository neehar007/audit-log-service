package com.schwab.audit.dto;

import jakarta.validation.constraints.NotBlank;

public class RedactRequest {
    @NotBlank(message = "Field name must be provided")
    private String field;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
