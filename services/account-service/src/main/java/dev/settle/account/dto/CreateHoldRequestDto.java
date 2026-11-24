package dev.settle.account.dto;

public record CreateHoldRequestDto(
        String idempotencyKey,
        String transferId,
        String sourceAccountId,
        String destinationAccountId,
        long amount,
        String currency
) {}
