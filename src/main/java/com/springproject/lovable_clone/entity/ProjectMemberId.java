package com.springproject.lovable_clone.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

// can be put easily inside any of the table
// and inside table easily embedded
// suppose some field going to repeat in multiple tables
// so make that embeddable
@Embeddable
@RequiredArgsConstructor
@AllArgsConstructor
public class ProjectMemberId {
    Long projectId;
    Long userId;

}
