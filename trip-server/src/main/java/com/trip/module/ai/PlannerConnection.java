package com.trip.module.ai;

import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PlannerConnection(@NotBlank @Size(max=500) @JsonDeserialize(using=StrictPlannerJson.Text.class) String baseUrl,
                                @NotBlank @Size(max=50) @Pattern(regexp="[a-zA-Z0-9._:/-]+") @JsonDeserialize(using=StrictPlannerJson.Text.class) String model,
                                @Size(max=512) @Pattern(regexp="[\\x20-\\x7E]*") @JsonDeserialize(using=StrictPlannerJson.Text.class) String apiKey) {
    @Override public String toString() { return "PlannerConnection[redacted]"; }
}
