package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class PoolConnection extends AbstractConnection {

    private final Connection delegate;

    public PoolConnection(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new PoolPreparedStatement(this, delegate.prepareStatement(sql));
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int arg1, int arg2) throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new PoolStatement(this, delegate.createStatement());
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws SQLException {
        return delegate.getMetaData();
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(delegate) || type.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return type.isInstance(delegate) ? type.cast(delegate) : type.cast(this);
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
