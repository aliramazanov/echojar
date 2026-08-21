package com.aliramazanov.echojar.bootstrap.watch;

public interface DiagnosticsMXBean {

    long getExecutions();

    long getLeasesClosed();

    long getLeasesOpen();

    long getStatementsTemplated();

    long getTypesTransformed();

    long getStackWalks();

    long getSuppressedFailures();

    boolean isHealthy();

    String[] getSuppressedDetail();
}
