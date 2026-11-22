package com.springproject.lovable_clone.common.error;

/** The request conflicts with current state (e.g. a generation already running), HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
