package textanalysis.algorithms;

import java.util.Objects;

/** A comparison-based suffix-array helper; run it outside Kasai measurements. */
public final class SuffixArrays {
    private SuffixArrays() {
    }

    /** Sorts suffix indices without allocating suffix substrings. */
    public static int[] naiveSuffixArray(String text) {
        Objects.requireNonNull(text, "text");
        int[] suffixes = new int[text.length()];
        for (int i = 0; i < suffixes.length; i++) {
            suffixes[i] = i;
        }
        mergeSort(text, suffixes, new int[suffixes.length], 0, suffixes.length);
        return suffixes;
    }

    private static void mergeSort(String text, int[] suffixes, int[] scratch,
                                  int begin, int end) {
        if (end - begin < 2) {
            return;
        }
        int middle = begin + (end - begin) / 2;
        mergeSort(text, suffixes, scratch, begin, middle);
        mergeSort(text, suffixes, scratch, middle, end);
        int left = begin;
        int right = middle;
        for (int out = begin; out < end; out++) {
            if (left < middle && (right == end
                    || compareSuffixes(text, suffixes[left], suffixes[right]) <= 0)) {
                scratch[out] = suffixes[left++];
            } else {
                scratch[out] = suffixes[right++];
            }
        }
        System.arraycopy(scratch, begin, suffixes, begin, end - begin);
    }

    private static int compareSuffixes(String text, int left, int right) {
        int n = text.length();
        while (left < n && right < n) {
            int comparison = Character.compare(text.charAt(left), text.charAt(right));
            if (comparison != 0) {
                return comparison;
            }
            left++;
            right++;
        }
        return Integer.compare(n - left, n - right);
    }
}
