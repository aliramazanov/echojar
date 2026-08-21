package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Lease;

public interface LeaseCarrier {

    Lease echojarLease();

    void echojarLease(Lease lease);
}
