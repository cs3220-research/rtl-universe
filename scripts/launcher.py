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

# Concurrency (128-CPU, 1TB-RAM lab server; aggressive for 24h target)
MAX_TOTAL = 16     # max harbor processes at once
MAX_HEAVY = 8      # at most N HEAVY tasks (coralnpu/nvdla-full) running
MAX_CLAUDE = 4     # ClaudeCode (Opus + Sonnet combined) — 2 accounts × 2 each
MAX_CODEX = 5      # Codex (GPT-5.5)
# OpenCode (everything else) is implicitly unlimited up to MAX_TOTAL.
# Agent timeout: 10h cap (task.toml is 24h). Most will finish much sooner.
AGENT_TIMEOUT_MULTIPLIER = "0.42"
# Watchdog: heartbeat every iteration so we know the launcher is alive
TICK_SEC = 20
# Track when this launcher started so stale exception.txt files (from prior
# machines or git-clone-rewritten mtimes) don't trigger backoff forever.
LAUNCHER_START_TS = time.time() if 'time' in dir() else __import__('time').time()

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
     [("CODEX_AUTH_JSON_PATH", "/nethome/dandrews47/.codex/auth.json")],
     None,
     [("reasoning_effort", "xhigh")]),  # --ak items
    # Claude Code
    ("opus", "agents.sandboxed:ClaudeCodeSandboxed", "opus",
     [("CLAUDE_CONFIG_DIR", "/home/builder/.claude"),
      ("CLAUDE_CODE_MAX_OUTPUT_TOKENS", "128000")],
     # Mount ONLY the credentials FILE, not the whole .claude DIR.
     # Mounting the dir caused Claude Code (and its sub-tools like make/Verilator)
     # to write multi-hundred-GB session/build dumps to /tmp on the host,
     # filling the / partition and crashing the shared lab server.
     # By mounting just the file, the rest of ~/.claude is the container's
     # ephemeral overlay layer and gets garbage-collected on container exit.
     [str(CLAUDE_CREDS_DIR) + "/.credentials.json:/home/builder/.claude/.credentials.json"],
     []),
    ("sonnet", "agents.sandboxed:ClaudeCodeSandboxed", "sonnet",
     [("CLAUDE_CONFIG_DIR", "/home/builder/.claude"),
      ("CLAUDE_CODE_MAX_OUTPUT_TOKENS", "128000")],
     # Mount ONLY the credentials FILE, not the whole .claude DIR.
     # Mounting the dir caused Claude Code (and its sub-tools like make/Verilator)
     # to write multi-hundred-GB session/build dumps to /tmp on the host,
     # filling the / partition and crashing the shared lab server.
     # By mounting just the file, the rest of ~/.claude is the container's
     # ephemeral overlay layer and gets garbage-collected on container exit.
     [str(CLAUDE_CREDS_DIR) + "/.credentials.json:/home/builder/.claude/.credentials.json"],
     []),
    # OpenCode via OpenRouter — cheap models re-enabled (gemini/qwen-max stay paused — too expensive)
    # ("gemini-3.1-pro", "agents.sandboxed:OpenCodeSandboxed", "openrouter/google/gemini-3.1-pro-preview", "OPENROUTER", None, []),
    # ("qwen3.6-max",     "agents.sandboxed:OpenCodeSandboxed", "openrouter/qwen/qwen3.6-max-preview",     "OPENROUTER", None, []),
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
    """A trial is DONE if it has a reward AND the agent actually did work.

    False-positive guard: a job that died at agent setup (e.g. auth failure,
    docker compose kill) still has a reward.txt written by the verifier
    running on the bare skeleton, which makes it look "covered". Skip such
    runs — they should be re-queued.

    Heuristic: real agent runs leave an agent log >50KB. Setup-failure runs
    leave logs <10KB (just the instruction text echoed via tee).
    """
    for trial in glob.glob(str(job_dir / f"{task}__*")):
        rwd = os.path.join(trial, "verifier", "reward.txt")
        if not os.path.isfile(rwd):
            continue
        # Check agent log size
        agent_dir = os.path.join(trial, "agent")
        log_files = glob.glob(os.path.join(agent_dir, "*.txt"))
        max_log_size = max((os.path.getsize(f) for f in log_files), default=0)
        # Also accept it as done if exception.txt is absent (verifier ran cleanly)
        has_exception = os.path.isfile(os.path.join(trial, "exception.txt"))
        if max_log_size >= 50_000 or not has_exception:
            return True
    rj = job_dir / "result.json"
    if rj.is_file():
        try:
            d = json.load(open(rj))
            for ev in d.get("stats",{}).get("evals",{}).values():
                if ev.get("reward_stats",{}).get("reward"):
                    # Same guard as above
                    for trial in glob.glob(str(job_dir / f"{task}__*")):
                        agent_dir = os.path.join(trial, "agent")
                        log_files = glob.glob(os.path.join(agent_dir, "*.txt"))
                        max_log_size = max((os.path.getsize(f) for f in log_files), default=0)
                        has_exception = os.path.isfile(os.path.join(trial, "exception.txt"))
                        if max_log_size >= 50_000 or not has_exception:
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
    """Has this (model, task) failed in the last N seconds *of this launcher's lifetime*?

    Only counts failures with mtime > LAUNCHER_START_TS so old exception.txt
    files (from prior machines / git clone rewriting mtimes) don't permanently
    starve a cell.
    """
    import time as _t
    now = _t.time()
    for jd in _matching_job_dirs(model_label, task):
        for trial in jd.glob(f"{task}__*"):
            exc = trial / "exception.txt"
            if not exc.exists(): continue
            mtime = exc.stat().st_mtime
            if mtime < LAUNCHER_START_TS: continue
            if (now - mtime) < max_age_sec: return True
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

