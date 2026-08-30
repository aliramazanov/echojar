# What echojar is

echojar is a Java agent that finds N+1 queries in a program while it runs, and tells you the line
of code that caused them.

## The problem it looks for

An N+1 query is one query that fetches a list, followed by one more query for every item in that
list. You ask for ten orders and the database runs eleven queries, you ask for a thousand and it
runs a thousand and one.

Nobody writes this deliberately. It comes out of an ORM doing exactly what it was told to do:

```java
for (Order order : orders.findAll()) {
    total += order.getItems().size();   // one query per order, hidden in a getter
}
```

Reading that code, there is nothing to see. `getItems()` looks like it reads a field, and the
query it actually fires does not appear anywhere in the source.

## Why it is hard to catch

The first thing to say is that it does not fail. Nothing throws, and the answer that comes back is
correct, so there is no error to notice.

It also passes the tests, because test data is small. Eleven queries over ten rows is fast, and
the same code over a hundred thousand rows is not, but by the time anyone finds that out it is in
production.

Code review does not catch it either, because the code looks normal. The problem is not in what
the developer wrote, it is in what the ORM decides to do when the program runs, and that decision
is not visible in the source at all. This is the reason echojar is an agent rather than a linter,
the evidence only exists while the program is running.

## What you get out of it

The report is three facts and nothing else. The query with its values stripped out, so that a
thousand lookups differing only by an id show up as one line. How many times it ran inside a
single unit of work. And the line of your own code that ran it.

There is an example of the output at the top of the [README](../README.md).

## What it does not do

It does not fix anything, it shows you where to look.

It also cannot tell a mistake from a decision. A bulk insert that runs a thousand inserts looks
exactly the same as a lazy collection that should have been fetched in one query, and echojar
reports both the same way, so which one is wrong is your call.

It is not an APM either. There are no dashboards, nothing is sent anywhere, and it answers one
question and then stays out of the way.

And it does not ask you to change your application, there is no dependency to add, no annotation
to write, and no rebuild.

## What it costs

A few tens of nanoseconds for every query, measured against a database round trip that takes tens
to hundreds of microseconds, which means on a real application you cannot measure the difference
at all.

For the reasoning behind the design, see [why.md](why.md), and for the mechanics,
see [how.md](how.md).
