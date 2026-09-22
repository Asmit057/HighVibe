package com.springproject.lovable_clone.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;


@Getter
@Setter
// make all fields private dont need to make private each field separately
@FieldDefaults(level = AccessLevel.PRIVATE)

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
// as postgres have already user table so
@Table(name = "users")
public class User implements UserDetails {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     Long id;
     String username;
     String password;
     String name;


     @CreationTimestamp
     Instant createdAt;
     @UpdateTimestamp
     Instant updatedAt;
     Instant deletedAt; // soft delete

     @Override
     public Collection<? extends GrantedAuthority> getAuthorities() {
          return List.of();
     }
}
