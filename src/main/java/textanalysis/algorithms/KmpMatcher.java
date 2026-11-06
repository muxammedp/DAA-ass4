package textanalysis.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** KMP; comparisons include both prefix-table construction and the text search. */
public final class KmpMatcher {
    private KmpMatcher() {
    }

    public static MatchResult findAll(String text, String pattern) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(pattern, "pattern");
        if (pattern.isEmpty()) {
            return MatchResult.emptyPattern(text.length());
        }
        if (pattern.length() > text.length()) {
            return new MatchResult(List.of(), 0, 0, 0);
        }

        int[] prefix = new int[pattern.length()];
        long comparisons = 0;
        int matched = 0;
        for (int i = 1; i < pattern.length(); i++) {
            while (true) {
                comparisons++;
                if (pattern.charAt(i) == pattern.charAt(matched)) {
                    matched++;
                    break;
                }
                if (matched == 0) {
                    break;
                }
                matched = prefix[matched - 1];
            }
            prefix[i] = matched;
        }

        List<Integer> matches = new ArrayList<>();
        matched = 0;
        int i = 0;
        while (i < text.length()) {
            comparisons++;
            if (text.charAt(i) == pattern.charAt(matched)) {
                i++;
                matched++;
                if (matched == pattern.length()) {
                    matches.add(i - matched);
                    matched = prefix[matched - 1];
                }
            } else if (matched > 0) {
                matched = prefix[matched - 1];
            } else {
                i++;
            }
        }
        return new MatchResult(matches, comparisons, 0, 0);
    }
}
