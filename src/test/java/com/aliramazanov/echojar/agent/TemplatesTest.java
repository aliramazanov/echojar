package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.aliramazanov.echojar.bootstrap.findings.SqlTemplate;
import org.junit.jupiter.api.Test;

class TemplatesTest {

    @Test
    void stripsNumericLiterals() {
        assertEquals("SELECT * FROM t WHERE id = ?", Templates.normalize("SELECT * FROM t WHERE id = 42"));
        assertEquals("SELECT * FROM t WHERE p = ?", Templates.normalize("SELECT * FROM t WHERE p = 3.14"));
        assertEquals("SELECT * FROM t WHERE p = ?", Templates.normalize("SELECT * FROM t WHERE p = 1e-9"));
    }

    @Test
    void stripsStringLiterals() {
        assertEquals("SELECT * FROM t WHERE n = ?", Templates.normalize("SELECT * FROM t WHERE n = 'ali'"));
        assertEquals("SELECT * FROM t WHERE n = ?", Templates.normalize("SELECT * FROM t WHERE n = 'O''Brien'"));
    }

    @Test
    void keepsIdentifiersThatContainDigits() {
        assertEquals("SELECT col1 FROM t2", Templates.normalize("SELECT col1 FROM t2"));
    }

    @Test
    void keepsQuotedIdentifiers() {
        assertEquals("SELECT \"order\" FROM t", Templates.normalize("SELECT \"order\" FROM t"));
    }

    @Test
    void collapsesWhitespace() {
        assertEquals("SELECT a FROM t", Templates.normalize("  SELECT   a\n\tFROM  t  "));
    }

    @Test
    void collapsesInLists() {
        assertEquals("SELECT * FROM t WHERE id IN (?)", Templates.normalize("SELECT * FROM t WHERE id IN (?, ?, ?)"));
        assertEquals("SELECT * FROM t WHERE id IN (?)", Templates.normalize("SELECT * FROM t WHERE id in (1, 2, 3)"));
    }

    @Test
    void doesNotCollapseInsertValueLists() {
        assertEquals("INSERT INTO t VALUES (?, ?, ?)", Templates.normalize("INSERT INTO t VALUES (1, 2, 3)"));
    }

    @Test
    void stripsComments() {
        assertEquals("SELECT a FROM t", Templates.normalize("SELECT a FROM t -- trailing note"));
        assertEquals("SELECT a FROM t", Templates.normalize("/* hint */ SELECT a FROM t"));
    }

    @Test
    void differentLiteralsShareOneTemplateIdentity() {
        Templates templates = new Templates(100, true);
        SqlTemplate first = templates.of("SELECT * FROM t WHERE id = 1");
        SqlTemplate second = templates.of("SELECT * FROM t WHERE id = 2");
        assertSame(first, second, "inlined literals must resolve to one template");
        assertEquals(1, templates.cached());
    }

    @Test
    void distinctStatementsGetDistinctIdentities() {
        Templates templates = new Templates(100, true);
        assertNotEquals(
                templates.of("SELECT a FROM t").id(),
                templates.of("SELECT b FROM t").id());
    }

    @Test
    void rawCacheIsBoundedButTemplatesStillResolve() {
        Templates templates = new Templates(2, true);
        for (int i = 0; i < 50; i++) {
            templates.of("SELECT * FROM t WHERE id = " + i);
        }
        assertEquals(1, templates.cached(), "one normalized template regardless of literal count");
    }

    @Test
    void noiseIsFlaggedOnlyWhenEnabled() {
        assertEquals(true, new Templates(100, true).of("SELECT 1").noise());
        assertEquals(false, new Templates(100, false).of("SELECT 1").noise());
        assertEquals(true, new Templates(100, true).of("select nextval('hibernate_sequence')").noise());
        assertEquals(false, new Templates(100, true).of("SELECT * FROM orders WHERE id = 1").noise());
    }
}
