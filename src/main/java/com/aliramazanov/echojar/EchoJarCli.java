package com.aliramazanov.echojar;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public final class EchoJarCli {

    private EchoJarCli() {
    }

    public static void main(String[] arguments) {
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
            System.err.println("echojar: " + (reason == null ? failure.getClass().getSimpleName() : reason));
            System.exit(1);
        }
    }

    private static void attach(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            usage();
            System.exit(2);
        }

        String pid = arguments[1];
        String options = arguments.length > 2 ? arguments[2] : "";
        Path jar = agentJar();
        VirtualMachine machine = VirtualMachine.attach(pid);

        try {
            machine.loadAgent(jar.toString(), options);
        } finally {
            machine.detach();
        }

        System.err.printf("echojar: attached to %s%n", pid);
    }

    private static void dump(String[] arguments) throws Exception {
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
            System.err.printf("echojar: wrote the current findings of %s to %s%n", pid, arguments[2]);
        } else {
            System.err.printf("echojar: asked %s to print its current findings to its own stderr%n", pid);
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

    private static Path agentJar() throws Exception {
        URI location = EchoJarCli.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path jar = Paths.get(location).toAbsolutePath();

        if (!jar.toFile().isFile()) {
            throw new IllegalStateException("echojar must be run from its jar, found " + jar);
        }

        return jar;
    }

    private static void usage() {
        System.err.println(
                """
                        usage:
                          java -jar echojar.jar attach <pid> [options]   load the agent into a running JVM
                          java -jar echojar.jar dump <pid> [file] [n]    print what a running agent has found, optionally at threshold n
                          java -jar echojar.jar reset <pid>              forget everything counted so far
                          java -jar echojar.jar list                     show attachable JVMs

                        options are comma separated, for example threshold=10,noise=false

                        the target JVM must allow dynamic agent loading. JDK 21 and later print a
                        warning on attach; start the target with -XX:+EnableDynamicAgentLoading to
                        silence it.""");
    }
}
