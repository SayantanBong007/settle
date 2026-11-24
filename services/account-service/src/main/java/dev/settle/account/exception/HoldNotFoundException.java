package dev.settle.account.exception;

public class HoldNotFoundException extends RuntimeException {
    public HoldNotFoundException(String holdId) {
        super("Hold not found: " + holdId);
    }
}
