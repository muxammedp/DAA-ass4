package textanalysis.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Rolling-hash matching with character verification of every hash hit. */
public final class RabinKarpMatcher {
    private static final long BASE = 257;
    private static final long MODULUS = 1_000_000_007;

    private RabinKarpMatcher() {
    }

    public static MatchResult findAll(String text, String pattern) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(pattern, "pattern");
        int n = text.length();
        int m = pattern.length();
        if (m == 0) {
            return MatchResult.emptyPattern(n);
        }
        if (m > n) {
            return new MatchResult(List.of(), 0, 0, 0);
        }

        long leadingPower = 1;
        long patternHash = 0;
        long windowHash = 0;
        for (int i = 0; i < m; i++) {
            patternHash = (patternHash * BASE + pattern.charAt(i)) % MODULUS;
            windowHash = (windowHash * BASE + text.charAt(i)) % MODULUS;
            if (i < m - 1) {
                leadingPower = leadingPower * BASE % MODULUS;
            }
        }

        List<Integer> matches = new ArrayList<>();
        long comparisons = 0;
        long hashChecks = 0;
        long verifiedHashHits = 0;
        for (int start = 0; start <= n - m; start++) {
            hashChecks++;
            if (windowHash == patternHash) {
                verifiedHashHits++;
                int j = 0;
                while (j < m) {
                    comparisons++;
                    if (text.charAt(start + j) != pattern.charAt(j)) {
                        break;
                    }
                    j++;
                }
                if (j == m) {
                    matches.add(start);
                }
            }
            if (start < n - m) {
                long outgoing = text.charAt(start) * leadingPower % MODULUS;
                windowHash = (windowHash - outgoing + MODULUS) % MODULUS;
                windowHash = (windowHash * BASE + text.charAt(start + m)) % MODULUS;
            }
        }
        return new MatchResult(matches, comparisons, hashChecks, verifiedHashHits);
    }
}
