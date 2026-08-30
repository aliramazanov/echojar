package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class AdviceTotalityTest {

    private static final Object GARBAGE = new Object();
    private static final Throwable FAILED = new IllegalStateException("the query failed");

    @Test
    void thePrepareAdviceSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            JdbcAdvice.Prepare.enter();
            JdbcAdvice.Prepare.exit(null, null, null);

            JdbcAdvice.Prepare.enter();
            JdbcAdvice.Prepare.exit(null, "SELECT 1", null);
        });
    }

    @Test
    void theCloseAdviceSurvivesANullConnection() {
        assertDoesNotThrow(() -> JdbcAdvice.CloseConnection.enter((Connection) null));
    }

    @Test
    void thePreparedStatementAdviceSurvivesAStatementThatIsNotOne() {
        assertDoesNotThrow(() -> {
            Object frame = JdbcAdvice.ExecutePrepared.enter(GARBAGE);
            JdbcAdvice.ExecutePrepared.exit(GARBAGE, frame, null);

            Object afterFailure = JdbcAdvice.ExecutePrepared.enter(GARBAGE);
            JdbcAdvice.ExecutePrepared.exit(GARBAGE, afterFailure, FAILED);

            JdbcAdvice.ExecutePrepared.exit(GARBAGE, GARBAGE, null);
            JdbcAdvice.ExecutePrepared.exit(null, null, null);
        });
    }

    @Test
    void theBatchAdviceSurvivesAStatementThatIsNotOne() {
        assertDoesNotThrow(() -> {
            Object added = JdbcAdvice.AddBatchPrepared.enter(GARBAGE);
            JdbcAdvice.AddBatchPrepared.exit(null, added, null);

            Object executed = JdbcAdvice.ExecuteBatchPrepared.enter(GARBAGE);
            JdbcAdvice.ExecuteBatchPrepared.exit(null, executed, null);

            Object thrown = JdbcAdvice.ExecuteBatchPrepared.enter(GARBAGE);
            JdbcAdvice.ExecuteBatchPrepared.exit(null, thrown, FAILED);
        });
    }

    @Test
    void thePlainStatementAdviceSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            JdbcAdvice.ExecuteStatement.enter();
            JdbcAdvice.ExecuteStatement.exit(GARBAGE, "SELECT 1", null);

            JdbcAdvice.ExecuteStatement.enter();
            JdbcAdvice.ExecuteStatement.exit(null, null, null);

            JdbcAdvice.ExecuteStatement.enter();
            JdbcAdvice.ExecuteStatement.exit(GARBAGE, "SELECT 1", FAILED);
        });
    }

    @Test
    void thePlainStatementBatchAdviceSurvivesAnythingHandedToIt() {
        assertDoesNotThrow(() -> {
            JdbcAdvice.AddBatchStatement.enter();
            JdbcAdvice.AddBatchStatement.exit(null, "INSERT INTO t VALUES (1)", null);

            JdbcAdvice.ExecuteBatchStatement.enter();
            JdbcAdvice.ExecuteBatchStatement.exit((Statement) null, null);

            JdbcAdvice.ExecuteBatchStatement.enter();
            JdbcAdvice.ExecuteBatchStatement.exit((Statement) null, FAILED);

            JdbcAdvice.ClearBatch.exit((Statement) null);
        });
    }

    @Test
    void theRequestBoundaryAdviceSurvivesBeingUnbalanced() {
        assertDoesNotThrow(() -> {
            RequestAdvice.Boundary.exit();
            RequestAdvice.Boundary.enter();
            RequestAdvice.Boundary.enter();
            RequestAdvice.Boundary.exit();
            RequestAdvice.Boundary.exit();
        });
    }

    @Test
    void anAdviceMethodNeverReturnsSomethingTheNextOneCannotRead() {
        assertDoesNotThrow(() -> {
            PreparedStatement notAStatement = null;
            Object frame = JdbcAdvice.AddBatchPrepared.enter(GARBAGE);
            JdbcAdvice.AddBatchPrepared.exit(notAStatement, frame, null);
        });
    }
}
