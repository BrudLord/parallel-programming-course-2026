package org.example.steps.step3;

import org.example.MetricsCollector;
import org.example.Snapshot;

import java.util.ArrayList;
import java.util.List;

public class MetricsCollectorThreadLocalImpl implements MetricsCollector {
    private static final int BUCKET_SIZE = 256;
    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();
    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState(BUCKET_SIZE);
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState state = myState.get();
        int bucketIdx = Math.toIntExact(Math.min(value / 4, BUCKET_SIZE - 1));
        state.buckets.setRelease(bucketIdx, state.buckets.getPlain(bucketIdx) + 1);
        state.count.setRelease(state.count.getPlain() + 1);
        state.sum.setRelease(state.sum.getPlain() + value);
        if (value < state.min.getPlain()) {
            state.min.setRelease(value);
        }
        if (value > state.max.getPlain()) {
            state.max.setRelease(value);
        }
    }

    private long findPercentile(long[] buckets, long count, double percentile) {
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
        List<ThreadState> states;
        synchronized (listLock) {
            states = new ArrayList<>(allStates);
        }

        long[] newBuckets = new long[BUCKET_SIZE];
        long newCount = 0;
        long newSum = 0;
        long newMin = Long.MAX_VALUE;
        long newMax = 0;

        for (ThreadState state : states) {
            for (int i = 0; i < BUCKET_SIZE; i++) {
                newBuckets[i] += state.buckets.get(i);
            }
            newCount += state.count.get();
            newSum += state.sum.get();
            newMin = Math.min(newMin, state.min.get());
            newMax = Math.max(newMax, state.max.get());
        }

        long p50 = findPercentile(newBuckets, newCount, 0.5);
        long p99 = findPercentile(newBuckets, newCount, 0.99);
        return new Snapshot(
                newBuckets,
                newCount,
                newSum,
                newMin,
                newMax,
                p50,
                p99
        );
    }
}
