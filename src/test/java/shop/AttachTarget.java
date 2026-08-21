package shop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import com.aliramazanov.echojar.fake.Db;
import com.aliramazanov.echojar.fake.FakeDriver;

public final class AttachTarget {

    private AttachTarget() {
    }

    public static void main(String[] arguments) throws Exception {
        Path signal = Path.of(arguments[0]);
        workload();
        System.out.println("pre-attach=" + Db.executed().size());
        System.out.println("pid=" + ProcessHandle.current().pid());
        System.out.flush();

        for (int waited = 0; waited < 600 && !Files.exists(signal); waited++) {
            Thread.sleep(100);
        }

        Db.reset();
        workload();

        System.out.println("post-attach=" + Db.executed().size());
        System.out.println("findings=" + findings());
        System.out.flush();
    }

    private static String findings() {
        try {
            Class<?> ledger = Class.forName("com.aliramazanov.echojar.bootstrap.findings.Ledger");
            List<?> found = (List<?>) ledger.getMethod("findings").invoke(null);

            if (found.isEmpty()) {
                return "none";
            }

            Object first = found.get(0);
            Object template = first.getClass().getMethod("template").invoke(first);
            Object peak = first.getClass().getMethod("peakPerLease").invoke(first);
            Object site = first.getClass().getMethod("site").invoke(first);

            return found.size() + "|" + template + "|" + peak + "|" + site;
        } catch (ClassNotFoundException absent) {
            return "agent-absent";
        } catch (Exception failure) {
            return "error:" + failure;
        }
    }

    private static void workload() throws Exception {
        for (int lease = 0; lease < 2; lease++) {
            try (Connection connection = FakeDriver.pooled()) {
                new OrderService(connection).summarise(6);
            }
        }
    }
}
