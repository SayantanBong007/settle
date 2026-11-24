package dev.settle.account.service;

import dev.settle.account.dto.CancelHoldRequestDto;
import dev.settle.account.dto.CancelHoldResponseDto;
import dev.settle.account.dto.CompleteHoldRequestDto;
import dev.settle.account.dto.CompleteHoldResponseDto;
import dev.settle.account.dto.CreateHoldRequestDto;
import dev.settle.account.dto.CreateHoldResponseDto;

public interface AccountService {

    /**
     * Creates (if needed) and funds an account via a double-entry ledger posting from SYSTEM_FUNDING,
     * preserving Invariant #1 (sum of all entries = 0) and Invariant #2 (balance = sum of entries).
     */
    void fundAccount(String accountId, String ownerId, String currency, long amount);

    CreateHoldResponseDto createHold(CreateHoldRequestDto request);

    CompleteHoldResponseDto completeHold(CompleteHoldRequestDto request);

    CancelHoldResponseDto cancelHold(CancelHoldRequestDto request);
}
