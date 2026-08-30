package com.aliramazanov.echojar.agent;

import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;

import com.aliramazanov.echojar.bootstrap.Echo;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

import net.bytebuddy.utility.JavaModule;

final class Modules {

    private static volatile Instrumentation instrumentation;

    private Modules() {
    }

    static void using(Instrumentation available) {
        instrumentation = available;
    }

    static void ensureReads(JavaModule module) {
        Instrumentation available = instrumentation;

        if (available == null || module == null || !module.isNamed()) {
            return;
        }

        try {
            Module target = (Module) module.unwrap();
            Module recorder = Echo.class.getModule();

            if (target.canRead(recorder)) {
                return;
            }

            if (!available.isModifiableModule(target)) {
                return;
            }

            available.redefineModule(
                    target,
                    Set.of(recorder),
                    Map.of(),
                    Map.of(),
                    Set.of(),
                    Map.of()
            );
        } catch (Throwable failure) {
            Diagnostics.suppressed(Diagnostics.Site.TRANSFORM, failure);
        }
    }

    static net.bytebuddy.dynamic.DynamicType.Builder<?> reading(
            JavaModule module,
            net.bytebuddy.dynamic.DynamicType.Builder<?> builder
    ) {
        ensureReads(module);
        return builder;
    }
}
