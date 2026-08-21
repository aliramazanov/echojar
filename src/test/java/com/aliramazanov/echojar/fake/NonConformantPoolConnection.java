package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class NonConformantPoolConnection extends AbstractConnection {

    private final Connection delegate;

    public NonConformantPoolConnection(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new NonConformantPoolPreparedStatement(this, delegate.prepareStatement(sql));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int arg1, int arg2) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        throw new SQLException("this pool does not implement Wrapper");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        throw new SQLException("this pool does not implement Wrapper");
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
