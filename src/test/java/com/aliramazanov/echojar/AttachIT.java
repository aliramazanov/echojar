package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AttachIT {

    @Test
    void attachesToAJvmThatIsAlreadyRunning() throws Exception {
        Path agentJar = Path.of(System.getProperty("echojar.agent.jar"));
        Path testClasses = Path.of(System.getProperty("echojar.test.classes"));
        Path work = Files.createTempDirectory("echojar-attach");
        Path signal = work.resolve("go");
        Path output = work.resolve("target.out");

        Process target = new ProcessBuilder(
                javaBinary(), "-cp", testClasses.toString(), "shop.AttachTarget", signal.toString())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            long pid = awaitPid(output, target);
            Process attach = new ProcessBuilder(
                    javaBinary(), "-jar", agentJar.toString(), "attach", Long.toString(pid), "threshold=3,framework=com.aliramazanov.echojar.fake.")
                    .redirectErrorStream(true)
                    .start();
            String attachOutput = new String(attach.getInputStream().readAllBytes());
            assertTrue(attach.waitFor(60, TimeUnit.SECONDS), "attach command timed out");
            assertEquals(0, attach.exitValue(), "attach failed: " + attachOutput);

            Process second = new ProcessBuilder(
                    javaBinary(), "-jar", agentJar.toString(), "attach", Long.toString(pid), "threshold=3")
                    .redirectErrorStream(true)
                    .start();
            String secondOutput = new String(second.getInputStream().readAllBytes());
            assertTrue(second.waitFor(60, TimeUnit.SECONDS), "second attach timed out");
            assertEquals(0, second.exitValue(), "attaching twice must not fail: " + secondOutput);

            Files.createFile(signal);
            assertTrue(target.waitFor(60, TimeUnit.SECONDS), "target did not finish");

            List<String> lines = Files.readAllLines(output);
            String findings = line(lines, "findings=");
            assertEquals("12", line(lines, "post-attach="), "the workload ran after attach");
            assertTrue(findings.startsWith("1|"), "expected one finding after attach, got " + findings);
            assertTrue(findings.contains("SELECT * FROM order_item WHERE order_id = ?"), findings);
            assertTrue(findings.contains("|6|"), "expected six executions per lease, got " + findings);
            assertTrue(findings.contains("OrderService.summarise"), "expected a call site, got " + findings);
        } finally {
            target.destroyForcibly();
        }
    }

    private static long awaitPid(Path output, Process target) throws Exception {
        for (int waited = 0; waited < 300; waited++) {
            if (Files.exists(output)) {
                for (String line : Files.readAllLines(output)) {
                    if (line.startsWith("pid=")) {
                        return Long.parseLong(line.substring(4).trim());
                    }
                }
            }
            if (!target.isAlive()) {
                fail("target exited early:\n" + Files.readString(output));
            }
            Thread.sleep(100);
        }
        return fail("target never reported its pid");
    }

    private static String line(List<String> lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return fail("no line starting with " + prefix + " in " + lines);
    }

    private static String javaBinary() throws IOException {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
