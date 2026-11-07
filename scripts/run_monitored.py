import argparse
import csv
import json
import subprocess
import time
from pathlib import Path

import psutil


ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(
        description="Run and monitor one Java benchmark configuration."
    )
    parser.add_argument("method", choices=["sequential", "parallel"])
    parser.add_argument("n", type=int)
    parser.add_argument("threads", type=int)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--warmups", type=int, default=20)
    parser.add_argument("--repetitions", type=int, default=10)
    parser.add_argument("--timeout", type=float, default=120.0)
    parser.add_argument("--memory-mib", type=float, default=1024.0)
    args = parser.parse_args()

    if min(
        args.n, args.threads, args.warmups, args.repetitions,
        args.timeout, args.memory_mib
    ) <= 0:
        parser.error("All numeric parameters must be positive.")

    if args.method == "sequential" and args.threads != 1:
        parser.error("Sequential execution requires threads=1.")

    output_dir = args.output_dir
    if not output_dir.is_absolute():
        output_dir = ROOT / output_dir
    output_dir = output_dir.resolve()

    # Refuse to overwrite previous experiments.
    output_dir.mkdir(parents=True, exist_ok=False)

    command = [
        "java",
        "-Xms64m",
        "-Xmx512m",
        "-cp", str(ROOT / "out"),
        "Main",
        args.method,
        str(args.n),
        str(args.threads),
        str(args.warmups),
        str(args.repetitions),
        str(output_dir / "measurements.csv"),
    ]

    sample_interval = 0.01
    memory_limit_bytes = args.memory_mib * 1024 * 1024
    samples = []
    process = None
    status = "starting"
    error = None
    return_code = None
    started = time.perf_counter()

    print(
        f"Running: {args.method}, n={args.n}, "
        f"threads={args.threads}",
        flush=True,
    )

    try:
        # Redirect Java output to a file to avoid filling a pipe buffer.
        with (output_dir / "java.log").open(
            "w", encoding="utf-8"
        ) as log:
            process = subprocess.Popen(
                command,
                cwd=ROOT,
                stdout=log,
                stderr=subprocess.STDOUT,
            )

            monitored = psutil.Process(process.pid)
            status = "running"

            while process.poll() is None:
                elapsed = time.perf_counter() - started

                try:
                    rss = monitored.memory_info().rss
                    samples.append((elapsed, rss))
                except psutil.NoSuchProcess:
                    break

                if rss > memory_limit_bytes:
                    status = "memory_limit"
                    process.kill()
                    break

                if elapsed >= args.timeout:
                    status = "timeout"
                    process.kill()
                    break

                time.sleep(sample_interval)

            return_code = process.wait()

            if status == "running":
                status = "ok" if return_code == 0 else "java_error"

    except Exception as exc:
        status = "monitor_error"
        error = f"{type(exc).__name__}: {exc}"

    finally:
        # Ensure that a monitoring failure cannot leave Java running.
        if process is not None:
            if process.poll() is None:
                process.kill()
            return_code = process.wait()

        wall_seconds = time.perf_counter() - started

        with (output_dir / "memory_samples.csv").open(
            "w", newline="", encoding="utf-8"
        ) as file:
            writer = csv.writer(file)
            writer.writerow(["elapsed_s", "rss_bytes"])
            writer.writerows(samples)

        peak_rss = max(
            (rss for _, rss in samples),
            default=None,
        )

        summary = {
            "method": args.method,
            "n": args.n,
            "dtype": "double",
            "threads": args.threads,
            "warmups": args.warmups,
            "repetitions": args.repetitions,
            "seed_a": 2026,
            "seed_b": 2027,
            "command": command,
            "status": status,
            "return_code": return_code,
            "error": error,
            "wall_seconds": wall_seconds,
            "sample_interval_requested_s": sample_interval,
            "memory_sample_count": len(samples),
            "peak_sampled_rss_bytes": peak_rss,
            "peak_sampled_rss_mib": (
                peak_rss / (1024 * 1024)
                if peak_rss is not None else None
            ),
            "memory_scope": "entire Java process lifetime",
            "timeout_seconds": args.timeout,
            "rss_budget_mib": args.memory_mib,
            "java_heap_max_mib": 512,
        }

        (output_dir / "resources.json").write_text(
            json.dumps(summary, indent=2),
            encoding="utf-8",
        )

    print(f"Status: {status}")
    print(f"Process wall time: {wall_seconds:.3f} s")

    if peak_rss is not None:
        print(f"Peak sampled RSS: {peak_rss / (1024 * 1024):.2f} MiB")

    if error:
        print(f"Error: {error}")

    print(f"Results: {output_dir}")

    return 0 if status == "ok" else 1


if __name__ == "__main__":
    raise SystemExit(main())