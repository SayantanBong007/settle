package dev.settle.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.settle.account.dao.AccountDao;
import dev.settle.account.dto.CancelHoldRequestDto;
import dev.settle.account.dto.CancelHoldResponseDto;
import dev.settle.account.dto.CompleteHoldRequestDto;
import dev.settle.account.dto.CompleteHoldResponseDto;
import dev.settle.account.dto.CreateHoldRequestDto;
import dev.settle.account.dto.CreateHoldResponseDto;
import dev.settle.account.entity.AccountEntity;
import dev.settle.account.entity.HoldEntity;
import dev.settle.account.entity.IdempotencyRecordEntity;
import dev.settle.account.entity.LedgerEntryEntity;
import dev.settle.account.exception.AccountNotFoundException;
import dev.settle.account.exception.HoldNotActiveException;
import dev.settle.account.exception.HoldNotFoundException;
import dev.settle.account.exception.IdempotencyConflictException;
import dev.settle.account.exception.InsufficientFundsException;
import dev.settle.account.service.AccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    public static final String SYSTEM_FUNDING_ACCOUNT_ID = "SYSTEM_FUNDING";

    private final AccountDao accountDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceImpl(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    @Override
    @Transactional
    public void fundAccount(String accountId, String ownerId, String currency, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Funding amount must be positive");
        }
        String systemAccountId = SYSTEM_FUNDING_ACCOUNT_ID + "_" + currency;
        AccountEntity systemAccount = accountDao.findAccountByIdForUpdate(systemAccountId)
                .orElseGet(() -> accountDao.saveAccount(new AccountEntity(systemAccountId, "SYSTEM", currency, 0L)));

        AccountEntity targetAccount = accountDao.findAccountByIdForUpdate(accountId)
                .orElseGet(() -> accountDao.saveAccount(new AccountEntity(accountId, ownerId, currency, 0L)));

        systemAccount.setAvailableBalance(systemAccount.getAvailableBalance() - amount);
        targetAccount.setAvailableBalance(targetAccount.getAvailableBalance() + amount);
        accountDao.saveAccount(systemAccount);
        accountDao.saveAccount(targetAccount);

        String fundingTxId = "fund_" + UUID.randomUUID();
        LedgerEntryEntity debitSystem = new LedgerEntryEntity(
                fundingTxId, null, systemAccountId, LedgerEntryEntity.EntryType.DEBIT, amount);
        LedgerEntryEntity creditTarget = new LedgerEntryEntity(
                fundingTxId, null, accountId, LedgerEntryEntity.EntryType.CREDIT, amount);
        accountDao.saveLedgerEntries(List.of(debitSystem, creditTarget));
    }

    @Override
    @Transactional
    public CreateHoldResponseDto createHold(CreateHoldRequestDto request) {
        if (request.amount() <= 0) {
            throw new IllegalArgumentException("Hold amount must be positive");
        }
        if (request.destinationAccountId() == null || request.destinationAccountId().isBlank()) {
            throw new IllegalArgumentException("Destination account ID is required");
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts cannot be the same");
        }

        // Lock sender account row first so concurrent duplicate requests serialize on the row lock
        AccountEntity account = accountDao.findAccountByIdForUpdate(request.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.sourceAccountId()));

        // Real 64-char SHA-256 hex digest (always fits VARCHAR(64) regardless of account ID length!)
        String requestHash = sha256("CREATE_HOLD:" + request.transferId() + ":" + request.sourceAccountId()
                + ":" + request.destinationAccountId() + ":" + request.amount() + ":" + request.currency());

        Optional<IdempotencyRecordEntity> existing = accountDao.findIdempotencyRecord(request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(request.idempotencyKey());
            }
            return fromJson(existing.get().getResponseJson(), CreateHoldResponseDto.class);
        }

        if (!account.getCurrency().equals(request.currency())) {
            throw new IllegalArgumentException("Currencies do not match");
        }
        if (account.getAvailableBalance() < request.amount()) {
            throw new InsufficientFundsException("Insufficient available balance on account " + request.sourceAccountId());
        }

        account.setAvailableBalance(account.getAvailableBalance() - request.amount());
        account.setPendingBalance(account.getPendingBalance() + request.amount());
        accountDao.saveAccount(account);

        String holdId = UUID.randomUUID().toString();
        HoldEntity hold = new HoldEntity(
                holdId,
                request.transferId(),
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.currency(),
                Instant.now().plusSeconds(1800)
        );
        accountDao.saveHold(hold);

        CreateHoldResponseDto response = new CreateHoldResponseDto(
                holdId, account.getAvailableBalance(), account.getPendingBalance());
        accountDao.saveIdempotencyRecord(new IdempotencyRecordEntity(
                request.idempotencyKey(),
                requestHash,
                toJson(response)
        ));

        return response;
    }

    @Override
    @Transactional
    public CompleteHoldResponseDto completeHold(CompleteHoldRequestDto request) {
        HoldEntity hold = accountDao.findHoldByIdForUpdate(request.holdId())
                .orElseThrow(() -> new HoldNotFoundException(request.holdId()));

        String destId = (request.destinationAccountId() == null || request.destinationAccountId().isBlank())
                ? hold.getDestinationAccountId()
                : request.destinationAccountId();

        if (!hold.getDestinationAccountId().equals(destId)) {
            throw new IllegalArgumentException(
                    "Destination account mismatch: hold bound to " + hold.getDestinationAccountId() + " but got " + destId);
        }

        String requestHash = sha256("COMPLETE_HOLD:" + request.holdId() + ":" + destId);
        Optional<IdempotencyRecordEntity> existing = accountDao.findIdempotencyRecord(request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(request.idempotencyKey());
            }
            return fromJson(existing.get().getResponseJson(), CompleteHoldResponseDto.class);
        }

        if (hold.getStatus() != HoldEntity.HoldStatus.PENDING) {
            throw new HoldNotActiveException(hold.getId(), hold.getStatus().name());
        }

        String sourceId = hold.getSourceAccountId();

        // Lock BOTH accounts in alphabetical ID order to prevent deadlocks
        AccountEntity sourceAccount;
        AccountEntity destAccount;
        if (sourceId.compareTo(destId) < 0) {
            sourceAccount = accountDao.findAccountByIdForUpdate(sourceId)
                    .orElseThrow(() -> new AccountNotFoundException(sourceId));
            destAccount = accountDao.findAccountByIdForUpdate(destId)
                    .orElseThrow(() -> new AccountNotFoundException(destId));
        } else {
            destAccount = accountDao.findAccountByIdForUpdate(destId)
                    .orElseThrow(() -> new AccountNotFoundException(destId));
            sourceAccount = accountDao.findAccountByIdForUpdate(sourceId)
                    .orElseThrow(() -> new AccountNotFoundException(sourceId));
        }

        long amount = hold.getAmount();
        if (sourceAccount.getPendingBalance() < amount) {
            throw new InsufficientFundsException("Insufficient pending balance on account " + sourceId);
        }

        sourceAccount.setPendingBalance(sourceAccount.getPendingBalance() - amount);
        destAccount.setAvailableBalance(destAccount.getAvailableBalance() + amount);
        accountDao.saveAccount(sourceAccount);
        accountDao.saveAccount(destAccount);

        hold.setStatus(HoldEntity.HoldStatus.COMPLETED);
        accountDao.saveHold(hold);

        LedgerEntryEntity debitEntry = new LedgerEntryEntity(
                hold.getTransferId(), hold.getId(), sourceId, LedgerEntryEntity.EntryType.DEBIT, amount);
        LedgerEntryEntity creditEntry = new LedgerEntryEntity(
                hold.getTransferId(), hold.getId(), destId, LedgerEntryEntity.EntryType.CREDIT, amount);
        accountDao.saveLedgerEntries(List.of(debitEntry, creditEntry));

        CompleteHoldResponseDto response = new CompleteHoldResponseDto(
                hold.getTransferId(), HoldEntity.HoldStatus.COMPLETED.name());
        accountDao.saveIdempotencyRecord(new IdempotencyRecordEntity(
                request.idempotencyKey(),
                requestHash,
                toJson(response)
        ));

        return response;
    }

    @Override
    @Transactional
    public CancelHoldResponseDto cancelHold(CancelHoldRequestDto request) {
        HoldEntity hold = accountDao.findHoldByIdForUpdate(request.holdId())
                .orElseThrow(() -> new HoldNotFoundException(request.holdId()));

        String requestHash = sha256("CANCEL_HOLD:" + request.holdId());
        Optional<IdempotencyRecordEntity> existing = accountDao.findIdempotencyRecord(request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException(request.idempotencyKey());
            }
            return fromJson(existing.get().getResponseJson(), CancelHoldResponseDto.class);
        }

        if (hold.getStatus() != HoldEntity.HoldStatus.PENDING) {
            throw new HoldNotActiveException(hold.getId(), hold.getStatus().name());
        }

        AccountEntity sourceAccount = accountDao.findAccountByIdForUpdate(hold.getSourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(hold.getSourceAccountId()));

        long amount = hold.getAmount();
        if (sourceAccount.getPendingBalance() < amount) {
            throw new InsufficientFundsException("Insufficient pending balance to cancel on account " + hold.getSourceAccountId());
        }
        sourceAccount.setPendingBalance(sourceAccount.getPendingBalance() - amount);
        sourceAccount.setAvailableBalance(sourceAccount.getAvailableBalance() + amount);
        accountDao.saveAccount(sourceAccount);

        hold.setStatus(HoldEntity.HoldStatus.CANCELLED);
        hold.setCancelReason(request.cancelReason());
        accountDao.saveHold(hold);

        CancelHoldResponseDto response = new CancelHoldResponseDto(true, sourceAccount.getAvailableBalance());
        accountDao.saveIdempotencyRecord(new IdempotencyRecordEntity(
                request.idempotencyKey(),
                requestHash,
                toJson(response)
        ));

        return response;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
