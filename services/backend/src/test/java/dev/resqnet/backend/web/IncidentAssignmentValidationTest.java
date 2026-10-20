package dev.resqnet.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

import dev.resqnet.backend.domain.Category;
import dev.resqnet.backend.domain.Incident;
import dev.resqnet.backend.domain.ResponderAccount;
import dev.resqnet.backend.domain.ResponderRole;
import dev.resqnet.backend.repository.IncidentRepository;
import dev.resqnet.backend.repository.ResponderAccountRepository;
import dev.resqnet.backend.security.JwtService;
import dev.resqnet.backend.service.IncidentService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IncidentAssignmentValidationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private ResponderAccountRepository accounts;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentService incidents;

    private String token;
    private Incident incident;
    private ResponderAccount assignee;

    @BeforeEach
    void setUp() {
        ResponderAccount responder = accounts.save(new ResponderAccount(
                "assigner-" + System.nanoTime(), "Assigner",
                encoder.encode("pw"), ResponderRole.RESPONDER));

        assignee = accounts.save(new ResponderAccount(
                "assignee-" + System.nanoTime(), "Assignee",
                encoder.encode("pw"), ResponderRole.RESPONDER));

        token = jwt.issue(responder.getUsername(), ResponderRole.RESPONDER,
                responder.getId());

        incident = incidents.createFromReport(
                "Assignment validation test", Category.MEDICAL, 0.7,
                "rqn1_assignment_test", "rqn1_gateway", null, null,
                "req-assignment-test-" + System.nanoTime(), false);
    }

    @Test
    @DisplayName("assigns an incident through HTTP and persists the assignee")
    void assignsIncidentThroughHttp() throws Exception {
        mvc.perform(post("/api/v1/incidents/{reference}/assign",
                        incident.getReference())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + assignee.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value(assignee.getId()));

        assertThat(incidentRepository.findByReference(incident.getReference())
                .orElseThrow().getAssignedTo()).isEqualTo(assignee.getId());
    }

    @Test
    @DisplayName("rejects a null account ID with a validation error")
    void rejectsNullAccountId() throws Exception {
        mvc.perform(post("/api/v1/incidents/{reference}/assign",
                        incident.getReference())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.accountId").exists());
    }

    @Test
    @DisplayName("rejects a missing account ID with a validation error")
    void rejectsMissingAccountId() throws Exception {
        mvc.perform(post("/api/v1/incidents/{reference}/assign",
                        incident.getReference())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.accountId").exists());
    }
}
