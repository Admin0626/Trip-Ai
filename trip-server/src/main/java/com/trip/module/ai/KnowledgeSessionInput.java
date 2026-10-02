package com.trip.module.ai;

import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;

public final class KnowledgeSessionInput {
    private KnowledgeSessionInput() {}
    public record Create(@JsonDeserialize(using=StrictPlannerJson.Text.class) String title) {}
    public record Rename(@NotBlank @JsonDeserialize(using=StrictPlannerJson.Text.class) String title,
                         @NotNull @Positive @JsonDeserialize(using=KnowledgeInput.LongNumber.class) Long expectedRevision) {}
    public record Search(@NotNull @Positive @JsonDeserialize(using=KnowledgeInput.LongNumber.class) Long sessionId,
                         @NotBlank @JsonDeserialize(using=StrictPlannerJson.Text.class) String query,
                         @Min(1) @Max(5) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer topK,
                         @NotBlank @Pattern(regexp="[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                         @JsonDeserialize(using=StrictPlannerJson.Text.class) String requestId) {}
}
