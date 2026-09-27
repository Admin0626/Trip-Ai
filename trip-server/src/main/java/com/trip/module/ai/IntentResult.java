package com.trip.module.ai;

import java.util.List;

/** Unknown values stay null; fallback confidence always requires user confirmation. */
public record IntentResult(Intent intent, double confidence, String source) {
    public record Intent(List<String> destinations, Integer days, Integer budget,
                         String budgetLevel, List<String> preferenceTags, String companions,
                         String pace, Integer travelMonth, List<String> mustVisit, List<String> avoid) {}
}
