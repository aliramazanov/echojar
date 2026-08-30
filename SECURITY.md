# Security

## Reporting a vulnerability

Report privately through GitHub's advisory form:

https://github.com/aliramazanov/echojar/security/advisories/new

Do not open a public issue for a vulnerability. Expect a first reply within a week.

## What echojar can reach

echojar is a Java agent, so it runs inside your application with the same permissions as your
application. Two things follow from that, and you should know both before running it.

**It reads your SQL.** The agent stores every statement it sees, with the values removed. A query
like `SELECT * FROM users WHERE email = 'someone@example.com'` is stored as
`SELECT * FROM users WHERE email = ?`. Parameter values are never stored, and never printed.

Table and column names are kept, because they are what makes a report useful. If your schema names
are themselves sensitive, the report is sensitive too.

**It reads your stack traces.** To name the line that ran a query, the agent walks the stack. Class
names, method names and line numbers from your application end up in the report.

## Where that data goes

Nowhere, unless you send it there.

echojar has no network code. It does not phone home, check for updates or upload anything. The
report is written to stderr, or to the file you name with `out=`.

The JMX bean and the JFR events are local to the process. They are readable by anything that can
already attach to your JVM, which is the same access needed to attach the agent.

## Attaching to a running process

`echojar attach` uses the JVM attach mechanism, which requires the same user as the target process
or root. This is the JVM's own boundary, not something echojar enforces.

Loading an agent into a process gives that agent full control of the process. Only attach jars you
trust.

## What is inside the jar

echojar ships one third party library, which is ByteBuddy, and it is relocated under
`com.aliramazanov.echojar.shaded` so that it cannot collide with a different version of ByteBuddy
already loaded in your application. Everything else in the jar is echojar's own code. There are no
native binaries, no shell scripts and no resources that get executed.

The agent has no network code at all, it starts no processes, and it loads no classes from outside
its own jar, so there is nothing in it that reaches out of the JVM it was attached to.

Continuous integration checks these properties on every change rather than trusting that they stay
true. A build fails if a native binary appears in the jar, if any class ships outside echojar's own
package, or if the set of libraries that reach your JVM is anything other than ByteBuddy on its own.

## Checking that a release is really from this source

Every release is built by a GitHub Actions workflow in this repository and is signed with a build
provenance attestation, so you can confirm that the jar you downloaded came out of that workflow
and out of this source, rather than off somebody's laptop:

```bash
gh attestation verify echojar.jar --repo aliramazanov/echojar
```

Each release also carries a checksum file:

```bash
sha256sum -c echojar.jar.sha256
```

If you would rather trust nothing at all, the build is reproducible, which means building the same
tag yourself gives you a jar that is identical byte for byte to the released one, so you can compare
the two hashes and see for yourself that no extra code was added on the way:

```bash
git checkout v0.1.0
mvn --strict-checksums -DskipTests package
sha256sum target/echojar.jar
```

Use a JDK 25 build to compare, because the compiler that produced the release is the Temurin 25
build that the workflow uses, and a different compiler can legitimately emit different bytecode.
Continuous integration builds the project twice on every change and fails if the two jars differ,
so this property cannot quietly stop being true.

## How code gets into a release

Changes land through pull requests, the repository has a `CODEOWNERS` file so that every change
needs the owner's review, and releases are cut from tags rather than from a branch anyone can push
to.

The workflows pin every GitHub Action they use to a full commit hash instead of a version tag,
because a tag is only a pointer and whoever controls an action can move it to different code without
anyone noticing. The release workflow publishes with GitHub's own command line tool rather than a
third party action, so no outside code ever runs with permission to write to this repository. The
checkout step is told not to leave a credential behind for later steps to find, and Maven runs with
strict checksums so a tampered artifact from a mirror fails the build instead of quietly going in.

Dependency updates arrive as pull requests and go through the same review and the same test suite as
anything else.

## Supported versions

Fixes go to the latest release. This project has not reached 1.0, so there are no maintained
older branches yet.
