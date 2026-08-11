package pt.eventlab.payment.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByWorkflowId(UUID workflowId);
}
