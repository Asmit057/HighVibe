package com.springproject.lovable_clone.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * The single error contract shared by every HighVibe service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        HttpStatus status,
        String message,
        Instant timestamp,
        List<ApiFieldError> errors
) {
    public ApiError(HttpStatus status, String message) {
        this(status, message, Instant.now(), null);
    }

    public ApiError(HttpStatus status, String message, List<ApiFieldError> errors) {
        this(status, message, Instant.now(), errors);
    }

    public record ApiFieldError(String field, String message) {}
}
