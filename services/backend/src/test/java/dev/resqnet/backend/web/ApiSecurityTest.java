package dev.resqnet.backend.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import dev.resqnet.backend.domain.ResponderAccount;
import dev.resqnet.backend.domain.ResponderRole;
import dev.resqnet.backend.repository.ResponderAccountRepository;
import dev.resqnet.backend.security.JwtService;

/**
 * Endpoint-level authorisation.
 *
 * <p>Written against the running application rather than against mock objects,
 * because the question these tests answer is "what does an unauthenticated
 * request actually get", and that depends on the filter chain, the CORS
 * configuration and the method security all being wired correctly at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private ResponderAccountRepository accounts;

    private String tokenFor(ResponderRole role, String username) {
        ResponderAccount account = accounts.save(new ResponderAccount(
                username + "-" + System.nanoTime(), username, encoder.encode("pw"), role));
        return jwt.issue(account.getUsername(), role, account.getId());
    }

    @Test
    @DisplayName("rejects an unauthenticated read")
    void rejectsUnauthenticatedRead() throws Exception {
        mvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects simulation ingest with no device token")
    void rejectsAnonymousSimulationIngest() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"t1\",\"topology\":\"RING\",\"nodeCount\":4}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects simulation ingest with the wrong device token")
    void rejectsWrongGatewayTokenForSimulationIngest() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .header("Authorization", "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"t1\",\"topology\":\"RING\",\"nodeCount\":4}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("accepts simulation ingest from a device holding the shared token")
    void acceptsDeviceTokenForSimulationIngest() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .header("Authorization", "Bearer test-gateway-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"t1\",\"topology\":\"RING\",\"nodeCount\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated").value(true));
    }

    @Test
    @DisplayName("accepts simulation ingest from a responder session")
    void acceptsResponderSessionForSimulationIngest() throws Exception {
        mvc.perform(post("/api/v1/simulations")
                        .header("Authorization", "Bearer " + tokenFor(ResponderRole.ADMIN, "sim"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"runId\":\"t2\",\"topology\":\"MESH\",\"nodeCount\":6}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("keeps simulation reads behind a responder session")
    void keepsSimulationReadsAuthenticated() throws Exception {
        // A device may publish a run but must not be able to read the history
        // back, which is responder-only operational data.
        mvc.perform(get("/api/v1/simulations")
                        .header("Authorization", "Bearer test-gateway-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects a forged bearer token")
    void rejectsForgedToken() throws Exception {
        mvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer not.a.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("allows an authenticated read")
    void allowsAuthenticatedRead() throws Exception {
        mvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer " + tokenFor(ResponderRole.OBSERVER,
                                "observer")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refuses a write from an observer, even with a valid token")
    void refusesObserverWrite() throws Exception {
        String token = tokenFor(ResponderRole.OBSERVER, "observer");

        mvc.perform(patch("/api/v1/incidents/RQ-TEST/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refuses a write with no token at all")
    void refusesAnonymousWrite() throws Exception {
        mvc.perform(patch("/api/v1/incidents/RQ-TEST/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("rejects an empty title with field-level detail")
    void rejectsInvalidFields() throws Exception {
        String token = tokenFor(ResponderRole.RESPONDER, "responder");

        mvc.perform(post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"category\":\"FIRE\",\"severity\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.title").exists());
    }

    @Test
    @DisplayName("reports an unparseable body as a bad request, not a server fault")
    void rejectsMalformedBody() throws Exception {
        String token = tokenFor(ResponderRole.RESPONDER, "responder");

        mvc.perform(post("/api/v1/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Fire\",\"category\":\"NOT_A_CATEGORY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    @DisplayName("rejects a gateway sync without the shared token")
    void rejectsUnauthenticatedGatewaySync() throws Exception {
        mvc.perform(post("/api/v1/gateway/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"android\",\"deviceId\":\"rqn1_x\",\"records\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("accepts a gateway sync with the shared token")
    void acceptsAuthenticatedGatewaySync() throws Exception {
        mvc.perform(post("/api/v1/gateway/sync")
                        .header("Authorization", "Bearer test-gateway-token")
                        .header("X-ResQNet-Source", "simulator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source":"android","deviceId":"rqn1_gateway_1","records":[
                                  {"messageId":"m1","state":"ACKNOWLEDGED","priority":"SOS",
                                   "type":"SOS","category":"FLOODING","origin":"rqn1_a",
                                   "createdAt":1700000000000,"expiresAt":1700000900000,
                                   "attempts":2,"detail":"confirmed"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdIncidents").value(1));
    }

    @Test
    @DisplayName("a gateway token cannot read the responder API")
    void gatewayCannotReadIncidents() throws Exception {
        mvc.perform(get("/api/v1/incidents")
                        .header("Authorization", "Bearer test-gateway-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("restricts the audit trail to administrators")
    void restrictsAuditTrail() throws Exception {
        mvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer "
                                + tokenFor(ResponderRole.RESPONDER, "responder")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + tokenFor(ResponderRole.ADMIN,
                                "admin")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("exposes health without deployment detail")
    void exposesHealthOnly() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("returns a structured error, never a stack trace")
    void returnsStructuredErrors() throws Exception {
        mvc.perform(get("/api/v1/incidents/RQ-DOES-NOT-EXIST")
                        .header("Authorization", "Bearer " + tokenFor(ResponderRole.OBSERVER,
                                "observer")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("echoes a request id so an action can be traced")
    void echoesRequestId() throws Exception {
        mvc.perform(get("/api/v1/incidents/overview")
                        .header("Authorization", "Bearer " + tokenFor(ResponderRole.OBSERVER,
                                "observer"))
                        .header("X-ResQNet-Request-Id", "trace-me-123"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("X-ResQNet-Request-Id", "trace-me-123"));
    }
}
