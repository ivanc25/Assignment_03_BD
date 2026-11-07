"""Aggregate per-process medians; plot benchmark results (requires matplotlib)."""
import argparse, csv, json, statistics
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
p = argparse.ArgumentParser()
p.add_argument('input', type=Path)
p.add_argument('output', type=Path)
a = p.parse_args()
a.output.mkdir(parents=True, exist_ok=True)
groups = {}
rows = []
for f in sorted(a.input.glob('fork*/measurements.csv')):
    data = list(csv.DictReader(f.open()))
    r = json.loads(f.with_name('resources.json').read_text())
    assert r['status'] == 'ok' and r['return_code'] == 0, f
    assert len(data) == 10 and all(x['verified'].lower() == 'true' for x in data), f
    times = [float(x['time_ms']) for x in data]
    assert all(t > 0 for t in times), f
    key = (r['n'], r['method'], r['threads'])
    row = dict(n=key[0], method=key[1], threads=key[2], fork=f.parent.name.split('_')[0], median_ms=statistics.median(times), min_ms=min(times), max_ms=max(times), peak_rss_mib=r['peak_sampled_rss_mib'])
    rows.append(row)
    groups.setdefault(key, []).append(row)
assert len(rows) == 60 and len(groups) == 20
assert all(len(v) == 3 for v in groups.values())
def save(name, data):
    with (a.output / name).open('w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=data[0].keys()); w.writeheader(); w.writerows(data)
save('fork_summary.csv', rows)
summary = []
for (n, method, threads), values in sorted(groups.items()):
    times = [v['median_ms'] for v in values]
    baseline = statistics.median(v['median_ms'] for (nn, mm, pp), vv in groups.items() if nn == n and mm == 'sequential' for v in vv)
    median = statistics.median(times)
    summary.append(dict(n=n, method=method, threads=threads, median_ms=median, fork_min_ms=min(times), fork_max_ms=max(times), fork_cv_percent=100*statistics.stdev(times)/statistics.mean(times), speedup=baseline/median, efficiency=baseline/median/threads, median_peak_rss_mib=statistics.median(v['peak_rss_mib'] for v in values)))
save('summary.csv', summary)
for metric, label in [('median_ms','Runtime (ms)'), ('speedup','Speedup vs sequential'), ('efficiency','Efficiency'), ('median_peak_rss_mib','Median process peak RSS (MiB)')]:
    fig, ax = plt.subplots(figsize=(7,4.5))
    for n in sorted({r['n'] for r in summary}):
        v = sorted([r for r in summary if r['n']==n and r['method']=='parallel'], key=lambda r:r['threads'])
        ax.plot([r['threads'] for r in v], [r[metric] for r in v], 'o-', label=f'n={n}')
    ax.set(xlabel='Threads', ylabel=label, xticks=[1,2,4,8]); ax.grid(alpha=.25); ax.legend(); fig.tight_layout(); fig.savefig(a.output / (metric+'.png'), dpi=180); plt.close(fig)
print('Validated 60 processes, 600 verified measurements, 20 configurations.')
for r in summary: print(r)
