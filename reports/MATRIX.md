# RTL-Universe — Coverage Matrix
_Generated 2026-05-03 14:15:34_

Legend: `0.95✓` sandboxed DONE · `0.95✗` unsandboxed DONE (suspect cheating) · `RUN` in progress · `·` not started

## Matrix

| Model | cor-e2e | cor-full | ibex-e2e | ibex-ful | nvdla-e2 | nvdla-fu | opn-e2e | opn-full | pulp-ful | secworks | veer-el2 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Opus4.7 | 1.00✓ | 0.10✗ | FAIL | · | FAIL | · | · | RUN | FAIL | 1.00✓ | FAIL |
| GPT5.5 | · | · | 1.00✓ | · | FAIL | · | 0.00✓ | · | 0.00✓ | 1.00✓ | · |
| Gemini3.1Pro | · | · | FAIL | RUN | FAIL | · | · | · | FAIL | 1.00✓ | · |
| Sonnet4.6 | · | · | 1.00✗ | FAIL | · | · | · | · | · | 0.00✗ | 0.00✗ |
| QwenMax | · | · | FAIL | · | FAIL | · | · | · | · | 0.00✓ | 0.00✓ |
| DeepSeek | 0.03✓ | 0.02✗ | 0.00✗ | 0.20✗ | 0.14✓ | 0.00✓ | 1.00✓ | 0.00✓ | 0.00✓ | 0.88✗ | 1.00✗ |
| Kimi | 0.00✓ | 0.01✗ | 0.00✓ | 0.00✓ | 1.00✓ | 1.00✓ | 0.00✓ | 0.06✓ | FAIL | 0.00✓ | 0.00✗ |
| GLM5.1 | · | · | FAIL | · | FAIL | · | · | · | · | 0.00✓ | RUN |
| Qwen27B | · | · | RUN | · | FAIL | · | · | · | · | 0.00✓ | · |

## Coverage stats

| Model | Legit | Need rerun (unsand) | In progress | Missing | Cost (legit only) |
|---|---|---|---|---|---|
| Opus4.7 | 2/11 | 1 | 1 | 7 | $0.00 |
| GPT5.5 | 4/11 | 0 | 0 | 7 | $14.47 |
| Gemini3.1Pro | 1/11 | 0 | 1 | 9 | $1.49 |
| Sonnet4.6 | 0/11 | 3 | 0 | 8 | $0.00 |
| QwenMax | 2/11 | 0 | 0 | 9 | $0.00 |
| DeepSeek | 6/11 | 5 | 0 | 0 | $36.77 |
| Kimi | 8/11 | 2 | 0 | 1 | $23.77 |
| GLM5.1 | 1/11 | 0 | 1 | 9 | $0.29 |
| Qwen27B | 1/11 | 0 | 1 | 9 | $0.00 |

## Sandbox effectiveness

- Total egress attempts blocked across sandboxed runs: **12**
- Per run:
  - GPT5.5 on ibex-e2e: 2 attempts blocked
  - GPT5.5 on pulp-common-cells-full: 2 attempts blocked
  - GPT5.5 on secworks-aes-full: 2 attempts blocked
  - Opus4.7 on coralnpu-e2e: 2 attempts blocked
  - Opus4.7 on secworks-aes-full: 2 attempts blocked
  - GLM5.1 on veer-el2-block: 2 attempts blocked

## Per-task results (legit runs)

### coralnpu-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 570 min | ? | `dns-block-opus-coralnpu-e2e` |
| DeepSeek | 0.033 | 295 min | $16.22 | `sandboxed-deepseek-coralnpu-e2e` |
| Kimi | 0.000 | 122 min | $3.02 | `sandboxed-kimi-coralnpu-e2e` |

### coralnpu-full
_no legit runs yet_

### ibex-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| GPT5.5 | 1.000 | 14 min | $2.47 | `codex-gpt55-xhigh-ibex-e2e` |
| Kimi | 0.000 | 94 min | $3.82 | `sandboxed-kimi-ibex-e2e` |

### ibex-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Kimi | 0.000 | 21 min | $0.36 | `sandboxed-kimi-ibex-full` |

### nvdla-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Kimi | 1.000 | 89 min | $3.36 | `sandboxed-kimi-nvdla-e2e` |
| DeepSeek | 0.143 | 155 min | $5.57 | `sandboxed-deepseek-nvdla-e2e` |

### nvdla-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Kimi | 1.000 | 178 min | $8.44 | `sandboxed-kimi-nvdla-full` |
| DeepSeek | 0.000 | 225 min | $7.17 | `sandboxed-deepseek-nvdla-full` |

### openpiton-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| DeepSeek | 1.000 | 146 min | $1.63 | `sandboxed-deepseek-openpiton-e2e-r2` |
| GPT5.5 | 0.000 | 56 min | $2.84 | `codex-gpt55-xhigh-openpiton-e2e` |
| Kimi | 0.000 | 95 min | $0.60 | `sandboxed-kimi-openpiton-e2e-r2` |

### openpiton-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Kimi | 0.056 | 294 min | $4.09 | `sandboxed-kimi-openpiton-full-r2` |
| DeepSeek | 0.000 | 187 min | $4.59 | `sandboxed-deepseek-openpiton-full-r2` |

### pulp-common-cells-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| GPT5.5 | 0.000 | 24 min | $5.58 | `codex-gpt55-xhigh-pulp-common-cells-full` |
| DeepSeek | 0.000 | 134 min | $1.58 | `sandboxed-deepseek-pulp-common-cells-full-r2` |

### secworks-aes-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 19 min | ? | `dns-block-opus-secworks-aes-full` |
| GPT5.5 | 1.000 | 17 min | $3.58 | `codex-gpt55-xhigh-secworks-aes-full` |
| Gemini3.1Pro | 1.000 | 15 min | $1.49 | `openrouter-gemini-3.1-pro-secworks-aes-full` |
| QwenMax | 0.000 | 8 min | ? | `openrouter-qwen3.6-max-secworks-aes-full` |
| Kimi | 0.000 | 4 min | $0.08 | `sandboxed-kimi-secworks-aes-full` |
| GLM5.1 | 0.000 | 11 min | $0.29 | `openrouter-glm-5.1-secworks-aes-full` |
| Qwen27B | 0.000 | 1 min | ? | `openrouter-qwen3.6-27b-secworks-aes-full` |

### veer-el2-block
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| QwenMax | 0.000 | 5 min | ? | `openrouter-qwen3.6-max-veer-el2-block` |
