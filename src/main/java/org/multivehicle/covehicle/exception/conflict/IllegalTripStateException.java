package org.multivehicle.covehicle.exception.conflict;

public class IllegalTripStateException extends ConflictException {
    public IllegalTripStateException(String message) { super(message); }
    public IllegalTripStateException() { this("Cannot make this trip status transition"); }
}
