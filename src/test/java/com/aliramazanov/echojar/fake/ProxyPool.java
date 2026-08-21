package com.aliramazanov.echojar.fake;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

public final class ProxyPool {

    private ProxyPool() {
    }

    public static Connection connection() {
        return wrap(new FakeConnection(), Connection.class);
    }

    private static <T> T wrap(Object target, Class<T> contract) {
        Object proxy = Proxy.newProxyInstance(
                ProxyPool.class.getClassLoader(),
                new Class<?>[] { contract },
                new Delegating(target));
        return contract.cast(proxy);
    }

    private record Delegating(Object target) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object result;

            try {
                result = method.invoke(target, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }

            if (result instanceof PreparedStatement statement) {
                return wrap(statement, PreparedStatement.class);
            }

            if (result instanceof Statement statement) {
                return wrap(statement, Statement.class);
            }

            return result;
        }
    }
}
