package com.aliramazanov.echojar.bootstrap.findings;

public record SqlTemplate(String text, int id, boolean noise) {

    @Override
    public String toString() {
        return text;
    }
}
