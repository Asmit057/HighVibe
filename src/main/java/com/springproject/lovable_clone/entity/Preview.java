package com.springproject.lovable_clone.entity;


import com.springproject.lovable_clone.enums.PreviewStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE
)
public class Preview {

    Long id;
    Project project;
     String namespace;
     // pod smallest unit controlling kubernatives
     String podName;
     String previewUrl;

     PreviewStatus status;
     Instant startedAt;
     Instant terminatedAt;
     Instant createdAt;

}
