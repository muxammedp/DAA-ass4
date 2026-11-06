package textanalysis;

import textanalysis.algorithms.DpResult;
import textanalysis.algorithms.EditDistance;
import textanalysis.algorithms.Kasai;
import textanalysis.algorithms.KmpMatcher;
import textanalysis.algorithms.LcpResult;
import textanalysis.algorithms.Lcs;
import textanalysis.algorithms.MatchResult;
import textanalysis.algorithms.NaiveMatcher;
import textanalysis.algorithms.RabinKarpMatcher;
import textanalysis.algorithms.SuffixArrays;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free assertions, reference oracles, and seeded regression tests. */
public final class TestSuite {
    private static long checks;

    private TestSuite() { }

    public static void main(String[] args) {
        dpFixtures();
        matchingFixtures();
        kasaiFixtures();
        invalidInputs();
        exhaustiveLcs();
        exhaustiveEditDistance();
        randomMatching();
        randomSuffixes();
        stressCases();
        System.out.println("PASS: " + checks + " checks");
        System.out.println("Required fixtures, exhaustive DP oracles, seeded random matching/LCP, "
                + "hash collision, input contracts, and repetitive stress cases passed.");
    }

    private static void dpFixtures() {
        lcs("identical", "algorithm", "algorithm", 9);
        lcs("different", "aaaa", "bbbb", 0);
        lcs("left empty", "", "abc", 0);
        lcs("right empty", "abc", "", 0);
        lcs("both empty", "", "", 0);
        lcs("classic", "ABCBDAB", "BDCABA", 4);
        lcs("subsequence not substring", "abcde", "ace", 3);
        lcs("case sensitive", "A", "a", 0);
        lcs("UTF-16 code units", "\uD83D\uDE00", "\uD83D\uDE00", 2);

        edit("identical", "algorithm", "algorithm", 0);
        edit("left empty", "", "abc", 3);
        edit("right empty", "abc", "", 3);
        edit("both empty", "", "", 0);
        edit("substitution", "cat", "cut", 1);
        edit("insertion", "cat", "cart", 1);
        edit("deletion", "cart", "cat", 1);
        edit("mixed edits", "kitten", "sitting", 3);
        edit("transposition is two edits", "ab", "ba", 2);
        edit("case sensitive", "A", "a", 1);
        edit("UTF-16 code units", "\uD83D\uDE00", "", 2);
    }

    private static void lcs(String label, String a, String b, int expected) {
        DpResult result = Lcs.compute(a, b);
        equal(expected, result.value(), "LCS " + label);
        equal((long) a.length() * b.length(), result.cellUpdates(), "LCS cell updates " + label);
    }

    private static void edit(String label, String a, String b, int expected) {
        DpResult result = EditDistance.compute(a, b);
        equal(expected, result.value(), "Edit distance " + label);
        equal((long) a.length() * b.length(), result.cellUpdates(), "Edit cell updates " + label);
    }

    private static void matchingFixtures() {
        matches("beginning", "abcxyz", "abc", List.of(0));
        matches("middle", "xxabcxx", "abc", List.of(2));
        matches("end", "xyzabc", "abc", List.of(3));
        matches("multiple", "abcxabcxabc", "abc", List.of(0, 4, 8));
        matches("overlap", "aaaaa", "aaa", List.of(0, 1, 2));
        matches("prefix fallback", "ababababaca", "ababaca", List.of(4));
        matches("absent", "abcdefgh", "xyz", List.of());
        matches("longer pattern", "abc", "abcd", List.of());
        matches("empty pattern", "abc", "", List.of(0, 1, 2, 3));
        matches("both empty", "", "", List.of(0));
        matches("empty text", "", "a", List.of());
        matches("whole string", "abc", "abc", List.of(0));
        matches("one character", "banana", "a", List.of(1, 3, 5));
        matches("case sensitive", "AaAa", "a", List.of(1, 3));
        matches("UTF-16 positions", "x\uD83D\uDE00\uD83D\uDE00", "\uD83D\uDE00", List.of(1, 3));
        matches("NUL and high characters", "\u0000\uFFFF\u0000\uFFFF", "\u0000\uFFFF", List.of(0, 2));

        // Base 257 gives 1*257+257 == 2*257+0. Verification must reject the hit.
        matches("deliberate hash collision", "\u0001\u0101", "\u0002\u0000", List.of());
        MatchResult collision = RabinKarpMatcher.findAll("\u0001\u0101", "\u0002\u0000");
        equal(1L, collision.hashChecks(), "collision window hash check");
        equal(1L, collision.verifiedHashHits(), "colliding candidate verified");
        check(collision.comparisons() > 0, "collision causes character verification");

        List<Integer> mutable = new ArrayList<>(List.of(2));
        MatchResult result = new MatchResult(mutable, 0, 0, 0);
        mutable.add(3);
        equal(List.of(2), result.indices(), "match result copies input list");
        throwsType(UnsupportedOperationException.class, () -> result.indices().add(4),
                "match indices immutable");
    }

