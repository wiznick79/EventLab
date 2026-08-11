package pt.eventlab.fulfilment.domain;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FulfilmentRepository extends JpaRepository<Fulfilment, UUID> {
    Optional<Fulfilment> findByWorkflowId(UUID workflowId);
    List<Fulfilment> findByStaleEventSentFalseAndStaleEventDueAtBefore(Instant now);
}
