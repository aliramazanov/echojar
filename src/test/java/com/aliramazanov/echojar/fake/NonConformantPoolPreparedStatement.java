package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class NonConformantPoolPreparedStatement extends AbstractPreparedStatement {

    private final Connection connection;
    private final PreparedStatement delegate;

    NonConformantPoolPreparedStatement(Connection connection, PreparedStatement delegate) {
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
    public void setInt(int arg0, int arg1) throws SQLException {
        delegate.setInt(arg0, arg1);
    }

    @Override
    public void close() throws SQLException {
        delegate.close();
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        throw new SQLException("this pool does not implement Wrapper");
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        throw new SQLException("this pool does not implement Wrapper");
    }
}