def task_in_flight_count(task):
    """How many jobs of this task are currently running?

    On modern docker (29+ with BuildKit default), parallel builds of the
    same Dockerfile share cache properly — no need to serialize.
    """
    return sum(1 for line in harbor_processes() if f"minrepro_task/{task} " in line + " ")

# Per-Dockerfile concurrency cap (1 = old strict dedup, higher = parallel)
MAX_PER_TASK = 3

def task_in_flight(task):
    """Backward-compat: True iff at MAX_PER_TASK or more jobs of this task."""
    return task_in_flight_count(task) >= MAX_PER_TASK

def agent_class_in_flight(agent_class_substr):
    """Count harbor procs whose --agent-import-path contains the given substring.
    e.g. 'ClaudeCodeSandboxed' or 'CodexSandboxed'."""
    return sum(1 for line in harbor_processes() if agent_class_substr in line)

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

CLAUDE_ACCOUNT_SOURCES = [
    Path.home() / ".claude" / ".credentials.json",          # account 1: David's primary Max
    Path("/tmp/harbor-claude-creds-account2/.credentials.json"),  # account 2: second Max key
]

def _claude_account_for_job(job: str) -> Path:
    """Pick which source creds file to use. Balance load across in-flight jobs.

    Counts how many currently-running Claude jobs are using each account, picks
    the one with fewer. Falls back to job-name hash for tie-break.
    """
    import hashlib, glob
    sources = [p for p in CLAUDE_ACCOUNT_SOURCES if p.exists()]
    if not sources:
        return CLAUDE_CREDS_DIR / ".credentials.json"
    if len(sources) == 1:
        return sources[0]

    # Count in-flight per account. Account2 has a stable hardcoded token, so we
    # detect it by hash-equality. Account1 (David's) rotates, so any per-job
    # creds file that does NOT match the account2 hash is treated as account1.
    import hashlib as h
    acct2_hash = None
    acct2_path = Path("/tmp/harbor-claude-creds-account2/.credentials.json")
    if acct2_path.exists():
        try:
            acct2_hash = h.md5(acct2_path.read_bytes()).hexdigest()
        except Exception:
            pass
    counts = {p: 0 for p in sources}
    acct1_src = next((p for p in sources if "account2" not in str(p)), sources[0])
    acct2_src = next((p for p in sources if "account2" in str(p)), None)
    for cred_path in glob.glob("/tmp/harbor-claude-creds-*/.credentials.json"):
        if "account2" in cred_path:  # skip the source itself
            continue
        try:
            cur = h.md5(open(cred_path,'rb').read()).hexdigest()
            if acct2_hash and cur == acct2_hash and acct2_src:
                counts[acct2_src] += 1
            else:
                counts[acct1_src] += 1
        except Exception:
            pass
    # Pick the source with fewest in-flight (ties: job-hash for stability)
    min_count = min(counts.values())
    candidates = [p for p, c in counts.items() if c == min_count]
    idx = int(hashlib.md5(job.encode()).hexdigest(), 16) % len(candidates)
    return candidates[idx]

