package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;

        public Replay(String trace) {
            this.trace = trace;
        }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() {
            position = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            char name = trace.charAt(position);
            position++;
            return name - 'A';
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) {
            rnd = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private List<Node> options;
        private Integer idx;
        private Integer maxBound;
        private Integer currentBound;

        public Dfs(int bound) {
            options = new ArrayList<>();
            idx = 0;
            maxBound = bound;
        }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            idx = 0;
            currentBound = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (idx >= options.size()) {
                List<Integer> allowed = new ArrayList<>();
                for (int candidate : enabled) {
                    if (!(last != -1 && candidate != last && enabled.contains(last)) || currentBound < maxBound) {
                        allowed.add(candidate);
                    }
                }
                options.add(new Node(allowed));
            }
            var next = options.get(idx).pick();
            idx++;
            if (last != -1 && last != next && enabled.contains(last)) {
                currentBound++;
            }
            return next;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (options.size() > idx) {
                options.removeLast();
            }
            while (!options.isEmpty()) {
                if (options.getLast().next()) {
                    return true;
                }
                options.removeLast();
            }
            return false;
        }

        private static final class Node {
            final List<Integer> options;
            int idx;

            Node(List<Integer> enabled) {
                options = List.copyOf(enabled);
                idx = 0;
            }

            public Integer pick() {
                return options.get(idx);
            }

            public boolean next() {
                idx++;
                return idx < options.size();
            }
        }
    }
}
