package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.ResultSet;

public class FakePreparedStatement extends AbstractPreparedStatement {

    private final Connection connection;
    private final String sql;
    private int batched;
    private boolean closed;

    protected FakePreparedStatement(Connection connection, String sql) {
        this.connection = connection;
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }

    @Override
    public boolean execute() throws SQLException {
        Db.executed(sql);
        return true;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        Db.executed(sql);
        return new FakeResultSet();
    }

    @Override
    public int executeUpdate() throws SQLException {
        Db.executed(sql);
        return 1;
    }

    @Override
    public void addBatch() {
        batched++;
    }

    @Override
    public long executeLargeUpdate() throws SQLException {
        Db.executed(sql);
        return 1;
    }

    @Override
    public long[] executeLargeBatch() throws SQLException {
        long[] results = new long[batched];
        for (int i = 0; i < batched; i++) {
            Db.executed(sql);
            results[i] = 1;
        }
        batched = 0;
        return results;
    }

    @Override
    public int[] executeBatch() throws SQLException {
        int[] results = new int[batched];
        for (int i = 0; i < batched; i++) {
            Db.executed(sql);
            results[i] = 1;
        }
        batched = 0;
        return results;
    }

    @Override
    public void clearBatch() {
        batched = 0;
    }

    @Override
    public void setInt(int arg0, int arg1) {
    }

    @Override
    public void setLong(int arg0, long arg1) {
    }

    @Override
    public void setString(int arg0, String arg1) {
    }

    @Override
    public void setObject(int arg0, Object arg1) {
    }

    @Override
    public void clearParameters() {
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
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
}
