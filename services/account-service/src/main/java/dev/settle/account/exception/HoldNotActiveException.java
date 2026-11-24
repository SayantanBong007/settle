package dev.settle.account.exception;

public class HoldNotActiveException extends RuntimeException {
    public HoldNotActiveException(String holdId, String currentStatus) {
        super("Hold " + holdId + " is not PENDING (current status: " + currentStatus + ")");
    }
}