def per_job_claude_creds_dir(job: str) -> Path:
    """Make a per-job claude creds dir from a LIVE source credentials file.

    Multi-account: round-robin between primary (~/.claude) and account2
    (/tmp/harbor-claude-creds-account2) so we can run more concurrent
    Claude Code jobs without sharing one account's rate limit.
    """
    host_creds = _claude_account_for_job(job)
    if not host_creds.exists():
        host_creds = CLAUDE_CREDS_DIR / ".credentials.json"
    dst_dir = Path(f"/tmp/harbor-claude-creds-{job}")
    dst_dir.mkdir(exist_ok=True)
    os.chmod(dst_dir, 0o700)
    dst_file = dst_dir / ".credentials.json"
    dst_file.write_bytes(host_creds.read_bytes())
    os.chmod(dst_file, 0o600)
    return dst_dir

_RECENTLY_LAUNCHED = {}  # job_name -> launch_timestamp; survives within a launcher process

def launch(model_entry, task):
    label, agent_class, model_name, ae_or_kind, mounts, ak_items = model_entry
    job = make_jobname(label, task)
    # Race-guard: if we just launched this job in the last 2 minutes,
    # the harbor process may not be visible to ps yet. Skip.
    import time as _t
    if job in _RECENTLY_LAUNCHED and _t.time() - _RECENTLY_LAUNCHED[job] < 120:
        log(f"  skip {job} — launched {_t.time() - _RECENTLY_LAUNCHED[job]:.0f}s ago, race-guard")
        return
    _RECENTLY_LAUNCHED[job] = _t.time()
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

# ----- stuck-job killer ----------------------------------------------------
# A harbor process is "stuck" if its job dir's agent log hasn't been written
# to in 30+ minutes AND it's >30min old (give time for build phase). Killing
# frees the slot so the launcher can re-launch the same cell.
STUCK_LOG_AGE_SEC = 30 * 60   # log untouched 30+ min
STUCK_MIN_PROC_AGE_SEC = 30 * 60  # process must be >30min old to be considered

def _harbor_proc_info():
    """Return list of (pid, etime_sec, jobname) for current harbor processes."""
    try:
        out = subprocess.check_output(["ps", "-eo", "pid,etimes,args"], text=True)
    except Exception:
        return []
    procs = []
    for line in out.splitlines()[1:]:
        if "harbor run" not in line or "minrepro_task" not in line:
            continue
        parts = line.split(None, 2)
        if len(parts) < 3:
            continue
        try:
            pid = int(parts[0]); etime = int(parts[1])
        except ValueError:
            continue
        args = parts[2]
        # extract job-name
        import re
        m = re.search(r"--job-name (\S+)", args)
        if m:
            procs.append((pid, etime, m.group(1)))
    return procs

def kill_stuck_jobs():
    """Find harbor processes whose agent log is stale, SIGKILL them."""
    import time as _t
    now = _t.time()
    killed = []
    for pid, etime, jobname in _harbor_proc_info():
        if etime < STUCK_MIN_PROC_AGE_SEC:
            continue
        # Find the trial dir for this job
        jd = JOBS / jobname
        if not jd.exists():
            continue
        # Find newest agent log (use _path to avoid shadowing log())
        latest_log_age = None
        for log_path in glob.glob(str(jd / "*__*" / "agent" / "*.txt")):
            age = now - os.path.getmtime(log_path)
            if latest_log_age is None or age < latest_log_age:
                latest_log_age = age
        # Skip if no agent log yet (still in build phase)
        if latest_log_age is None:
            continue
        if latest_log_age > STUCK_LOG_AGE_SEC:
            log(f"  STUCK: {jobname} (pid={pid}, etime={etime/60:.0f}m, log age {latest_log_age/60:.0f}m) — SIGKILL")
            try:
                os.kill(pid, 9)
                killed.append(jobname)
            except ProcessLookupError:
                pass
    return killed

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

