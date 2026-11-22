package com.springproject.lovable_clone.common.error;

import lombok.Getter;

/** Entity absent or not accessible to the caller, HTTP 404. */
@Getter
public class ResourceNotFoundException extends RuntimeException {
    private final String resourceName;
    private final String resourceId;

    public ResourceNotFoundException(String resourceName, String resourceId) {
        super(resourceName + " with id " + resourceId + " not found");
        this.resourceName = resourceName;
        this.resourceId = resourceId;
    }
}
