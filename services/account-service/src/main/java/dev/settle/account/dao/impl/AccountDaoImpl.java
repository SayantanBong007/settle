package dev.settle.account.dao.impl;

import dev.settle.account.dao.AccountDao;
import dev.settle.account.entity.AccountEntity;
import dev.settle.account.entity.HoldEntity;
import dev.settle.account.entity.IdempotencyRecordEntity;
import dev.settle.account.entity.LedgerEntryEntity;
import dev.settle.account.repository.AccountRepository;
import dev.settle.account.repository.HoldRepository;
import dev.settle.account.repository.IdempotencyRecordRepository;
import dev.settle.account.repository.LedgerEntryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AccountDaoImpl implements AccountDao {

    private final AccountRepository accountRepository;
    private final HoldRepository holdRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public AccountDaoImpl(
            AccountRepository accountRepository,
            HoldRepository holdRepository,
            LedgerEntryRepository ledgerEntryRepository,
            IdempotencyRecordRepository idempotencyRecordRepository) {
        this.accountRepository = accountRepository;
        this.holdRepository = holdRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Override
    public Optional<AccountEntity> findAccountById(String accountId) {
        return accountRepository.findById(accountId);
    }

    @Override
    public Optional<AccountEntity> findAccountByIdForUpdate(String accountId) {
        return accountRepository.findByIdForUpdate(accountId);
    }

    @Override
    public AccountEntity saveAccount(AccountEntity account) {
        return accountRepository.save(account);
    }

    @Override
    public Optional<HoldEntity> findHoldByIdForUpdate(String holdId) {
        return holdRepository.findByIdForUpdate(holdId);
    }

    @Override
    public HoldEntity saveHold(HoldEntity hold) {
        return holdRepository.save(hold);
    }

    @Override
    public void saveLedgerEntries(List<LedgerEntryEntity> entries) {
        ledgerEntryRepository.saveAll(entries);
    }

    @Override
    public Optional<IdempotencyRecordEntity> findIdempotencyRecord(String idempotencyKey) {
        return idempotencyRecordRepository.findById(idempotencyKey);
    }

    @Override
    public IdempotencyRecordEntity saveIdempotencyRecord(IdempotencyRecordEntity record) {
        return idempotencyRecordRepository.save(record);
    }
}
