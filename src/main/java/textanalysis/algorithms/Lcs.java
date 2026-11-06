package textanalysis.algorithms;

import java.util.Objects;

/** Longest common subsequence length using the full dynamic-programming table. */
public final class Lcs {
    private Lcs() {
    }

    public static DpResult compute(String a, String b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        int[][] table = new int[a.length() + 1][b.length() + 1];
        long updates = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    table[i][j] = table[i - 1][j - 1] + 1;
                } else {
                    table[i][j] = Math.max(table[i - 1][j], table[i][j - 1]);
                }
                updates++;
            }
        }
        return new DpResult(table[a.length()][b.length()], updates);
    }
}
