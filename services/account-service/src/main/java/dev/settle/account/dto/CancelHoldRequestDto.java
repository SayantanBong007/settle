package dev.settle.account.dto;

public record CancelHoldRequestDto(
        String idempotencyKey,
        String holdId,
        String cancelReason
) {}
