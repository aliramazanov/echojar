package com.aliramazanov.echojar.bootstrap.watch;

import java.lang.management.ManagementFactory;
import javax.management.JMException;
import javax.management.ObjectName;

import jdk.jfr.FlightRecorder;

public final class Telemetry {

    private Telemetry() {
    }

    public static void register() {
        registerFlightRecorder();
        registerManagementBean();
    }

    private static void registerFlightRecorder() {
        try {
            FlightRecorder.addPeriodicEvent(HealthEvent.class, HealthEvent::emit);
            Journal.info("flight recorder events registered under the echojar category");
        } catch (RuntimeException | Error failure) {
            Diagnostics.suppressed(Diagnostics.Site.REPORT, failure);
        }
    }

    private static void registerManagementBean() {
        try {
            ManagementFactory.getPlatformMBeanServer().registerMBean(
                    new DiagnosticsBean(),
                    new ObjectName("com.aliramazanov.echojar:type=Diagnostics")
            );

            Journal.info("diagnostics bean registered at com.aliramazanov" +
                    ".echojar:type=Diagnostics");
        } catch (JMException | RuntimeException | Error failure) {
            Diagnostics.suppressed(Diagnostics.Site.REPORT, failure);
        }
    }
}
