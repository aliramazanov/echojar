package com.aliramazanov.echojar.fake;

import java.sql.PreparedStatement;

public class ExplodingConnection extends FakeConnection {

    @Override
    public PreparedStatement prepareStatement(String sql) {
        return new ExplodingPreparedStatement(this, sql);
    }
}
