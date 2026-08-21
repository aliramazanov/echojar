package com.aliramazanov.echojar.agent;

import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;
import com.aliramazanov.echojar.bootstrap.watch.Journal;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

final class TransformJournal extends AgentBuilder.Listener.Adapter {

    @Override
    public void onTransformation(
            TypeDescription type,
            ClassLoader loader,
            JavaModule module,
            boolean loaded,
            DynamicType dynamicType) {
        Diagnostics.transformed();
        Journal.debug("transformed " + type.getName());
    }

    @Override
    public void onError(
            String name, ClassLoader loader, JavaModule module, boolean loaded, Throwable failure) {
        if (unresolvableType(failure)) {
            Diagnostics.unresolvable();
            Journal.debug("type hierarchy incomplete for " + name + ": " + failure.getMessage());
            return;
        }
        Diagnostics.suppressed(Diagnostics.Site.TRANSFORM, failure);
        Journal.warn("could not transform " + name + ": " + failure);
    }

    private static boolean unresolvableType(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getName().contains("NoSuchTypeException")) {
                return true;
            }
        }
        return false;
    }
}
