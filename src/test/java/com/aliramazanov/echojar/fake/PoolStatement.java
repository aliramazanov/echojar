package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class PoolStatement extends AbstractStatement {

    private final Connection connection;
    private final Statement delegate;

    PoolStatement(Connection connection, Statement delegate) {
        this.connection = connection;
        this.delegate = delegate;
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        return delegate.execute(sql);
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        return delegate.executeQuery(sql);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        return delegate.executeUpdate(sql);
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        delegate.addBatch(sql);
    }

    @Override
    public int[] executeBatch() throws SQLException {
        return delegate.executeBatch();
    }

    @Override
    public void clearBatch() throws SQLException {
        delegate.clearBatch();
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return delegate.isClosed();
    }

    @Override
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(delegate) || type.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return type.isInstance(delegate) ? type.cast(delegate) : type.cast(this);
    }
}
