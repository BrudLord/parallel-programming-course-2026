package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    public static final ThreadLocal<ThreadState> currentThread = new ThreadLocal<>();
    private final List<ThreadState> threads = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder();
    private final Semaphore schedulerWakeup = new Semaphore(0);

    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    public Scheduler() {
    }

    void registerLock(SchedLock lock) {
        locks.add(lock);
    }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        threads.add(new ThreadState(this, threads.size(), body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        startThreads();
        int last = -1;
        while (true) {
            List<Integer> enabled = new ArrayList<>();
            boolean allFinished = true;
            for (ThreadState thread : threads) {
                if (thread.state == State.FINISHED) continue;
                allFinished = false;
                if (thread.waitingLock != null) {
                    thread.state = thread.waitingLock.owner == null
                            ? State.WAITING_TURN
                            : State.WAITING_LOCK;
                } else if (thread.condition != null) {
                    thread.state = thread.condition.getAsBoolean()
                            ? State.WAITING_TURN
                            : State.WAITING_CONDITION;
                }
                if (thread.state == State.WAITING_TURN) {
                    enabled.add(thread.id);
                }
            }
            if (allFinished) break;
            if (enabled.isEmpty()) {
                List<String> held = new ArrayList<>();
                for (SchedLock lock : locks) {
                    if (lock.owner != null) {
                        held.add(lock.name + ":" + threads.get(lock.owner).getName());
                    }
                }
                deadlock = "held={" + String.join(", ", held) + "}";
                break;
            }

            int next = strategy.pick(enabled, last);
            ThreadState selected = threads.get(next);
            trace.append(selected.getName());
            last = next;
            selected.workerWakeup.release();
            schedulerWakeup.acquireUninterruptibly();
        }
        stopThreads();
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        ThreadState thread = currentThread.get();
        thread.needsPause = true;
        log("window");
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        ThreadState thread = currentThread.get();
        boolean ready = cond.getAsBoolean();
        thread.condition = cond;
        while (thread.needsPause || !ready) {
            thread.state = ready ? State.WAITING_TURN : State.WAITING_CONDITION;
            thread.needsPause = false;
            pause();
            thread.state = State.RUNNING;
            ready = cond.getAsBoolean();
        }

        log("await done");
        thread.condition = null;
        thread.needsPause = true;
    }

    public static void log(String operation) {
        ThreadState thread = currentThread.get();
        Scheduler scheduler = thread.scheduler;
        if (scheduler.logging) {
            scheduler.log.add(thread.getName() + "  " + operation);
        }
    }

    static void yieldToScheduler() {
        ThreadState thread = currentThread.get();
        if (thread.needsPause) {
            thread.state = State.WAITING_TURN;
            thread.needsPause = false;
            pause();
            thread.state = State.RUNNING;
        }
        thread.needsPause = true;
    }

    public static void pause() {
        ThreadState thread = currentThread.get();
        thread.scheduler.schedulerWakeup.release();
        thread.workerWakeup.acquireUninterruptibly();
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    private void startThreads() {
        for (ThreadState thread : threads) {
            thread.thread = createThread(thread);
            thread.thread.start();
        }
    }

    private Thread createThread(ThreadState thread) {
        return new Thread(() -> {
            currentThread.set(thread);
            try {
                thread.workerWakeup.acquireUninterruptibly();
                if (Thread.currentThread().isInterrupted()) return;
                thread.state = State.RUNNING;
                thread.needsPause = false;
                thread.body.run();
            } catch (CancellationException cancelled) {
                if (!Thread.currentThread().isInterrupted()) throw cancelled;
            } finally {
                thread.state = State.FINISHED;
                currentThread.remove();
                schedulerWakeup.release();
            }
        });
    }

    private void stopThreads() {
        for (ThreadState thread : threads) {
            if (thread.state != State.FINISHED) {
                thread.thread.interrupt();
                thread.workerWakeup.release();
            }
        }
        boolean interrupted = false;
        for (ThreadState thread : threads) {
            while (true) {
                try {
                    thread.thread.join();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    public enum State {
        RUNNING,
        WAITING_TURN,
        WAITING_LOCK,
        WAITING_CONDITION,
        FINISHED
    }

    public static final class ThreadState {
        final Scheduler scheduler;
        final Runnable body;
        Integer id;
        final Semaphore workerWakeup = new Semaphore(0);
        Thread thread;
        State state = State.WAITING_TURN;
        BooleanSupplier condition;
        SchedLock waitingLock;
        boolean needsPause = true;

        ThreadState(Scheduler scheduler, Integer id, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.body = body;
        }

        public char getName() {
            return (char) ('A' + id);
        }
    }
}
