package org.multivehicle.covehicle.exception.conflict;

public class TripNotStartedException extends ConflictException {
    public TripNotStartedException(String message) { super(message); }
    public TripNotStartedException() { this("A trip can only be completed after its departure time"); }
}
