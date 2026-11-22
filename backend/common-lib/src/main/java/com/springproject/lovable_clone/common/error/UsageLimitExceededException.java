package com.springproject.lovable_clone.common.error;

/** A plan limit (tokens, projects, previews) was reached, HTTP 429. */
public class UsageLimitExceededException extends RuntimeException {
    public UsageLimitExceededException(String message) {
        super(message);
    }
}
