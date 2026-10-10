package com.springproject.lovable_clone.entity;

import java.time.Instant;

public class ChatSession {
    // one user can be part of only one project
    Project project;
    User user;
    String title;
    Instant createdAt;
    Instant updatedAt;
    Instant deletedAt;
}
