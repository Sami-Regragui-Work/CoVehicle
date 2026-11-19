package org.multivehicle.covehicle.exception.conflict;

public class InsufficientSeatsException extends ConflictException {
    public InsufficientSeatsException(String message) { super(message); }
    public InsufficientSeatsException() { this("Not enough seats left on this trip"); }
}
