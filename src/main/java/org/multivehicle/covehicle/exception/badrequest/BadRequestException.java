package org.multivehicle.covehicle.exception.badrequest;

import org.multivehicle.covehicle.exception.BaseException;

public abstract class BadRequestException extends BaseException {
    protected BadRequestException(String message) { super(message); }
    protected BadRequestException() { this("Invalid request"); }
}
