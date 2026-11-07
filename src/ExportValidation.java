import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ExportValidation {

    public static void main(String[] args) throws Exception {
        Path root = Path.of("results", "numpy_validation");

        if (Files.exists(root)) {
            throw new IllegalArgumentException(
                    "Validation directory already exists: " + root);
        }

        Files.createDirectories(root);

        int[] sizes = {31, 64};
        int[] threadCounts = {1, 2, 4, 8};

        for (int n : sizes) {
            Path directory = root.resolve("n" + n);
            Files.createDirectory(directory);

            double[] a = MatrixInputs.randomMatrix(n, 2026L);
            double[] b = MatrixInputs.randomMatrix(n, 2027L);

            writeMatrix(directory.resolve("a.csv"), a, n);
            writeMatrix(directory.resolve("b.csv"), b, n);

            double[] sequential =
                    MatrixMultiplication.multiplySequential(a, b, n);

            writeMatrix(
                    directory.resolve("sequential.csv"),
                    sequential,
                    n);

            for (int threads : threadCounts) {
                try (ParallelMatrixMultiplication parallel =
                             new ParallelMatrixMultiplication(threads)) {

                    writeMatrix(
                            directory.resolve(
                                    "parallel_p" + threads + ".csv"),
                            parallel.multiply(a, b, n),
                            n);
                }
            }

            System.out.println("Exported validation matrices: n=" + n);
        }

        System.out.println("Saved: " + root.toAbsolutePath());
    }

    private static void writeMatrix(
            Path path, double[] matrix, int n) throws Exception {

        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {

            for (int row = 0; row < n; row++) {
                for (int column = 0; column < n; column++) {
                    if (column > 0) {
                        writer.write(",");
                    }

                    // Preserve the double value through text conversion.
                    writer.write(Double.toString(
                            matrix[row * n + column]));
                }

                writer.newLine();
            }
        }
    }
}