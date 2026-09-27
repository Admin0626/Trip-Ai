package com.trip.module.ai;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import java.math.BigDecimal;

/** Scoped to planner DTOs; does not change existing endpoint coercion rules. */
public final class StrictPlannerJson {
    private StrictPlannerJson() {}
    public static class Text extends ValueDeserializer<String> {
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) return context.reportInputMismatch(String.class, "JSON string required");
            return parser.getString();
        }
    }
    public static class IntegerNumber extends ValueDeserializer<Integer> {
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) return context.reportInputMismatch(Integer.class, "JSON integer required");
            return parser.getIntValue();
        }
    }
    public static class DecimalNumber extends ValueDeserializer<BigDecimal> {
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT)
                return context.reportInputMismatch(BigDecimal.class, "JSON number required");
            return parser.getDecimalValue();
        }
    }
}
