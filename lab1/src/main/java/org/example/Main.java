package org.example;


import org.example.steps.step0.MetricsCollectorStep0Impl;
import org.example.steps.step1.MetricsCollectorStep1EmptyLockImpl;
import org.example.steps.step1.MetricsCollectorStep1Impl;
import org.example.steps.step2.MetricsCollectorSharedMutexImpl;
import org.example.steps.step3.MetricsCollectorThreadLocalImpl;
import org.example.steps.step4.MetricsCollectorDoubleBufferImpl;

public class Main {

    public static void step0() throws InterruptedException {
        var load = LoadGenerator.loadGenerator();
        var collector = new MetricsCollectorStep0Impl();
        System.out.printf("%.1f", Speedtest.measurePoint(collector, load, 1) / 1_000_000);
    }

    public static void step1() throws InterruptedException {
        var load = LoadGenerator.loadGenerator();
        var collector = new MetricsCollectorStep1Impl();
        System.out.printf("%.1f", Speedtest.measurePoint(collector, load, 2) / 1_000_000);
    }

    public static void step1EmptyLock() throws InterruptedException {
        var load = LoadGenerator.loadGenerator();
        var collector = new MetricsCollectorStep1EmptyLockImpl();
        System.out.printf("%.1f", Speedtest.measurePoint(collector, load, 8) / 1_000_000);
    }

    public static void step2() throws InterruptedException {
        var load = LoadGenerator.loadGenerator();
        var collector = new MetricsCollectorSharedMutexImpl();
        System.out.printf("%.1f", Speedtest.measurePoint(collector, load, 2) / 1_000_000);
    }

    public static void step3() throws InterruptedException {
        var load = LoadGenerator.loadGenerator();
        var collector = new MetricsCollectorThreadLocalImpl();
        System.out.printf("%.1f", Speedtest.measurePoint(collector, load, 8) / 1_000_000);
    }

    public static void step4() throws InterruptedException {
        var load = LoadGenerator.loadGenerator();
        var collector = new MetricsCollectorDoubleBufferImpl();
        System.out.printf("%.1f", Speedtest.measurePoint(collector, load, 8) / 1_000_000);
    }

    public static void main(String[] args) throws InterruptedException {
        step2();
    }
}
