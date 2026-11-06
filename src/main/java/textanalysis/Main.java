package textanalysis;

import java.util.Arrays;
import textanalysis.algorithms.EditDistance;
import textanalysis.algorithms.Kasai;
import textanalysis.algorithms.KmpMatcher;
import textanalysis.algorithms.Lcs;
import textanalysis.algorithms.NaiveMatcher;
import textanalysis.algorithms.RabinKarpMatcher;

/** Small, manually checkable examples of all six required algorithms. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.out.println("Assignment 4 - Text Analysis");
        System.out.println("LCS(AGGTAB, GXTXAYB) = " + Lcs.compute("AGGTAB", "GXTXAYB").value());
        System.out.println("Edit distance(kitten, sitting) = "
                + EditDistance.compute("kitten", "sitting").value());
        String text = "bananana";
        String pattern = "ana";
        System.out.println("Text: " + text + ", pattern: " + pattern);
        System.out.println("Naive:      " + NaiveMatcher.findAll(text, pattern).indices());
        System.out.println("KMP:        " + KmpMatcher.findAll(text, pattern).indices());
        System.out.println("Rabin-Karp: " + RabinKarpMatcher.findAll(text, pattern).indices());
        int[] suffixArray = {5, 3, 1, 0, 4, 2};
        System.out.println("banana suffix array: " + Arrays.toString(suffixArray));
        System.out.println("banana LCP array:    "
                + Arrays.toString(Kasai.compute("banana", suffixArray).lcp()));
    }
}
