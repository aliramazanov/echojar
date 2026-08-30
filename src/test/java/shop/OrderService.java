package shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class OrderService {

    private final Connection connection;

    public OrderService(Connection connection) {
        this.connection = connection;
    }

    public void summarise(int orders) throws SQLException {
        PreparedStatement items = connection.prepareStatement("SELECT * FROM order_item WHERE order_id = ?");
        for (int order = 0; order < orders; order++) {
            items.setInt(1, order);
            items.executeQuery();
        }
    }

    public void summariseWithLiterals(int orders) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (int order = 0; order < orders; order++) {
                statement.executeQuery("SELECT * FROM order_item WHERE order_id = " + order);
            }
        }
    }

    public void insertBatch(int rows) throws SQLException {
        PreparedStatement insert = connection.prepareStatement("INSERT INTO audit (note) VALUES (?)");

        for (int row = 0; row < rows; row++) {
            insert.setString(1, "row " + row);
            insert.addBatch();
        }

        insert.executeBatch();
    }

    public void ping() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        }
    }
}
