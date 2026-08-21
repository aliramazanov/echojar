package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CliIT {

    @Test
    void noArgumentsPrintsUsageAndFails() throws Exception {
        Result result = run();
        assertEquals(2, result.exit);
        assertTrue(result.output.contains("usage:"), result.output);
    }

    @Test
    void anUnknownCommandPrintsUsageAndFails() throws Exception {
        Result result = run("summon");
        assertEquals(2, result.exit);
        assertTrue(result.output.contains("usage:"), result.output);
    }

    @Test
    void attachWithoutAPidPrintsUsageAndFails() throws Exception {
        Result result = run("attach");
        assertEquals(2, result.exit);
        assertTrue(result.output.contains("usage:"), result.output);
    }

    @Test
    void attachingToAPidThatIsNotAJvmFailsCleanly() throws Exception {
        Result result = run("attach", "21474836");
        assertEquals(1, result.exit, result.output);
        assertTrue(result.output.contains("echojar:"), result.output);
        assertFalse(result.output.contains("\tat com.sun.tools.attach"),
                "a missing process is a user error, not a stack trace:\n" + result.output);
    }

    @Test
    void attachingToNonsenseFailsCleanly() throws Exception {
        Result result = run("attach", "not-a-pid");
        assertEquals(1, result.exit, result.output);
        assertTrue(result.output.contains("echojar:"), result.output);
        assertFalse(result.output.contains("Exception in thread"),
                "a bad pid must not surface as an unhandled exception:\n" + result.output);
    }

    @Test
    void listSucceeds() throws Exception {
        Result result = run("list");
        assertEquals(0, result.exit, result.output);
    }

    private static Result run(String... arguments) throws Exception {
        String[] command = new String[arguments.length + 3];
        command[0] = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        command[1] = "-jar";
        command[2] = System.getProperty("echojar.agent.jar");
        System.arraycopy(arguments, 0, command, 3, arguments.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "cli timed out");
        return new Result(process.exitValue(), output);
    }

    private record Result(int exit, String output) {
    }
}
