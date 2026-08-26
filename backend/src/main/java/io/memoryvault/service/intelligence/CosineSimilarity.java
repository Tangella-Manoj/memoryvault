package io.memoryvault.service.intelligence;

import java.util.List;

/**
 * Pure-Java cosine similarity between two equal-length embedding vectors — no external
 * vector-math library needed for the volumes this app deals with.
 */
final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * @param a first vector
     * @param b second vector, must be the same length as {@code a}
     * @return cosine similarity in [-1, 1], or 0.0 if either vector is empty/all-zero or
     *         the two vectors differ in length
     */
    static double of(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }
        double dot = 0.0;
        double magA = 0.0;
        double magB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            magA += x * x;
            magB += y * y;
        }
        if (magA == 0.0 || magB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }
}
