package com.aliramazanov.echojar.agent;

import java.util.Locale;

enum Command {
    INSTALL,
    DUMP,
    RESET;

    static Command of(String value) {
        if (value == null || value.isBlank()) {
            return INSTALL;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return INSTALL;
        }
    }
}
