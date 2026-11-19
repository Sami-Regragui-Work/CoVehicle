package org.multivehicle.covehicle.exception.forbidden;

public class NotVehicleOwnerException extends ForbiddenException {
    public NotVehicleOwnerException(String message) { super(message); }
    public NotVehicleOwnerException() { this("This vehicle belongs to another driver"); }
}
