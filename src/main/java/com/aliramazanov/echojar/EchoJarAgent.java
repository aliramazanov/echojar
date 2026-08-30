package com.aliramazanov.echojar;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

import com.aliramazanov.echojar.agent.Installer;
import com.aliramazanov.echojar.agent.Mode;

/**
 * The class the JVM calls to start the agent.
 *
 * <p>Its job is to copy the small counting jar into the bootstrap loader, so that the code
 * echojar adds to a driver can reach it from any classloader. It does this once, because both
 * entry points lead here and the agent can be loaded more than once in the same JVM, such as a
 * second {@code -javaagent} flag or an attach after a startup load.
 *
 * <p>If the copy fails, the agent stops instead of adding calls that would not resolve. It
 * prints the reason to {@code System.err}, because the log class lives in the jar that just
 * failed to load.
 */
public final class EchoJarAgent {

    private static final String BOOTSTRAP_RESOURCE = "echojar-bootstrap.jar";

    private static volatile boolean injected;

    private EchoJarAgent() {
    }

    /**
     * The JVM calls this when the application is started with {@code -javaagent:echojar.jar}, and
     * it runs before the application's own {@code main} method, so echojar is in place before the
     * first query. It is found through the {@code Premain-Class} line in the manifest, and the
     * name and the parameters are fixed, so changing either leaves the jar with no entry point.
     *
     * <p>Nothing has loaded yet at this point, which is what makes it the better of the two ways
     * in, since a class can still be given a new variable while it loads and a connection can
     * then carry its own count.
     *
     * @param arguments       the text after the {@code =} sign, or null if there was none
     * @param instrumentation the JVM's handle for rewriting classes
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        install(arguments, instrumentation, Mode.STARTUP);
    }

    /**
     * The JVM calls this when echojar is loaded into a program that is already running, which is
     * what {@code echojar attach <pid>} does, and it is found through the {@code Agent-Class}
     * line in the manifest. It is also how {@code dump} and {@code reset} reach a live JVM, since
     * those attach a second time and pass a command rather than installing anything.
     *
     * <p>The driver classes were loaded long ago by then, so the code inside their methods can be
     * replaced but no new variable can be added to them, and a connection's count has to be held
     * in a lookup table keyed by the connection.
     *
     * @param arguments       the text sent by the attaching process, or null if there was none
     * @param instrumentation the JVM's handle for rewriting classes
     */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        install(arguments, instrumentation, Mode.ATTACH);
    }

    private static void install(String arguments, Instrumentation instrumentation, Mode mode) {
        try {
            if (!injected) {
                injectBootstrap(instrumentation);
                injected = true;
            }
        } catch (IOException | RuntimeException failure) {
            System.err.println("echojar: could not inject the bootstrap jar, agent disabled: " + failure);
            return;
        }

        Installer.install(arguments, instrumentation, mode);
    }

    private static void injectBootstrap(Instrumentation instrumentation) throws IOException {
        Path jar = Files.createTempFile("echojar-bootstrap", ".jar");

        try (InputStream source = EchoJarAgent.class.getResourceAsStream(BOOTSTRAP_RESOURCE)) {
            if (source == null) {
                throw new IOException(BOOTSTRAP_RESOURCE + " is missing from the agent jar");
            }

            Files.copy(source, jar, StandardCopyOption.REPLACE_EXISTING);
        }

        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar.toFile()));
        jar.toFile().deleteOnExit();
    }
}
