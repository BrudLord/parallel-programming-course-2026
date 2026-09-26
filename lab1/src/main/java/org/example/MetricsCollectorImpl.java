package org.example;

import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollectorImpl implements MetricsCollector {
    private static final int BUCKET_SIZE = 256;
    private final AtomicLong[] buckets;
    private final AtomicLong count;
    private final AtomicLong sum;
    private final AtomicLong min;
    private final AtomicLong max;

    public MetricsCollectorImpl() {
        buckets = new AtomicLong[BUCKET_SIZE];
        count = new AtomicLong();
        sum = new AtomicLong();
        min = new AtomicLong(Integer.MAX_VALUE);
        max = new AtomicLong();

    }

    @Override
    public void record(long value) {
        int bucket_idx = Math.toIntExact(Math.min(value / 4, 255));
        buckets[bucket_idx].getAndIncrement();
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
        for (int i = 0; i < BUCKET_SIZE; i++) {
            newBuckets[i] = buckets[i].get();
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
