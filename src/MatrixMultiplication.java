public final class MatrixMultiplication {

    private MatrixMultiplication() {
        // Prevent instantiation of this utility class.
    }

    public static double[] multiplySequential(
            double[] a, double[] b, int n) {

        validateInputs(a, b, n);

        // A new Java array is initialized to zero.
        double[] c = new double[a.length];

        // Compute C = A * B using the i-k-j loop order.
        for (int i = 0; i < n; i++) {
            int rowOffset = i * n;

            for (int k = 0; k < n; k++) {
                double aik = a[rowOffset + k];
                int bRowOffset = k * n;

                for (int j = 0; j < n; j++) {
                    c[rowOffset + j] += aik * b[bRowOffset + j];
                }
            }
        }

        return c;
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
}