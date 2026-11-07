import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class BenchmarkRunner {

    private static final long SEED_A = 2026L;
    private static final long SEED_B = 2027L;

    private static final double ABS_TOLERANCE = 1e-10;
    private static final double REL_TOLERANCE = 1e-10;

    private BenchmarkRunner() {
        // Prevent instantiation of this utility class.
    }

    public static void run(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                    "Usage: Main <sequential|parallel> <n> <threads> "
                            + "<warmups> <repetitions> <output.csv>");
        }

        String method = args[0];
        int n = Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);
        int warmups = Integer.parseInt(args[3]);
        int repetitions = Integer.parseInt(args[4]);
        Path output = Path.of(args[5]).toAbsolutePath();

        validateConfiguration(method, n, threads, warmups, repetitions);

        if (Files.exists(output)) {
            throw new IllegalArgumentException(
                    "Output already exists: " + output);
        }

        Files.createDirectories(output.getParent());

        double[] a = MatrixInputs.randomMatrix(n, SEED_A);
        double[] b = MatrixInputs.randomMatrix(n, SEED_B);

        // Prepare the expected result outside all timed regions.
        // Independent small tests must also be run before benchmarking.
        double[] expected =
                MatrixMultiplication.multiplySequential(a, b, n);

        System.out.printf(
                Locale.ROOT,
                "Method=%s, n=%d, threads=%d, warmups=%d, repetitions=%d%n",
                method, n, threads, warmups, repetitions);

        // A separate worker pool is used only for the parallel method.
        if (method.equals("parallel")) {
            try (ParallelMatrixMultiplication parallel =
                         new ParallelMatrixMultiplication(threads)) {

                execute(
                        method, n, threads, warmups, repetitions,
                        output, a, b, expected, parallel);
            }
        } else {
            execute(
                    method, n, threads, warmups, repetitions,
                    output, a, b, expected, null);
        }

        System.out.println("Saved: " + output);
    }

    private static void execute(
            String method,
            int n,
            int threads,
            int warmups,
            int repetitions,
            Path output,
            double[] a,
            double[] b,
            double[] expected,
            ParallelMatrixMultiplication parallel) throws Exception {

        for (int run = 1; run <= warmups; run++) {
            double[] result = multiply(a, b, n, parallel);
            checkResult(expected, result);
            System.out.println("Warm-up " + run + "/" + warmups);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                output,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {

            writer.write(
                    "method,n,dtype,threads,repetition,warmups,"
                            + "seed_a,seed_b,time_ms,verified");
            writer.newLine();
            writer.flush();

            for (int repetition = 1;
                 repetition <= repetitions;
                 repetition++) {

                long start = System.nanoTime();

                double[] result = multiply(a, b, n, parallel);

                long elapsedNs = System.nanoTime() - start;

                // Validation and console/file output are not timed.
                checkResult(expected, result);

                double elapsedMs = elapsedNs / 1_000_000.0;

                writer.write(String.format(
                        Locale.ROOT,
                        "%s,%d,double,%d,%d,%d,%d,%d,%.6f,true",
                        method, n, threads, repetition, warmups,
                        SEED_A, SEED_B, elapsedMs));

                writer.newLine();
                writer.flush();

                System.out.printf(
                        Locale.ROOT,
                        "Run %d/%d: %.3f ms%n",
                        repetition, repetitions, elapsedMs);
            }
        }
    }

    private static double[] multiply(
            double[] a,
            double[] b,
            int n,
            ParallelMatrixMultiplication parallel) throws Exception {

        if (parallel == null) {
            return MatrixMultiplication.multiplySequential(a, b, n);
        }

        return parallel.multiply(a, b, n);
    }

    private static void checkResult(
            double[] expected, double[] actual) {

        if (expected.length != actual.length) {
            throw new AssertionError("Result length mismatch.");
        }

        for (int index = 0; index < expected.length; index++) {
            double tolerance = ABS_TOLERANCE
                    + REL_TOLERANCE * Math.abs(expected[index]);

            if (!Double.isFinite(actual[index])
                    || Math.abs(expected[index] - actual[index])
                    > tolerance) {

                throw new AssertionError(
                        "Incorrect result at index " + index
                                + ": expected " + expected[index]
                                + ", obtained " + actual[index]);
            }
        }
    }

    private static void validateConfiguration(
            String method,
            int n,
            int threads,
            int warmups,
            int repetitions) {

        if (!method.equals("sequential") && !method.equals("parallel")) {
            throw new IllegalArgumentException(
                    "Method must be sequential or parallel.");
        }

        if (n <= 0 || threads <= 0 || warmups < 1 || repetitions < 1) {
            throw new IllegalArgumentException(
                    "Size, threads, warmups and repetitions "
                            + "must be positive.");
        }

        if (method.equals("sequential") && threads != 1) {
            throw new IllegalArgumentException(
                    "The sequential baseline requires threads=1.");
        }
    }
}