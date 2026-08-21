package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ExplodingPreparedStatement extends FakePreparedStatement {

    ExplodingPreparedStatement(Connection connection, String sql) {
        super(connection, sql);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        throw new SQLException("the database said no");
    }
}
