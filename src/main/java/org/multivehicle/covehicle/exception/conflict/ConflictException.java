package org.multivehicle.covehicle.exception.conflict;

import org.multivehicle.covehicle.exception.BaseException;

public abstract class ConflictException extends BaseException {
    protected ConflictException(String message) { super(message); }
    protected ConflictException() { this("Request conflicts with the current state"); }
}
