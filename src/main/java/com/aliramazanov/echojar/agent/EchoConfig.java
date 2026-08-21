package com.aliramazanov.echojar.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aliramazanov.echojar.bootstrap.watch.Journal;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.none;

final class EchoConfig {

    private static final String PROPERTY_PREFIX = "echojar.";

    private static final List<String> DEFAULT_IGNORED_TYPES = List.of(
            "org.jboss.jca.adapters.jdbc.",
            "org.apache.shardingsphere.shardingjdbc.jdbc.core.statement.",
            "org.apache.shardingsphere.driver.jdbc.core.statement.");

    private static final List<String> DEFAULT_FRAMEWORK_PREFIXES = List.of(
            "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.",
            "com.aliramazanov.echojar.agent.",
            "com.aliramazanov.echojar.bootstrap.",
            "com.aliramazanov.echojar.shaded.",
            "org.hibernate.", "org.springframework.", "org.jboss.", "org.apache.",
            "com.zaxxer.hikari.", "org.postgresql.", "com.mysql.", "oracle.jdbc.",
            "org.h2.", "org.mariadb.", "org.sqlite.", "com.microsoft.sqlserver.",
            "net.bytebuddy.");

    private final int threshold;
    private final int templateCacheLimit;
    private final int stackDepth;
    private final boolean suppressNoise;
    private final boolean verbose;
    private final boolean units;
    private final ElementMatcher.Junction<TypeDescription> ignoredTypes;
    private final List<String> frameworkPrefixes;
    private final String output;
    private final Journal.Level logLevel;
    private final boolean diagnostics;
    private final Command command;
    private final int thresholdIfSet;

    private EchoConfig(Map<String, String> options) {
        this.threshold = atLeastOne(integer(options, "threshold", 5), 5);
        this.templateCacheLimit = atLeastOne(integer(options, "templates", 5000), 5000);
        this.stackDepth = atLeastOne(integer(options, "depth", 200), 200);
        this.suppressNoise = bool(options, "noise", true);
        this.verbose = bool(options, "verbose", false);
        this.units = bool(options, "units", true);
        this.ignoredTypes = matcher(prefixes(options.get("ignore"), DEFAULT_IGNORED_TYPES));
        this.frameworkPrefixes = prefixes(options.get("framework"), DEFAULT_FRAMEWORK_PREFIXES);
        this.output = options.get("out");
        this.logLevel = level(options.get("log"));
        this.diagnostics = bool(options, "diagnostics", false);
        this.command = Command.of(options.get("command"));
        this.thresholdIfSet = options.containsKey("threshold") ? threshold : 0;
    }

    static EchoConfig parse(String arguments) {
        Map<String, String> options = new LinkedHashMap<>();

        if (arguments != null && !arguments.isBlank()) {
            for (String pair : arguments.split(",")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    options.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
                }
            }
        }

        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(PROPERTY_PREFIX)) {
                options.put(name.substring(PROPERTY_PREFIX.length()), System.getProperty(name));
            }
        }

        return new EchoConfig(options);
    }

    Command command() {
        return command;
    }

    int thresholdIfSet() {
        return thresholdIfSet;
    }

    Journal.Level logLevel() {
        return logLevel;
    }

    boolean diagnostics() {
        return diagnostics;
    }

    private static Journal.Level level(String value) {
        if (value == null || value.isBlank()) {
            return Journal.Level.WARN;
        }

        try {
            return Journal.Level.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Journal.Level.WARN;
        }
    }

    String output() {
        return output;
    }

    int threshold() {
        return threshold;
    }

    int templateCacheLimit() {
        return templateCacheLimit;
    }

    int stackDepth() {
        return stackDepth;
    }

    boolean suppressNoise() {
        return suppressNoise;
    }

    boolean verbose() {
        return verbose;
    }

    boolean units() {
        return units;
    }

    List<String> frameworkPrefixes() {
        return frameworkPrefixes;
    }

    ElementMatcher.Junction<TypeDescription> ignoredTypes() {
        return ignoredTypes;
    }

    private static ElementMatcher.Junction<TypeDescription> matcher(List<String> prefixes) {
        ElementMatcher.Junction<TypeDescription> matcher = none();

        for (String prefix : prefixes) {
            matcher = matcher.or(nameStartsWith(prefix));
        }

        return matcher;
    }

    private static List<String> prefixes(String value, List<String> fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        List<String> merged = new ArrayList<>(fallback);

        for (String prefix : value.split(";")) {
            String trimmed = prefix.trim();
            if (!trimmed.isEmpty()) {
                merged.add(trimmed);
            }
        }

        return List.copyOf(merged);
    }

    private static int atLeastOne(int value, int fallback) {
        return value < 1 ? fallback : value;
    }

    private static int integer(Map<String, String> options, String key, int fallback) {
        String value = options.get(key);

        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Map<String, String> options, String key, boolean fallback) {
        String value = options.get(key);

        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty()
                || "true".equalsIgnoreCase(trimmed)
                || "on".equalsIgnoreCase(trimmed)
                || "yes".equalsIgnoreCase(trimmed)
                || "1".equals(trimmed);
    }
}
