package org.multivehicle.covehicle.exception.badrequest;

public class SeatsExceedCapacityException extends BadRequestException {
    public SeatsExceedCapacityException(String message) { super(message); }
    public SeatsExceedCapacityException() { this("Offered seats exceed the vehicle capacity"); }
}
