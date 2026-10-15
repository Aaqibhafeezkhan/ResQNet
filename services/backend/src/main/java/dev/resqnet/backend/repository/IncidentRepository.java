package dev.resqnet.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.resqnet.backend.domain.Incident;
import dev.resqnet.backend.domain.IncidentStatus;

/** Persistence for incidents. */
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByReference(String reference);

    Page<Incident> findByStatusInOrderBySeverityDescCreatedAtDesc(List<IncidentStatus> statuses,
                                                                 Pageable pageable);

    Page<Incident> findAllByOrderBySeverityDescCreatedAtDesc(Pageable pageable);

    /** Count used by the dashboard header. */
    long countByStatus(IncidentStatus status);

    @Query("select count(i) from Incident i where i.status not in "
            + "(dev.resqnet.backend.domain.IncidentStatus.RESOLVED, "
            + "dev.resqnet.backend.domain.IncidentStatus.DISMISSED)")
    long countOpen();

    /** Incidents that have been waiting longest, for the queue view. */
    @Query("select i from Incident i where i.status in "
            + "(dev.resqnet.backend.domain.IncidentStatus.NEW) "
            + "order by i.createdAt asc")
    List<Incident> findWaitingOldest(Pageable pageable);
}
