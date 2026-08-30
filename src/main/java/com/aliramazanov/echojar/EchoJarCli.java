package com.aliramazanov.echojar;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * The command line tool. Adding a {@code -javaagent} flag means restarting the application, and
 * echojar is meant for the process that is already running and cannot be restarted, so it needs
 * another way in, and that way is the JVM's attach API.
 *
 * <p>It ships inside the agent's own jar because attaching means giving the target a path to a
 * jar, and one file can find where it was loaded from and pass itself, while two files would mean
 * the user keeps both and has to pass the right one.
 *
 * <p>attach, dump and reset are the same attach underneath and only the options differ, so an
 * empty string lets the agent install itself, while {@code command=dump} and
 * {@code command=reset} are read by the installer and answered before anything is installed,
 * which is how a running JVM is read and cleared without echojar opening a socket. list does not
 * attach to anything, it only shows which JVMs you could attach to.
 */
public final class EchoJarCli {

    private EchoJarCli() {
    }

    static void main(String @NotNull [] arguments) {
        if (arguments.length == 0) {
            usage();
            System.exit(2);
        }

        try {
            switch (arguments[0]) {
                case "attach" -> attach(arguments);
                case "dump" -> dump(arguments);
                case "reset" -> reset(arguments);
                case "list" -> list();

                default -> {
                    usage();
                    System.exit(2);
                }
            }
        } catch (Exception failure) {
            String reason = failure.getMessage();
            String failureClass = failure.getClass().getSimpleName();
            System.err.println("echojar: " + (reason == null ? failureClass : reason));
            System.exit(1);
        }
    }

    private static void attach(String @NotNull [] arguments) throws Exception {
        if (arguments.length < 2) {
            usage();
            System.exit(2);
        }

        String pid = arguments[1];

        load(pid, arguments.length > 2 ? arguments[2] : "");

        System.err.printf("echojar: attached to %s%n", pid);
    }

    private static void dump(String @NotNull [] arguments) throws Exception {
        if (arguments.length < 2) {
            usage();
            System.exit(2);
        }

        String pid = arguments[1];

        StringBuilder options = new StringBuilder("command=dump");

        boolean toFile = arguments.length > 2 && !arguments[2].isBlank();

        if (toFile) {
            options.append(",out=").append(arguments[2]);
        }

        if (arguments.length > 3) {
            options.append(",threshold=").append(arguments[3]);
        }

        load(pid, options.toString());

        if (toFile) {
            System.err.printf("echojar: wrote findings from %s to %s%n", pid, arguments[2]);
        } else {
            System.err.printf("echojar: dumped %s to its own stderr%n", pid);
        }
    }

    private static void reset(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            usage();
            System.exit(2);
        }

        load(arguments[1], "command=reset");
        System.err.printf("echojar: cleared the counters of %s%n", arguments[1]);
    }

    private static void load(String pid, String options) throws Exception {
        VirtualMachine machine = VirtualMachine.attach(pid);
        try {
            machine.loadAgent(agentJar().toString(), options);
        } finally {
            machine.detach();
        }
    }

    private static void list() {
        List<VirtualMachineDescriptor> machines = VirtualMachine.list();

        if (machines.isEmpty()) {
            System.err.println("echojar: no attachable JVMs found");
            return;
        }

        for (VirtualMachineDescriptor machine : machines) {
            System.out.printf("%-8s %s%n", machine.id(), machine.displayName());
        }
    }

    private static @NotNull Path agentJar() throws Exception {
        URI location = EchoJarCli.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path jar = Paths.get(location).toAbsolutePath();

        if (!jar.toFile().isFile()) {
            throw new IllegalStateException("echojar must be run from its jar, found " + jar);
        }

        return jar;
    }

    private static void usage() {
        System.err.println("""
                usage:
                  java -jar echojar.jar attach <pid> [options]  load the agent into a running JVM
                  java -jar echojar.jar dump <pid> [file] [n]   print findings, at threshold n
                  java -jar echojar.jar reset <pid>             clear the counters
                  java -jar echojar.jar list                    show attachable JVMs

                options are comma separated, for example threshold=10,noise=false

                the target JVM must allow dynamic agent loading. JDK 21 and later warn on
                attach, and -XX:+EnableDynamicAgentLoading on the target silences it.""");
    }
}
