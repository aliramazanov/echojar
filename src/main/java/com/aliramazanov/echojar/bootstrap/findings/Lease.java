package com.aliramazanov.echojar.bootstrap.findings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lease {

    private final Map<SqlTemplate, Echoes> byTemplate = new HashMap<>();

    public synchronized Echoes record(SqlTemplate template, int count) {
        Echoes echoes = byTemplate.computeIfAbsent(template, Echoes::new);
        echoes.record(count);
        return echoes;
    }

    public synchronized boolean empty() {
        return byTemplate.isEmpty();
    }

    public synchronized List<Echoes> echoes() {
        return new ArrayList<>(byTemplate.values());
    }
}
