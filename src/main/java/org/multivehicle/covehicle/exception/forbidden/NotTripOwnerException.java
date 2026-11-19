package org.multivehicle.covehicle.exception.forbidden;

public class NotTripOwnerException extends ForbiddenException {
    public NotTripOwnerException(String message) { super(message); }
    public NotTripOwnerException() { this("This trip belongs to another driver"); }
}
