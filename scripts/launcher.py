#!/usr/bin/env python3
"""Smart launcher: drains the model x task matrix, capping concurrency.

Defines what counts as "covered" (sandboxed legit run exists), enumerates
all uncovered cells, and launches harbor runs in batches. Stateless —
re-derives the work list from `jobs/` state on every tick.

Run in background and tail /tmp/launcher.log:
    nohup python3 scripts/launcher.py > /tmp/launcher.log 2>&1 &

To pause: `touch /tmp/launcher.pause`
To stop:  `touch /tmp/launcher.stop`
"""
import json, os, subprocess, sys, time, glob, shlex, datetime as dt
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JOBS = ROOT / "jobs"
PAUSE_FILE = Path("/tmp/launcher.pause")
STOP_FILE = Path("/tmp/launcher.stop")
LOG_DIR = Path("/tmp/codex-runs")
LOG_DIR.mkdir(exist_ok=True)
CLAUDE_CREDS_DIR = Path("/tmp/harbor-claude-creds")

# Concurrency
MAX_TOTAL = 6      # max harbor processes at once
MAX_HEAVY = 1      # at most one HEAVY task running
# Agent timeout multiplier (task.toml sets 86400s = 24h; 0.42 → ~10h)
AGENT_TIMEOUT_MULTIPLIER = "0.42"

# Task weights (for resource scheduling)
HEAVY = {"coralnpu-e2e", "coralnpu-full", "nvdla-full"}
MEDIUM = {"nvdla-e2e", "openpiton-full", "veer-el2-block", "ibex-full", "openpiton-e2e"}
SMALL = {"secworks-aes-full", "ibex-e2e", "pulp-common-cells-full"}
TASKS_ORDERED = list(SMALL) + list(MEDIUM) + list(HEAVY)
TASKS = SMALL | MEDIUM | HEAVY

def env_or_die(name):
    v = os.environ.get(name)
    if not v:
        # Try .env
        env_file = ROOT / ".env"
        if env_file.exists():
            for line in env_file.read_text().splitlines():
                if line.startswith(name + "="):
                    return line.split("=",1)[1].strip()
        sys.exit(f"missing {name}")
    return v

# Model registry: (label, agent_class, model_name, extra_ae_list, extra_mounts_json_or_None)
MODELS = [
    # Codex (uses ChatGPT subscription via auth.json)
    ("gpt55", "agents.sandboxed:CodexSandboxed", "gpt-5.5",
     [("CODEX_AUTH_JSON_PATH", "/home/broyojo/.codex/auth.json")],
     None,
     [("reasoning_effort", "xhigh")]),  # --ak items
    # Claude Code
    ("opus", "agents.sandboxed:ClaudeCodeSandboxed", "opus",
     [("CLAUDE_CONFIG_DIR", "/home/builder/.claude"),
      ("CLAUDE_CODE_MAX_OUTPUT_TOKENS", "128000")],
     [str(CLAUDE_CREDS_DIR) + ":/home/builder/.claude"],
     []),
    ("sonnet", "agents.sandboxed:ClaudeCodeSandboxed", "sonnet",
     [("CLAUDE_CONFIG_DIR", "/home/builder/.claude"),
      ("CLAUDE_CODE_MAX_OUTPUT_TOKENS", "128000")],
     [str(CLAUDE_CREDS_DIR) + ":/home/builder/.claude"],
     []),
    # OpenCode via OpenRouter
    ("gemini-3.1-pro", "agents.sandboxed:OpenCodeSandboxed", "openrouter/google/gemini-3.1-pro-preview", "OPENROUTER", None, []),
    ("qwen3.6-max",     "agents.sandboxed:OpenCodeSandboxed", "openrouter/qwen/qwen3.6-max-preview",     "OPENROUTER", None, []),
    ("glm-5.1",         "agents.sandboxed:OpenCodeSandboxed", "openrouter/z-ai/glm-5.1",                 "OPENROUTER", None, []),
    ("qwen3.6-27b",     "agents.sandboxed:OpenCodeSandboxed", "openrouter/qwen/qwen3.6-27b",             "OPENROUTER", None, []),
    ("deepseek",        "agents.sandboxed:OpenCodeSandboxed", "openrouter/deepseek/deepseek-v4-pro",     "OPENROUTER", None, []),
    ("kimi",            "agents.sandboxed:OpenCodeSandboxed", "openrouter/moonshotai/kimi-k2.6",         "OPENROUTER", None, []),
]

# Map model label → "covered" job-name prefixes (to detect existing runs).
# Coverage means: a sandboxed run with a reward already exists.
COVERAGE_PREFIXES = {
    "gpt55":          ["codex-gpt55-xhigh-"],
    "opus":           ["dns-block-opus-", "openrouter-opus-", "sandboxed-opus-"],
    "sonnet":         ["sandboxed-sonnet-", "openrouter-sonnet-"],
    "gemini-3.1-pro": ["openrouter-gemini-3.1-pro-"],
    "qwen3.6-max":    ["openrouter-qwen3.6-max-"],
    "glm-5.1":        ["openrouter-glm-5.1-"],
    "qwen3.6-27b":    ["openrouter-qwen3.6-27b-"],
    "deepseek":       ["sandboxed-deepseek-", "openrouter-deepseek-"],
    "kimi":           ["sandboxed-kimi-", "dns-block-kimi-", "openrouter-kimi-"],
}

