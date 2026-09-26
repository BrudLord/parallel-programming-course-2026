package org.example.steps.step0;

import org.example.MetricsCollector;
import org.example.Snapshot;

public class MetricsCollectorStep0Impl implements MetricsCollector {
    private static final int BUCKET_SIZE = 256;
    private final long[] buckets = new long[BUCKET_SIZE];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    @Override
    public void record(long value) {
        int bucketIdx = Math.toIntExact(Math.min(value / 4, BUCKET_SIZE - 1));
        buckets[bucketIdx]++;
        count++;
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
    }

    private long findPercentile(
            long[] buckets,
            long count,
            double percentile
    ) {
        double threshold = count * percentile;
        long summarized = 0;
        for (int i = 0; i < BUCKET_SIZE; i++) {
            summarized += buckets[i];
            if (summarized >= threshold) {
                return i * 4L;
            }
        }
        return (BUCKET_SIZE - 1L) * 4;
    }

    @Override
    public Snapshot snapshot() {
        long[] newBuckets = buckets.clone();
        long newCount = count;
        long p50 = findPercentile(newBuckets, newCount, 0.5);
        long p99 = findPercentile(newBuckets, newCount, 0.99);

        return new Snapshot(
                newBuckets,
                newCount,
                sum,
                min,
                max,
                p50,
                p99
        );
    }
}
