package com.springproject.lovable_clone.common.error;

/** Business-rule violation, mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
