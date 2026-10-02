package com.springproject.lovable_clone.entity;

// table whihc help to join two tables
// so project member table dont have any kind of id

import com.springproject.lovable_clone.enums.ProjectRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table( name = "project_members")
@Entity
public class ProjectMember {

    // we are using composite id here
    // primary key is maid by combining all these things
    // we have to specify both projectid and userid
    @EmbeddedId // take all the fields prsent in projectmemberid means multiple primary key
    ProjectMemberId id;

    @ManyToOne
     @MapsId("projectId")
    Project project;

    @ManyToOne
    @MapsId("userId")
    User user;


    @Enumerated(EnumType.STRING)
     @Column(nullable = false)
    ProjectRole projectRole;

    Instant invitedAt;
    Instant acceptedAt;

}
