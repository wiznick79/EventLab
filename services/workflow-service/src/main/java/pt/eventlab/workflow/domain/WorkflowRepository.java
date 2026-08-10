package pt.eventlab.workflow.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WorkflowRepository extends JpaRepository<WorkflowRun, UUID> {
}