    private static void matches(String label, String text, String pattern, List<Integer> expected) {
        MatchResult naive = NaiveMatcher.findAll(text, pattern);
        MatchResult kmp = KmpMatcher.findAll(text, pattern);
        MatchResult rk = RabinKarpMatcher.findAll(text, pattern);
        equal(expected, naive.indices(), "Naive " + label);
        equal(expected, kmp.indices(), "KMP " + label);
        equal(expected, rk.indices(), "Rabin-Karp " + label);
        equal(naive.indices(), kmp.indices(), "Naive/KMP agreement " + label);
        equal(naive.indices(), rk.indices(), "Naive/Rabin-Karp agreement " + label);
        check(naive.comparisons() >= 0 && kmp.comparisons() >= 0 && rk.comparisons() >= 0,
                "nonnegative comparisons " + label);
        long windows = pattern.isEmpty() || pattern.length() > text.length()
                ? 0 : text.length() - pattern.length() + 1L;
        equal(windows, rk.hashChecks(), "rolling hash window count " + label);
    }

    private static void kasaiFixtures() {
        lcp("banana", "banana", new int[]{5, 3, 1, 0, 4, 2}, new int[]{0, 1, 3, 0, 0, 2});
        lcp("empty", "", new int[0], new int[0]);
        lcp("singleton", "x", new int[]{0}, new int[]{0});
        lcp("repeated", "aaaaa", new int[]{4, 3, 2, 1, 0}, new int[]{0, 1, 2, 3, 4});
        lcp("all distinct", "abcd", new int[]{0, 1, 2, 3}, new int[]{0, 0, 0, 0});
        arrayEqual(new int[]{5, 3, 1, 0, 4, 2}, SuffixArrays.naiveSuffixArray("banana"),
                "manual suffix array");

        int[] supplied = {5, 3, 1, 0, 4, 2};
        int[] before = supplied.clone();
        LcpResult result = Kasai.compute("banana", supplied);
        arrayEqual(before, supplied, "Kasai preserves supplied suffix array");
        int[] exposed = result.lcp();
        exposed[1] = 999;
        arrayEqual(new int[]{0, 1, 3, 0, 0, 2}, result.lcp(), "LCP accessor defensive copy");
        int[] mutable = {0, 1};
        LcpResult copied = new LcpResult(mutable, 1);
        mutable[1] = 999;
        arrayEqual(new int[]{0, 1}, copied.lcp(), "LCP constructor defensive copy");
    }

    private static void lcp(String label, String text, int[] sa, int[] expected) {
        LcpResult result = Kasai.compute(text, sa);
        arrayEqual(expected, result.lcp(), "Kasai " + label);
        check(result.comparisons() >= 0 && result.comparisons() <= 3L * text.length(),
                "Kasai linear comparison bound " + label);
    }

