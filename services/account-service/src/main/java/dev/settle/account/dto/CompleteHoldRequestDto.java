package dev.settle.account.dto;

public record CompleteHoldRequestDto(
        String idempotencyKey,
        String holdId,
        String destinationAccountId
) {}
