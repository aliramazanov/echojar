package com.aliramazanov.echojar.agent;

import com.aliramazanov.echojar.bootstrap.Units;
import net.bytebuddy.asm.Advice;

final class RequestAdvice {

    private RequestAdvice() {
    }

    static final class Boundary {

        private Boundary() {
        }

        @Advice.OnMethodEnter
        public static void enter() {
            Units.enter();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit() {
            Units.exit();
        }
    }
}
