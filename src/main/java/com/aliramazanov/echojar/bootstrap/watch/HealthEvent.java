package com.aliramazanov.echojar.bootstrap.watch;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;

@Name("echojar.Health")
@Label("echojar health")
@Category({"echojar"})
@Description("Counters describing the agent's own behaviour, including suppressed failures")
@Period("10 s")
@StackTrace(false)
public final class HealthEvent extends Event {

    @Label("Executions observed")
    public long executions;

    @Label("Leases closed")
    public long leasesClosed;

    @Label("Leases still open")
    public long leasesOpen;

    @Label("Statements templated")
    public long statementsTemplated;

    @Label("Types transformed")
    public long typesTransformed;

    @Label("Stack walks")
    public long stackWalks;

    @Label("Types with an incomplete hierarchy")
    public long typesUnresolvable;

    @Label("Failures suppressed")
    public long suppressed;

    static void emit() {
        HealthEvent event = new HealthEvent();

        if (!event.isEnabled()) {
            return;
        }

        Diagnostics.Snapshot health = Diagnostics.snapshot();
        event.executions = health.executions();
        event.leasesClosed = health.leasesClosed();
        event.leasesOpen = health.leasesOpen();
        event.statementsTemplated = health.statementsTemplated();
        event.typesTransformed = health.typesTransformed();
        event.stackWalks = health.stackWalks();
        event.typesUnresolvable = health.typesUnresolvable();
        event.suppressed = health.suppressedTotal();

        event.commit();
    }
}
