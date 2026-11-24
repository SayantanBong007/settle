package dev.settle.account.dao;

import dev.settle.account.entity.AccountEntity;
import dev.settle.account.entity.HoldEntity;
import dev.settle.account.entity.IdempotencyRecordEntity;
import dev.settle.account.entity.LedgerEntryEntity;
import java.util.List;
import java.util.Optional;

public interface AccountDao {

    Optional<AccountEntity> findAccountById(String accountId);

    Optional<AccountEntity> findAccountByIdForUpdate(String accountId);

    AccountEntity saveAccount(AccountEntity account);

    Optional<HoldEntity> findHoldByIdForUpdate(String holdId);

    HoldEntity saveHold(HoldEntity hold);

    void saveLedgerEntries(List<LedgerEntryEntity> entries);

    Optional<IdempotencyRecordEntity> findIdempotencyRecord(String idempotencyKey);

    IdempotencyRecordEntity saveIdempotencyRecord(IdempotencyRecordEntity record);
}
