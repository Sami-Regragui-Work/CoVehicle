package org.multivehicle.covehicle.exception.conflict;

public class VehicleInUseException extends ConflictException {
    public VehicleInUseException(String message) { super(message); }
    public VehicleInUseException() { this("This vehicle is linked to a trip and cannot be deleted"); }
}
