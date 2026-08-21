package com.aliramazanov.echojar.fake;

public class FakeResultSet extends AbstractResultSet {

    private int row;

    @Override
    public boolean next() {
        return row++ < 1;
    }

    @Override
    public void close() {
    }

    @Override
    public int getInt(int arg0) {
        return 1;
    }

    @Override
    public String getString(int arg0) {
        return "fake";
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
