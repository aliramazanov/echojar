package com.aliramazanov.echojar.fake;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

public final class FakeDriver implements Driver {

    public static final String URL = "jdbc:echojar-fake:memory";

    static {
        try {
            DriverManager.registerDriver(new FakeDriver());
        } catch (SQLException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static Connection pooled() throws SQLException {
        return new PoolConnection(new FakeConnection());
    }

    @Override
    public Connection connect(String url, Properties info) {
        return acceptsURL(url) ? new FakeConnection() : null;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:echojar-fake:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }
}
