package com.aliramazanov.echojar.agent;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class NormalizerAdversarialTest {

    @Test
    void survivesEmptyAndWhitespaceOnlySql() {
        assertEquals("", Templates.normalize(""));
        assertEquals("", Templates.normalize("   \n\t "));
    }

    @Test
    void survivesUnterminatedStringLiteral() {
        assertEquals("SELECT * FROM t WHERE n = ?", Templates.normalize("SELECT * FROM t WHERE n = 'unclosed"));
    }

    @Test
    void survivesUnterminatedQuotedIdentifier() {
        assertTrue(Templates.normalize("SELECT \"unclosed FROM t").startsWith("SELECT \""));
    }

    @Test
    void survivesUnterminatedBlockComment() {
        assertEquals("SELECT a", Templates.normalize("SELECT a /* never closed"));
    }

    @Test
    void survivesTrailingBackslashInsideLiteral() {
        assertEquals("SELECT ?", Templates.normalize("SELECT 'ends with backslash\\"));
    }

    @Test
    void doesNotHangOnALongUnclosedInList() {
        String sql = "SELECT * FROM t WHERE id IN (" + "?, ".repeat(20_000);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> Templates.normalize(sql));
    }

    @Test
    void doesNotHangOnManyInListsInOneStatement() {
        String sql = "SELECT * FROM t WHERE " + "id IN (?, ?, ?) OR ".repeat(20_000) + "id = 1";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            String out = Templates.normalize(sql);
            assertTrue(out.contains("IN (?)"));
        });
    }

    @Test
    void hexLiteralsDoNotProduceGarbage() {
        assertEquals("SELECT * FROM t WHERE mask = ?", Templates.normalize("SELECT * FROM t WHERE mask = 0x1F"));
    }

    @Test
    void aLiteralInsideAStringIsNotTreatedAsANumber() {
        Templates templates = new Templates(100, false);
        assertSame(
                templates.of("SELECT * FROM t WHERE n = 'order 66'"),
                templates.of("SELECT * FROM t WHERE n = 'order 66'"),
                "same input must be stable");
        assertEquals("SELECT * FROM t WHERE n = ?", Templates.normalize("SELECT * FROM t WHERE n = 'order 66'"));
    }

    @Test
    void statementsThatDifferOnlyInIdentifiersStayDistinct() {
        assertNotEquals(
                Templates.normalize("SELECT * FROM orders WHERE id = 1"),
                Templates.normalize("SELECT * FROM invoices WHERE id = 1"));
    }

    @Test
    void templateCacheIsBoundedAgainstUnboundedDistinctStatements() {
        Templates templates = new Templates(10, false);
        for (int i = 0; i < 5_000; i++) {
            templates.of("SELECT * FROM t" + i + " WHERE id = 1");
        }
        assertTrue(templates.cached() <= 10,
                "the template cache must honour its limit, held " + templates.cached());
    }
}
