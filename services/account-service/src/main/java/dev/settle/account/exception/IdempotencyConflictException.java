package dev.settle.account.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key '" + idempotencyKey + "' reused with different request parameters");
    }
}
