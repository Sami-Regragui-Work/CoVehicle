package org.multivehicle.covehicle.exception.badrequest;

public class InvalidStopSequenceException extends BadRequestException {
    public InvalidStopSequenceException(String message) { super(message); }
    public InvalidStopSequenceException() { this("Invalid stops for this trip"); }
}
