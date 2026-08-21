package com.aliramazanov.echojar.fake;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class FakeConnection extends AbstractConnection {

    private boolean closed;
    private boolean metadataQueriesDatabase;

    public void metadataQueriesDatabase(boolean value) {
        this.metadataQueriesDatabase = value;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) {
        return new FakePreparedStatement(this, sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int arg1, int arg2) {
        return prepareStatement(sql);
    }

    @Override
    public java.sql.CallableStatement prepareCall(String sql) {
        return new FakeCallableStatement(this, sql);
    }

    @Override
    public Statement createStatement() {
        return new FakeStatement(this);
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws SQLException {
        if (metadataQueriesDatabase) {
            try (Statement statement = createStatement()) {
                statement.execute("SELECT version()");
            }
        }
        return new FakeDatabaseMetaData();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return type.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return type.cast(this);
    }

    @Override
    public void setAutoCommit(boolean arg0) {
    }

    @Override
    public boolean getAutoCommit() {
        return true;
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
    }
}
