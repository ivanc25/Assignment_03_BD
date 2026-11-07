from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parent.parent
VALIDATION_DIR = ROOT / "results" / "numpy_validation"

ABS_TOLERANCE = 1e-10
REL_TOLERANCE = 1e-10


def main():
    checks = 0

    for n in (31, 64):
        directory = VALIDATION_DIR / f"n{n}"

        a = np.loadtxt(directory / "a.csv", delimiter=",")
        b = np.loadtxt(directory / "b.csv", delimiter=",")

        if a.shape != (n, n) or b.shape != (n, n):
            raise AssertionError(f"Invalid input shape for n={n}")

        if not np.isfinite(a).all() or not np.isfinite(b).all():
            raise AssertionError(f"Non-finite inputs for n={n}")

        # Use NumPy as the external correctness reference.
        expected = a @ b

        filenames = ["sequential.csv"] + [
            f"parallel_p{threads}.csv"
            for threads in (1, 2, 4, 8)
        ]

        for filename in filenames:
            actual = np.loadtxt(directory / filename, delimiter=",")

            if actual.shape != expected.shape:
                raise AssertionError(
                    f"Invalid output shape: n={n}, {filename}"
                )

            if not np.isfinite(actual).all():
                raise AssertionError(
                    f"Non-finite output: n={n}, {filename}"
                )

            np.testing.assert_allclose(
                actual,
                expected,
                rtol=REL_TOLERANCE,
                atol=ABS_TOLERANCE,
                equal_nan=False,
                err_msg=f"NumPy comparison failed: n={n}, {filename}",
            )

            max_error = float(np.max(np.abs(actual - expected)))

            print(
                f"PASS - n={n}, {filename}, "
                f"max_abs_error={max_error:.3e}"
            )
            checks += 1

    print(f"All {checks} NumPy comparisons passed.")
    print(f"NumPy version: {np.__version__}")


if __name__ == "__main__":
    main()