package com.aliramazanov.echojar.bootstrap;

import com.aliramazanov.echojar.bootstrap.findings.Lease;
import com.aliramazanov.echojar.bootstrap.findings.OpenLeases;
import com.aliramazanov.echojar.bootstrap.watch.Diagnostics;

public interface LeaseCarrier {

    Lease echojarLease();

    void echojarLease(Lease lease);

    default Lease echojarOpenLease() {
        Lease started = echojarLease();

        if (started != null) {
            return started;
        }

        synchronized (this) {
            Lease existing = echojarLease();

            if (existing != null) {
                return existing;
            }

            Lease opened = new Lease();

            echojarLease(opened);
            OpenLeases.opened(opened);
            Diagnostics.leaseOpened();

            return opened;
        }
    }

    default Lease echojarTakeLease() {
        synchronized (this) {
            Lease held = echojarLease();

            if (held != null) {
                echojarLease(null);
            }

            return held;
        }
    }
}