    private static void invalidInputs() {
        throwsType(NullPointerException.class, () -> Lcs.compute(null, ""), "null LCS left");
        throwsType(NullPointerException.class, () -> Lcs.compute("", null), "null LCS right");
        throwsType(NullPointerException.class, () -> EditDistance.compute(null, ""), "null edit left");
        throwsType(NullPointerException.class, () -> EditDistance.compute("", null), "null edit right");
        throwsType(NullPointerException.class, () -> NaiveMatcher.findAll(null, ""), "null naive text");
        throwsType(NullPointerException.class, () -> NaiveMatcher.findAll("", null), "null naive pattern");
        throwsType(NullPointerException.class, () -> KmpMatcher.findAll(null, ""), "null KMP text");
        throwsType(NullPointerException.class, () -> KmpMatcher.findAll("", null), "null KMP pattern");
        throwsType(NullPointerException.class, () -> RabinKarpMatcher.findAll(null, ""), "null RK text");
        throwsType(NullPointerException.class, () -> RabinKarpMatcher.findAll("", null), "null RK pattern");
        throwsType(NullPointerException.class, () -> Kasai.compute(null, new int[0]), "null Kasai text");
        throwsType(NullPointerException.class, () -> Kasai.compute("", null), "null suffix array");
        throwsType(NullPointerException.class, () -> SuffixArrays.naiveSuffixArray(null), "null SA text");
        throwsType(IllegalArgumentException.class, () -> Kasai.compute("ab", new int[]{0}), "SA length");
        throwsType(IllegalArgumentException.class, () -> Kasai.compute("ab", new int[]{0, 0}), "SA duplicates");
        throwsType(IllegalArgumentException.class, () -> Kasai.compute("ab", new int[]{0, 2}), "SA high index");
        throwsType(IllegalArgumentException.class, () -> Kasai.compute("ab", new int[]{-1, 0}), "SA negative index");
    }

    private static void exhaustiveLcs() {
        List<String> inputs = binaryStrings(6);
        for (String a : inputs) {
            for (String b : inputs) {
                int expected = bruteLcs(a, b);
                lcs("exhaustive " + a + "/" + b, a, b, expected);
            }
        }
    }

    /** Enumerate subsequences instead of using a DP recurrence. */
    private static int bruteLcs(String a, String b) {
        int best = 0;
        for (int mask = 0; mask < (1 << a.length()); mask++) {
            int length = Integer.bitCount(mask);
            if (length <= best) continue;
            int cursor = 0;
            boolean found = true;
            for (int i = 0; i < a.length(); i++) {
                if ((mask & (1 << i)) == 0) continue;
                while (cursor < b.length() && b.charAt(cursor) != a.charAt(i)) cursor++;
                if (cursor == b.length()) {
                    found = false;
                    break;
                }
                cursor++;
            }
            if (found) best = length;
        }
        return best;
    }

    private static void exhaustiveEditDistance() {
        List<String> inputs = binaryStrings(4);
        // BFS explores actual insertion, deletion and substitution operations.
        // The tiny binary-string state graph is independent of the DP table.
        for (String a : inputs) {
            Map<String, Integer> distances = editBfs(a, 4);
            for (String b : inputs) {
                edit("BFS " + a + "/" + b, a, b, distances.get(b));
            }
        }
    }

    private static Map<String, Integer> editBfs(String start, int maximumLength) {
        Map<String, Integer> distances = new HashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        distances.put(start, 0);
        queue.add(start);
        while (!queue.isEmpty()) {
            String s = queue.remove();
            int nextDistance = distances.get(s) + 1;
            for (int i = 0; i < s.length(); i++) {
                visit(s.substring(0, i) + s.substring(i + 1), nextDistance, distances, queue);
                for (char replacement : new char[]{'a', 'b'}) {
                    visit(s.substring(0, i) + replacement + s.substring(i + 1),
                            nextDistance, distances, queue);
                }
            }
            if (s.length() < maximumLength) {
                for (int i = 0; i <= s.length(); i++) {
                    for (char inserted : new char[]{'a', 'b'}) {
                        visit(s.substring(0, i) + inserted + s.substring(i),
                                nextDistance, distances, queue);
                    }
                }
            }
        }
        return distances;
    }

