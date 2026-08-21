package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class FakeStatement extends AbstractStatement {

    private final Connection connection;
    private final List<String> batch = new ArrayList<>();
    private boolean closed;

    FakeStatement(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean execute(String sql) {
        Db.executed(sql);
        return true;
    }

    @Override
    public ResultSet executeQuery(String sql) {
        Db.executed(sql);
        return new FakeResultSet();
    }

    @Override
    public int executeUpdate(String sql) {
        Db.executed(sql);
        return 1;
    }

    @Override
    public void addBatch(String sql) {
        batch.add(sql);
    }

    @Override
    public int[] executeBatch() {
        int[] results = new int[batch.size()];
        for (int i = 0; i < batch.size(); i++) {
            Db.executed(batch.get(i));
            results[i] = 1;
        }
        batch.clear();
        return results;
    }

    @Override
    public void clearBatch() {
        batch.clear();
    }

    @Override
    public Connection getConnection() {
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
    public boolean isWrapperFor(Class<?> type) {
        return type.isInstance(this);
    }

    @Override
    public <T> T unwrap(Class<T> type) {
        return type.cast(this);
    }
}
