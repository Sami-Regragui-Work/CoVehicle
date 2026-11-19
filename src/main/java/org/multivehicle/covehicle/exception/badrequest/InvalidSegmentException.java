package org.multivehicle.covehicle.exception.badrequest;

public class InvalidSegmentException extends BadRequestException {
    public InvalidSegmentException(String message) { super(message); }
    public InvalidSegmentException() { this("Pick-up stop must come before drop-off stop on the same trip"); }
}
