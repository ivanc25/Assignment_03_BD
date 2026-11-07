import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ParallelMatrixMultiplication
        implements AutoCloseable {

    private final int threadCount;
    private final ExecutorService executor;

    public ParallelMatrixMultiplication(int threadCount) {
        if (threadCount <= 0) {
            throw new IllegalArgumentException(
                    "Thread count must be positive.");
        }

        this.threadCount = threadCount;
        this.executor = Executors.newFixedThreadPool(threadCount);
    }

    public double[] multiply(double[] a, double[] b, int n)
            throws InterruptedException, ExecutionException {

        validateInputs(a, b, n);

        // Each multiplication starts with a zero-filled result.
        double[] c = new double[a.length];

        List<Callable<Void>> tasks =
                new ArrayList<>(threadCount);

        for (int worker = 0; worker < threadCount; worker++) {
            // Integer boundaries also handle uneven row counts.
            final int startRow =
                    (int) ((long) worker * n / threadCount);

            final int endRow =
                    (int) ((long) (worker + 1) * n / threadCount);

            tasks.add(() -> {
                multiplyRows(a, b, c, n, startRow, endRow);
                return null;
            });
        }

        // Wait for every task and propagate any computation failure.
        List<Future<Void>> futures = executor.invokeAll(tasks);

        for (Future<Void> future : futures) {
            future.get();
        }

        return c;
    }

    private static void multiplyRows(
            double[] a,
            double[] b,
            double[] c,
            int n,
            int startRow,
            int endRow) {

        // Use the same i-k-j algorithm as the sequential baseline.
        for (int i = startRow; i < endRow; i++) {
            int rowOffset = i * n;

            for (int k = 0; k < n; k++) {
                double aik = a[rowOffset + k];
                int bRowOffset = k * n;

                for (int j = 0; j < n; j++) {
                    c[rowOffset + j] += aik * b[bRowOffset + j];
                }
            }
        }
    }

    private static void validateInputs(
            double[] a, double[] b, int n) {

        if (n <= 0) {
            throw new IllegalArgumentException(
                    "Matrix size must be positive.");
        }

        if (a == null || b == null) {
            throw new IllegalArgumentException(
                    "Input matrices must not be null.");
        }

        long expectedLength = (long) n * n;

        if (a.length != expectedLength || b.length != expectedLength) {
            throw new IllegalArgumentException(
                    "Each input must contain exactly n * n elements.");
        }
    }

    @Override
    public void close() {
        // Shut down the pool and wait for its threads to terminate.
        executor.close();
    }
}