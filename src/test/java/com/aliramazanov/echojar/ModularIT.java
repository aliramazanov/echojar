package com.aliramazanov.echojar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModularIT {

    @Test
    void aDriverOnTheModulePathDoesNotBringTheApplicationDown() throws Exception {
        Path agentJar = Path.of(System.getProperty("echojar.agent.jar"));
        Path testClasses = Path.of(System.getProperty("echojar.test.classes"));

        Path driverJar = Path.of(org.hsqldb.jdbc.JDBCDriver.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());

        Path report = Files.createTempFile("echojar-modular", ".txt");

        Process target = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-javaagent:" + agentJar + "=threshold=3,app=shop.,out=" + report,
                "--module-path",
                driverJar.toString(),
                "--add-modules",
                "org.hsqldb",
                "-cp",
                testClasses.toString(),
                "shop.ModularTarget"
        ).redirectErrorStream(true).start();

        String output = new String(target.getInputStream().readAllBytes());
        assertTrue(target.waitFor(120, TimeUnit.SECONDS), "the modular target timed out");

        assertEquals(
                0,
                target.exitValue(),
                "a driver in a named module must not fail its superinterface check:\n" + output
        );

        assertTrue(output.contains("modular target finished"), output);

        String findings = Files.readString(report);

        assertTrue(
                findings.contains("8 executions in one connection lease"),
                "the agent must still count through a named module:\n" + findings
        );

        assertTrue(
                findings.contains("ModularTarget"),
                "and still name the call site:\n" + findings
        );
    }
}
