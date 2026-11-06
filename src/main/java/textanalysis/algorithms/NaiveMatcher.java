package textanalysis.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Checks every possible alignment, including overlapping matches. */
public final class NaiveMatcher {
    private NaiveMatcher() {
    }

    public static MatchResult findAll(String text, String pattern) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(pattern, "pattern");
        if (pattern.isEmpty()) {
            return MatchResult.emptyPattern(text.length());
        }
        List<Integer> matches = new ArrayList<>();
        long comparisons = 0;
        for (int start = 0; start <= text.length() - pattern.length(); start++) {
            int j = 0;
            while (j < pattern.length()) {
                comparisons++;
                if (text.charAt(start + j) != pattern.charAt(j)) {
                    break;
                }
                j++;
            }
            if (j == pattern.length()) {
                matches.add(start);
            }
        }
        return new MatchResult(matches, comparisons, 0, 0);
    }
}
