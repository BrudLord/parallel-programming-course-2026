package org.example.steps.step1;

import org.example.Snapshot;
import org.example.steps.step0.MetricsCollectorStep0Impl;

public class MetricsCollectorStep1EmptyLockImpl extends MetricsCollectorStep0Impl {

    @Override
    public synchronized void record(long value) {
    }

    @Override
    public synchronized Snapshot snapshot() {
        return new Snapshot(new long[256], 0, 0, 0, 0, 0, 0);
    }
}
