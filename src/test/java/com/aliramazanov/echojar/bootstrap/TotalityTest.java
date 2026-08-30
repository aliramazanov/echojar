package com.aliramazanov.echojar.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

class TotalityTest {

    private static final Object GARBAGE = new Object();

    @Test
    void theExecutePathSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            Chain.executeEnter(null);
            Chain.executeEnter(GARBAGE);
            Chain.executeClaim(null, null);
            Chain.executeClaim(GARBAGE, GARBAGE);
            Chain.executeClaim(null, GARBAGE);
        });
        assertFalse(Chain.executeClaim(GARBAGE, GARBAGE),
                "a handle that is not a frame counts nothing rather than counting wrongly");
    }

    @Test
    void thePreparePathSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            Chain.prepareEnter();
            Chain.prepareExit(null, null, null);
            Chain.prepareExit(GARBAGE, "SELECT 1", GARBAGE);
            Chain.prepareExit(null, "SELECT 1", null);
        });
    }

    @Test
    void thePlainStatementPathSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            Chain.statementEnter();
            Chain.statementExit();
            Chain.reenter();
            Chain.release();
            Chain.release();
        });
    }

    @Test
    void theRequestBoundarySurvivesBeingUnbalanced() {
        assertDoesNotThrow(() -> {
            Units.exit();
            Units.exit();
            Units.enter();
            Units.enter();
            Units.exit();
            Units.exit();
            Units.exit();
        });
    }

    @Test
    void theRecordingPathSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            Echo.closed(null);
            Echo.closed(GARBAGE);
            Echo.executed(null, 1);
            Echo.executed(GARBAGE, 1);
            Echo.executedOn(null, null, 1);
            Echo.executedSql(null, null, 1);
            Echo.batched(null);
            Echo.batchExecuted(null);
            Echo.batchCleared(null);
        });
    }
}
