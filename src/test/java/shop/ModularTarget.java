package shop;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public final class ModularTarget {

    public static void main(String[] args) throws Exception {
        Class<?> driver = Class.forName("org.hsqldb.jdbc.JDBCDriver");

        if (!driver.getModule().isNamed()) {
            throw new IllegalStateException("the driver must be a named module for this to prove anything");
        }

        try (Connection connection = DriverManager.getConnection("jdbc:hsqldb:mem:modular", "SA", "")) {
            try (Statement setup = connection.createStatement()) {
                setup.execute("CREATE TABLE modular_item (order_id INT, sku VARCHAR(32))");
                for (int row = 0; row < 8; row++) {
                    setup.execute("INSERT INTO modular_item VALUES (" + row + ", 'sku-" + row + "')");
                }
            }

            try (PreparedStatement lookup = connection
                    .prepareStatement("SELECT sku FROM modular_item WHERE order_id = ?")) {

                for (int order = 0; order < 8; order++) {
                    lookup.setInt(1, order);

                    try (ResultSet rows = lookup.executeQuery()) {
                        while (rows.next()) {
                            rows.getString(1);
                        }
                    }
                }
            }
        }

        System.out.println("modular target finished");
    }

    private ModularTarget() {
    }
}
