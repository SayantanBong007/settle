package dev.settle.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecordEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IdempotencyRecordEntity() {
    }

    public IdempotencyRecordEntity(String idempotencyKey, String requestHash, String responseJson) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseJson = responseJson;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
