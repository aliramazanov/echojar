package com.aliramazanov.echojar.bootstrap.findings;

import org.jetbrains.annotations.NotNull;

public record SqlTemplate(String text, int id, boolean noise) {

    @Override
    public @NotNull String toString() {
        return text;
    }
}
