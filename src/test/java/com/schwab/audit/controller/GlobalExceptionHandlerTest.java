package com.schwab.audit.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleValidationException returns 400 ProblemDetail with field errors")
    void handleValidationException_ReturnsProblemDetail400() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "eventType", "eventType is required"));
        bindingResult.addError(new FieldError("target", "actorId", "actorId is required"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ProblemDetail problemDetail = exceptionHandler.handleValidationException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
        assertThat(problemDetail.getDetail()).isEqualTo("Validation failed for one or more fields");

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problemDetail.getProperties().get("errors");
        assertThat(errors).isNotNull();
        assertThat(errors).containsEntry("eventType", "eventType is required");
        assertThat(errors).containsEntry("actorId", "actorId is required");
        assertThat(problemDetail.getProperties().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("handleIllegalArgumentException returns 400 ProblemDetail")
    void handleIllegalArgumentException_ReturnsProblemDetail400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");

        ProblemDetail problemDetail = exceptionHandler.handleIllegalArgumentException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
        assertThat(problemDetail.getDetail()).isEqualTo("Invalid argument provided");
        assertThat(problemDetail.getProperties().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("handleGeneralException returns 500 ProblemDetail")
    void handleGeneralException_ReturnsProblemDetail500() {
        RuntimeException ex = new RuntimeException("Unexpected internal failure");

        ProblemDetail problemDetail = exceptionHandler.handleGeneralException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problemDetail.getDetail()).isEqualTo("An unexpected internal error occurred");
        assertThat(problemDetail.getProperties().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("handleGeneralException with null message uses default detail")
    void handleGeneralException_NullMessage_UsesDefaultDetail() {
        RuntimeException ex = new RuntimeException((String) null);

        ProblemDetail problemDetail = exceptionHandler.handleGeneralException(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getDetail()).isEqualTo("An unexpected internal error occurred");
    }
}
