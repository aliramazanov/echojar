package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PoolPreparedStatement extends AbstractPreparedStatement {

    private final Connection connection;
    private final PreparedStatement delegate;

    PoolPreparedStatement(Connection connection, PreparedStatement delegate) {
        this.connection = connection;
        this.delegate = delegate;
    }

    @Override
    public boolean execute() throws SQLException {
        return delegate.execute();
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        return delegate.executeQuery();
    }

    @Override
    public int executeUpdate() throws SQLException {
        return delegate.executeUpdate();
    }

    @Override
    public void addBatch() throws SQLException {
        delegate.addBatch();
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        return delegate.executeLargeUpdate();
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        return delegate.executeLargeBatch();
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
    public void setInt(int arg0, int arg1) throws SQLException {
        delegate.setInt(arg0, arg1);
    }

    @Override
    public void setLong(int arg0, long arg1) throws SQLException {
        delegate.setLong(arg0, arg1);
    }

    @Override
    public void setString(int arg0, String arg1) throws SQLException {
        delegate.setString(arg0, arg1);
    }

    @Override
    public void setObject(int arg0, Object arg1) throws SQLException {
        delegate.setObject(arg0, arg1);
    }

    @Override
    public void clearParameters() throws SQLException {
        delegate.clearParameters();
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
