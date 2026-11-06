package textanalysis.algorithms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Matches use zero-based UTF-16 indices; comparison counters count equality tests. */
public record MatchResult(List<Integer> indices, long comparisons,
                          long hashChecks, long verifiedHashHits) {
    public MatchResult {
        indices = List.copyOf(Objects.requireNonNull(indices, "indices"));
    }

    static MatchResult emptyPattern(int textLength) {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i <= textLength; i++) {
            matches.add(i);
        }
        return new MatchResult(matches, 0, 0, 0);
    }
}
