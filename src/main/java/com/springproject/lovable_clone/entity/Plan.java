package com.springproject.lovable_clone.entity;


// this entity will tell what plan we have opted for subscription

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE
)
public class Plan {
    Long id;

    String name;
    String stripePriceId;
    Integer maxProjects;
    // if we use int in place of Integer we get error in database bcz it is entity
    Integer maxTokensPerDay;
    // this field tell that how many times u run ur application
    Integer maxPreviews;

    Boolean unlimitedAi;
    Boolean active;
}
