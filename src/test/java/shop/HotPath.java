package shop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;

import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeDriver;

public final class HotPath {

    private static final String SQL = "SELECT * FROM order_item WHERE order_id = ?";

    private HotPath() {
    }

    public static void main(String[] arguments) throws Exception {
        Db.recording(false);
        int warmup = Integer.getInteger("hotpath.warmup", 100_000);
        int leases = Integer.getInteger("hotpath.leases", 200_000);
        int perLease = Integer.getInteger("hotpath.queries", 20);

        for (int run = 0; run < warmup; run++) {
            lease(perLease);
        }

        long[] samples = new long[leases];
        for (int run = 0; run < leases; run++) {
            long started = System.nanoTime();
            lease(perLease);
            samples[run] = System.nanoTime() - started;
        }
        Arrays.sort(samples);

        long total = 0;
        for (long sample : samples) {
            total += sample;
        }

        double perQuery = (double) total / leases / perLease;
        System.out.printf(
                "hotpath: perQuery=%.1fns p50Lease=%.1fns queries=%d%n",
                perQuery, (double) samples[leases / 2], (long) leases * perLease);
    }

    private static final boolean PREPARE_EACH = Boolean.getBoolean("hotpath.prepareEach");

    private static void lease(int queries) throws Exception {
        try (Connection connection = FakeDriver.pooled()) {
            if (PREPARE_EACH) {
                for (int query = 0; query < queries; query++) {
                    PreparedStatement statement = connection.prepareStatement(SQL);
                    statement.setInt(1, query);
                    statement.executeQuery();
                }
                return;
            }

            PreparedStatement statement = connection.prepareStatement(SQL);

            for (int query = 0; query < queries; query++) {
                statement.setInt(1, query);
                statement.executeQuery();
            }
        }
    }
}
