package dev.settle.account.repository;

import dev.settle.account.entity.LedgerEntryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {

    List<LedgerEntryEntity> findByAccountIdOrderByIdAsc(String accountId);

    List<LedgerEntryEntity> findByTransferIdOrderByIdAsc(String transferId);
}
