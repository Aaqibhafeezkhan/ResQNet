package dev.resqnet.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import dev.resqnet.backend.domain.AuditEvent;

/**
 * Persistence for the audit trail.
 *
 * <p>Only {@code save} and read methods are declared. There is deliberately no
 * delete: an application that can remove its own audit records has no audit
 * trail.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /**
     * Chronological order for one target.
     *
     * <p>Ordered by id rather than timestamp: two transitions in the same
     * millisecond would otherwise be returned in an undefined order, which makes
     * the audit trail untestable and confusing to read.
     */
    List<AuditEvent> findTop100ByTargetTypeAndTargetIdOrderByIdAsc(String targetType,
                                                                    String targetId);
}