def _tick_once(state):
    """One pass of the launcher loop. Returns False to stop."""
    if STOP_FILE.exists():
        log("STOP file present — exiting")
        return False
    if PAUSE_FILE.exists():
        log(f"paused (pool={len(harbor_processes())})")
        return True
    # Periodic stuck-job sweep (every ~5 ticks = 100s)
    state["sweeps"] = state.get("sweeps", 0) + 1
    if state["sweeps"] % 5 == 0:
        kill_stuck_jobs()
    procs = harbor_processes()
    nrun = len(procs)
    nheavy = heavy_in_flight()
    slots = MAX_TOTAL - nrun
    queue = build_queue()
    # Heartbeat every tick so we know we're alive
    nclaude_now = agent_class_in_flight("ClaudeCodeSandboxed")
    ncodex_now = agent_class_in_flight("CodexSandboxed")
    log(f"tick: pool={nrun}/{MAX_TOTAL} heavy={nheavy}/{MAX_HEAVY} claude={nclaude_now}/{MAX_CLAUDE} codex={ncodex_now}/{MAX_CODEX} queue={len(queue)} slots={slots}")
    if slots <= 0:
        return True
    if not queue:
        log("queue empty — all matrix cells covered or in flight")
        regenerate_report()
        if not harbor_processes():
            log("no jobs left — exiting")
            return False
        return True
    launched = 0
    # Track per-task launches THIS tick so we don't overshoot MAX_PER_TASK
    local_task_count = {}
    nclaude = agent_class_in_flight("ClaudeCodeSandboxed")
    ncodex = agent_class_in_flight("CodexSandboxed")
    for model_entry, task in queue:
        if launched >= slots:
            break
        if task in HEAVY and nheavy >= MAX_HEAVY:
            continue
        running_now = task_in_flight_count(task) + local_task_count.get(task, 0)
        if running_now >= MAX_PER_TASK:
            continue
        agent_class = model_entry[1]
        # Per-agent caps (Anthropic / OpenAI subscription rate limits)
        if "ClaudeCodeSandboxed" in agent_class and nclaude >= MAX_CLAUDE:
            continue
        if "CodexSandboxed" in agent_class and ncodex >= MAX_CODEX:
            continue
        if "ClaudeCode" in agent_class and not (CLAUDE_CREDS_DIR/".credentials.json").exists():
            continue
        try:
            launch(model_entry, task)
            launched += 1
            local_task_count[task] = local_task_count.get(task, 0) + 1
            if task in HEAVY:
                nheavy += 1
            if "ClaudeCodeSandboxed" in agent_class: nclaude += 1
            if "CodexSandboxed" in agent_class: ncodex += 1
            time.sleep(2)
        except Exception as e:
            log(f"  launch failed for {model_entry[0]}/{task}: {e}")
    state["launched_total"] = state.get("launched_total", 0) + launched
    if time.time() - state.get("last_report", 0) > 60:
        regenerate_report()
        state["last_report"] = time.time()
    return True

def main():
    setup_claude_creds()
    state = {"last_report": 0, "launched_total": 0, "tick_count": 0, "crash_count": 0}
    log(f"=== launcher start: PID={os.getpid()} MAX_TOTAL={MAX_TOTAL} MAX_HEAVY={MAX_HEAVY} TIMEOUT={AGENT_TIMEOUT_MULTIPLIER} ===")
    while True:
        state["tick_count"] += 1
        try:
            cont = _tick_once(state)
            if not cont:
                return
        except Exception as e:
            state["crash_count"] += 1
            import traceback
            log(f"!! tick exception #{state['crash_count']}: {e}\n{traceback.format_exc()}")
            # Don't die — sleep and retry
        time.sleep(TICK_SEC)

if __name__ == "__main__":
    main()
