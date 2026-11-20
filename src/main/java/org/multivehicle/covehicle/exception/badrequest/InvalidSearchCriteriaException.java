package org.multivehicle.covehicle.exception.badrequest;

public class InvalidSearchCriteriaException extends BadRequestException {
    public InvalidSearchCriteriaException(String message) { super(message); }
    public InvalidSearchCriteriaException() { this("Invalid search criteria"); }
}
