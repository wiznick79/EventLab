package pt.eventlab.fulfilment.api;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pt.eventlab.fulfilment.domain.FulfilmentApplicationService;
import pt.eventlab.contracts.messages.FulfilmentRecoveryRequested;

@RestController
@RequestMapping("/api/v1/fulfilments")
class FulfilmentRecoveryController {
    private final FulfilmentApplicationService fulfilments;

    FulfilmentRecoveryController(FulfilmentApplicationService fulfilments) {
        this.fulfilments = fulfilments;
    }

    @PostMapping("/{workflowId}/recover")
    ResponseEntity<Void> recover(
            @PathVariable UUID workflowId,
            @RequestBody FulfilmentRecoveryRequested request) {
        if (!workflowId.equals(request.workflowId())) {
            throw new IllegalArgumentException("Path and recovery workflow IDs must match");
        }
        fulfilments.recover(request);
        return ResponseEntity.accepted().build();
    }
}
