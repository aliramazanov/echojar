# echojar

**How many queries did that request really make?** Attach to a running JVM and hear the echo.

echojar is a Java agent. It attaches to an application you did not modify, listens at the
JDBC layer, and reports where one logical operation turned into hundreds of physical
queries, with the line of code that caused it.

```console
$ java -javaagent:echojar.jar -jar shop.jar

echojar: 1 echo in 1 call site

  select i1_0.order_id,i1_0.id,i1_0.quantity,i1_0.sku from order_item i1_0 where i1_0.order_id=?
    40 executions in one connection lease
    OrderService.summarise(OrderService.java:19)
```

N+1 queries do not fail. They pass every test, look fine on a laptop with forty rows, and
only surface as latency once real data arrives. The ORM is doing exactly what it was told,
so nothing in the code looks wrong. The evidence exists only at runtime.

## Use

Attach at startup:

```bash
java -javaagent:echojar.jar -jar yourapp.jar
```

Or attach to a JVM that is already running:

```bash
java -jar echojar.jar attach <pid>
java -jar echojar.jar list
```

A service that has been up for a week has counted a week of history, including whatever it
did while starting, so the findings can be sliced rather than only read at shutdown:

```bash
java -jar echojar.jar reset <pid>            # forget history, start a clean window
curl http://localhost:8080/the-slow-page     # exercise the thing you suspect
java -jar echojar.jar dump <pid> out.txt     # what did that alone cost
java -jar echojar.jar dump <pid> out.txt 20  # same window, only statements past twenty
```

Re-rendering at a different threshold costs nothing: what is recorded, the busiest unit of
work each statement ever had, does not depend on the threshold.

## Options

Comma separated, either on the `-javaagent` argument or as `-Dechojar.*` system properties.

| option | default | meaning |
| --- | --- | --- |
| `threshold` | `5` | executions of one template in one unit of work before it counts as an echo |
| `units` | `true` | treat a servlet or filter as a request boundary, above the connection lease |
| `noise` | `true` | suppress validation pings and sequence reads |
| `depth` | `200` | maximum stack frames walked when resolving a call site |
| `framework` | see report | extra package prefixes to treat as framework, not application code |
| `ignore` | see report | extra package prefixes to never instrument |
| `templates` | `5000` | cap on the raw SQL to template cache |
| `out` | stderr | write the report to a file instead |
| `log` | `warn` | agent diagnostics level: `off`, `warn`, `info`, `debug` |
| `diagnostics` | `false` | always print the agent health block, not only when something went wrong |
| `verbose` | `false` | log every instrumentation decision |

## Watching the agent itself

An agent that swallows every exception so it cannot disturb its host is an agent that can
fail silently, so echojar counts everything it discards. The report ends with a health block
whenever anything was suppressed, naming the site and the first failure seen there;
`diagnostics=true` prints it on clean runs too. The same counters are live over JMX at
`com.aliramazanov.echojar:type=Diagnostics`, and as two JFR events, `echojar.Echo` when a
statement crosses the threshold and `echojar.Health` every ten seconds.

## What it does not do

- It does not fix anything. It tells you where to look and what caused it.
- It cannot tell a deliberate loop of queries from an accidental one. A bulk insert loop is
  reported the same way a lazy collection is.
- The unit of work is the request where echojar finds one, and the connection lease
  otherwise. A servlet or filter marks the request, outermost wins. Applications that are not
  servlet based can mark their own with `Units.enter()` and `Units.exit()`.
- Work that leaves the request thread falls back to the connection lease, where a loop that
  reconnects for every query cannot be told from normal traffic. The report says so, with the
  count, when most units of work were not inside a request.
- A statement gets a bounded number of stack walks, so if the same SQL runs from several
  places the report names one and says how many were ambiguous.
- Only one agent per JVM. A second `attach` is refused rather than weaving twice.
- The report prints the loudest 25 echoes and states how many it left out.

## Overhead

A few tens of nanoseconds per query. `shop.HotPath` in the test sources measures it against
a fake in-process driver that does no work, so the whole difference between an instrumented
and an uninstrumented run is the agent:

```bash
mvn test-compile
java -cp target/test-classes shop.HotPath
java -javaagent:target/echojar.jar=out=/dev/null -cp target/test-classes shop.HotPath
```

On a real workload the cost is below what the workload can resolve, which follows from the
microbenchmark: tens of nanoseconds against a database round trip of tens to hundreds of
microseconds. Counts have been checked against `pg_stat_statements` on a Spring service under
forty way concurrency, 589,250 executions with exact agreement.

## Requirements

**Java 25 or newer, on the target JVM as well as the build.** The agent is compiled to class
file version 69, so an older JVM refuses it with `UnsupportedClassVersionError`. The source
itself uses no language feature past Java 21, so building for an older target is a matter of
lowering `maven.compiler.release`.

Attaching to a running JVM needs dynamic agent loading, which JDK 21 and later warn about and
a future release will disable by default. Start the target with
`-XX:+EnableDynamicAgentLoading` to silence the warning.

## Build

```bash
mvn verify
```

Produces `target/echojar.jar`, which is both the agent and the CLI. No Docker and no
database are needed: the tests run against a fake in-process JDBC driver, H2 behind a DBCP2
pool, and forked JVMs for the attach path.

## License

MIT. See [LICENSE](LICENSE).
