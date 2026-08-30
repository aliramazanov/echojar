/**
 * echojar finds N+1 queries in a running Java application.
 *
 * <p>This package holds the two entry points the JVM calls, {@code premain} and {@code agentmain},
 * and the command line tool. The agent's one job before anything else is to copy the counting tier
 * into the bootstrap classloader, so that code woven into a driver can reach it from any
 * classloader.
 *
 * <p>The docs folder in the repository explains the tool in full. what.md covers the problem it
 * looks for, why.md covers the reasons behind the design, and how.md covers the mechanics.
 *
 * @see com.aliramazanov.echojar.agent the code that changes classes and writes the report
 * @see com.aliramazanov.echojar.bootstrap the counting code that woven classes call
 */
package com.aliramazanov.echojar;
