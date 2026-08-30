package com.aliramazanov.echojar.agent;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.time.Instant;

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

/**
 * Sets the agent up. {@code EchoJarAgent} calls this once the counting jar is in place.
 *
 * <p>The options string decides which of three things happens. dump and reset are sent in by the
 * command line tool to a JVM that already has an agent in it, and both answer and return without
 * setting anything up. Anything else installs the agent.
 *
 * <p>Installing goes in a fixed order because each step needs the one before it. The counting
 * classes have to be reachable before anything is rewritten. The detector has to reach the
 * counting code before a query can be judged, and the report has to exist before the shutdown
 * hook that prints it.
 */
public final class Installer {

    private static final int ONE_CLASS_AT_A_TIME = 1;

    private Installer() {
    }

    public static void install(String arguments, Instrumentation instrumentation, Mode mode) {
        EchoConfig config = EchoConfig.parse(arguments);

        switch (config.command()) {
            case DUMP -> dump(config);
            case RESET -> reset(config);
            case INSTALL -> {
                if (claimInstall()) {
                    installAgent(config, instrumentation, mode);
                }
            }
        }
    }

    private static void installAgent(
            EchoConfig config,
            Instrumentation instrumentation,
            Mode mode
    ) {
        Modules.using(instrumentation);

        Detector detector = new Detector(config);
        Echo.install(detector);

        Journal.configure(config.logLevel(), openStream(config));
        Telemetry.register();

        AgentBuilder builder = newAgentBuilder(config, mode, Journal.out());
        builder = JdbcInstrumentation.apply(builder, config, mode);
        builder = RequestInstrumentation.apply(builder, config);
        builder.installOn(instrumentation);

        Report report = new Report(config.threshold(), detector, config.diagnostics());
        LiveReport.install(report::print);
        Runtime.getRuntime()
                .addShutdownHook(new Thread(() -> report.print(Journal.out()), "echojar-report"));
    }

    private static void dump(EchoConfig config) {
        try (Output output = openOutput(config)) {
            if (!LiveReport.print(output.stream(), config.thresholdIfSet())) {
                output.stream().println("echojar: no agent is installed in this JVM");
            }
        }
    }

    private static void reset(EchoConfig config) {
        Ledger.reset();
        Diagnostics.resetWindow();

        try (Output output = openOutput(config)) {
            output.stream().printf("%n=== echojar counters reset %s ===%n", Instant.now());
        }
    }

    private static boolean claimInstall() {
        if (!countingCodeIsOnBootstrapLoader()) {
            System.err.println("echojar: bootstrap classes did not land on the bootstrap loader, agent " + "disabled");
            return false;
        }

        if (!Echo.claimInstallation()) {
            System.err.println("echojar: already installed in this JVM, ignoring the second agent");
            return false;
        }

        return true;
    }

    private static boolean countingCodeIsOnBootstrapLoader() {
        return Echo.class.getClassLoader() == null;
    }

    private static AgentBuilder newAgentBuilder(
            EchoConfig config,
            @NotNull Mode mode,
            PrintStream out
    ) {
        AgentBuilder builder = new AgentBuilder.Default().ignore(ignored(config));

        if (mode.frozen()) {
            builder = alsoRedoAlreadyLoadedClasses(builder);
        }

        builder = builder.with(new TransformJournal());

        if (config.verbose()) {
            builder = builder.with(new AgentBuilder.Listener.StreamWriting(out));
        }

        return builder;
    }

    private static AgentBuilder alsoRedoAlreadyLoadedClasses(AgentBuilder builder) {
        return builder.disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.RedefinitionStrategy.BatchAllocator.ForFixedSize.ofSize(ONE_CLASS_AT_A_TIME))
                .with(AgentBuilder.RedefinitionStrategy.DiscoveryStrategy.Reiterating.INSTANCE);
    }

    private static AgentBuilder.RawMatcher ignored(EchoConfig config) {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        ElementMatcher<? super TypeDescription> neverTouch =
                JdbcInstrumentation.globalIgnores(config);

        return (type, loader, _, _, _)
                -> belongsToTheJdk(loader, platform) || neverTouch.matches(type);
    }

    private static boolean belongsToTheJdk(ClassLoader loader, ClassLoader platform) {
        return loader == null || loader == platform;
    }

    private static Output openOutput(EchoConfig config) {
        PrintStream stream = openStream(config);

        return new Output(stream, stream != System.err);
    }

    private static PrintStream openStream(@NotNull EchoConfig config) {
        if (config.output() == null) {
            return System.err;
        }

        try {
            return new PrintStream(new FileOutputStream(config.output(), true), true);
        } catch (IOException failure) {
            System.err.println("echojar: cannot write to " + config.output() + ", reporting to stderr");
            return System.err;
        }
    }

    private record Output(PrintStream stream, boolean weOpenedIt) implements AutoCloseable {

        @Override
        public void close() {
            stream.flush();

            if (weOpenedIt) {
                stream.close();
            }
        }
    }
}
