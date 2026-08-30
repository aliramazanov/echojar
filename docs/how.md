# How echojar works

## How a Java agent works at all

The JVM loads a class the first time something uses it, and a Java agent is a jar that the JVM
shows every class to before it loads, with permission to rewrite the bytecode on the way through.
That is how a tool can measure a program without the program's source code being touched.

What echojar rewrites is the JDBC classes, so that running a query also counts it.

There are two ways an agent can start, and they are not the same thing.

`premain` runs before the application starts. No JDBC class has loaded yet, so echojar changes
each one as it loads, and it can also add new fields to those classes, which is the cheap way to
keep a counter on a connection.

`agentmain` runs when you attach to a program that is already running. The driver classes loaded
long ago, so the JVM has to re-transform them, and while the JVM will let you replace the code
inside a method, it will not let you add fields to a class it has already loaded. On attach,
echojar keeps the same information in a separate map instead.

## How the counting code stays reachable

A classloader turns a class name into bytes, and one JVM has several of them:

| loader      | holds                                        |
|-------------|----------------------------------------------|
| bootstrap   | the core, such as `java.lang.String`         |
| platform    | the rest of the JDK, including `java.sql`    |
| application | your code and your jars                      |
| one per app | in a server like Tomcat, one per deployment  |

Each loader asks its parent before it answers, so a loader can see whatever its parents loaded,
it can never see what a child loaded, and two deployments in the same server cannot see each
other at all.

echojar's counting code has to be called from inside driver classes, which belong to a loader
echojar did not choose, so the counters have to be reachable from every loader there is. The
bootstrap loader is the only one that is true of, and that is why echojar ships a second small jar
holding the counters and copies it into the bootstrap loader at startup.

## How a query becomes a finding

1. Your application prepares a statement, and echojar stores the SQL with the values taken out, so
   a thousand lookups that differ only by an id all become one statement.
2. Your application runs it, and echojar adds one to that statement's counter.
3. The counter belongs to a unit of work, which is one HTTP request when a servlet or a filter can
   be found, and one connection otherwise.
4. The unit ends, and echojar saves what it counted, keeping for each statement the highest count
   it has seen inside any single unit.
5. A statement crosses the threshold, and echojar walks the stack once to find the line that ran
   it, then remembers that line so it never has to walk for that statement again.

## How the same query is only counted once

A pool wraps a statement, sometimes with several layers, so one call from your code can arrive at
echojar three or four times.

What echojar keeps is a small stack of the statement objects currently running on the thread, and
when a call finishes it only counts if that same object is not already sitting further down the
stack. The same object arriving again is one query moving through layers, and a different object
is a different query, which is what a trigger produces.

## How to see what the agent is doing

echojar handles its own errors so that it cannot break your application, and the price of that is
that it could fail without telling you, so it counts every error it handles.

The report ends with a health block whenever anything was caught, saying where it happened and
what it was, and setting `diagnostics=true` prints that block on clean runs too.

The same numbers can be read while the program is still running, either over JMX at
`com.aliramazanov.echojar:type=Diagnostics`, or as JFR events, where `echojar.Echo` fires when a
statement crosses the threshold and `echojar.Health` fires every ten seconds.

## How the source is laid out

| package                                       | holds                                            |
|-----------------------------------------------|--------------------------------------------------|
| `com.aliramazanov.echojar`                    | the entry points and the command line tool       |
| `com.aliramazanov.echojar.agent`              | rewriting classes, finding call sites, reporting |
| `com.aliramazanov.echojar.bootstrap`          | the counting code that woven classes call        |
| `com.aliramazanov.echojar.bootstrap.findings` | what was counted, and the results                |
| `com.aliramazanov.echojar.bootstrap.watch`    | what the agent is doing to itself                |

The data only moves one way. Woven code records, the counting tier adds it up, and the agent tier
decides what it means and prints it.
