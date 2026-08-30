package com.aliramazanov.echojar.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class InstallerOutputTest {

    @Test
    void resettingDoesNotCloseTheApplicationsStderr() {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

            Installer.install("command=reset", null, Mode.ATTACH);
            System.err.println("the application still owns its stderr");
        } finally {
            System.setErr(original);
        }

        String written = captured.toString(StandardCharsets.UTF_8);

        assertTrue(
                written.contains("the application still owns its stderr"),
                "closing the borrowed stderr would silence the application it is observing"
        );
    }

    @Test
    void dumpingDoesNotCloseTheApplicationsStderr() {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

            Installer.install("command=dump", null, Mode.ATTACH);
            System.err.println("still writable after a dump");
        } finally {
            System.setErr(original);
        }

        assertTrue(
                captured.toString(StandardCharsets.UTF_8).contains("still writable after a dump"),
                "a dump borrows stderr and must hand it back open"
        );
    }

    @Test
    void aFileSinkIsClosedRatherThanLeaked() throws Exception {
        Path file = Files.createTempFile("echojar-sink", ".log");

        Installer.install("command=reset,out=" + file, null, Mode.ATTACH);

        String written = Files.readString(file);

        assertTrue(written.contains("echojar counters reset"), "the file received the message");

        assertTrue(
                Files.deleteIfExists(file),
                "an open handle on Windows would stop the file being removed"
        );
    }

    @Test
    void anUnwritableFileFallsBackToStderrInsteadOfFailing() {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();

        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));

            Installer.install("command=reset,out=/nonexistent-directory/echojar.log", null,
                    Mode.ATTACH);

            System.err.println("stderr survived the fallback");
        } finally {
            System.setErr(original);
        }

        String written = captured.toString(StandardCharsets.UTF_8);

        assertTrue(written.contains("cannot write to"), "the failure is reported");
        assertTrue(written.contains("stderr survived the fallback"), "and stderr stays open");
        assertFalse(written.isBlank());
    }
}
