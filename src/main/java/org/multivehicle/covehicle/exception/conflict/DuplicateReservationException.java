package org.multivehicle.covehicle.exception.conflict;

public class DuplicateReservationException extends ConflictException {
    public DuplicateReservationException(String message) { super(message); }
    public DuplicateReservationException() { this("This passenger already has a confirmed reservation on this trip"); }
}
