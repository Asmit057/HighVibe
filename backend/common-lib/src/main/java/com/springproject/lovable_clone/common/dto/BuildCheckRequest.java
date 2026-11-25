package com.springproject.lovable_clone.common.dto;

import java.util.Map;

/** Files (path to content) to put over the saved project before it is test-built. */
public record BuildCheckRequest(Map<String, String> files) {}
