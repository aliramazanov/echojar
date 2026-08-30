package com.aliramazanov.echojar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliramazanov.echojar.fake.FakeDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shop.OrderService;

class TelemetryEventsIT {

    @BeforeEach
    void reset() {
        AgentState.reset();
    }

    @Test
    void anEchoReachesFlightRecorderWithItsSqlAndCallSite() throws Exception {
        List<RecordedEvent> echoes = record("echojar.Echo", () -> {
            try (Connection connection = FakeDriver.pooled()) {
                new OrderService(connection).summarise(9);
            }
        });

        assertFalse(echoes.isEmpty(), "the repeated statement should have produced an event");

        RecordedEvent echo = echoes.getFirst();

        assertTrue(
                echo.getString("sql").contains("order_item"),
                "the event carries the template: " + echo.getString("sql")
        );

        assertTrue(
                echo.getInt("executions") >= 3,
                "the event fires once the statement has crossed the threshold"
        );

        assertTrue(
                echo.getString("callSite").contains("OrderService"),
                "the event names the line that ran it: " + echo.getString("callSite")
        );
    }

    @Test
    void theHealthEventCarriesTheSameCountersAsTheBean() throws Exception {
        List<RecordedEvent> health = record("echojar.Health", () -> {
            try (Connection connection = FakeDriver.pooled()) {
                new OrderService(connection).summarise(5);
            }
        });

        assertFalse(health.isEmpty(), "the periodic health event should have been emitted");

        RecordedEvent last = health.getLast();

        assertTrue(last.getLong("executions") >= 5, "the run's queries are counted");
        assertTrue(last.getLong("leasesClosed") >= 1, "the lease that just closed is counted");
        assertTrue(last.getLong("statementsTemplated") >= 1);
        assertTrue(last.getLong("typesTransformed") >= 1);
        assertEquals(0, last.getLong("suppressed"), "nothing should have been suppressed");
    }

    private static List<RecordedEvent> record(String event, Work work) throws Exception {
        Path dump = Files.createTempFile("echojar-events", ".jfr");

        try (Recording recording = new Recording()) {
            recording.enable(event).withPeriod(java.time.Duration.ofMillis(100));
            recording.start();

            work.run();

            Thread.sleep(300);
            recording.stop();
            recording.dump(dump);
        }

        try {
            return RecordingFile.readAllEvents(dump).stream()
                    .filter(recorded -> recorded.getEventType().getName().equals(event))
                    .toList();
        } finally {
            Files.deleteIfExists(dump);
        }
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }
}