def log(msg):
    print(f"[{dt.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)

def is_done(job_dir, task):
    for trial in glob.glob(str(job_dir / f"{task}__*")):
        if os.path.isfile(os.path.join(trial, "verifier", "reward.txt")):
            return True
    rj = job_dir / "result.json"
    if rj.is_file():
        try:
            d = json.load(open(rj))
            for ev in d.get("stats",{}).get("evals",{}).values():
                if ev.get("reward_stats",{}).get("reward"):
                    return True
        except Exception:
            pass
    return False

def is_running(job_dir, task):
    for trial in glob.glob(str(job_dir / f"{task}__*")):
        if os.path.isdir(trial) and not os.path.isfile(os.path.join(trial, "verifier", "reward.txt")) \
                and not os.path.isfile(os.path.join(trial, "exception.txt")):
            return True
    return False

def _matching_job_dirs(model_label, task):
    """Find all jobs/<dir> that look like (model_label, task), incl. -r2 suffixes."""
    out = []
    for pfx in COVERAGE_PREFIXES.get(model_label, []):
        prefix_with_task = f"{pfx}{task}"
        for jd in JOBS.glob(f"{prefix_with_task}*"):
            # Allow exact, "-r2", "-r3", etc. suffixes — but NOT a different task
            # whose name happens to start with `task`.
            tail = jd.name[len(prefix_with_task):]
            if tail == "" or tail.startswith("-r") or tail.startswith("_"):
                out.append(jd)
    return out

def recently_failed(model_label, task, max_age_sec=1800):
    """Has this (model, task) failed in the last N seconds?

    Avoids the launcher re-launching a job that fails fast in setup
    (e.g., a Dockerfile bug) every 30s, eating slots and money.
    """
    import time as _t
    now = _t.time()
    for jd in _matching_job_dirs(model_label, task):
        for trial in jd.glob(f"{task}__*"):
            exc = trial / "exception.txt"
            if exc.exists() and (now - exc.stat().st_mtime) < max_age_sec:
                return True
    return False

def covered(model_label, task):
    """Is there a DONE sandboxed run for this (model, task)? Includes -r2 retries."""
    for jd in _matching_job_dirs(model_label, task):
        if is_done(jd, task):
            return True
    return False

def is_in_flight(model_label, task):
    """Active job — requires both an unfinished trial dir AND a live harbor process."""
    procs = harbor_processes()
    for jd in _matching_job_dirs(model_label, task):
        if not is_running(jd, task):
            continue
        # Require a live harbor process matching this job dir
        for line in procs:
            if jd.name in line:
                return True
    return False

def harbor_processes():
    try:
        out = subprocess.check_output(["ps", "-eo", "args"], text=True)
    except Exception:
        return []
    procs = []
    for line in out.splitlines():
        if "harbor run" in line and "minrepro_task/" in line:
            procs.append(line)
    return procs

def heavy_in_flight():
    n = 0
    for line in harbor_processes():
        for h in HEAVY:
            if f"minrepro_task/{h} " in line + " ":
                n += 1
                break
    return n

def task_in_flight(task):
    """Is a job for this exact task currently running?

    docker classic builder serializes builds, so kicking off >1 of the
    same Dockerfile just queues them and wastes a worker slot.
    """
    for line in harbor_processes():
        if f"minrepro_task/{task} " in line + " ":
            return True
    return False

def make_jobname(model_label, task):
    # Codex uses unique prefix "codex-gpt55-xhigh-"; others use "openrouter-<label>-" or
    # "sandboxed-<label>-". Pick the canonical first-listed prefix.
    pfx = COVERAGE_PREFIXES[model_label][0]
    return f"{pfx}{task}"

def setup_claude_creds():
    """Mirror ~/.claude/.credentials.json into a 0700 dir for bind-mount."""
    src = Path.home() / ".claude" / ".credentials.json"
    if not src.exists():
        log("WARN: ~/.claude/.credentials.json missing — Claude Code runs will fail")
        return False
    CLAUDE_CREDS_DIR.mkdir(exist_ok=True)
    os.chmod(CLAUDE_CREDS_DIR, 0o700)
    dest = CLAUDE_CREDS_DIR / ".credentials.json"
    dest.write_bytes(src.read_bytes())
    os.chmod(dest, 0o600)
    return True

def per_job_claude_creds_dir(job: str) -> Path:
    """Make a per-job claude creds dir. Required because Claude Code's installer
    writes its binary to ~/.claude/downloads/, and multiple parallel installs
    sharing the same bind-mount collide with `Text file busy`.
    """
    src = CLAUDE_CREDS_DIR / ".credentials.json"
    dst_dir = Path(f"/tmp/harbor-claude-creds-{job}")
    dst_dir.mkdir(exist_ok=True)
    os.chmod(dst_dir, 0o700)
    dst_file = dst_dir / ".credentials.json"
    dst_file.write_bytes(src.read_bytes())
    os.chmod(dst_file, 0o600)
    return dst_dir

def launch(model_entry, task):
    label, agent_class, model_name, ae_or_kind, mounts, ak_items = model_entry
    job = make_jobname(label, task)
    job_dir = JOBS / job
    if job_dir.exists():
        # Stale — wipe so harbor doesn't refuse the lock
        subprocess.run(["rm", "-rf", str(job_dir)])
    cmd = [
        "harbor", "run",
        "--path", f"minrepro_task/{task}",
        "--agent-import-path", agent_class,
        "--model", model_name,
        "-n", "1", "-y",
        "--job-name", job,
        "--artifact", "/app",
        "--agent-timeout-multiplier", AGENT_TIMEOUT_MULTIPLIER,
    ]
    # Rewrite shared CLAUDE_CREDS_DIR mount to a per-job dir
    if mounts:
        rewritten = []
        for m in mounts:
            if str(CLAUDE_CREDS_DIR) in m:
                pj = per_job_claude_creds_dir(job)
                rewritten.append(m.replace(str(CLAUDE_CREDS_DIR), str(pj)))
            else:
                rewritten.append(m)
        cmd += ["--mounts-json", json.dumps(rewritten)]
    # Resolve --ae items
    if ae_or_kind == "OPENROUTER":
        cmd += ["--ae", f"OPENROUTER_API_KEY={env_or_die('OPENROUTER_API_KEY')}"]
    else:
        for k, v in ae_or_kind:
            cmd += ["--ae", f"{k}={v}"]
    for k, v in ak_items:
        cmd += ["--ak", f"{k}={v}"]
    log_path = LOG_DIR / f"{job}.log"
    log(f"LAUNCH {job}  ({task} via {label})")
    with open(log_path, "wb") as lf:
        subprocess.Popen(cmd, stdout=lf, stderr=subprocess.STDOUT, start_new_session=True)

def regenerate_report():
    try:
        subprocess.run([sys.executable, str(ROOT/"scripts"/"report.py")],
                       check=False, capture_output=True, timeout=30)
    except Exception as e:
        log(f"report regen failed: {e}")

def build_queue():
    """Yield (model_entry, task) tuples that need to run.

    Order: small tasks first, then medium, then heavy. Within each task
    bucket, models are round-robined by coverage gap (most-gappy first).
    This gives BREADTH first — launcher fills 6 smoke slots across 6
    different models before piling onto any one model.
    """
    coverage_gap = {m[0]: sum(1 for t in TASKS if not covered(m[0], t) and not is_in_flight(m[0], t)) for m in MODELS}
    models_sorted = sorted(MODELS, key=lambda m: -coverage_gap[m[0]])
    items = []
    for task in TASKS_ORDERED:
        for model_entry in models_sorted:
            label = model_entry[0]
            if covered(label, task):  continue
            if is_in_flight(label, task):  continue
            if recently_failed(label, task):  continue  # backoff after fail
            items.append((model_entry, task))
    return items

def main():
    if not setup_claude_creds():
        log("(continuing without Claude creds — Opus/Sonnet runs will skip)")
    last_report = 0
    while True:
        if STOP_FILE.exists():
            log("STOP file present — exiting")
            return
        if PAUSE_FILE.exists():
            log("paused")
            time.sleep(30)
            continue
        procs = harbor_processes()
        nrun = len(procs)
        nheavy = heavy_in_flight()
        slots = MAX_TOTAL - nrun
        if slots <= 0:
            time.sleep(30)
            continue
        queue = build_queue()
        if not queue:
            log("queue empty — all matrix cells covered or in flight")
            regenerate_report()
            time.sleep(60)
            # If no harbor procs left either, exit
            if not harbor_processes():
                log("no jobs left — exiting")
                return
            continue
        launched = 0
        # Track which task images we kicked off this tick so we don't double up
        local_tasks_taken = set()
        for model_entry, task in queue:
            if launched >= slots:
                break
            if task in HEAVY and nheavy >= MAX_HEAVY:
                continue
            # Avoid concurrent builds of the same Dockerfile (classic builder serializes)
            if task in local_tasks_taken or task_in_flight(task):
                continue
            # Skip if we lack credentials for this agent
            if "ClaudeCode" in model_entry[1] and not (CLAUDE_CREDS_DIR/".credentials.json").exists():
                continue
            launch(model_entry, task)
            launched += 1
            local_tasks_taken.add(task)
            if task in HEAVY:
                nheavy += 1
            time.sleep(2)  # spread launches a bit
        if time.time() - last_report > 60:
            regenerate_report()
            last_report = time.time()
        time.sleep(30)

if __name__ == "__main__":
    main()
