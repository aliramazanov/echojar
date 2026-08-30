package com.aliramazanov.echojar.agent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import com.aliramazanov.echojar.bootstrap.Chain;
import com.aliramazanov.echojar.bootstrap.Echo;

import net.bytebuddy.asm.Advice;

final class JdbcAdvice {

    private JdbcAdvice() {
    }

    static final class Prepare {

        @Advice.OnMethodEnter
        public static void enter() {
            Chain.prepareEnter();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
                @Advice.This Connection connection,
                @Advice.Argument(0) String sql,
                @Advice.Return PreparedStatement statement
        ) {
            Chain.prepareExit(connection, sql, statement);
        }
    }

    static final class CloseConnection {

        @Advice.OnMethodEnter
        public static void enter(@Advice.This Connection connection) {
            Echo.closed(connection);
        }
    }

    static final class ExecutePrepared {

        @Advice.OnMethodEnter
        public static Object enter(@Advice.This Object statement) {
            return Chain.executeEnter(statement);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
                @Advice.This Object statement,
                @Advice.Enter Object frame,
                @Advice.Thrown Throwable error
        ) {
            if (!Chain.executeClaim(frame, statement) || error != null) {
                return;
            }

            if (Echo.executed(statement, 1)) {
                return;
            }

            try {
                Echo.executedOn(statement, ((PreparedStatement) statement).getConnection(), 1);
            } catch (SQLException | RuntimeException | Error ignored) {
            }
        }
    }

    static final class AddBatchPrepared {

        @Advice.OnMethodEnter
        public static Object enter(@Advice.This Object statement) {
            return Chain.executeEnter(statement);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
                @Advice.This PreparedStatement statement,
                @Advice.Enter Object frame,
                @Advice.Thrown Throwable error
        ) {

            if (!Chain.executeClaim(frame, statement) || error != null) {
                return;
            }

            Echo.batched(statement);
        }
    }

    static final class ExecuteBatchPrepared {

        @Advice.OnMethodEnter
        public static Object enter(@Advice.This Object statement) {
            return Chain.executeEnter(statement);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
                @Advice.This PreparedStatement statement,
                @Advice.Enter Object frame,
                @Advice.Thrown Throwable error
        ) {
            if (!Chain.executeClaim(frame, statement) || error != null) {
                return;
            }
            int owed = Echo.batchExecuted(statement);
            if (owed <= 0) {
                return;
            }
            try {
                Echo.executedOn(statement, statement.getConnection(), owed);
            } catch (SQLException | RuntimeException | Error ignored) {
            }
        }
    }

    static final class ExecuteStatement {

        @Advice.OnMethodEnter
        public static void enter() {
            Chain.statementEnter();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
                @Advice.This Object statement,
                @Advice.Argument(0) String sql,
                @Advice.Thrown Throwable error
        ) {
            if (!Chain.statementExit() || error != null) {
                return;
            }
            if (!Chain.reenter()) {
                return;
            }
            try {
                Echo.executedSql(((Statement) statement).getConnection(), sql, 1);
            } catch (SQLException | RuntimeException | Error ignored) {
            } finally {
                Chain.release();
            }
        }
    }

    static final class AddBatchStatement {

        @Advice.OnMethodEnter
        public static void enter() {
            Chain.statementEnter();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(
                @Advice.This Statement statement,
                @Advice.Argument(0) String sql,
                @Advice.Thrown Throwable error
        ) {
            if (!Chain.statementExit() || error != null) {
                return;
            }
            Echo.batchedSql(statement, sql);
        }
    }

    static final class ExecuteBatchStatement {

        @Advice.OnMethodEnter
        public static void enter() {
            Chain.statementEnter();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.This Statement statement, @Advice.Thrown Throwable error) {
            if (!Chain.statementExit() || error != null) {
                return;
            }
            if (!Chain.reenter()) {
                return;
            }
            try {
                Echo.batchExecutedSql(statement, statement.getConnection());
            } catch (SQLException | RuntimeException | Error ignored) {
            } finally {
                Chain.release();
            }
        }
    }

    static final class ClearBatch {

        @Advice.OnMethodExit
        public static void exit(@Advice.This Statement statement) {
            Echo.batchCleared(statement);
        }
    }
}
