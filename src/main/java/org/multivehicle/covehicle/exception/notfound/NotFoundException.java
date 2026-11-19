package org.multivehicle.covehicle.exception.notfound;

import org.multivehicle.covehicle.exception.BaseException;

public abstract class NotFoundException extends BaseException {
    protected NotFoundException(String message) { super(message); }
    protected NotFoundException() { this("Resource not found"); }
}
