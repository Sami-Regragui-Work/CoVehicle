package org.multivehicle.covehicle.exception.notfound;

public class ResourceNotFoundException extends NotFoundException {
    public ResourceNotFoundException(String message) { super(message); }
    public ResourceNotFoundException() { super(); }
}
