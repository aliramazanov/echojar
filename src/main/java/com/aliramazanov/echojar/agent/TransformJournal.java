package com.aliramazanov.echojar.agent;

import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import com.aliramazanov.echojar.bootstrap.watch.Journal;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.jetbrains.annotations.NotNull;

final class TransformJournal extends AgentBuilder.Listener.Adapter {

    private static final int MOST_ORIGINS_REMEMBERED = 5_000;

    private static final ConcurrentMap<String, String> ORIGINS = new ConcurrentHashMap<>();


    static DynamicType.Builder<?> rewriting(
            TypeDescription type,
            JavaModule module,
            ProtectionDomain domain,
            DynamicType.Builder<?> builder
    ) {
        Modules.ensureReads(module);

        if (ORIGINS.size() < MOST_ORIGINS_REMEMBERED) {
            ORIGINS.putIfAbsent(type.getName(), origin(domain));
        }

        return builder;
    }

    static String origin(ProtectionDomain domain) {
        if (domain == null) {
            return "an unknown source";
        }

        CodeSource source = domain.getCodeSource();

        if (source == null || source.getLocation() == null) {
            return "the runtime";
        }

        String location = source.getLocation().toString();
        int lastSlash = location.lastIndexOf('/');

        return lastSlash >= 0 && lastSlash < location.length() - 1
                ? location.substring(lastSlash + 1)
                : location;
    }

    private static boolean unresolvableType(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getName().contains("NoSuchTypeException")) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onTransformation(
            @NotNull TypeDescription type,
            ClassLoader loader,
            JavaModule module,
            boolean loaded,
            @NotNull DynamicType dynamicType
    ) {
        Diagnostics.transformed();

        if (Journal.writes(Journal.Level.DEBUG)) {
            Journal.debug("transformed " + type.getName()
                    + " from " + ORIGINS.getOrDefault(type.getName(), "an unknown source"));
        }
    }

    @Override
    public void onError(
            @NotNull String name,
            ClassLoader loader,
            JavaModule module,
            boolean loaded,
            @NotNull Throwable failure
    ) {
        if (unresolvableType(failure)) {
            Diagnostics.unresolvable();

            if (Journal.writes(Journal.Level.DEBUG)) {
                Journal.debug("type hierarchy incomplete for " + name + ": " + failure.getMessage());
            }

            return;
        }

        Diagnostics.suppressed(Diagnostics.Site.TRANSFORM, failure);
        Journal.warn("could not transform " + name
                + " from " + ORIGINS.getOrDefault(name, "an unknown source") + ": " + failure);
    }
}
