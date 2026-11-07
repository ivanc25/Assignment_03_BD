public class CorrectnessTests {

    private static final double ABS_TOLERANCE = 1e-10;
    private static final double REL_TOLERANCE = 1e-10;

    public static void main(String[] args) throws Exception {
        System.out.println(
                "Assignment 3 - Correctness checks");

        testKnownProduct();
        testIdentityMatrix();
        testZeroMatrix();
        testOddSize();

        testParallelMultiplication();
        testRandomMatrices();

        System.out.println("All correctness tests passed.");
    }

    private static void testKnownProduct() {
        double[] a = {
                1, 2,
                3, 4
        };

        double[] b = {
                5, 6,
                7, 8
        };

        double[] expected = {
                19, 22,
                43, 50
        };

        double[] actual =
                MatrixMultiplication.multiplySequential(a, b, 2);

        assertMatrixEquals(expected, actual, 2, "Known product");
    }

    private static void testIdentityMatrix() {
        double[] a = {
                1.5, -2,
                3, 0.25
        };

        double[] identity = {
                1, 0,
                0, 1
        };

        double[] actual =
                MatrixMultiplication.multiplySequential(a, identity, 2);

        assertMatrixEquals(a, actual, 2, "Identity matrix");
    }

    private static void testZeroMatrix() {
        double[] a = {
                1, 2,
                3, 4
        };

        double[] zero = new double[4];

        double[] actual =
                MatrixMultiplication.multiplySequential(a, zero, 2);

        assertMatrixEquals(zero, actual, 2, "Zero matrix");
    }

    private static void testOddSize() {
        double[] a = {
                1, 2, 3,
                4, 5, 6,
                7, 8, 9
        };

        double[] b = {
                9, 8, 7,
                6, 5, 4,
                3, 2, 1
        };

        double[] expected = {
                30, 24, 18,
                84, 69, 54,
                138, 114, 90
        };

        double[] actual =
                MatrixMultiplication.multiplySequential(a, b, 3);

        assertMatrixEquals(expected, actual, 3, "Odd matrix size");
    }

    private static void assertMatrixEquals(
            double[] expected,
            double[] actual,
            int n,
            String testName) {

        if (expected.length != actual.length) {
            throw new AssertionError(
                    testName + ": matrix lengths differ.");
        }

        for (int index = 0; index < expected.length; index++) {
            double reference = expected[index];
            double value = actual[index];

            double tolerance = ABS_TOLERANCE
                    + REL_TOLERANCE * Math.abs(reference);

            if (!Double.isFinite(value)
                    || Math.abs(reference - value) > tolerance) {

                int row = index / n;
                int column = index % n;

                throw new AssertionError(
                        testName + " failed at (" + row + ", "
                                + column + "): expected " + reference
                                + ", obtained " + value);
            }
        }

        System.out.println("PASS - " + testName);
    }

    private static void testParallelMultiplication() throws Exception {
        double[] a = {
                1, 2, 3,
                4, 5, 6,
                7, 8, 9
        };

        double[] b = {
                9, 8, 7,
                6, 5, 4,
                3, 2, 1
        };

        double[] expected = {
                30, 24, 18,
                84, 69, 54,
                138, 114, 90
        };

        double[] identity = {
                1, 0, 0,
                0, 1, 0,
                0, 0, 1
        };

        double[] zero = new double[9];

        int[] threadCounts = {1, 2, 4, 8};

        for (int threads : threadCounts) {
            try (ParallelMatrixMultiplication parallel =
                         new ParallelMatrixMultiplication(threads)) {

                assertMatrixEquals(
                        expected,
                        parallel.multiply(a, b, 3),
                        3,
                        "Parallel known product, threads=" + threads);

                assertMatrixEquals(
                        a,
                        parallel.multiply(a, identity, 3),
                        3,
                        "Parallel identity, threads=" + threads);

                assertMatrixEquals(
                        zero,
                        parallel.multiply(a, zero, 3),
                        3,
                        "Parallel zero, threads=" + threads);

                // Reuse the same pool to detect stale result accumulation.
                assertMatrixEquals(
                        expected,
                        parallel.multiply(a, b, 3),
                        3,
                        "Parallel repeated call, threads=" + threads);
            }
        }
    }

    private static double[] referenceMultiply(
            double[] a, double[] b, int n) {

        double[] result = new double[a.length];

        // Compute each output element as an independent dot product.
        // This i-j-k implementation is used only for small correctness tests.
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                double sum = 0.0;

                for (int k = 0; k < n; k++) {
                    sum += a[row * n + k] * b[k * n + column];
                }

                result[row * n + column] = sum;
            }
        }

        return result;
    }

    private static void testRandomMatrices() throws Exception {
        int[] sizes = {1, 2, 3, 7, 16, 31};
        int[] threadCounts = {1, 2, 4, 8};

        long seedA = 2026L;
        long seedB = 2027L;

        for (int n : sizes) {
            double[] a = MatrixInputs.randomMatrix(n, seedA);
            double[] b = MatrixInputs.randomMatrix(n, seedB);

            double[] originalA = a.clone();
            double[] originalB = b.clone();

            // Verify that the same seed reproduces the same input.
            if (!java.util.Arrays.equals(
                    a, MatrixInputs.randomMatrix(n, seedA))
                    || !java.util.Arrays.equals(
                    b, MatrixInputs.randomMatrix(n, seedB))) {

                throw new AssertionError(
                        "Input reproducibility failed for n=" + n);
            }

            double[] expected = referenceMultiply(a, b, n);

            assertMatrixEquals(
                    expected,
                    MatrixMultiplication.multiplySequential(a, b, n),
                    n,
                    "Random sequential, n=" + n);

            for (int threads : threadCounts) {
                try (ParallelMatrixMultiplication parallel =
                             new ParallelMatrixMultiplication(threads)) {

                    assertMatrixEquals(
                            expected,
                            parallel.multiply(a, b, n),
                            n,
                            "Random parallel, n=" + n
                                    + ", threads=" + threads);
                }
            }

            // Multiplication must leave both input matrices unchanged.
            if (!java.util.Arrays.equals(a, originalA)
                    || !java.util.Arrays.equals(b, originalB)) {

                throw new AssertionError(
                        "Input matrices were modified for n=" + n);
            }

            System.out.println(
                    "PASS - Input reproducibility and preservation, n=" + n);
        }
    }
}