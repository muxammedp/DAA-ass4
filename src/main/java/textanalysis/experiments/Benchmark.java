package textanalysis.experiments;

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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

/** Fixed assignment workloads. Only the algorithm call lies inside each timed region. */
public final class Benchmark {
    public static final long SEED = 2522L;
    public static final String ALPHABET = "abcd";
    public static final int WARMUPS = 10;
    public static final int TRIALS = 5;
    public static final int PATTERN_LENGTH = 32;
    private static volatile long blackhole;

    private Benchmark() { }

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            throw new IllegalArgumentException("Usage: Benchmark [output-directory]");
        }
        Path output = Path.of(args.length == 0 ? "results" : args[0]);
        Files.createDirectories(output);
        List<Measurement> raw = new ArrayList<>();
        List<String> inputs = new ArrayList<>();
        inputs.add("family,scenario,input_size,second_size,derived_seed,first_sha256,second_sha256,expected_matches");

        for (int n : new int[] {100, 500, 1000}) {
            long seed = SEED + n;
            Random random = new Random(seed);
            String first = randomString(random, n);
            String second = randomString(random, n);
            inputs.add(inputRow("dp", "random", n, n, seed, first, second, ""));
            measure(raw, "dp", "LCS", "random", n, n, () -> Lcs.compute(first, second));
            measure(raw, "dp", "Edit Distance", "random", n, n,
                    () -> EditDistance.compute(first, second));
        }

        for (int n : new int[] {10_000, 50_000, 100_000}) {
            long seed = SEED + 1_000_000L + n;
            Random random = new Random(seed);
            String text = randomString(random, n);
            int start = (n - PATTERN_LENGTH) / 2;
            String present = text.substring(start, start + PATTERN_LENGTH);
            String absent;
            // Input preparation is untimed. The absent pattern uses the same alphabet.
            do {
                absent = randomString(random, PATTERN_LENGTH);
            } while (text.contains(absent));
            for (String scenario : List.of("present", "absent")) {
                String pattern = scenario.equals("present") ? present : absent;
                List<Integer> expected = NaiveMatcher.findAll(text, pattern).indices();
                if (!expected.equals(KmpMatcher.findAll(text, pattern).indices())
                        || !expected.equals(RabinKarpMatcher.findAll(text, pattern).indices())) {
                    throw new IllegalStateException("Matchers disagree during input validation");
                }
                inputs.add(inputRow("matching", scenario, n, pattern.length(), seed,
                        text, pattern, Integer.toString(expected.size())));
                measure(raw, "matching", "Naive", scenario, n, pattern.length(),
                        () -> NaiveMatcher.findAll(text, pattern));
                measure(raw, "matching", "KMP", scenario, n, pattern.length(),
                        () -> KmpMatcher.findAll(text, pattern));
                measure(raw, "matching", "Rabin-Karp", scenario, n, pattern.length(),
                        () -> RabinKarpMatcher.findAll(text, pattern));
            }
        }

        for (int n : new int[] {1000, 5000, 10_000}) {
            long seed = SEED + 2_000_000L + n;
            String text = randomString(new Random(seed), n);
            // Suffix-array construction is explicitly excluded from Kasai's measurements.
            int[] suffixArray = SuffixArrays.naiveSuffixArray(text);
            inputs.add(inputRow("lcp", "random", n, 0, seed, text, "", ""));
            measure(raw, "lcp", "Kasai", "random", n, 0,
                    () -> Kasai.compute(text, suffixArray));
        }

        List<Summary> summaries = summarize(raw);
        writeRaw(output.resolve("raw.csv"), raw);
        writeSummary(output.resolve("summary.csv"), summaries);
        Files.write(output.resolve("inputs.csv"), inputs, StandardCharsets.UTF_8);
        Files.writeString(output.resolve("environment.txt"), environment(), StandardCharsets.UTF_8);
        PngCharts.writeAll(output, summaries);
        System.out.printf(Locale.ROOT,
                "Recorded %d measurements across %d workloads (%d warmups, %d trials each).%n",
                raw.size(), summaries.size(), WARMUPS, TRIALS);
        System.out.println("Results and PNG plots: " + output.toAbsolutePath().normalize());
    }

    private static void measure(List<Measurement> output, String family, String algorithm,
                                String scenario, int n, int m, Supplier<?> action) {
        Snapshot expected = null;
        for (int warmup = 0; warmup < WARMUPS; warmup++) {
            Snapshot result = snapshot(action.get());
            expected = validate(expected, result);
            blackhole ^= result.fingerprint();
        }
        for (int trial = 1; trial <= TRIALS; trial++) {
            long start = System.nanoTime();
            Object result = action.get();
            long elapsed = System.nanoTime() - start;
            // Inspect, validate and consume returned results only after stopping the clock.
            Snapshot observation = snapshot(result);
            validate(expected, observation);
            blackhole ^= observation.fingerprint();
            output.add(new Measurement(family, algorithm, scenario, n, m, trial, elapsed,
                    observation.comparisons(), observation.cellUpdates(), observation.hashChecks(),
                    observation.verifiedHashHits(), observation.resultValue()));
        }
    }

    private static Snapshot validate(Snapshot expected, Snapshot observed) {
        if (expected != null && !expected.equals(observed)) {
            throw new IllegalStateException("An algorithm returned inconsistent results or counters");
        }
        return observed;
    }

    private static Snapshot snapshot(Object value) {
        if (value instanceof DpResult result) {
            return new Snapshot(0, result.cellUpdates(), 0, 0, result.value(), result.value());
        }
        if (value instanceof MatchResult result) {
            return new Snapshot(result.comparisons(), 0, result.hashChecks(),
                    result.verifiedHashHits(), result.indices().size(), result.indices().hashCode());
        }
        if (value instanceof LcpResult result) {
            int[] lcp = result.lcp();
            int maximum = 0;
            for (int length : lcp) {
                maximum = Math.max(maximum, length);
            }
            return new Snapshot(result.comparisons(), 0, 0, 0, maximum, Arrays.hashCode(lcp));
        }
        throw new IllegalArgumentException("Unsupported benchmark result type");
    }

    private static String randomString(Random random, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    private static String inputRow(String family, String scenario, int n, int m, long seed,
                                   String first, String second, String expectedMatches) {
        return String.join(",", family, scenario, Integer.toString(n), Integer.toString(m),
                Long.toString(seed), sha256(first), sha256(second), expectedMatches);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("A Java runtime must support SHA-256", e);
        }
    }

    private static List<Summary> summarize(List<Measurement> raw) {
        List<Summary> summaries = new ArrayList<>();
        for (int offset = 0; offset < raw.size(); offset += TRIALS) {
            Measurement first = raw.get(offset);
            double mean = 0;
            long minimum = Long.MAX_VALUE;
            long maximum = Long.MIN_VALUE;
            for (int i = 0; i < TRIALS; i++) {
                long nanos = raw.get(offset + i).nanos();
                mean += nanos / (double) TRIALS;
                minimum = Math.min(minimum, nanos);
                maximum = Math.max(maximum, nanos);
            }
            double variance = 0;
            for (int i = 0; i < TRIALS; i++) {
                double difference = raw.get(offset + i).nanos() - mean;
                variance += difference * difference / TRIALS;
            }
            summaries.add(new Summary(first.family(), first.algorithm(), first.scenario(),
                    first.n(), first.m(), mean, minimum, maximum, Math.sqrt(variance),
                    first.comparisons(), first.cellUpdates(), first.hashChecks(),
                    first.verifiedHashHits(), first.resultValue()));
        }
        return List.copyOf(summaries);
    }

    private static void writeRaw(Path path, List<Measurement> rows) throws IOException {
        StringBuilder csv = new StringBuilder("family,algorithm,scenario,input_size,second_size,trial,elapsed_ns,comparisons,cell_updates,hash_checks,verified_hash_hits,result_value\n");
        for (Measurement row : rows) {
            csv.append(String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    row.family(), row.algorithm(), row.scenario(), row.n(), row.m(), row.trial(),
                    row.nanos(), row.comparisons(), row.cellUpdates(), row.hashChecks(),
                    row.verifiedHashHits(), row.resultValue()));
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static void writeSummary(Path path, List<Summary> rows) throws IOException {
        StringBuilder csv = new StringBuilder("family,algorithm,scenario,input_size,second_size,trials,mean_ns,mean_ms,min_ns,max_ns,stddev_ns,comparisons,cell_updates,hash_checks,verified_hash_hits,result_value\n");
        for (Summary row : rows) {
            csv.append(String.format(Locale.ROOT,
                    "%s,%s,%s,%d,%d,%d,%.3f,%.6f,%d,%d,%.3f,%d,%d,%d,%d,%d%n",
                    row.family(), row.algorithm(), row.scenario(), row.n(), row.m(), TRIALS,
                    row.meanNanos(), row.meanNanos() / 1_000_000.0, row.minNanos(), row.maxNanos(),
                    row.stddevNanos(), row.comparisons(), row.cellUpdates(), row.hashChecks(),
                    row.verifiedHashHits(), row.resultValue()));
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8);
    }

    private static String environment() {
        return "Generated (UTC): " + Instant.now() + "\n"
                + "Java version: " + System.getProperty("java.version") + "\n"
                + "Java runtime: " + System.getProperty("java.runtime.name") + "\n"
                + "Java VM: " + System.getProperty("java.vm.name") + "\n"
                + "Java vendor: " + System.getProperty("java.vendor") + "\n"
                + "JVM arguments: " + ManagementFactory.getRuntimeMXBean().getInputArguments() + "\n"
                + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch") + "\n"
                + "Available processors: " + Runtime.getRuntime().availableProcessors() + "\n"
                + "Maximum JVM heap (bytes): " + Runtime.getRuntime().maxMemory() + "\n"
                + "Base seed: " + SEED + "\nAlphabet: " + ALPHABET + "\n"
                + "Generator: java.util.Random; DP seed = base + n; matching seed = base + 1000000 + n; Kasai seed = base + 2000000 + n\n"
                + "Matching pattern length: " + PATTERN_LENGTH + "\n"
                + "Present pattern starts at (n - patternLength) / 2; absent pattern is sampled from the same alphabet until absent\n"
                + "Warmups per algorithm/input: " + WARMUPS + "\nMeasured trials per algorithm/input: " + TRIALS + "\n"
                + "Clock: System.nanoTime(); elapsed values are nanoseconds\n"
                + "Timed: complete algorithm invocation, including required preprocessing, validation, allocations, and instrumentation\n"
                + "Excluded: random generation, suffix-array construction, result inspection, console output, CSV/file operations, and PNG generation\n"
                + "Summary standard deviation: population standard deviation of the five measured times\n"
                + "Counter values are deterministic and identical across trials; summary lists their common value\n"
                + "result_value: LCS length / edit distance / match count / maximum LCP, depending on algorithm\n"
                + "Runtime caveat: short JVM microbenchmarks are affected by compilation, garbage collection, scheduling, and instrumentation\n";
    }

    private record Snapshot(long comparisons, long cellUpdates, long hashChecks,
                            long verifiedHashHits, int resultValue, long fingerprint) { }

    private record Measurement(String family, String algorithm, String scenario, int n, int m,
                               int trial, long nanos, long comparisons, long cellUpdates,
                               long hashChecks, long verifiedHashHits, int resultValue) { }

    record Summary(String family, String algorithm, String scenario, int n, int m,
                   double meanNanos, long minNanos, long maxNanos, double stddevNanos,
                   long comparisons, long cellUpdates, long hashChecks, long verifiedHashHits,
                   int resultValue) { }
}
