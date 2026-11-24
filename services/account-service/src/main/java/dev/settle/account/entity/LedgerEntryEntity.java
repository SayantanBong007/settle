package dev.settle.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntryEntity {

    public enum EntryType {
        DEBIT,
        CREDIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "transfer_id", nullable = false, length = 64, updatable = false)
    private String transferId;

    @Column(name = "hold_id", length = 64, updatable = false)
    private String holdId;

    @Column(name = "account_id", nullable = false, length = 64, updatable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10, updatable = false)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LedgerEntryEntity() {
    }

    public LedgerEntryEntity(String transferId, String holdId, String accountId, EntryType entryType, long amount) {
        this.transferId = transferId;
        this.holdId = holdId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTransferId() { return transferId; }
    public String getHoldId() { return holdId; }
    public String getAccountId() { return accountId; }
    public EntryType getEntryType() { return entryType; }
    public long getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
