package dev.settle.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "holds")
public class HoldEntity {

    public enum HoldStatus {
        PENDING,
        COMPLETED,
        CANCELLED
    }

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "transfer_id", nullable = false, length = 64)
    private String transferId;

    @Column(name = "source_account_id", nullable = false, length = 64)
    private String sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, length = 64)
    private String destinationAccountId;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HoldStatus status;

    @Column(name = "cancel_reason", length = 128)
    private String cancelReason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public HoldEntity() {
    }

    public HoldEntity(
            String id,
            String transferId,
            String sourceAccountId,
            String destinationAccountId,
            long amount,
            String currency,
            Instant expiresAt) {
        this.id = id;
        this.transferId = transferId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = HoldStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }

    public String getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(String destinationAccountId) { this.destinationAccountId = destinationAccountId; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public HoldStatus getStatus() { return status; }
    public void setStatus(HoldStatus status) { this.status = status; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
