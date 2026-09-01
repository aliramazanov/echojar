# Using echojar

## Getting it

Download `echojar.jar` from the [releases page](https://github.com/aliramazanov/echojar/releases).
Every release carries a checksum and a build provenance attestation, so you can confirm the jar
came out of this repository and not from somewhere else:

```bash
sha256sum -c echojar.jar.sha256
gh attestation verify echojar.jar --repo aliramazanov/echojar
```

The same jar is on Maven Central, which is easier if you would rather a build fetched it:

```xml
<dependency>
  <groupId>io.github.aliramazanov</groupId>
  <artifactId>echojar</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Running it

Start the application with the agent attached:

```bash
java -javaagent:echojar.jar -jar yourapp.jar
```

Or attach to one that is already running:

```bash
java -jar echojar.jar list
java -jar echojar.jar attach <pid>
```

What comes out looks like this:

```console
echojar: 1 echo in 1 call site

  select i1_0.order_id,i1_0.id,i1_0.quantity,i1_0.sku from order_item i1_0 where i1_0.order_id=?
    40 executions in one connection lease
    OrderService.summarise(OrderService.java:19)
```

A server that has been up for a week is holding a week of counts, including everything it did
while it was starting, so you can clear that and then measure one thing on its own:

```bash
java -jar echojar.jar reset <pid>            # clear the counts
curl http://localhost:8080/the-slow-page     # do the thing you want to measure
java -jar echojar.jar dump <pid> out.txt     # see what it cost
java -jar echojar.jar dump <pid> out.txt 20  # same counts, at a higher threshold
```

That last line prints the same counts again at a higher threshold. echojar keeps the busiest unit
of work for every statement, so changing that number costs nothing and needs no second run.

## Options

Comma separated, either after `-javaagent:echojar.jar=` or as `-Dechojar.*` system properties.

| option | default | what it does |
|---|---|---|
| `threshold` | `5` | how many times one statement must run in one unit of work to be reported |
| `units` | `true` | count per HTTP request when a servlet or filter is found, not per connection |
| `noise` | `true` | ignore connection checks and sequence reads |
| `depth` | `200` | how many stack frames to search for the calling line |
| `app` | unset | your own package names. When set, only these count as your code |
| `framework` | see report | extra package names to treat as framework code |
| `ignore` | see report | extra package names to never touch |
| `templates` | `5000` | how many distinct statements to remember |
| `out` | stderr | write the report to a file |
| `log` | `warn` | how much echojar says about itself: `off`, `warn`, `info`, `debug` |
| `fail` | unset | fail the run if a statement runs this many times in one unit of work |
| `format` | `text` | `json` prints the report as one line of JSON instead of prose |
| `diagnostics` | `false` | always print the health block, not only after a problem |
| `verbose` | `false` | log every class echojar looks at |

## Failing a build

Give `fail` a number and echojar ends the run with a failing status when any statement crosses it,
so an N+1 that creeps in stops the pipeline rather than waiting to be noticed in production:

```bash
mvn verify -DargLine="-javaagent:echojar.jar=fail=25"
```

The report still prints before the run ends, so the failure says which query and which line.

This is off unless you ask for it, because ending the JVM this way skips any shutdown work the
application had left to do. That is a fair trade in a build and a bad one in production.

## Reading it from a script

`format=json` prints the whole report as a single line of JSON, which is easier to feed into a
dashboard or a pull request comment than the prose version:

```bash
java -javaagent:echojar.jar=format=json -jar yourapp.jar
```

It carries the same numbers as the text report, with each echo holding the SQL, how many times it
ran in the busiest unit of work, the totals, and the call site broken into class, method, file and
line. The health block comes along as well, so a script can tell the difference between a clean run
and one where the agent quietly gave up.

## What it needs

Java 25 or newer, both to build echojar and to run the application you attach it to. The agent is
compiled to class file version 69, so an older JVM refuses it with `UnsupportedClassVersionError`.
You can build for Java 22 or later by lowering `maven.compiler.release`.

Attaching to a running JVM needs dynamic agent loading. JDK 21 and later print a warning about it,
and a future release will turn it off by default, so start the application with
`-XX:+EnableDynamicAgentLoading` if you want the warning gone.

## Where it has been tried

PostgreSQL, MySQL, MariaDB, SQL Server, CockroachDB, H2, HSQLDB, Derby and SQLite, behind HikariCP,
DBCP2, c3p0, Agroal and the Tomcat pool, through Hibernate, EclipseLink, MyBatis, jOOQ, JDBI,
Spring JDBC and plain JDBC.
