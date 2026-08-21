package com.aliramazanov.echojar.fake;

public class FakeDatabaseMetaData extends AbstractDatabaseMetaData {

    @Override
    public String getDatabaseProductName() {
        return "echojar-fake";
    }

    @Override
    public String getURL() {
        return FakeDriver.URL;
    }
}
