package textanalysis.algorithms;

/** The optimal DP value and the number of non-boundary table cells evaluated. */
public record DpResult(int value, long cellUpdates) {
}
