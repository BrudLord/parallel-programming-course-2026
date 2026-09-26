package org.example;

import java.util.Arrays;
import java.util.Random;

public class LoadGenerator {
    public static long[] loadGenerator() {
        double[] prefWeight = new double[1023];
        double totalWeight = 0;
        for (int k = 1; k <= prefWeight.length; k++) {
            totalWeight += 1.0 / Math.pow(k, 1.15);
            prefWeight[k - 1] = totalWeight;
        }

        Random random = new Random(19);
        long[] values = new long[1 << 20];
        for (int i = 0; i < values.length; i++) {
            double target = random.nextDouble() * totalWeight;
            int idx = Arrays.binarySearch(prefWeight, target);
            if (idx < 0) {
                idx = -idx - 1;
            }
            values[i] = idx + 1;
        }
        return values;
    }
}
