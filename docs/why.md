# Why echojar is built this way

Every choice below came out of a problem, so the problem comes first and the choice follows from
it.

## Why an agent and not a library

A library has to be added to your application, and that rules out the case you care about most,
which is the program that is already running in production and is slow for reasons nobody can
name.

An agent attaches to a process that is already up, measures it, and leaves. There is nothing to
add to the build, and nothing to redeploy.

## Why the JDBC layer and not the ORM

Tools that hook into Hibernate break when Hibernate changes. The same tools broke again on JDK 16
when proxy class naming changed, and again on the move to Jakarta, and their issue trackers are
full of people saying detection stopped working after an upgrade.

`java.sql` has barely moved in twenty years, and every ORM, every pool and every framework ends up
calling it, so watching that layer means it does not matter which ORM you use or which version of
it you are on.

The cost of that choice is that echojar sees SQL and not entities. It can tell you a query ran
forty times and which line ran it, but it cannot tell you which mapping caused it.

## Why counting is the hard part

The obvious approach is to count the calls to `executeQuery`, and it is wrong, and this is where
every tool in this space has had bugs.

A pool wraps a statement, and sometimes several layers wrap it, so one call from your code can
arrive at the agent three or four times while the database only ran one query. Count all of them
and every number is too big.

The usual fix is to count only the outermost call, which you track by how deep the calls are
nested, and that is wrong too. A trigger runs a second statement inside the first one, on the same
thread, and depth cannot tell a trigger apart from a wrapper, so the trigger's query gets dropped.

echojar looks at the statement object instead. The same object arriving again is one query passing
through layers, a different object is a different query, and both cases then come out right.

## Why the counting is per connection and not per thread

To say a query ran forty times you need a boundary to count inside, and the boundary you pick
decides what the tool is able to prove.

Most tools count per thread, and that is not safe, because application servers reuse threads. If
the state is not cleared perfectly between requests, one request gets blamed for the previous
request's queries, and real tools have shipped exactly that bug.

echojar counts per connection, so the counter lives on the connection object itself. It cannot
leak from one request into the next, and it survives the work being handed to another thread part
way through.

There is one case where that fails. If the code opens a new connection for every query, then a
hundred queries are a hundred connections holding one query each, and nothing looks repeated at
all. So when echojar can find a servlet or a filter, it counts per HTTP request instead, and that
loop becomes obvious.

Work handed off to another thread goes back to counting per connection, because following a
request onto a pooled worker thread would bring back the leak described above.

## Why the project ships as two jars

echojar puts its code inside driver classes, and those classes belong to a classloader that
echojar does not choose, so the counters have to be reachable from every classloader in the JVM.
Only one of them is, and how that works is in [how.md](how.md).

The part worth knowing here is what the decision costs. The counting tier can only use what that
loader can see, and `java.sql` is not in that set, so the counting code takes `Object` parameters
and anything that needs a JDBC type has to happen in the woven code instead. That is why the
counting classes look oddly untyped when you read them.

## Why the agent catches its own errors

If the agent throws, it breaks the application it was asked to observe, and the error lands inside
a JDBC call the application author wrote and expects to work. For a diagnostic tool that is not an
acceptable way to fail.

So every method the woven code calls handles its own errors, and on failure it returns the answer
that counts nothing rather than an answer that counts wrongly.

The risk that comes with that is failing quietly, so every error it catches is counted and shown
in the health block, because a tool that has silently stopped working looks exactly like an
application with no problems.

## Why the tests build their cases at random

Every counting bug in tools like this one comes down to the same thing, which is that the number
the agent reports is not the number the driver ran.

Writing one test for each known bug only ever catches the bugs that are already known, so the main
test builds the cases at random instead. Pooled, wrapped, subclassed, broken and trigger firing
connections, prepared and plain statements, every execute method, batches and cleared batches. It
runs three thousand times, and each run checks the agent's count against the driver's, statement
by statement.

Then the test itself gets checked, by breaking the agent on purpose and confirming that the test
notices.

## Why stack walks are rationed

Finding the line that ran a query means walking the stack, and that is expensive enough that doing
it on every query would cost real time.

So echojar walks once, the first time a statement crosses the threshold, then remembers the answer
and reuses it. If the same SQL runs from several places the report names one of them and says how
many were unclear, which is cheaper than walking every time in order to be certain.
