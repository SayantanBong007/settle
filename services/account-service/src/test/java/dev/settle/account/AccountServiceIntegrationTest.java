package dev.settle.account;

import static org.assertj.core.api.Assertions.assertThat;

import dev.settle.account.dto.CancelHoldRequestDto;
import dev.settle.account.dto.CancelHoldResponseDto;
import dev.settle.account.dto.CompleteHoldRequestDto;
import dev.settle.account.dto.CompleteHoldResponseDto;
import dev.settle.account.dto.CreateHoldRequestDto;
import dev.settle.account.dto.CreateHoldResponseDto;
import dev.settle.account.entity.AccountEntity;
import dev.settle.account.entity.LedgerEntryEntity;
import dev.settle.account.repository.AccountRepository;
import dev.settle.account.repository.HoldRepository;
import dev.settle.account.repository.IdempotencyRecordRepository;
import dev.settle.account.repository.LedgerEntryRepository;
import dev.settle.account.service.AccountService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "settle.grpc.port=0" // Disable network port binding during DB integration tests
})
@Testcontainers
class AccountServiceIntegrationTest {

    private static final String ACC_A = "acc_sayantan_primary_wallet";
    private static final String ACC_B = "acc_rahul_savings_wallet";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private HoldRepository holdRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        holdRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        accountRepository.deleteAll();

        // Fund accounts through real double-entry postings from SYSTEM_FUNDING_INR
        // so Invariant #1 (sum of all ledger entries == 0) and Invariant #2 (balance == sum of account entries) hold!
        accountService.fundAccount(ACC_A, "sayantan", "INR", 100_000L);
        accountService.fundAccount(ACC_B, "rahul", "INR", 100_000L);
    }

    @Test
    void happyPath_createAndCompleteHold_movesBalancesAndPreservesAllLedgerInvariants() {
        // 1. Freeze ₹500 (50000 paise) on ACC_A bound to ACC_B (using normal-length 25+ char account IDs!)
        CreateHoldResponseDto holdRes = accountService.createHold(
                new CreateHoldRequestDto("tx1:hold", "tx1", ACC_A, ACC_B, 50_000L, "INR"));

        assertThat(holdRes.availableBalance()).isEqualTo(50_000L);
        assertThat(holdRes.pendingBalance()).isEqualTo(50_000L);

        // 2. Complete the hold to ACC_B
        CompleteHoldResponseDto completeRes = accountService.completeHold(
                new CompleteHoldRequestDto("tx1:complete", holdRes.holdId(), ACC_B));

        assertThat(completeRes.status()).isEqualTo("COMPLETED");

        // 3. Replaying createHold after completion returns the ORIGINAL snapshot (50_000 available, 50_000 pending), not today's 0 pending!
        CreateHoldResponseDto replayHoldRes = accountService.createHold(
                new CreateHoldRequestDto("tx1:hold", "tx1", ACC_A, ACC_B, 50_000L, "INR"));
        assertThat(replayHoldRes).isEqualTo(holdRes);

        // 4. Verify final balances AND Invariant #1 & #2
        assertLedgerInvariantsHold();
        AccountEntity accA = accountRepository.findById(ACC_A).orElseThrow();
        AccountEntity accB = accountRepository.findById(ACC_B).orElseThrow();
        assertThat(accA.getAvailableBalance()).isEqualTo(50_000L);
        assertThat(accA.getPendingBalance()).isEqualTo(0L);
        assertThat(accB.getAvailableBalance()).isEqualTo(150_000L);
    }

    @Test
    void rollbackPath_createAndCancelHold_restoresAvailableBalanceAndPreservesInvariants() {
        CreateHoldResponseDto holdRes = accountService.createHold(
                new CreateHoldRequestDto("tx2:hold", "tx2", ACC_A, ACC_B, 30_000L, "INR"));

        CancelHoldResponseDto cancelRes = accountService.cancelHold(
                new CancelHoldRequestDto("tx2:cancel", holdRes.holdId(), "BANK_FAILED"));

        assertThat(cancelRes.cancelled()).isTrue();
        assertThat(cancelRes.availableBalance()).isEqualTo(100_000L);
        assertThat(ledgerEntryRepository.findByTransferIdOrderByIdAsc("tx2")).isEmpty();
        assertLedgerInvariantsHold();
    }

    @Test
    void breakIt_50ParallelDuplicateRequestsWithSameIdempotencyKey_chargesExactlyOnce() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startGate = new CountDownLatch(1);
        Set<String> returnedHoldIds = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit((Callable<Void>) () -> {
                startGate.await();
                CreateHoldResponseDto res = accountService.createHold(
                        new CreateHoldRequestDto("tx_dup:hold", "tx_dup", ACC_A, ACC_B, 40_000L, "INR"));
                returnedHoldIds.add(res.holdId());
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(returnedHoldIds).hasSize(1);
        AccountEntity accA = accountRepository.findById(ACC_A).orElseThrow();
        assertThat(accA.getAvailableBalance()).isEqualTo(60_000L);
        assertThat(accA.getPendingBalance()).isEqualTo(40_000L);
        assertLedgerInvariantsHold();
    }

    @Test
    void breakIt_simultaneousCrossTransfers_AtoB_and_BtoA_zeroDeadlocksAndZeroMoneyLost() throws Exception {
        int pairs = 10;
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < pairs; i++) {
            final int idx = i;
            futures.add(pool.submit((Callable<Void>) () -> {
                startGate.await();
                String txId = "tx_AB_" + idx;
                CreateHoldResponseDto h = accountService.createHold(
                        new CreateHoldRequestDto(txId + ":hold", txId, ACC_A, ACC_B, 1_000L, "INR"));
                accountService.completeHold(
                        new CompleteHoldRequestDto(txId + ":complete", h.holdId(), ACC_B));
                return null;
            }));
            futures.add(pool.submit((Callable<Void>) () -> {
                startGate.await();
                String txId = "tx_BA_" + idx;
                CreateHoldResponseDto h = accountService.createHold(
                        new CreateHoldRequestDto(txId + ":hold", txId, ACC_B, ACC_A, 1_000L, "INR"));
                accountService.completeHold(
                        new CompleteHoldRequestDto(txId + ":complete", h.holdId(), ACC_A));
                return null;
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        AccountEntity accA = accountRepository.findById(ACC_A).orElseThrow();
        AccountEntity accB = accountRepository.findById(ACC_B).orElseThrow();
        assertThat(accA.getAvailableBalance()).isEqualTo(100_000L);
        assertThat(accB.getAvailableBalance()).isEqualTo(100_000L);
        assertLedgerInvariantsHold();
    }

    private void assertLedgerInvariantsHold() {
        // Invariant #1: Sum of all ledger entries across the entire database == 0
        long globalNet = ledgerEntryRepository.findAll().stream()
                .mapToLong(e -> e.getEntryType() == LedgerEntryEntity.EntryType.CREDIT ? e.getAmount() : -e.getAmount())
                .sum();
        assertThat(globalNet).isZero();

        // Invariant #2: Every account's total balance (available + pending) == sum of its ledger entries
        for (AccountEntity account : accountRepository.findAll()) {
            long sumOfEntries = ledgerEntryRepository.findByAccountIdOrderByIdAsc(account.getId()).stream()
                    .mapToLong(e -> e.getEntryType() == LedgerEntryEntity.EntryType.CREDIT ? e.getAmount() : -e.getAmount())
                    .sum();
            assertThat(account.getAvailableBalance() + account.getPendingBalance())
                    .as("Balance of account %s must equal sum of its ledger entries", account.getId())
                    .isEqualTo(sumOfEntries);
        }
    }
}
