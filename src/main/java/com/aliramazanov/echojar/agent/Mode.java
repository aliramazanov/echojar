package com.aliramazanov.echojar.agent;

public enum Mode {

    STARTUP,

    ATTACH;

    boolean frozen() {
        return this == ATTACH;
    }
}
