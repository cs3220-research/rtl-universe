#!/usr/bin/env python3
"""Generate the model x task coverage matrix from jobs/."""
import json, glob, os, sys, datetime as dt
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JOBS = os.path.join(ROOT, "jobs")

TASKS = [
    "coralnpu-e2e", "coralnpu-full",
    "ibex-e2e", "ibex-full",
    "nvdla-e2e", "nvdla-full",
    "openpiton-e2e", "openpiton-full",
    "pulp-common-cells-full",
    "secworks-aes-full",
    "veer-el2-block",
]

# models.md order
MODELS = [
    "Opus4.7", "GPT5.5", "Gemini3.1Pro", "Sonnet4.6",
    "QwenMax", "DeepSeek", "Kimi", "GLM5.1", "Qwen27B",
]

# Map job-name prefix → model label. Order matters: longest match first.
MODEL_FROM_JOB = [
    ("codex-gpt55-xhigh-",       "GPT5.5"),
    ("openrouter-gemini-3.1-pro-","Gemini3.1Pro"),
    ("openrouter-qwen3.6-max-",  "QwenMax"),
    ("openrouter-qwen3.6-27b-",  "Qwen27B"),
    ("openrouter-glm-5.1-",      "GLM5.1"),
    ("dns-block-opus-",          "Opus4.7"),
    ("dns-block-kimi-",          "Kimi"),
    ("sandboxed-deepseek-",      "DeepSeek"),
    ("sandboxed-kimi-",          "Kimi"),
    ("netiso-",                  None),  # all failed - skip
    ("opus-",                    "Opus4.7"),
    ("sonnet-",                  "Sonnet4.6"),
    ("haiku-",                   None),  # not in models.md
    ("opencode-deepseek-v4-",    "DeepSeek"),
    ("opencode-kimi-k2.6-",      "Kimi"),
]

def parse_job_dir(jobname: str):
    """Return (model, task, sandboxed) or None."""
    sandboxed = jobname.startswith((
        "sandboxed-", "dns-block-", "codex-gpt55-xhigh-", "openrouter-"
    ))
    s = jobname
    model = None
    for prefix, m in MODEL_FROM_JOB:
        if s.startswith(prefix):
            if m is None:
                return None
            model = m
            s = s[len(prefix):]
            break
    if not model:
        return None
    task = None
    for t in sorted(TASKS, key=len, reverse=True):
        if s == t or s.startswith(t):
            task = t
            break
    if not task:
        return None
    return (model, task, sandboxed)

def reward_from_result(rj_path: str):
    try:
        d = json.load(open(rj_path))
    except Exception:
        return None, None, None
    evals = d.get("stats", {}).get("evals", {})
    reward = None
    for ev in evals.values():
        rs = list(ev.get("reward_stats", {}).get("reward", {}).keys())
        if rs:
            reward = float(rs[0])
            break
    started = d.get("started_at"); finished = d.get("finished_at")
    runtime_min = None
    if started and finished:
        try:
            runtime_min = (dt.datetime.fromisoformat(finished) -
                           dt.datetime.fromisoformat(started)).total_seconds() / 60
        except Exception:
            pass
    cost = d.get("stats", {}).get("cost_usd")
    return reward, runtime_min, cost

def cheat_attempts(job_dir, task):
    # Count blocked egress in agent logs (codex.txt or similar)
    n = 0
    for trial in glob.glob(os.path.join(job_dir, f"{task}__*")):
        for log in glob.glob(os.path.join(trial, "agent", "*.txt")):
            try:
                txt = open(log, errors="ignore").read()
                # Pattern: "Failed to connect to github" or curl exit_code 7/128
                n += txt.count("Failed to connect to github")
                n += txt.count("Failed to connect to raw.githubusercontent")
                n += txt.count("Failed to connect to gitlab")
            except Exception:
                pass
    return n

def in_progress(job_dir, task):
    """Job has a trial dir with trial.log but no reward.txt."""
    for trial in glob.glob(os.path.join(job_dir, f"{task}__*")):
        if os.path.isfile(os.path.join(trial, "trial.log")) and \
           not os.path.isfile(os.path.join(trial, "verifier", "reward.txt")) and \
           not os.path.isfile(os.path.join(trial, "exception.txt")):
            return True
    return False

def collect():
    """results[model][task] = list of (sandboxed, reward, runtime, cost, cheats, jobname, status)"""
    results = defaultdict(lambda: defaultdict(list))
    for jd in sorted(glob.glob(os.path.join(JOBS, "*"))):
        if not os.path.isdir(jd):
            continue
        jobname = os.path.basename(jd)
        parsed = parse_job_dir(jobname)
        if not parsed:
            continue
        model, task, sand = parsed
        rj = os.path.join(jd, "result.json")
        reward, runtime, cost = reward_from_result(rj) if os.path.isfile(rj) else (None, None, None)
        cheats = cheat_attempts(jd, task)
        status = "DONE" if reward is not None else (
            "RUN" if in_progress(jd, task) else "FAIL")
        results[model][task].append({
            "sandboxed": sand, "reward": reward, "runtime": runtime,
            "cost": cost, "cheats": cheats, "jobname": jobname, "status": status,
        })
    return results

