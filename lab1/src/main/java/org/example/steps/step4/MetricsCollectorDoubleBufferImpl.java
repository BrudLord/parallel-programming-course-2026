package org.example.steps.step4;

import org.example.MetricsCollector;
import org.example.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MetricsCollectorDoubleBufferImpl implements MetricsCollector {
    private static final int BUCKET_SIZE = 256;
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private final Object snapLock = new Object();
    private volatile int active;
    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers buffers = new ThreadBuffers(BUCKET_SIZE);
        synchronized (snapLock) {
            allBuffers.add(buffers);
        }
        return buffers;
    });

    private final long[] buckets = new long[BUCKET_SIZE];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public void record(long value) {
        ThreadBuffers buffers = myBuffers.get();
        int bucketIdx = Math.toIntExact(Math.min(value / 4, BUCKET_SIZE - 1));
        int bufferIdx;

        while (true) {
            bufferIdx = active;
            buffers.inside.set(bufferIdx);
            if (active == bufferIdx) {
                break;
            }
            buffers.inside.setRelease(ThreadBuffers.NOWHERE);
        }

        buffers.buckets[bufferIdx][bucketIdx]++;
        buffers.count[bufferIdx]++;
        buffers.sum[bufferIdx] += value;
        buffers.min[bufferIdx] = Math.min(buffers.min[bufferIdx], value);
        buffers.max[bufferIdx] = Math.max(buffers.max[bufferIdx], value);
        buffers.inside.setRelease(ThreadBuffers.NOWHERE);
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
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers buffers : allBuffers) {
                while (buffers.inside.get() == old) {
                    Thread.onSpinWait();
                }

                for (int i = 0; i < BUCKET_SIZE; i++) {
                    buckets[i] += buffers.buckets[old][i];
                }
                count += buffers.count[old];
                sum += buffers.sum[old];
                min = Math.min(min, buffers.min[old]);
                max = Math.max(max, buffers.max[old]);

                Arrays.fill(buffers.buckets[old], 0);
                buffers.count[old] = 0;
                buffers.sum[old] = 0;
                buffers.min[old] = Long.MAX_VALUE;
                buffers.max[old] = 0;
            }

            long[] newBuckets = buckets.clone();
            long p50 = findPercentile(newBuckets, count, 0.5);
            long p99 = findPercentile(newBuckets, count, 0.99);
            return new Snapshot(
                    newBuckets,
                    count,
                    sum,
                    min,
                    max,
                    p50,
                    p99
            );
        }
    }
}
