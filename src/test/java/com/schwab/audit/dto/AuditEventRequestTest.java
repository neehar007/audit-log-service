package com.schwab.audit.dto;

import com.schwab.audit.model.AuditRecord;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Valid AuditEventRequest passes all validation constraints")
    void validRequest_PassesValidation() {
        AuditEventRequest request = new AuditEventRequest(
                "USER_LOGIN",
                "user-123",
                "SESSION",
                "sess-456",
                "{\"ip\":\"127.0.0.1\"}"
        );

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Blank eventType produces validation violation")
    void blankEventType_FailsValidation() {
        AuditEventRequest request = new AuditEventRequest(
                "",
                "user-123",
                "SESSION",
                "sess-456",
                "{\"ip\":\"127.0.0.1\"}"
        );

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("eventType");
    }

    @Test
    @DisplayName("Null actorId produces validation violation")
    void nullActorId_FailsValidation() {
        AuditEventRequest request = new AuditEventRequest(
                "USER_LOGIN",
                null,
                "SESSION",
                "sess-456",
                "{\"ip\":\"127.0.0.1\"}"
        );

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("actorId");
    }

    @Test
    @DisplayName("Blank resourceType produces validation violation")
    void blankResourceType_FailsValidation() {
        AuditEventRequest request = new AuditEventRequest(
                "USER_LOGIN",
                "user-123",
                "   ",
                "sess-456",
                "{\"ip\":\"127.0.0.1\"}"
        );

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("resourceType");
    }

    @Test
    @DisplayName("Null resourceId produces validation violation")
    void nullResourceId_FailsValidation() {
        AuditEventRequest request = new AuditEventRequest(
                "USER_LOGIN",
                "user-123",
                "SESSION",
                null,
                "{\"ip\":\"127.0.0.1\"}"
        );

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("resourceId");
    }

    @Test
    @DisplayName("Blank payload produces validation violation")
    void blankPayload_FailsValidation() {
        AuditEventRequest request = new AuditEventRequest(
                "USER_LOGIN",
                "user-123",
                "SESSION",
                "sess-456",
                ""
        );

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("payload");
    }

    @Test
    @DisplayName("All fields null produces 5 validation violations")
    void allFieldsNull_FailsValidation() {
        AuditEventRequest request = new AuditEventRequest();

        Set<ConstraintViolation<AuditEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(5);
    }

    @Test
    @DisplayName("toAuditRecord correctly maps all fields to AuditRecord")
    void toAuditRecord_MapsFields() {
        AuditEventRequest request = new AuditEventRequest(
                "ORDER_SUBMITTED",
                "trader-99",
                "ORDER",
                "ord-100",
                "{\"symbol\":\"SCHW\",\"shares\":50}"
        );

        AuditRecord record = request.toAuditRecord();

        assertThat(record.getEventType()).isEqualTo("ORDER_SUBMITTED");
        assertThat(record.getActorId()).isEqualTo("trader-99");
        assertThat(record.getResourceType()).isEqualTo("ORDER");
        assertThat(record.getResourceId()).isEqualTo("ord-100");
        assertThat(record.getPayload()).isEqualTo("{\"symbol\":\"SCHW\",\"shares\":50}");
        assertThat(record.getId()).isNull();
        assertThat(record.getTimestamp()).isNull();
        assertThat(record.getPreviousHash()).isNull();
        assertThat(record.getHash()).isNull();
    }

    @Test
    @DisplayName("Getters, setters, equals, hashCode, and toString operate correctly")
    void gettersSettersEqualsHashCodeToString() {
        AuditEventRequest req1 = new AuditEventRequest();
        req1.setEventType("EVT");
        req1.setActorId("ACT");
        req1.setResourceType("RES");
        req1.setResourceId("ID1");
        req1.setPayload("DATA");

        AuditEventRequest req2 = new AuditEventRequest("EVT", "ACT", "RES", "ID1", "DATA");
        AuditEventRequest req3 = new AuditEventRequest("EVT2", "ACT", "RES", "ID1", "DATA");

        assertThat(req1.getEventType()).isEqualTo("EVT");
        assertThat(req1.getActorId()).isEqualTo("ACT");
        assertThat(req1.getResourceType()).isEqualTo("RES");
        assertThat(req1.getResourceId()).isEqualTo("ID1");
        assertThat(req1.getPayload()).isEqualTo("DATA");

        assertThat(req1).isEqualTo(req2);
        assertThat(req1).hasSameHashCodeAs(req2);
        assertThat(req1).isNotEqualTo(req3);
        assertThat(req1).isNotEqualTo(null);
        assertThat(req1.toString()).contains("EVT", "ACT", "RES", "ID1", "DATA");
    }
}
