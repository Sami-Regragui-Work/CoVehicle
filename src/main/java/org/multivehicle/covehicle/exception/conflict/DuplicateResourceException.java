package org.multivehicle.covehicle.exception.conflict;

public class DuplicateResourceException extends ConflictException {
    public DuplicateResourceException(String message) { super(message); }
    public DuplicateResourceException() { this("This value is already used"); }
}
