package com.trip.module.ai;

import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PlannerConnection(@NotBlank @Size(max=500) @JsonDeserialize(using=StrictPlannerJson.Text.class) String baseUrl,
                                @NotBlank @Size(max=50) @Pattern(regexp="[a-zA-Z0-9._:/-]+") @JsonDeserialize(using=StrictPlannerJson.Text.class) String model,
                                @Size(max=512) @Pattern(regexp="[\\x20-\\x7E]*") @JsonDeserialize(using=StrictPlannerJson.Text.class) String apiKey) {
    public PlannerConnection {
        if(apiKey!=null) {
            apiKey=apiKey.strip();
            if(apiKey.regionMatches(true,0,"Bearer ",0,7))apiKey=apiKey.substring(7).strip();
        }
    }
    @Override public String toString() { return "PlannerConnection[redacted]"; }
}
