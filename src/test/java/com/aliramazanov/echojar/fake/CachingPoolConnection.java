package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CachingPoolConnection extends AbstractConnection {

    private final Connection delegate;
    private final Map<String, PreparedStatement> cache = new HashMap<>();

    public CachingPoolConnection(Connection delegate) {
        this.delegate = delegate;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement cached = cache.get(sql);
        if (cached != null) {
            return cached;
        }
        PreparedStatement created = new PoolPreparedStatement(this, delegate.prepareStatement(sql));
        cache.put(sql, created);
        return created;
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int arg1, int arg2) throws SQLException {
        return prepareStatement(sql);
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
    public <T> T unwrap(Class<T> type) {
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
