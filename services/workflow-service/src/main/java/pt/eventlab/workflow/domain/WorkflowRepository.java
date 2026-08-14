package pt.eventlab.workflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.eventlab.contracts.WorkflowState;

interface WorkflowRepository extends JpaRepository<WorkflowRun, UUID> {
    Optional<WorkflowRun> findByExperimentPlanId(UUID experimentPlanId);
    List<WorkflowRun> findByStateInAndStepDeadlineBefore(List<WorkflowState> states, Instant deadline);
}
