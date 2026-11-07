import argparse
import json
import platform
import random
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import psutil


ROOT = Path(__file__).resolve().parent.parent


def save_json(path, data):
    path.write_text(
        json.dumps(data, indent=2),
        encoding="utf-8",
    )


def capture_command(command):
    result = subprocess.run(
        command,
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )

    return {
        "command": command,
        "return_code": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Run the controlled matrix multiplication study."
    )
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()

    output_dir = args.output_dir
    if not output_dir.is_absolute():
        output_dir = ROOT / output_dir
    output_dir = output_dir.resolve()

    # Each study must have its own directory.
    output_dir.mkdir(parents=True, exist_ok=False)

    sizes = [128, 256, 512, 1024]
    thread_counts = [1, 2, 4, 8]
    forks = 3
    warmups = 20
    repetitions = 10
    order_seed = 20261107
    pause_seconds = 2.0

    rng = random.Random(order_seed)
    jobs = []

    # Each block contains every configuration once.
    for fork in range(1, forks + 1):
        block = []

        for n in sizes:
            block.append({
                "fork": fork,
                "method": "sequential",
                "n": n,
                "threads": 1,
            })

            for threads in thread_counts:
                block.append({
                    "fork": fork,
                    "method": "parallel",
                    "n": n,
                    "threads": threads,
                })

        rng.shuffle(block)
        jobs.extend(block)

    for order, job in enumerate(jobs, start=1):
        job["order"] = order
        job["directory"] = (
            f"fork{job['fork']:02d}_"
            f"{job['method']}_n{job['n']}_p{job['threads']}"
        )

    plan = {
        "created_utc": datetime.now(timezone.utc).isoformat(),
        "sizes": sizes,
        "parallel_thread_counts": thread_counts,
        "forks": forks,
        "warmups_per_process": warmups,
        "measurements_per_process": repetitions,
        "order_seed": order_seed,
        "pause_between_processes_s": pause_seconds,
        "timeout_per_process_s": 120,
        "rss_budget_mib": 1024,
        "java_heap_max_mib": 512,
        "jobs": jobs,
    }

    save_json(output_dir / "plan.json", plan)

    environment = {
        "python_version": sys.version,
        "python_executable": sys.executable,
        "platform": platform.platform(),
        "physical_cores": psutil.cpu_count(logical=False),
        "logical_processors": psutil.cpu_count(logical=True),
        "ram_bytes": psutil.virtual_memory().total,
        "psutil_version": psutil.__version__,
        "java": capture_command(["java", "-version"]),
        "javac": capture_command(["javac", "-version"]),
        "git_commit": capture_command(
            ["git", "rev-parse", "HEAD"]
        ),
        "git_status": capture_command(
            ["git", "status", "--short"]
        ),
        "python_dependencies": capture_command(
            [sys.executable, "-m", "pip", "freeze"]
        ),
    }

    save_json(output_dir / "environment.json", environment)

    progress = []
    started = time.perf_counter()

    for index, job in enumerate(jobs, start=1):
        print(
            f"\n[{index}/{len(jobs)}] "
            f"fork={job['fork']}, "
            f"method={job['method']}, "
            f"n={job['n']}, threads={job['threads']}",
            flush=True,
        )

        run_directory = output_dir / job["directory"]

        command = [
            sys.executable,
            str(ROOT / "scripts" / "run_monitored.py"),
            job["method"],
            str(job["n"]),
            str(job["threads"]),
            str(run_directory),
            "--warmups", str(warmups),
            "--repetitions", str(repetitions),
            "--timeout", "120",
            "--memory-mib", "1024",
        ]

        completed = subprocess.run(
            command,
            cwd=ROOT,
            check=False,
        )

        resource_file = run_directory / "resources.json"
        status = "missing_resources"

        if resource_file.exists():
            resource_data = json.loads(
                resource_file.read_text(encoding="utf-8")
            )
            status = resource_data["status"]

        progress.append({
            **job,
            "launcher_return_code": completed.returncode,
            "status": status,
        })

        # Save progress after each process.
        save_json(output_dir / "progress.json", progress)

        if status != "ok" or completed.returncode != 0:
            print(
                f"\nStudy stopped: status={status}. "
                f"Inspect {run_directory}",
                flush=True,
            )
            return 1

        if index < len(jobs):
            time.sleep(pause_seconds)

    elapsed = time.perf_counter() - started

    print(f"\nStudy completed: {len(jobs)} successful processes.")
    print(f"Total duration: {elapsed / 60:.2f} minutes.")
    print(f"Results: {output_dir}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())