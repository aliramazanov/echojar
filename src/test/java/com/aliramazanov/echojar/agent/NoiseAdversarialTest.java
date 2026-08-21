package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoiseAdversarialTest {

    private static boolean suppressed(String sql) {
        return new Templates(1000, true).of(sql).noise();
    }

    @Test
    void realNoiseIsSuppressed() {
        assertTrue(suppressed("SELECT 1"));
        assertTrue(suppressed("select 1 from dual"));
        assertTrue(suppressed("select nextval('hibernate_sequence')"));
        assertTrue(suppressed("select next value for order_seq"));
        assertTrue(suppressed("commit"));
    }

    @Test
    void aTableWhoseNameContainsASequenceWordIsNotNoise() {
        assertFalse(suppressed("SELECT * FROM nextval_audit WHERE id = 1"),
                "a real table named nextval_audit is not a sequence read");
        assertFalse(suppressed("SELECT * FROM currval_log WHERE id = 1"),
                "a real table named currval_log is not a sequence read");
        assertFalse(suppressed("SELECT * FROM hibernate_sequence_audit WHERE id = 1"),
                "an audit table is not the sequence itself");
    }

    @Test
    void aColumnWhoseNameContainsASequenceWordIsNotNoise() {
        assertFalse(suppressed("SELECT nextval_count FROM orders WHERE id = 1"),
                "a column called nextval_count is application data");
    }

    @Test
    void aRealSingleColumnProjectionIsNotNoise() {
        assertFalse(suppressed("SELECT price FROM item WHERE id = 1"));
        assertFalse(suppressed("SELECT 1 FROM orders WHERE customer_id = 1"),
                "selecting a constant from a real table is a real query");
    }

    @Test
    void noiseSuppressionCanBeTurnedOff() {
        assertFalse(new Templates(1000, false).of("SELECT 1").noise());
    }
}
