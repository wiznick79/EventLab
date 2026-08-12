package pt.eventlab.console.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pt.eventlab.console.domain.ExperimentRunRegistry;

@Service
class AutomaticRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutomaticRecoveryService.class);
    private final ExperimentRunRegistry runs;
    private final DeadLetterRecoveryService recovery;

    AutomaticRecoveryService(ExperimentRunRegistry runs, DeadLetterRecoveryService recovery) {
        this.runs = runs;
        this.recovery = recovery;
    }

    @Scheduled(fixedDelay = 1000)
    void recoverEligibleRuns() {
        for (var workflowId : runs.automaticRecoveryCandidates()) {
            if (!runs.claimAutomaticRecovery(workflowId)) continue;
            try {
                recovery.recover(workflowId, "automatic-policy");
            } catch (RuntimeException exception) {
                runs.releaseAutomaticRecovery(workflowId);
                LOGGER.warn("Automatic recovery failed for {}; it will be retried", workflowId, exception);
            }
        }
    }
}
