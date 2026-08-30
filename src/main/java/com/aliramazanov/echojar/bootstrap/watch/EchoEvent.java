package com.aliramazanov.echojar.bootstrap.watch;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("echojar.Echo")
@Label("N+1 echo")
@Category({"echojar"})
@Description("A SQL template executed repeatedly inside a single connection lease")
@StackTrace(false)
public final class EchoEvent extends Event {

    @Label("SQL")
    public String sql;

    @Label("Executions in lease")
    public int executions;

    @Label("Call site")
    public String callSite;

    public static void record(String sql, int executions, String callSite) {
        EchoEvent event = new EchoEvent();

        if (!event.isEnabled()) {
            return;
        }

        event.sql = sql;
        event.executions = executions;
        event.callSite = callSite;
        event.commit();
    }
}
