package dev.settle.account.repository;

import dev.settle.account.entity.IdempotencyRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
}
