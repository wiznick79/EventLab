package pt.eventlab.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;
    private UUID workflowId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant authorizedAt;

    protected Payment() {
    }

    private Payment(UUID id, UUID workflowId, BigDecimal amount, String currency, Instant authorizedAt) {
        this.id = id;
        this.workflowId = workflowId;
        this.amount = amount;
        this.currency = currency;
        this.status = "AUTHORIZED";
        this.authorizedAt = authorizedAt;
    }

    static Payment authorize(UUID workflowId, BigDecimal amount, String currency, Instant now) {
        return new Payment(UUID.randomUUID(), workflowId, amount, currency, now);
    }

    public UUID id() { return id; }
    public UUID workflowId() { return workflowId; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
}
