package textanalysis.algorithms;

import java.util.Objects;

/** lcp[0] is zero; lcp[r] compares suffixes at ranks r - 1 and r. */
public record LcpResult(int[] lcp, long comparisons) {
    public LcpResult {
        lcp = Objects.requireNonNull(lcp, "lcp").clone();
    }

    @Override
    public int[] lcp() {
        return lcp.clone();
    }
}
