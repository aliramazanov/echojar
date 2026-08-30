# Contributing

## Building

```bash
mvn verify
```

You need JDK 25 or newer. You do not need Docker or a database. The tests use a fake in-process
JDBC driver, H2 behind a DBCP2 pool, and separate JVMs for the attach tests.

This produces `target/echojar.jar`, which is both the agent and the command line tool.

## What the tests are

`mvn verify` runs two sets, and they are different on purpose.

Unit tests run without the agent. They test the parts that are ordinary code, such as SQL
normalising and the report.

Integration tests run inside a JVM with the agent installed, which is the only way to test weaving
at all. They share one JVM and one installed agent, so the agent's state is global. Every
integration test must call `AgentState.reset()` in `@BeforeEach`. Resetting only the ledger is not
enough, and a test that forgets leaks state into whichever test runs next.

`ConformanceFuzzIT` is the important one. It builds pooled, wrapped, subclassed, broken and
trigger firing connections at random, three thousand times, and checks the agent's count against
the driver's, statement by statement. If you change how anything is counted, this is the test that
will tell you.

## If you change counting

Run `ConformanceFuzzIT` and take it seriously when it fails. It prints the seed, so a failure is
reproducible.

Then break your own change on purpose and confirm a test notices. A test that passes whether or
not the code works is worse than no test. Several bugs in this project were found that way.

## Comments and documentation

Use Javadoc. Every type gets a class level Javadoc saying what it is for and anything about it
that is not obvious from reading it, and the packages carry a `package-info.java` explaining the
area as a whole.

Avoid `//` comments. Reach for one only when there is no way to say the thing in Javadoc or in the
code itself, which usually means a warning to the next person, such as why a lock sits where it
does or why an obvious cheaper approach is wrong. If you can move it into the class Javadoc
instead, move it.

The long explanations belong in the docs folder. what.md is the problem, why.md is the reasoning
behind the design, and how.md is the machinery, so a class Javadoc can stay short and lean on
those rather than repeating them.

In prose, plain words and no semicolons and no em dashes.

## Things that will break the agent

These are not style preferences. They have each caused a real bug here.

**Do not let the agent throw.** Every method that woven code calls must handle its own errors and
return the answer that counts nothing. An exception escaping the agent lands inside a JDBC call
the application wrote and takes the application down. `TotalityTest` checks this.

**Do not name `java.sql` types in the bootstrap package.** That tier is loaded by the bootstrap
classloader, which cannot see `java.sql`. It takes `Object` parameters for that reason. Naming a
JDBC type there fails at runtime on the first query, not at compile time.

**Do not count by nesting depth.** A pool's proxy reaches the same execution several times on the
same object. A trigger runs a different statement inside the first. Only statement identity tells
those apart.

**Do not cache anything keyed by raw SQL without a length limit.** A cache bounded by entry count
is not bounded in bytes. An application building large SQL will fill the heap.

## Reporting a bug

Include the agent's health block, which is described in [how.md](docs/how.md). It says what the
agent caught and where, and it is usually the fastest route to the cause.

If the agent reported the wrong count, say what the database counted. `pg_stat_statements` or the
equivalent is the reference this project is tested against.
