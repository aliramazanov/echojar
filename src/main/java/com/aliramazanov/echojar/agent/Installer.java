package com.aliramazanov.echojar.agent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;

import com.aliramazanov.echojar.bootstrap.Echo;
import com.aliramazanov.echojar.bootstrap.LiveReport;
import com.aliramazanov.echojar.bootstrap.findings.Ledger;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import com.aliramazanov.echojar.bootstrap.watch.Journal;
import com.aliramazanov.echojar.bootstrap.watch.Telemetry;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jetbrains.annotations.NotNull;

public final class Installer {

    private Installer() {
    }

    public static void install(String arguments, Instrumentation instrumentation, Mode mode) {
        EchoConfig requested = EchoConfig.parse(arguments);

        if (requested.command() == Command.DUMP) {
            dump(requested);
            return;
        }

        if (requested.command() == Command.RESET) {
            Ledger.reset();
            Diagnostics.resetWindow();

            try (PrintStream out = sink(requested)) {
                out.printf("%n=== echojar counters reset %s ===%n", java.time.Instant.now());
                out.flush();
            }

            return;
        }

        if (!ready()) {
            return;
        }

        Modules.using(instrumentation);
        Detector detector = new Detector(requested);
        Echo.install(detector);

        PrintStream out = sink(requested);
        Journal.configure(requested.logLevel(), out, 200);
        Telemetry.register();

        RequestInstrumentation.apply(
                JdbcInstrumentation.apply(
                        agent(requested, mode, out),
                        requested,
                        mode
                ), requested
        ).installOn(instrumentation);

        Report report = new Report(requested.threshold(), detector, requested.diagnostics());
        LiveReport.install(report::print);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> report.print(out), "echojar-report"));
    }

    private static void dump(EchoConfig config) {
        PrintStream out = sink(config);

        if (!LiveReport.print(out, config.thresholdIfSet())) {
            out.println("echojar: no agent is installed in this JVM");
        }

        out.flush();
    }

    private static boolean ready() {
        if (Echo.class.getClassLoader() != null) {
            System.err.println(
                    "echojar: bootstrap classes did not land on the bootstrap loader, agent " +
                            "disabled");
            return false;
        }

        if (!Echo.claimInstallation()) {
            System.err.println("echojar: already installed in this JVM, ignoring the second agent");
            return false;
        }

        return true;
    }

    private static AgentBuilder agent(EchoConfig config, @NotNull Mode mode, PrintStream out) {
        AgentBuilder agent = new AgentBuilder.Default().ignore(ignored(config));
        if (mode.frozen()) {
            agent = agent.disableClassFormatChanges()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.RedefinitionStrategy.BatchAllocator.ForFixedSize.ofSize(1))
                    .with(AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE);
        }

        agent = agent.with(new TransformJournal());

        if (config.verbose()) {
            agent = agent.with(new AgentBuilder.Listener.StreamWriting(out));
        }

        return agent;
    }

    private static PrintStream sink(@NotNull EchoConfig config) {
        if (config.output() == null) {
            return System.err;
        }

        try {
            return new PrintStream(new FileOutputStream(config.output(), true), true);
        } catch (IOException failure) {
            System.err.println(
                    "echojar: cannot write to " + config.output() + ", reporting to stderr");
            return System.err;
        }
    }

    private static AgentBuilder.@NotNull RawMatcher ignored(EchoConfig config) {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        ElementMatcher<? super TypeDescription> types = JdbcInstrumentation.globalIgnores(config);

        return (type, loader, module, loaded, domain) -> loader == null || loader == platform ||
                types.matches(type);
    }
}
