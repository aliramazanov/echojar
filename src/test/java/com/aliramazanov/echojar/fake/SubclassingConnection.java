package com.aliramazanov.echojar.fake;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SubclassingConnection extends FakeConnection {

    @Override
    public PreparedStatement prepareStatement(String sql) {
        return new Subclassing(this, sql);
    }

    private static final class Subclassing extends FakePreparedStatement {

        private Subclassing(java.sql.Connection connection, String sql) {
            super(connection, sql);
        }

        @Override
        public ResultSet executeQuery() throws SQLException {
            return super.executeQuery();
        }

        @Override
        public int executeUpdate() throws SQLException {
            return super.executeUpdate();
        }

        @Override
        public boolean execute() throws SQLException {
            return super.execute();
        }

        @Override
        public int[] executeBatch() throws SQLException {
            return super.executeBatch();
        }
    }
}
