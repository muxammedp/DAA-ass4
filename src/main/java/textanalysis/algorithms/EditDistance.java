package textanalysis.algorithms;

import java.util.Objects;

/** Unit-cost Levenshtein distance using the full dynamic-programming table. */
public final class EditDistance {
    private EditDistance() {
    }

    public static DpResult compute(String a, String b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        int[][] table = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            table[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            table[0][j] = j;
        }
        long updates = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                table[i][j] = Math.min(table[i - 1][j - 1] + substitutionCost,
                        Math.min(table[i - 1][j] + 1, table[i][j - 1] + 1));
                updates++;
            }
        }
        return new DpResult(table[a.length()][b.length()], updates);
    }
}
