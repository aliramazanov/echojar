package shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.function.Supplier;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

public final class LabServlet implements Servlet {

    public interface Work {
        void run() throws Exception;
    }

    private final Work work;

    public LabServlet(Work work) {
        this.work = work;
    }

    public static LabServlet reconnecting(Supplier<Connection> pool, int queries) {
        return new LabServlet(() -> {
            for (int query = 0; query < queries; query++) {
                try (Connection connection = pool.get();

                        PreparedStatement statement = connection
                                .prepareStatement("SELECT * FROM request_item WHERE order_id = ?")) {

                    statement.setInt(1, query);
                    statement.executeQuery();
                }
            }
        });
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) {
        try {
            work.run();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override
    public void init(ServletConfig config) {
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public String getServletInfo() {
        return "lab";
    }

    @Override
    public void destroy() {
    }
}
