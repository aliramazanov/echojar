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

Integration tests run inside a JVM with the agent installed, which is the only way to test the
class rewriting at all. Most of them share that one JVM and one installed agent, so the agent's
state is global, and those must call `AgentState.reset()` in `@BeforeEach`. Resetting only the
ledger is not enough, and a test that forgets leaks state into whichever test runs next.

Two kinds are exempt. The attach and command line tests start a JVM of their own, so they have no
state to share. `ConformanceFuzzIT` clears the state itself at the top of each of its three
thousand runs, which is stricter than doing it once per test method.

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

In prose, plain words.

## Things that will break the agent

These are not style preferences. They have each caused a real bug here.

**Do not let the agent throw.** Every method that the added code calls must handle its own errors
and return the answer that counts nothing. An exception escaping the agent lands inside a JDBC call
the application wrote and takes the application down. `TotalityTest` checks this.

**Do not name `java.sql` types in the bootstrap package.** That code is loaded by the bootstrap
classloader, which cannot see `java.sql`. It takes `Object` parameters for that reason. Naming a
JDBC type there fails at runtime on the first query, not at compile time.

**Do not count by nesting depth.** A pool's proxy reaches the same execution several times on the
same object. A trigger runs a different statement inside the first. Only statement identity tells
those apart.

**Do not cache anything keyed by raw SQL without a length limit.** A cache bounded by entry count
is not bounded in bytes. An application building large SQL will fill the heap.

## Releasing

There are two branches. Work goes on dev, and main is only ever the last released state, so
merging dev into main is what publishes a release.

Versions are worked out from commit messages, so the message decides the number. A commit starting
`feat:` bumps the minor, `fix:` and `perf:` bump the patch, and anything else changes nothing.
Until 1.0 a breaking change bumps the minor rather than the major, so a `feat!:` on 0.3.2 gives
0.4.0. A message that does not start with one of those words is left out of the version, so a batch
of commits with no feat and no fix produces no new version at all.

On dev, release-please keeps a pull request open that carries the next version number and the
changelog entries for everything since the last release. It sits there and collects further commits
as you push them. Merging it writes the new version into the pom and the changelog, and still
releases nothing.

Releasing is merging dev into main. The workflow reads the version out of the pom, stops quietly if
that version is already tagged, and otherwise waits for you to approve it. Nobody else can approve
it, and the approval can only come from main.

Once approved it runs the full build and four gates that only matter for a release. The jar has to
carry the version being released, and it has to start and answer `list`. The build has to come out
reproducible, so that anyone can rebuild the tag and compare hashes. The result is signed with a
build provenance attestation. Any of those failing stops the release rather than publishing
something that cannot be checked. Only then is the tag created and the release published with the
jar and its checksum.

Publishing to Maven Central is wired into the release, but it only runs when the credentials are
there. Without a `MAVEN_USERNAME` secret the step reports that it is skipping and the release goes
out on GitHub alone, so nothing fails while the account is being set up.

Four secrets switch it on, which are `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `GPG_PRIVATE_KEY` and
`GPG_PASSPHRASE`. The username and password come from a Central Portal account, and the key is an
ascii armoured private GPG key whose public half has been sent to a keyserver, because Central
checks the signature against one.

The namespace is `io.github.aliramazanov`, which Central hands out for free to a GitHub account
after asking you to create a public repository with a name it chooses. A namespace under `com.`
would instead need you to own the matching domain and prove it with a DNS record.

Four things go to Central, which are the agent jar, its sources, its javadoc and the pom. The
bootstrap jar is deliberately left out, because it already sits inside the agent jar and its
classes have to be loaded by the bootstrap loader rather than found on a classpath.

A deployment does not go live on its own. It is uploaded, Central validates it, and then it waits
for you in the portal until you publish it. Setting `central.autoPublish` to true removes that
step once you trust the pipeline.

The release profile also refuses to build in three cases, which are a version that is still a
snapshot, a dependency that is still a snapshot, and a JDK older than 25. Those are the mistakes
that cannot be taken back once they reach Central, since a published version can never be
replaced.

## Reporting a bug

Include the agent's health block, which is described in [how.md](docs/how.md). It says what the
agent caught and where, and it is usually the fastest route to the cause.

If the agent reported the wrong count, say what the database counted. `pg_stat_statements` or the
equivalent is the reference this project is tested against.
