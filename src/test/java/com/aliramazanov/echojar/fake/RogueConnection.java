package com.aliramazanov.echojar.fake;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class RogueConnection extends FakeConnection {

    private final Mode mode;

    public enum Mode {
        THROWS,
        RETURNS_SELF,
        QUERIES_ON_UNWRAP
    }

    public RogueConnection(Mode mode) {
        this.mode = mode;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) {
        return new RoguePreparedStatement(this, sql, mode);
    }

    @Override
    public boolean isWrapperFor(Class<?> type) throws SQLException {
        if (mode == Mode.THROWS) {
            throw new SQLException("this driver does not implement Wrapper");
        }
        return true;
    }

    @Override
    public <T> T unwrap(Class<T> type) throws SQLException {
        switch (mode) {
            case THROWS -> throw new SQLException("this driver does not implement Wrapper");

            case QUERIES_ON_UNWRAP -> {
                try (Statement statement = super.createStatement()) {
                    statement.execute("SELECT unwrap_probe()");
                }
                return type.cast(this);
            }

            default -> {
                return type.cast(this);
            }
        }
    }
}
