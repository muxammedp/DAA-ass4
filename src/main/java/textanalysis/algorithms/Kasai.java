package textanalysis.algorithms;

import java.util.Objects;

/** Linear-time LCP construction for an already sorted suffix array. */
public final class Kasai {
    private Kasai() {
    }

    /**
     * Validates the permutation in O(n). Lexicographic sortedness is a caller
     * precondition, so expensive suffix comparisons are excluded from this method.
     */
    public static LcpResult compute(String text, int[] suffixArray) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(suffixArray, "suffixArray");
        int n = text.length();
        if (suffixArray.length != n) {
            throw new IllegalArgumentException("Suffix array length must equal text length");
        }
        int[] rank = new int[n];
        boolean[] seen = new boolean[n];
        for (int r = 0; r < n; r++) {
            int suffix = suffixArray[r];
            if (suffix < 0 || suffix >= n || seen[suffix]) {
                throw new IllegalArgumentException("Suffix array must be a permutation of 0..n-1");
            }
            seen[suffix] = true;
            rank[suffix] = r;
        }

        int[] lcp = new int[n];
        int common = 0;
        long comparisons = 0;
        for (int i = 0; i < n; i++) {
            int r = rank[i];
            if (r == 0) {
                common = 0;
                continue;
            }
            int previous = suffixArray[r - 1];
            while (common < n - i && common < n - previous) {
                comparisons++;
                if (text.charAt(i + common) != text.charAt(previous + common)) {
                    break;
                }
                common++;
            }
            lcp[r] = common;
            if (common > 0) {
                common--;
            }
        }
        return new LcpResult(lcp, comparisons);
    }
}
