package org.example;

import org.example.steps.step2.MetricsCollectorSharedMutexImpl;
import org.example.steps.step3.MetricsCollectorThreadLocalImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StressTest {
    @Test
    public void step2() throws InterruptedException, ExecutionException {
        var collector = new MetricsCollectorSharedMutexImpl();
        var values = LoadGenerator.loadGenerator();
        run(collector, values, 4);
    }

    @Test
    public void step3() throws InterruptedException, ExecutionException {
        var collector = new MetricsCollectorThreadLocalImpl();
        var values = LoadGenerator.loadGenerator();
        run(collector, values, 4);
    }

    public static void run(MetricsCollector collector, long[] values, int threadCnt)
            throws InterruptedException, ExecutionException {
        int snapshotCount = 10_000;
        int lessCount = 0;
        int greaterCount = 0;
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(threadCnt);
        ExecutorService threads = Executors.newFixedThreadPool(threadCnt);
        List<Future<Long>> writers = new ArrayList<>();

        for (int k = 0; k < threadCnt; k++) {
            int startIndex = k * 1000 % values.length;
            writers.add(threads.submit(() -> {
                long localCount = 0;
                int index = startIndex;
                try {
                    while (!stop.get()) {
                        collector.record(values[index]);
                        localCount++;
                        if (localCount == 1) {
                            ready.countDown();
                        }
                        index++;
                        if (index == values.length) {
                            index = 0;
                        }
                    }
                    return localCount;
                } finally {
                    ready.countDown();
                }
            }));
        }

        ready.await();
        for (int i = 0; i < snapshotCount; i++) {
            Snapshot snapshot = collector.snapshot();
            long bucketSum = Arrays.stream(snapshot.buckets()).sum();
            if (bucketSum < snapshot.count()) {
                lessCount++;
            } else if (bucketSum > snapshot.count()) {
                greaterCount++;
            }
        }

        stop.set(true);
        long totalCalls = 0;
        for (Future<Long> writer : writers) {
            totalCalls += writer.get();
        }
        Snapshot finalSnapshot = collector.snapshot();

        System.out.printf("Сумма корзин < count: %d%n", lessCount);
        System.out.printf("Сумма корзин > count: %d%n", greaterCount);
        System.out.printf("Вызовов record: %d%n", totalCalls);
        System.out.printf("Итоговый count: %d%n", finalSnapshot.count());

        assertEquals(totalCalls, finalSnapshot.count(), "Неверное итоговое число записей");

        stop.set(true);
        threads.shutdownNow();
    }
}