    private static void visit(String word, int distance, Map<String, Integer> distances,
                              ArrayDeque<String> queue) {
        if (distances.putIfAbsent(word, distance) == null) queue.add(word);
    }

    private static List<String> binaryStrings(int maxLength) {
        List<String> result = new ArrayList<>();
        result.add("");
        for (int length = 1; length <= maxLength; length++) {
            for (int value = 0; value < (1 << length); value++) {
                StringBuilder s = new StringBuilder(length);
                for (int bit = 0; bit < length; bit++) s.append((value & (1 << bit)) == 0 ? 'a' : 'b');
                result.add(s.toString());
            }
        }
        return result;
    }

    private static void randomMatching() {
        Random random = new Random(2522);
        for (int trial = 0; trial < 2_000; trial++) {
            String text = randomString(random, random.nextInt(101), "abcd");
            String pattern;
            if (trial % 2 == 0 && !text.isEmpty()) {
                int start = random.nextInt(text.length());
                int end = start + random.nextInt(text.length() - start + 1);
                pattern = text.substring(start, end);
            } else {
                pattern = randomString(random, random.nextInt(25), "abcd");
            }
            matches("seeded " + trial, text, pattern, referenceMatches(text, pattern));
        }
    }

    private static List<Integer> referenceMatches(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i <= text.length() - pattern.length(); i++) {
            if (text.startsWith(pattern, i)) positions.add(i);
        }
        return positions;
    }

    private static void randomSuffixes() {
        Random random = new Random(7144);
        for (int trial = 0; trial < 1_000; trial++) {
            String text = randomString(random, random.nextInt(101), "abcd");
            int[] sa = SuffixArrays.naiveSuffixArray(text);
            equal(text.length(), sa.length, "SA length random " + trial);
            boolean[] seen = new boolean[text.length()];
            for (int i = 0; i < sa.length; i++) {
                check(sa[i] >= 0 && sa[i] < text.length(), "SA range random " + trial);
                check(!seen[sa[i]], "SA unique random " + trial);
                seen[sa[i]] = true;
                if (i > 0) {
                    check(text.substring(sa[i - 1]).compareTo(text.substring(sa[i])) < 0,
                            "SA sorted random " + trial);
                }
            }
            int[] expected = new int[text.length()];
            for (int i = 1; i < sa.length; i++) {
                int a = sa[i - 1];
                int b = sa[i];
                while (a + expected[i] < text.length() && b + expected[i] < text.length()
                        && text.charAt(a + expected[i]) == text.charAt(b + expected[i])) {
                    expected[i]++;
                }
            }
            lcp("seeded " + trial, text, sa, expected);
        }
    }

    private static void stressCases() {
        String repetitive = "a".repeat(10_000);
        String pattern = "a".repeat(64);
        matches("large overlaps", repetitive, pattern, referenceMatches(repetitive, pattern));
        String difficultPattern = "a".repeat(63) + "b";
        matches("long common prefix absent", repetitive, difficultPattern, List.of());
        MatchResult linear = KmpMatcher.findAll(repetitive, difficultPattern);
        check(linear.comparisons() <= 4L * (repetitive.length() + difficultPattern.length()),
                "KMP repetitive linear comparisons");

        int[] sa = new int[repetitive.length()];
        int[] expected = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            sa[i] = sa.length - i - 1;
            expected[i] = i;
        }
        lcp("large repetitive supplied SA", repetitive, sa, expected);
    }

    private static String randomString(Random random, int length, String alphabet) {
        StringBuilder s = new StringBuilder(length);
        for (int i = 0; i < length; i++) s.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return s.toString();
    }

    private static void equal(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }

    private static void arrayEqual(int[] expected, int[] actual, String label) {
        checks++;
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label + ": expected " + Arrays.toString(expected)
                    + ", got " + Arrays.toString(actual));
        }
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static void throwsType(Class<? extends Throwable> expected, Runnable operation, String label) {
        checks++;
        try {
            operation.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError(label + ": wrong exception " + actual, actual);
        }
        throw new AssertionError(label + ": no exception");
    }
}
