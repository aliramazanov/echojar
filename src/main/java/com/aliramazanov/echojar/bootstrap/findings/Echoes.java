package com.aliramazanov.echojar.bootstrap.findings;

public final class Echoes {

    private final SqlTemplate template;
    private int executions;
    private volatile CallSite site;

    Echoes(SqlTemplate template) {
        this.template = template;
    }

    public SqlTemplate template() {
        return template;
    }

    public int executions() {
        return executions;
    }

    public CallSite site() {
        return site;
    }

    public void site(CallSite site) {
        this.site = site;
    }

    void record(int count) {
        executions += count;
    }
}
