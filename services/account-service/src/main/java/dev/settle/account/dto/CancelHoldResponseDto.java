package dev.settle.account.dto;

public record CancelHoldResponseDto(
        boolean cancelled,
        long availableBalance
) {}
