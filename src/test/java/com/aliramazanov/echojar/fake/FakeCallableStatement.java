package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class FakeCallableStatement extends AbstractCallableStatement {

    private final Connection connection;
    private final String sql;

    FakeCallableStatement(Connection connection, String sql) {
        this.connection = connection;
        this.sql = sql;
    }

    @Override
    public boolean execute() {
        Db.executed(sql);
        return true;
    }

    @Override
    public ResultSet executeQuery() {
        Db.executed(sql);
        return new FakeResultSet();
    }

    @Override
    public int executeUpdate() {
        Db.executed(sql);
        return 1;
    }

    @Override
    public void setInt(int arg0, int arg1) {
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        return type.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        return type.cast(this);
    }
}
