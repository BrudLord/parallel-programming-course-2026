package org.example.steps.step3;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

class ThreadState {
    final AtomicLongArray buckets;
    final AtomicLong count = new AtomicLong();
    final AtomicLong sum = new AtomicLong();
    final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong max = new AtomicLong();

    ThreadState(int bucketSize) {
        buckets = new AtomicLongArray(bucketSize);
    }
}
