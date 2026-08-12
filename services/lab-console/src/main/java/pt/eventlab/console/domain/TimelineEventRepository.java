package pt.eventlab.console.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {
    List<TimelineEvent> findByWorkflowIdOrderBySequenceNumber(UUID workflowId);
    TimelineEvent findFirstByWorkflowIdOrderBySequenceNumberDesc(UUID workflowId);
}
