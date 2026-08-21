package com.aliramazanov.echojar.agent;

import com.aliramazanov.echojar.bootstrap.Units;
import net.bytebuddy.asm.Advice;

final class RequestAdvice {

    private RequestAdvice() {
    }

    static final class Boundary {

        private Boundary() {
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        static void enter() {
            Units.enter();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        static void exit() {
            Units.exit();
        }
    }
}
