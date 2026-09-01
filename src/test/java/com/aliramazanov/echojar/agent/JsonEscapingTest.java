package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonEscapingTest {

    @Test
    void aPlainStringIsJustQuoted() {
        assertEquals("\"SELECT 1\"", Report.quote("SELECT 1"));
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        assertEquals("\"a \\\"b\\\" c\"", Report.quote("a \"b\" c"));
        assertEquals("\"a \\\\ b\"", Report.quote("a \\ b"));
    }

    @Test
    void newlinesAndTabsDoNotBreakTheLine() {
        assertEquals("\"a\\nb\"", Report.quote("a\nb"));
        assertEquals("\"a\\rb\"", Report.quote("a\rb"));
        assertEquals("\"a\\tb\"", Report.quote("a\tb"));
    }

    @Test
    void otherControlCharactersBecomeUnicodeEscapes() {
        assertEquals("\"a\\u0000b\"", Report.quote("a" + (char) 0 + "b"));
        assertEquals("\"a\\u001fb\"", Report.quote("a" + (char) 0x1f + "b"));
    }

    @Test
    void nullBecomesTheJsonNullLiteral() {
        assertEquals("null", Report.quote(null));
    }

    @Test
    void sqlWithEverythingAwkwardInItStaysOnOneLine() {
        String sql = "SELECT \"col\" FROM t\nWHERE p = 'x' AND q = ?\t-- note";
        String quoted = Report.quote(sql);

        assertTrue(quoted.startsWith("\"") && quoted.endsWith("\""), quoted);
        assertEquals(1, quoted.lines().count(), "a report that spans lines cannot be parsed");
    }
}
