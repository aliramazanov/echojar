# echojar

[![maven central](https://img.shields.io/maven-central/v/io.github.aliramazanov/echojar?label=maven%20central)](https://central.sonatype.com/artifact/io.github.aliramazanov/echojar)
[![javadoc](https://javadoc.io/badge2/io.github.aliramazanov/echojar/javadoc.svg)](https://javadoc.io/doc/io.github.aliramazanov/echojar)
[![ci](https://img.shields.io/github/actions/workflow/status/aliramazanov/echojar/ci.yml?branch=dev&label=ci)](https://github.com/aliramazanov/echojar/actions/workflows/ci.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

echojar is a Java agent that finds N+1 queries in a program while it runs, and tells you the line
of code that caused them. You do not change the application, there is no dependency to add and no
annotation to write.

It attaches to a process that is already running, so it works on the program in production that is
slow for reasons nobody can name.

## Documentation

[Using it](docs/use.md) covers getting the jar, running it, and every option.

[What it is](docs/what.md) is the problem it looks for and what it will not do.

[Why it is built this way](docs/why.md) is the reasoning behind each decision.

[How it works](docs/how.md) is the machinery.

## The rest

[Contributing](CONTRIBUTING.md) covers building it, the tests, and how a release is made.

[Security](SECURITY.md) covers what echojar can reach, how to check a download, and how to report a
vulnerability.

MIT licensed. See [LICENSE](LICENSE).
