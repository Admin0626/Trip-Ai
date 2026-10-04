package com.trip.module.user.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserExportServiceTest {
    @Test void spreadsheetCommandsRemainText() {
        for(String value:new String[]{"=HYPERLINK(\"https://example.invalid\")"," +1","-1","@SUM(A1)","\t=1","\n=1"})
            assertTrue(UserExportService.cell(value).startsWith("\"'"));
    }
    @Test void quotesCommasAndLineBreaksArePreservedWithoutNewColumns() {
        assertEquals("\"a,\"\"b\"\"\r\nc\"",UserExportService.cell("a,\"b\"\r\nc"));
        assertEquals("\"\"",UserExportService.cell(null));
    }
}
