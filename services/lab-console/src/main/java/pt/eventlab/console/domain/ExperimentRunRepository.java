package pt.eventlab.console.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.eventlab.contracts.RecoveryMode;

interface ExperimentRunRepository extends JpaRepository<ExperimentRun, UUID> {
    List<ExperimentRun> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ExperimentRun> findByRecoveryModeAndRecoveryClaimedFalse(RecoveryMode recoveryMode);

    @Modifying
    @Query("update ExperimentRun run set run.recoveryClaimed = true "
            + "where run.workflowId = :workflowId and run.recoveryClaimed = false")
    int claimRecovery(@Param("workflowId") UUID workflowId);

    @Modifying
    @Query("update ExperimentRun run set run.recoveryClaimed = false where run.workflowId = :workflowId")
    void releaseRecovery(@Param("workflowId") UUID workflowId);
}
