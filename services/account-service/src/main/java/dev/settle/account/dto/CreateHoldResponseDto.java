package dev.settle.account.dto;

public record CreateHoldResponseDto(
        String holdId,
        long availableBalance,
        long pendingBalance
) {}
