package dev.settle.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "available_balance", nullable = false)
    private long availableBalance;

    @Column(name = "pending_balance", nullable = false)
    private long pendingBalance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AccountEntity() {
    }

    public AccountEntity(String id, String ownerId, String currency, long availableBalance) {
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.availableBalance = availableBalance;
        this.pendingBalance = 0L;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public long getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(long availableBalance) { this.availableBalance = availableBalance; }

    public long getPendingBalance() { return pendingBalance; }
    public void setPendingBalance(long pendingBalance) { this.pendingBalance = pendingBalance; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
