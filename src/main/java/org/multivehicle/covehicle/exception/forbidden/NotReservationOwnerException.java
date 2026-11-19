package org.multivehicle.covehicle.exception.forbidden;

public class NotReservationOwnerException extends ForbiddenException {
    public NotReservationOwnerException(String message) { super(message); }
    public NotReservationOwnerException() { this("This reservation belongs to another passenger"); }
}
