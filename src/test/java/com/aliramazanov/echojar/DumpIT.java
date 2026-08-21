package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DumpIT {

    @Test
    void printsWhatARunningAgentHasFoundWithoutStoppingIt() throws Exception {
        Path agentJar = Path.of(System.getProperty("echojar.agent.jar"));
        Path testClasses = Path.of(System.getProperty("echojar.test.classes"));
        Path work = Files.createTempDirectory("echojar-dump");
        Path signal = work.resolve("go");
        Path output = work.resolve("target.out");
        Path dump = work.resolve("dump.txt");

        Process target = new ProcessBuilder(
                java(),
                "-javaagent:" + agentJar + "=threshold=3,framework=com.aliramazanov.echojar.fake.,out=/dev/null",
                "-cp", testClasses.toString(), "shop.AttachTarget", signal.toString())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            long pid = awaitPid(output, target);

            Process dumper = new ProcessBuilder(
                    java(), "-jar", agentJar.toString(), "dump", Long.toString(pid), dump.toString())
                    .redirectErrorStream(true)
                    .start();
            String dumperOutput = new String(dumper.getInputStream().readAllBytes());
            assertTrue(dumper.waitFor(60, TimeUnit.SECONDS), "dump timed out");
            assertEquals(0, dumper.exitValue(), "dump failed: " + dumperOutput);

            assertTrue(target.isAlive(), "the target must still be running after a dump");

            String findings = Files.readString(dump);
            assertTrue(findings.contains("order_item"),
                    "the live dump should carry the findings so far:\n" + findings);
            assertTrue(findings.contains("executions in one connection lease"), findings);

            Files.createFile(signal);
            assertTrue(target.waitFor(60, TimeUnit.SECONDS), "target did not finish");
        } finally {
            target.destroyForcibly();
        }
    }

    private static long awaitPid(Path output, Process target) throws Exception {
        for (int waited = 0; waited < 600; waited++) {
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

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
