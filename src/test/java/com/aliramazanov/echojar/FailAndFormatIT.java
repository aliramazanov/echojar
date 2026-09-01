package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class FailAndFormatIT {

    @Test
    void aRunOverTheFailThresholdEndsWithAFailingStatus() throws Exception {
        Result result = run("threshold=3,fail=10");

        assertEquals(1, result.exit, result.output);
        assertTrue(result.output.contains("failing this run"), result.output);
    }

    @Test
    void aRunUnderTheFailThresholdSucceeds() throws Exception {
        Result result = run("threshold=3,fail=100");

        assertEquals(0, result.exit, result.output);
        assertFalse(result.output.contains("failing this run"), result.output);
    }

    @Test
    void withoutTheFailOptionTheRunIsLeftAlone() throws Exception {
        Result result = run("threshold=3");

        assertEquals(0, result.exit, result.output);
        assertFalse(result.output.contains("failing this run"), result.output);
    }

    @Test
    void theReportStillPrintsWhenTheRunIsFailed() throws Exception {
        Result result = run("threshold=3,fail=10");

        assertTrue(result.output.contains("order_item"),
                "failing the run must not swallow the report:\n" + result.output);
    }

    @Test
    void jsonOutputIsWellFormedAndCarriesTheFinding() throws Exception {
        Result result = run("threshold=3,format=json");

        assertEquals(0, result.exit, result.output);

        String json = json(result.output);

        assertTrue(json.startsWith("{") && json.endsWith("}"), json);
        assertEquals(0, depth(json), "braces are not balanced:\n" + json);

        for (String key : List.of("generated", "unit", "threshold", "echoes", "sql",
                "executions", "callSite", "health", "suppressedFailures")) {
            assertTrue(json.contains("\"" + key + "\""), "missing " + key + " in:\n" + json);
        }

        assertTrue(json.contains("order_item"), json);
        assertTrue(json.contains("HotPath"), json);
    }

    @Test
    void failingAndJsonWorkTogether() throws Exception {
        Result result = run("threshold=3,fail=10,format=json");

        assertEquals(1, result.exit, result.output);

        String json = json(result.output);

        assertEquals(0, depth(json), "the json is still parseable when the run fails:\n" + json);
    }

    private static String json(String output) {
        return output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{") && line.endsWith("}"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no json line in:\n" + output));
    }

    private static int depth(String json) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = 0; index < json.length(); index++) {
            char letter = json.charAt(index);

            if (escaped) {
                escaped = false;
            } else if (letter == '\\') {
                escaped = true;
            } else if (letter == '"') {
                inString = !inString;
            } else if (!inString && (letter == '{' || letter == '[')) {
                depth++;
            } else if (!inString && (letter == '}' || letter == ']')) {
                depth--;
            }
        }

        return depth;
    }

    private static Result run(String options) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-javaagent:" + System.getProperty("echojar.agent.jar") + "="
                + options + ",framework=com.aliramazanov.echojar.fake.");
        command.add("-Dhotpath.warmup=0");
        command.add("-Dhotpath.leases=3");
        command.add("-Dhotpath.queries=20");
        command.add("-cp");
        command.add(System.getProperty("echojar.test.classes"));
        command.add("shop.HotPath");

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());

        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "the forked jvm timed out");

        return new Result(process.exitValue(), output);
    }

    private record Result(int exit, String output) {
    }
}
