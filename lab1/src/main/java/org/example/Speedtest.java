package org.example;

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Speedtest {
    public static double run(MetricsCollector collector, long[] values, int threadCnt, int seconds)
            throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean stop = new AtomicBoolean(false);
        long[] ops = new long[threadCnt];
        ExecutorService threads = Executors.newFixedThreadPool(threadCnt);
        Future<?>[] tasks = new Future<?>[threadCnt];

        try {
            for (int k = 0; k < threadCnt; k++) {
                int thread_idx = k;
                tasks[k] = threads.submit(() -> {
                    long local_count = 0;
                    int i = thread_idx * 1000 % values.length;
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    while (!stop.get()) {
                        collector.record(values[i]);
                        local_count++;
                        i++;
                        if (i == values.length) {
                            i = 0;
                        }
                    }
                    ops[thread_idx] = local_count;
                });
            }

            long t0 = System.nanoTime();
            start.countDown();
            Thread.sleep(seconds * 1000L);
            stop.set(true);
            long t1 = System.nanoTime();

            for (Future<?> task : tasks) {
                task.get();
            }

            return (Arrays.stream(ops).sum()) * 1_000_000_000.0 / (t1 - t0);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Benchmark worker failed", e.getCause());
        } finally {
            stop.set(true);
            threads.shutdownNow();
        }
    }

    public static double measurePoint(MetricsCollector collector, long[] values, int threadCnt)
            throws InterruptedException {
        run(collector, values, threadCnt, 5);
        double[] results = new double[5];
        for (int i = 0; i < results.length; i++) {
            results[i] = run(collector, values, threadCnt, 5);
        }
        System.out.println(collector.snapshot().count());
        Arrays.sort(results);
        return results[results.length / 2];
    }
}
