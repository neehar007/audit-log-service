package com.schwab.audit.controller;

import com.schwab.audit.model.AuditRecord;
import com.schwab.audit.repository.AuditRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ComplianceReportTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuditRecordRepository auditRepository;

    @BeforeEach
    public void setup() {
        auditRepository.deleteAll();
    }

    @Test
    public void testComplianceReportCsv() {
        AuditRecord record = new AuditRecord();
        record.setResourceType("CLIENT_ACCOUNT");
        record.setResourceId("client-123");
        record.setActorId("actor-1");
        record.setEventType("LOGIN");
        record.setTimestamp(Instant.parse("2023-01-01T10:00:00Z"));
        record.setPayload("{\"key\":\"val with \\\"quotes\\\"\"}");
        record.setHash("hash1");
        record.setPreviousHash("genesis");
        auditRepository.save(record);

        String url = "http://localhost:" + port + "/api/audit/compliance/report?resourceId=client-123";

        ResponseEntity<String> response = restTemplate.withBasicAuth("admin", "secret-audit-key").getForEntity(url, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("ID,Timestamp,Event Type,Actor ID,Payload");
        // Expecting quotes around the payload, and internal quotes doubled
        assertThat(body).contains("\"{\"\"key\"\":\"\"val with \\\"\"quotes\\\"\"\"\"}\"");
    }
}
