package com.aliramazanov.echojar.bootstrap.findings;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lease {

    private final Map<SqlTemplate, Echoes> byTemplate = new HashMap<>();

    public synchronized @NotNull Echoes record(SqlTemplate template, int count) {
        Echoes echoes = byTemplate.computeIfAbsent(template, Echoes::new);
        echoes.record(count);
        return echoes;
    }

    public synchronized boolean empty() {
        return byTemplate.isEmpty();
    }

    @Contract(" -> new")
    public synchronized @NotNull List<Echoes> echoes() {
        return new ArrayList<>(byTemplate.values());
    }
}