def best_run(runs):
    """Pick the most authoritative run for a cell: sandboxed DONE > sandboxed RUN > unsand DONE > anything."""
    if not runs:
        return None
    sand_done = [r for r in runs if r["sandboxed"] and r["status"] == "DONE"]
    if sand_done:
        return max(sand_done, key=lambda r: r["reward"] or 0)
    sand_run = [r for r in runs if r["sandboxed"] and r["status"] == "RUN"]
    if sand_run: return sand_run[0]
    unsand_done = [r for r in runs if not r["sandboxed"] and r["status"] == "DONE"]
    if unsand_done: return max(unsand_done, key=lambda r: r["reward"] or 0)
    return runs[0]

def render_md(results):
    lines = []
    lines.append(f"# RTL-Universe — Coverage Matrix")
    lines.append(f"_Generated {dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n")
    lines.append("Legend: `0.95✓` sandboxed DONE · `0.95✗` unsandboxed DONE (suspect cheating) · `RUN` in progress · `·` not started\n")
    lines.append("## Matrix\n")
    # Header
    short = {t: t.replace("coralnpu","cor").replace("openpiton","opn").replace("pulp-common-cells","pulp")[:8] for t in TASKS}
    header = "| Model | " + " | ".join(short[t] for t in TASKS) + " |"
    sep = "|" + "---|" * (len(TASKS) + 1)
    lines.append(header); lines.append(sep)
    for m in MODELS:
        row = [m]
        for t in TASKS:
            best = best_run(results.get(m, {}).get(t, []))
            if best is None:
                row.append("·")
            elif best["status"] == "DONE":
                mark = "✓" if best["sandboxed"] else "✗"
                row.append(f"{best['reward']:.2f}{mark}")
            elif best["status"] == "RUN":
                row.append("RUN")
            else:
                row.append("FAIL")
        lines.append("| " + " | ".join(row) + " |")
    lines.append("")

    # Summary stats
    lines.append("## Coverage stats\n")
    lines.append("| Model | Legit | Need rerun (unsand) | In progress | Missing | Cost (legit only) |")
    lines.append("|---|---|---|---|---|---|")
    for m in MODELS:
        legit = unsand = inp = 0
        cost_total = 0.0
        for t in TASKS:
            best = best_run(results.get(m, {}).get(t, []))
            if not best: continue
            if best["status"] == "DONE":
                if best["sandboxed"]:
                    legit += 1
                    if best.get("cost"): cost_total += best["cost"]
                else:
                    unsand += 1
            elif best["status"] == "RUN":
                inp += 1
        miss = len(TASKS) - legit - unsand - inp
        lines.append(f"| {m} | {legit}/{len(TASKS)} | {unsand} | {inp} | {miss} | ${cost_total:.2f} |")

    # Sandbox validation
    lines.append("\n## Sandbox effectiveness\n")
    total_cheats = 0
    cheat_runs = []
    for m, tasks_d in results.items():
        for t, runs in tasks_d.items():
            for r in runs:
                if r["sandboxed"] and r["cheats"] > 0:
                    total_cheats += r["cheats"]
                    cheat_runs.append((m, t, r["cheats"]))
    lines.append(f"- Total egress attempts blocked across sandboxed runs: **{total_cheats}**")
    if cheat_runs:
        lines.append("- Per run:")
        for m, t, n in sorted(cheat_runs, key=lambda x: -x[2])[:10]:
            lines.append(f"  - {m} on {t}: {n} attempts blocked")

    # Per-task results
    lines.append("\n## Per-task results (legit runs)\n")
    for t in TASKS:
        lines.append(f"### {t}")
        rows = []
        for m in MODELS:
            best = best_run(results.get(m, {}).get(t, []))
            if best and best["status"] == "DONE" and best["sandboxed"]:
                rt = f"{best['runtime']:.0f} min" if best['runtime'] else "?"
                co = f"${best['cost']:.2f}" if best['cost'] else "?"
                rows.append((best['reward'], m, rt, co, best['jobname']))
        if not rows:
            lines.append("_no legit runs yet_\n")
            continue
        rows.sort(key=lambda r: -r[0])
        lines.append("| Model | Reward | Runtime | Cost | Job |")
        lines.append("|---|---|---|---|---|")
        for r, m, rt, co, jn in rows:
            lines.append(f"| {m} | {r:.3f} | {rt} | {co} | `{jn}` |")
        lines.append("")
    return "\n".join(lines)

if __name__ == "__main__":
    res = collect()
    md = render_md(res)
    out = os.path.join(ROOT, "reports", "MATRIX.md")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w").write(md)
    print(f"Wrote {out} ({len(md)} chars)")
    if "--print" in sys.argv:
        print(md)
