package com.trip.module.catalog;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import java.math.BigDecimal;

/** Refuse JSON scalar coercion on administrator writes. */
public final class StrictCatalogJson {
    private StrictCatalogJson() {}
    public static class Text extends ValueDeserializer<String> {
        public String deserialize(JsonParser p, DeserializationContext c) {
            if (p.currentToken() != JsonToken.VALUE_STRING) return c.reportInputMismatch(String.class, "JSON string required");
            return p.getString();
        }
    }
    public static class IntegerNumber extends ValueDeserializer<Integer> {
        public Integer deserialize(JsonParser p, DeserializationContext c) {
            if (p.currentToken() != JsonToken.VALUE_NUMBER_INT) return c.reportInputMismatch(Integer.class, "JSON integer required");
            return p.getIntValue();
        }
    }
    public static class LongNumber extends ValueDeserializer<Long> {
        public Long deserialize(JsonParser p, DeserializationContext c) {
            if (p.currentToken() != JsonToken.VALUE_NUMBER_INT) return c.reportInputMismatch(Long.class, "JSON integer required");
            return p.getLongValue();
        }
    }
    public static class DecimalNumber extends ValueDeserializer<BigDecimal> {
        public BigDecimal deserialize(JsonParser p, DeserializationContext c) {
            if (p.currentToken() != JsonToken.VALUE_NUMBER_INT && p.currentToken() != JsonToken.VALUE_NUMBER_FLOAT)
                return c.reportInputMismatch(BigDecimal.class, "JSON number required");
            return p.getDecimalValue();
        }
    }
}
