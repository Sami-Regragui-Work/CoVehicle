package org.multivehicle.covehicle.exception.forbidden;

import org.multivehicle.covehicle.exception.BaseException;

public abstract class ForbiddenException extends BaseException {
    protected ForbiddenException(String message) { super(message); }
    protected ForbiddenException() { this("You are not allowed to perform this action"); }
}
