package com.springproject.lovable_clone.intelligence.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** One conversation per (project, user), like the reference design. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "chat_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_sessions_project_user", columnNames = {"project_id", "user_id"}))
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "project_id", nullable = false)
    Long projectId;

    @Column(name = "user_id", nullable = false)
    Long userId;

    @CreationTimestamp
    Instant createdAt;

    @UpdateTimestamp
    Instant updatedAt;
}
