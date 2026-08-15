package com.schwab.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.dto.AuditEventRequest;
import com.schwab.audit.model.AuditRecord;
import com.schwab.audit.repository.AuditRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    private static final String AUTH_USER = "admin";
    private static final String AUTH_PASS = "secret-audit-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @BeforeEach
    void setUp() {
        auditRecordRepository.deleteAll();
    }

    @Nested
    @DisplayName("Authentication & Authorization Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("POST /events without credentials returns 401 Unauthorized")
        void postEvents_WithoutAuth_Returns401() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "USER_LOGIN", "user-1", "SESSION", "sess-1", "{\"status\":\"OK\"}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /events with invalid credentials returns 401 Unauthorized")
        void postEvents_WithInvalidCredentials_Returns401() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "USER_LOGIN", "user-1", "SESSION", "sess-1", "{\"status\":\"OK\"}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic("admin", "invalid-password"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /events without credentials returns 401 Unauthorized")
        void getEvents_WithoutAuth_Returns401() throws Exception {
            mockMvc.perform(get("/api/audit/events"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /verify without credentials returns 401 Unauthorized")
        void getVerify_WithoutAuth_Returns401() throws Exception {
            mockMvc.perform(get("/api/audit/verify"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/audit/events - Ingest Event Tests")
    class CreateEventTests {

        @Test
        @DisplayName("Valid event creation returns 201 Created and persists record with hash and previousHash")
        void createEvent_ValidPayload_Returns201Created() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "USER_LOGIN",
                    "user-101",
                    "SESSION",
                    "sess-202",
                    "{\"ip\":\"192.168.1.1\",\"client\":\"web\"}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.eventType", is("USER_LOGIN")))
                    .andExpect(jsonPath("$.actorId", is("user-101")))
                    .andExpect(jsonPath("$.resourceType", is("SESSION")))
                    .andExpect(jsonPath("$.resourceId", is("sess-202")))
                    .andExpect(jsonPath("$.payload", is("{\"ip\":\"192.168.1.1\",\"client\":\"web\"}")))
                    .andExpect(jsonPath("$.timestamp", notNullValue()))
                    .andExpect(jsonPath("$.previousHash", is("0000000000000000000000000000000000000000000000000000000000000000")))
                    .andExpect(jsonPath("$.hash", notNullValue()));
        }

        @Test
        @DisplayName("Sequential event creations maintain cryptographic previousHash linkage")
        void createEvent_Sequential_LinksHashes() throws Exception {
            AuditEventRequest req1 = new AuditEventRequest(
                    "ORDER_PLACED", "user-1", "ORDER", "ord-1", "{\"symbol\":\"SCHW\",\"shares\":10}"
            );
            AuditEventRequest req2 = new AuditEventRequest(
                    "ORDER_EXECUTED", "user-1", "ORDER", "ord-1", "{\"fillPrice\":75.50}"
            );

            String res1Json = mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            AuditRecord res1 = objectMapper.readValue(res1Json, AuditRecord.class);

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.previousHash", is(res1.getHash())))
                    .andExpect(jsonPath("$.hash", notNullValue()));
        }

        @Test
        @DisplayName("Missing eventType returns 400 Bad Request with ProblemDetail error")
        void createEvent_MissingEventType_Returns400() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    null, "user-1", "ORDER", "ord-1", "{\"test\":true}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.title", is("Bad Request")))
                    .andExpect(jsonPath("$.errors.eventType", notNullValue()));
        }

        @Test
        @DisplayName("Blank actorId returns 400 Bad Request with ProblemDetail error")
        void createEvent_BlankActorId_Returns400() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "LOGIN", "   ", "ORDER", "ord-1", "{\"test\":true}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.actorId", notNullValue()));
        }

        @Test
        @DisplayName("Blank resourceType returns 400 Bad Request with ProblemDetail error")
        void createEvent_BlankResourceType_Returns400() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "LOGIN", "user-1", "", "ord-1", "{\"test\":true}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.resourceType", notNullValue()));
        }

        @Test
        @DisplayName("Blank resourceId returns 400 Bad Request with ProblemDetail error")
        void createEvent_BlankResourceId_Returns400() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "LOGIN", "user-1", "ORDER", "", "{\"test\":true}"
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.resourceId", notNullValue()));
        }

        @Test
        @DisplayName("Blank payload returns 400 Bad Request with ProblemDetail error")
        void createEvent_BlankPayload_Returns400() throws Exception {
            AuditEventRequest request = new AuditEventRequest(
                    "LOGIN", "user-1", "ORDER", "ord-1", ""
            );

            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.payload", notNullValue()));
        }

        @Test
        @DisplayName("Empty JSON body returns 400 Bad Request with validation errors for all fields")
        void createEvent_EmptyBody_Returns400() throws Exception {
            mockMvc.perform(post("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.errors.eventType", notNullValue()))
                    .andExpect(jsonPath("$.errors.actorId", notNullValue()))
                    .andExpect(jsonPath("$.errors.resourceType", notNullValue()))
                    .andExpect(jsonPath("$.errors.resourceId", notNullValue()))
                    .andExpect(jsonPath("$.errors.payload", notNullValue()));
        }
    }

    @Nested
    @DisplayName("GET /api/audit/events - Query Events Tests")
    class GetEventsTests {

        @Test
        @DisplayName("Get events with empty repository returns empty page")
        void getEvents_Empty_ReturnsEmptyPage() throws Exception {
            mockMvc.perform(get("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("Get events retrieves all persisted records")
        void getEvents_ReturnsPersistedRecords() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "login1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o1", "trade1");

            mockMvc.perform(get("/api/audit/events")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements", is(2)))
                    .andExpect(jsonPath("$.content[0].actorId", is("alice")))
                    .andExpect(jsonPath("$.content[1].actorId", is("bob")));
        }

        @Test
        @DisplayName("Filter events by actorId")
        void getEvents_FilterByActorId() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "login1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o1", "trade1");

            mockMvc.perform(get("/api/audit/events")
                            .param("actorId", "alice")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].actorId", is("alice")));
        }

        @Test
        @DisplayName("Filter events by eventType and resourceType")
        void getEvents_FilterByEventTypeAndResourceType() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "login1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o1", "trade1");
            createRecordViaApi("TRADE", "charlie", "ACCOUNT", "a1", "trade2");

            mockMvc.perform(get("/api/audit/events")
                            .param("eventType", "TRADE")
                            .param("resourceType", "ORDER")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].actorId", is("bob")));
        }

        @Test
        @DisplayName("Filter events by resourceId")
        void getEvents_FilterByResourceId() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "login1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o200", "trade1");

            mockMvc.perform(get("/api/audit/events")
                            .param("resourceId", "o200")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].resourceId", is("o200")));
        }

        @Test
        @DisplayName("Filter events by timestamp range")
        void getEvents_FilterByTimestampRange() throws Exception {
            Instant before = Instant.now().minus(1, ChronoUnit.HOURS);
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "login1");
            Instant after = Instant.now().plus(1, ChronoUnit.HOURS);

            mockMvc.perform(get("/api/audit/events")
                            .param("from", before.toString())
                            .param("to", after.toString())
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)));
        }

        @Test
        @DisplayName("Pagination parameters return sliced pages")
        void getEvents_Pagination() throws Exception {
            createRecordViaApi("EVT1", "u1", "RES", "r1", "p1");
            createRecordViaApi("EVT2", "u2", "RES", "r2", "p2");
            createRecordViaApi("EVT3", "u3", "RES", "r3", "p3");

            mockMvc.perform(get("/api/audit/events")
                            .param("page", "0")
                            .param("size", "2")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements", is(3)))
                    .andExpect(jsonPath("$.totalPages", is(2)))
                    .andExpect(jsonPath("$.number", is(0)));

            mockMvc.perform(get("/api/audit/events")
                            .param("page", "1")
                            .param("size", "2")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.number", is(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/audit/verify - Chain Verification Tests")
    class VerifyChainTests {

        @Test
        @DisplayName("Empty repository returns INTACT verification status")
        void verifyChain_Empty_ReturnsIntact() throws Exception {
            mockMvc.perform(get("/api/audit/verify")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("INTACT")));
        }

        @Test
        @DisplayName("Valid chained records return INTACT verification status")
        void verifyChain_ValidChain_ReturnsIntact() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "p1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o1", "p2");
            createRecordViaApi("LOGOUT", "alice", "USER", "u1", "p3");

            mockMvc.perform(get("/api/audit/verify")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("INTACT")));
        }

        @Test
        @DisplayName("Tampered record payload returns BROKEN with HASH_MISMATCH")
        void verifyChain_TamperedPayload_ReturnsBroken() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "p1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o1", "p2");

            // Directly tamper with the database record
            AuditRecord record2 = auditRecordRepository.findAll().get(1);
            record2.setPayload("tampered_hacked_payload");
            auditRecordRepository.save(record2);

            mockMvc.perform(get("/api/audit/verify")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("BROKEN")))
                    .andExpect(jsonPath("$.firstInconsistentRecordId", is(record2.getId().intValue())))
                    .andExpect(jsonPath("$.violationType", is("HASH_MISMATCH")));
        }

        @Test
        @DisplayName("Tampered record previousHash returns BROKEN with PREVIOUS_HASH_MISMATCH")
        void verifyChain_TamperedPreviousHash_ReturnsBroken() throws Exception {
            createRecordViaApi("LOGIN", "alice", "USER", "u1", "p1");
            createRecordViaApi("TRADE", "bob", "ORDER", "o1", "p2");

            // Directly tamper with previousHash
            AuditRecord record2 = auditRecordRepository.findAll().get(1);
            record2.setPreviousHash("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
            auditRecordRepository.save(record2);

            mockMvc.perform(get("/api/audit/verify")
                            .with(httpBasic(AUTH_USER, AUTH_PASS)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("BROKEN")))
                    .andExpect(jsonPath("$.firstInconsistentRecordId", is(record2.getId().intValue())))
                    .andExpect(jsonPath("$.violationType", is("PREVIOUS_HASH_MISMATCH")));
        }
    }

    private void createRecordViaApi(String eventType, String actorId, String resourceType, String resourceId, String payload) throws Exception {
        AuditEventRequest request = new AuditEventRequest(eventType, actorId, resourceType, resourceId, payload);
        mockMvc.perform(post("/api/audit/events")
                        .with(httpBasic(AUTH_USER, AUTH_PASS))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
