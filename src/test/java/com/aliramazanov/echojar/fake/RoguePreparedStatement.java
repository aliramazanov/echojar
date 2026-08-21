package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.SQLException;

public class RoguePreparedStatement extends FakePreparedStatement {

    private final RogueConnection.Mode mode;

    RoguePreparedStatement(Connection connection, String sql, RogueConnection.Mode mode) {
        super(connection, sql);
        this.mode = mode;
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        if (mode == RogueConnection.Mode.THROWS) {
            throw new SQLException("this driver does not implement Wrapper");
        }

        return true;
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        if (mode == RogueConnection.Mode.THROWS) {
            throw new SQLException("this driver does not implement Wrapper");
        }

        return type.cast(this);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (mode == RogueConnection.Mode.THROWS) {
            throw new SQLException("no connection for you");
        }

        return super.getConnection();
    }
}
