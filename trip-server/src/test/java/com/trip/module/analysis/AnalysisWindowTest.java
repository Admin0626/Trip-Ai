package com.trip.module.analysis;

import com.trip.common.exception.BizException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisWindowTest {
    private Clock at(String instant) { return Clock.fixed(Instant.parse(instant),ZoneOffset.UTC); }
    @Test void includesTodayAndSixPreviousDaysInBeijing() {
        var w=AnalysisService.window(7,at("2026-10-03T16:00:00Z"));
        assertEquals(LocalDate.of(2026,10,4),w.endDate());
        assertEquals(LocalDate.of(2026,9,28),w.startDate());
        assertEquals(LocalDateTime.of(2026,10,5,0,0),w.until());
    }
    @Test void dateChangesAtBeijingMidnightRatherThanUtcMidnight() {
        assertEquals(LocalDate.of(2026,10,3),AnalysisService.window(7,at("2026-10-03T15:59:59Z")).endDate());
    }
    @Test void thirtyDaysCrossLeapDay() {
        assertEquals(LocalDate.of(2024,2,1),AnalysisService.window(30,at("2024-03-01T00:00:00Z")).startDate());
    }
    @Test void ninetyDaysCrossYear() {
        var w=AnalysisService.window(90,at("2026-01-01T00:00:00Z"));
        assertEquals(90,java.time.temporal.ChronoUnit.DAYS.between(w.start(),w.until()));
    }
    @Test void arbitraryOrUnboundedDaysAreRejected() {
        for(int days:new int[]{0,-1,1,8,31,365,Integer.MAX_VALUE})assertThrows(BizException.class,()->AnalysisService.window(days,at("2026-10-04T00:00:00Z")));
    }
}
