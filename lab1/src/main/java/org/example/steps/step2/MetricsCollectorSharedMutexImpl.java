package org.example.steps.step2;

import org.example.MetricsCollector;
import org.example.Snapshot;

import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollectorSharedMutexImpl implements MetricsCollector {
    private static final int BUCKET_SIZE = 256;
    private final long[] buckets;
    private final Object[] mutexes;
    private final AtomicLong count;
    private final AtomicLong sum;
    private final AtomicLong min;
    private final AtomicLong max;

    public MetricsCollectorSharedMutexImpl() {
        buckets = new long[BUCKET_SIZE];
        mutexes = new Object[16];
        for (int i = 0; i < mutexes.length; i++) {
            mutexes[i] = new Object();
        }
        count = new AtomicLong();
        sum = new AtomicLong();
        min = new AtomicLong(Long.MAX_VALUE);
        max = new AtomicLong();
    }

    @Override
    public void record(long value) {
        int bucket_idx = Math.toIntExact(Math.min(value / 4, 255));
        synchronized (mutexes[bucket_idx % mutexes.length]) {
            buckets[bucket_idx]++;
        }
        count.getAndIncrement();
        sum.addAndGet(value);
        min.updateAndGet(min -> Math.min(min, value));
        max.updateAndGet(max -> Math.max(max, value));

    }

    private long findPercentile(long[] newBuckets, long count, double percentile) {
        double threshold = count * percentile;
        long summarized = 0;
        for (int i = 0; i < BUCKET_SIZE; i++) {
            summarized += newBuckets[i];
            if (summarized >= threshold) {
                return i * 4;
            }
        }
        return (BUCKET_SIZE - 1L) * 4;
    }

    @Override
    public Snapshot snapshot() {
        long[] newBuckets = new long[BUCKET_SIZE];
        for (int i = 0; i < 16; i++) {
            synchronized (mutexes[i]) {
                for (int j = 0; j < 16; j++) {
                    newBuckets[16 * j + i] = buckets[16 * j + i];
                }
            }
        }
        long newCount = count.get();
        long p50 = findPercentile(newBuckets, newCount, 0.5);
        long p99 = findPercentile(newBuckets, newCount, 0.99);
        return new Snapshot(
                newBuckets,
                newCount,
                sum.get(),
                min.get(),
                max.get(),
                p50,
                p99
        );
    }
}
