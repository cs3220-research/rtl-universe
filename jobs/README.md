# Harbor Job Runs — Coral NPU

All runs use `claude-code` agent with `sonnet` (claude-sonnet-4-6) model.

## Results Summary

| Job | Task | Hardened | Agent Timeout | Reward | Runtime | Termination | Notes |
|-----|------|----------|---------------|--------|---------|-------------|-------|
| `2026-04-24__13-55-20` | coralnpu | No | 3h | 0.000 | 7s | RuntimeError | Docker build failed — `skeleton/` and `warm_src/` dirs missing |
| `2026-04-24__13-56-34` | coralnpu | No | 3h | 0.000 | 46m | NonZeroAgentExitCode | UID mismatch (host=1005, container=1000) — permission denied on `/logs/agent/sessions` |
| `2026-04-24__14-45-36` | coralnpu | No | 3h | 0.003 | 60m | NonZeroAgentExitCode | Auth failed — `CLAUDE_CODE_OAUTH_TOKEN` not set, agent printed "Not logged in" |
| `2026-04-24__17-08-06` | coralnpu | No | 3h | **0.043** | 3h 11m | AgentTimeoutError | First successful run. ~13/305 tests. Moderate cache exploitation (sandbox stash, Chisel JAR decompilation) |
| `2026-04-24__20-19-28` | coralnpu-e2e | No | 3h | **0.958** | 3h 42m | AgentTimeoutError | ~181/189 E2E tests. **Heavy cache exploitation** — agent read generated Verilog from warm build, compared md5sums, read firmware from sandbox stash |
| `2026-04-24__21-36-39` | coralnpu | **Yes** | 10h | **0.105** | 7h 23m | NonZeroAgentExitCode (401 auth) | ~32/305 tests. OAuth token expired mid-run (~7h). Agent made real progress on Chisel RTL |
| `2026-04-25__03-20-36` | coralnpu-e2e | **Yes** | 10h | 0.000 | 1h 13m | NonZeroAgentExitCode (401 auth) | OAuth token expired in subagent after ~1h |
| `2026-04-25__04-34-53` | coralnpu-e2e | **Yes** | 10h | — | — | (in progress) | Re-run with fresh OAuth token |
| `2026-04-25__07-06-25` | coralnpu | **Yes** | 10h | 0.000 | ~1m | NonZeroAgentExitCode | Failed — file mount created `~/.claude` as root-owned dir, broke Claude Code installer |
| `2026-04-25__07-07-58` | coralnpu | **Yes** | 10h | 0.003 | ~2m | NonZeroAgentExitCode | Auth failed — mounted creds to `~/.claude` but `CLAUDE_CONFIG_DIR` pointed elsewhere |
| `2026-04-25__08-17-29` | coralnpu | **Yes** | 10h | — | — | (in progress) | **Credentials file mount + CLAUDE_CONFIG_DIR override** — should support token refresh |

## Hardening Details

"Hardened" runs scrub warm-build artifacts from the bazel cache in the Docker final stage:
- `sandbox/sandbox_stash/` — generated Verilog from the warm Chisel build
- `testlogs/` — warm-run test logs with pass/fail patterns and assertion messages
- `bin/{hdl,tests,sw,examples,fpga,build,coralnpu_test_utils,hw_sim}/` — project-specific build outputs

External dependencies (`external/`) are preserved for build speed (Verilator, firtool, TFLite-Micro, cvfpu, common_cells).

## Auth Methods Tried

1. **No auth** → "Not logged in" error
2. **`CLAUDE_CODE_OAUTH_TOKEN` env var** → Works but access token expires after ~6-8h with no refresh
3. **Credentials file mount to `~/.claude/`** → Claude Code looks at `$CLAUDE_CONFIG_DIR` instead
4. **Credentials file mount + `CLAUDE_CONFIG_DIR` override via `--ae`** → Working approach, supports token refresh

### Working command (with token refresh):
```bash
# Create a minimal credentials directory
CREDS_DIR=/tmp/harbor-claude-creds
mkdir -p "$CREDS_DIR"
cp ~/.claude/.credentials.json "$CREDS_DIR/.credentials.json"
chmod 700 "$CREDS_DIR" && chmod 600 "$CREDS_DIR/.credentials.json"

uvx harbor run \
  --path minrepro_task/coralnpu \
  --agent claude-code \
  --model sonnet \
  -n 1 -y \
  --mounts-json '["/tmp/harbor-claude-creds:/home/builder/.claude"]' \
  --ae CLAUDE_CONFIG_DIR=/home/builder/.claude
```

## Cache Exploitation Analysis

### Unhardened coralnpu (reward 0.043)
- 70 suspicious cache accesses out of 874 tool calls
- Read Chisel library JARs (DataMirror API), sandbox stash Verilog, test logs
- Mostly exploratory, low-impact

### Unhardened coralnpu-e2e (reward 0.958)
- 89 suspicious cache accesses out of 511 tool calls
- **Critical**: read generated Verilog from `bin/hdl/`, compared md5sums with own output
- **Critical**: mined `sandbox_stash/Verilate/` for correct Verilog and headers
- **Critical**: read firmware source from `CppCompile` sandbox stash
- Read TFLite-Micro reference implementations from `external/tflite_micro/`
- Score substantially inflated by cache exploitation

### Hardened coralnpu (reward 0.105)
- 77 suspicious cache accesses out of 1329 tool calls
- Mostly reading `external/tflite_micro/` reference implementations (open-source, borderline)
- Sandbox stash, testlogs, and project bin/ outputs successfully blocked
- Score reflects genuine agent capability
