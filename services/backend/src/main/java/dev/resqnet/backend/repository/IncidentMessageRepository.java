package dev.resqnet.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.resqnet.backend.domain.IncidentMessage;

/** Persistence for per-message delivery metadata. */
public interface IncidentMessageRepository extends JpaRepository<IncidentMessage, Long> {

    List<IncidentMessage> findByIncidentIdOrderByCreatedAtAsc(Long incidentId);

    List<IncidentMessage> findByMessageId(String messageId);

    long countByIncidentId(Long incidentId);
}
