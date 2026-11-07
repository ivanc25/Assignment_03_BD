import java.util.Random;

public final class MatrixInputs {

    private MatrixInputs() {
        // Prevent instantiation of this utility class.
    }

    public static double[] randomMatrix(int n, long seed) {
        if (n <= 0) {
            throw new IllegalArgumentException(
                    "Matrix size must be positive.");
        }

        long elementCount = (long) n * n;

        if (elementCount > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Matrix exceeds the supported array length.");
        }

        Random random = new Random(seed);
        double[] matrix = new double[(int) elementCount];

        // Generate double values in the interval [-1, 1).
        for (int index = 0; index < matrix.length; index++) {
            matrix[index] = 2.0 * random.nextDouble() - 1.0;
        }

        return matrix;
    }
}