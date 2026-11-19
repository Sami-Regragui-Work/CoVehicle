package org.multivehicle.covehicle.exception.conflict;

public class CancellationTooLateException extends ConflictException {
    public CancellationTooLateException(String message) { super(message); }
    public CancellationTooLateException() { this("A reservation can only be canceled up to 24 hours before departure"); }
}
