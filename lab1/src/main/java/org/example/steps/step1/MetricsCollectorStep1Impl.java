package org.example.steps.step1;

import org.example.Snapshot;
import org.example.steps.step0.MetricsCollectorStep0Impl;

public class MetricsCollectorStep1Impl extends MetricsCollectorStep0Impl {

    @Override
    public synchronized void record(long value) {
        super.record(value);
    }

    @Override
    public synchronized Snapshot snapshot() {
        return super.snapshot();
    }
}
