# Thread Scaling in Matrix Multiplication

Big Data Assignment 3 — Ivan Giacomo Colella, ULPGC 2026/27.

Equivalent Java sequential and parallel i-k-j implementations using double arrays and seeded inputs. Sizes: 128, 256, 512, 1024; parallel workers: 1, 2, 4, 8. Each configuration uses 3 JVM forks, 20 warm-ups and 10 measurements per fork.

## Run (PowerShell, Java 21 and Python 3.13)

```powershell
python -m pip install -r requirements.txt
New-Item -ItemType Directory -Force out
javac -d out src/*.java
java -cp out CorrectnessTests
python scripts/run_suite.py results/final_v2
python scripts/analyze_results.py results/final_v2 results/analysis_final_v2
```

Use a new output directory for each benchmark run. To regenerate the existing analysis:

```powershell
python scripts/analyze_results.py results/final_v1 results/analysis_final_v1
```

## Materials

- `src/`: Java implementations and correctness checks.
- `scripts/`: measurement, independent NumPy validation and analysis tools.
- `results/final_v1/`: 600 raw measurements, environment, plan and resource logs.
- `results/analysis_final_v1/`: aggregated CSV tables and figures.
- `report/Assignment03_Colella_Report.docx`: editable report.

At n=1024, eight workers reduce median runtime from 760.943 to 229.886 ms (3.31x speedup). See the report for variability, timing boundaries and the unusual n=512 baseline behaviour. Analysis uses the median of three per-fork medians; memory is sampled whole-process peak RSS.
