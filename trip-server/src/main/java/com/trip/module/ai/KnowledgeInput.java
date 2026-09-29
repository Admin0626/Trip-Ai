package com.trip.module.ai;

import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class KnowledgeInput {
    private KnowledgeInput() {}
    public static class LongNumber extends ValueDeserializer<Long> {
        public Long deserialize(JsonParser p,DeserializationContext c) {
            if(p.currentToken()!=JsonToken.VALUE_NUMBER_INT)return c.reportInputMismatch(Long.class,"JSON integer required");
            return p.getLongValue();
        }
    }
    public record Save(@NotBlank @JsonDeserialize(using=StrictPlannerJson.Text.class) String title,
                       @NotBlank @Pattern(regexp="GUIDE|DESTINATION|ROUTE") @JsonDeserialize(using=StrictPlannerJson.Text.class) String docType,
                       @NotBlank @JsonDeserialize(using=StrictPlannerJson.Text.class) String content,
                       @Positive @JsonDeserialize(using=LongNumber.class) Long sourceId,
                       @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer status,
                       @Positive @JsonDeserialize(using=LongNumber.class) Long expectedRevision) {}
    public record Version(@NotNull @Positive @JsonDeserialize(using=LongNumber.class) Long expectedRevision) {}
    public record State(@NotNull @Positive @JsonDeserialize(using=LongNumber.class) Long expectedRevision,
                        @NotNull @Min(0) @Max(1) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer status) {}
    public record Rebuild(@NotNull @Positive @JsonDeserialize(using=LongNumber.class) Long id,
                          @NotNull @Positive @JsonDeserialize(using=LongNumber.class) Long expectedRevision) {}
    public record Search(@NotBlank @JsonDeserialize(using=StrictPlannerJson.Text.class) String query,
                         @Min(1) @Max(5) @JsonDeserialize(using=StrictPlannerJson.IntegerNumber.class) Integer topK) {}
}
