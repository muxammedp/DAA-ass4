package textanalysis.experiments;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Small dependency-free renderer for the assignment's measured runtime charts. */
final class PngCharts {
    private static final Color BACKGROUND = new Color(248, 250, 253);
    private static final Color INK = new Color(25, 42, 64);
    private static final Color MUTED = new Color(83, 101, 123);
    private static final Color GRID = new Color(221, 228, 238);
    private static final Color[] COLORS = {
            new Color(24, 105, 188), new Color(211, 95, 35), new Color(23, 139, 113)
    };

    private PngCharts() { }

    static void writeAll(Path output, List<Benchmark.Summary> rows) throws IOException {
        singleChart(output.resolve("dp-runtime.png"), "Dynamic programming runtime",
                "Full DP tables; equal input lengths", "Length of each input string", rows,
                "dp", "random", List.of("LCS", "Edit Distance"));
        matchingChart(output.resolve("matching-runtime.png"), rows);
        singleChart(output.resolve("kasai-runtime.png"), "Kasai LCP runtime",
                "Suffix array supplied; construction excluded", "String length", rows,
                "lcp", "random", List.of("Kasai"));
    }

    private static void singleChart(Path path, String title, String subtitle, String xLabel,
                                    List<Benchmark.Summary> rows, String family, String scenario,
                                    List<String> algorithms) throws IOException {
        BufferedImage image = canvas(1300, 860);
        Graphics2D graphics = graphics(image);
        heading(graphics, title, subtitle, 1300);
        plot(graphics, 110, 175, 1115, 485, xLabel, rows, family, scenario, algorithms, 0);
        legend(graphics, 110, 775, algorithms);
        footer(graphics, 1300, 830);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void matchingChart(Path path, List<Benchmark.Summary> rows) throws IOException {
        BufferedImage image = canvas(1680, 900);
        Graphics2D graphics = graphics(image);
        heading(graphics, "String matching runtime", "Same text and pattern for all three algorithms; pattern length 32", 1680);
        List<String> algorithms = List.of("Naive", "KMP", "Rabin-Karp");
        double sharedMaximum = rows.stream().filter(row -> row.family().equals("matching"))
                .mapToDouble(row -> row.maxNanos() / 1_000_000.0).max().orElse(1);
        graphics.setColor(INK);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 23));
        centered(graphics, "Pattern present", 448, 154);
        centered(graphics, "Pattern absent", 1253, 154);
        plot(graphics, 110, 195, 660, 480, "Text length", rows, "matching", "present", algorithms, sharedMaximum);
        plot(graphics, 915, 195, 660, 480, "Text length", rows, "matching", "absent", algorithms, sharedMaximum);
        legend(graphics, 110, 805, algorithms);
        footer(graphics, 1680, 870);
        graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    private static void heading(Graphics2D graphics, String title, String subtitle, int width) {
        graphics.setFont(new Font("SansSerif", Font.BOLD, 34));
        graphics.setColor(INK);
        centered(graphics, title, width / 2, 62);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 21));
        graphics.setColor(MUTED);
        centered(graphics, subtitle, width / 2, 103);
    }

    private static void plot(Graphics2D graphics, int left, int top, int width, int height,
                             String xLabel, List<Benchmark.Summary> allRows, String family,
                             String scenario, List<String> algorithms, double maximumOverride) {
        List<Benchmark.Summary> rows = allRows.stream()
                .filter(row -> row.family().equals(family) && row.scenario().equals(scenario))
                .sorted(Comparator.comparingInt(Benchmark.Summary::n)).toList();
        int[] sizes = rows.stream().mapToInt(Benchmark.Summary::n).distinct().sorted().toArray();
        double maximum = maximumOverride > 0 ? maximumOverride : rows.stream()
                .mapToDouble(row -> row.maxNanos() / 1_000_000.0).max().orElse(1);
        double yMax = niceMaximum(maximum * 1.12);
        int minN = sizes[0];
        int maxN = sizes[sizes.length - 1];
        int innerLeft = left + 18;
        int innerWidth = width - 36;
        graphics.setColor(Color.WHITE);
        graphics.fillRect(left, top, width, height);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 17));
        for (int tick = 0; tick <= 5; tick++) {
            int y = top + height - tick * height / 5;
            graphics.setColor(GRID);
            graphics.setStroke(new BasicStroke(1));
            graphics.drawLine(left, y, left + width, y);
            graphics.setColor(MUTED);
            String label = formatTime(yMax * tick / 5.0, yMax);
            graphics.drawString(label, left - 13 - graphics.getFontMetrics().stringWidth(label), y + 6);
        }
        for (int n : sizes) {
            int x = innerLeft + (int) Math.round((n - minN) * innerWidth / (double) (maxN - minN));
            graphics.setColor(GRID);
            graphics.drawLine(x, top, x, top + height);
            graphics.setColor(MUTED);
            centered(graphics, String.format(Locale.ROOT, "%,d", n), x, top + height + 31);
        }
        graphics.setColor(MUTED);
        graphics.setStroke(new BasicStroke(1.5f));
        graphics.drawLine(left, top, left, top + height);
        graphics.drawLine(left, top + height, left + width, top + height);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 20));
        graphics.drawString("Time (ms)", left, top - 22);
        centered(graphics, xLabel, left + width / 2, top + height + 63);
        for (int series = 0; series < algorithms.size(); series++) {
            String algorithm = algorithms.get(series);
            List<Benchmark.Summary> points = rows.stream().filter(row -> row.algorithm().equals(algorithm)).toList();
            graphics.setColor(COLORS[series]);
            int lastX = 0;
            int lastY = 0;
            boolean first = true;
            for (Benchmark.Summary row : points) {
                int x = innerLeft + (int) Math.round((row.n() - minN) * innerWidth / (double) (maxN - minN));
                int y = toY(row.meanNanos() / 1_000_000.0, yMax, top, height);
                graphics.setStroke(new BasicStroke(3));
                if (!first) {
                    graphics.drawLine(lastX, lastY, x, y);
                }
                int low = toY(row.minNanos() / 1_000_000.0, yMax, top, height);
                int high = toY(row.maxNanos() / 1_000_000.0, yMax, top, height);
                graphics.setStroke(new BasicStroke(1.6f));
                graphics.drawLine(x, high, x, low);
                graphics.drawLine(x - 5, high, x + 5, high);
                graphics.drawLine(x - 5, low, x + 5, low);
                graphics.fillOval(x - 6, y - 6, 12, 12);
                lastX = x;
                lastY = y;
                first = false;
            }
        }
    }

    private static double niceMaximum(double value) {
        if (!(value > 0)) {
            return 1;
        }
        double magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        return Math.ceil(value / magnitude * 2) * magnitude / 2;
    }

    private static int toY(double value, double maximum, int top, int height) {
        return top + height - (int) Math.round(value / maximum * height);
    }

    private static String formatTime(double value, double maximum) {
        int precision = Math.max(0, 2 - (int) Math.floor(Math.log10(maximum)));
        precision = Math.min(precision, 5);
        return String.format(Locale.ROOT, "% ." + precision + "f", value).trim();
    }

    private static void legend(Graphics2D graphics, int left, int baseline, List<String> algorithms) {
        graphics.setFont(new Font("SansSerif", Font.BOLD, 20));
        int x = left;
        for (int i = 0; i < algorithms.size(); i++) {
            graphics.setColor(COLORS[i]);
            graphics.setStroke(new BasicStroke(3));
            graphics.drawLine(x, baseline - 7, x + 34, baseline - 7);
            graphics.fillOval(x + 12, baseline - 12, 10, 10);
            graphics.setColor(INK);
            graphics.drawString(algorithms.get(i), x + 46, baseline);
            x += 46 + graphics.getFontMetrics().stringWidth(algorithms.get(i)) + 65;
        }
    }

    private static void footer(Graphics2D graphics, int width, int baseline) {
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 17));
        graphics.setColor(MUTED);
        centered(graphics, "Mean of 5 measured runs; bars show min-max. 10 warmups per case. Seed 2522; alphabet abcd.", width / 2, baseline);
    }

    private static void centered(Graphics2D graphics, String text, int x, int baseline) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, x - metrics.stringWidth(text) / 2, baseline);
    }
}
