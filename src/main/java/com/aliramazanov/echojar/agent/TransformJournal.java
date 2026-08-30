package com.aliramazanov.echojar.agent;

import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import com.aliramazanov.echojar.bootstrap.watch.Journal;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.jetbrains.annotations.NotNull;

final class TransformJournal extends AgentBuilder.Listener.Adapter {

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
        Journal.debug("transformed " + type.getName());
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
            Journal.debug("type hierarchy incomplete for " + name + ": " + failure.getMessage());
            return;
        }

        Diagnostics.suppressed(Diagnostics.Site.TRANSFORM, failure);
        Journal.warn("could not transform " + name + ": " + failure);
    }
}
