# RTL-Universe — Coverage Matrix
_Generated 2026-05-06 12:57:34_

Legend: `0.95✓` sandboxed DONE · `0.95✗` unsandboxed DONE (suspect cheating) · `RUN` in progress · `·` not started

## Matrix

| Model | cor-e2e | cor-full | ibex-e2e | ibex-ful | nvdla-e2 | nvdla-fu | opn-e2e | opn-full | pulp-ful | secworks | veer-el2 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Opus4.7 | 1.00✓ | 0.09✓ | 1.00✓ | 0.20✓ | 0.00✓ | 0.42✓ | 0.00✓ | 1.00✓ | 0.00✓ | 1.00✓ | 1.00✓ |
| GPT5.5 | 1.00✓ | 0.42✓ | 1.00✓ | 1.00✓ | 0.00✓ | 1.00✓ | 0.00✓ | FAIL | 0.00✓ | 1.00✓ | 1.00✓ |
| Gemini3.1Pro | · | · | FAIL | 1.00✓ | 0.00✓ | 0.67✓ | 0.00✓ | 0.00✓ | FAIL | 1.00✓ | 0.00✓ |
| Sonnet4.6 | RUN | FAIL | 0.00✓ | FAIL | 0.86✓ | FAIL | 0.00✓ | FAIL | 0.00✓ | 1.00✓ | 0.00✓ |
| QwenMax | · | 0.03✓ | RUN | 0.00✓ | FAIL | · | RUN | RUN | RUN | 0.00✓ | 0.00✓ |
| DeepSeek | 0.03✓ | 0.03✓ | 0.00✓ | 0.00✓ | 0.14✓ | 0.00✓ | 1.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ |
| Kimi | 0.00✓ | 0.02✓ | 0.00✓ | 0.00✓ | 1.00✓ | 1.00✓ | 0.00✓ | 0.06✓ | 0.00✓ | 0.00✓ | 0.00✓ |
| GLM5.1 | 0.00✓ | 0.00✓ | 1.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ |
| Qwen27B | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ | 0.00✓ |

## Coverage stats

| Model | Legit | Need rerun (unsand) | In progress | Missing | Cost (legit only) |
|---|---|---|---|---|---|
| Opus4.7 | 11/11 | 0 | 0 | 0 | $0.00 |
| GPT5.5 | 10/11 | 0 | 0 | 1 | $201.49 |
| Gemini3.1Pro | 7/11 | 0 | 0 | 4 | $7.30 |
| Sonnet4.6 | 6/11 | 0 | 1 | 4 | $0.00 |
| QwenMax | 4/11 | 0 | 4 | 3 | $0.00 |
| DeepSeek | 11/11 | 0 | 0 | 0 | $50.01 |
| Kimi | 11/11 | 0 | 0 | 0 | $32.29 |
| GLM5.1 | 11/11 | 0 | 0 | 0 | $22.23 |
| Qwen27B | 11/11 | 0 | 0 | 0 | $0.00 |

## Sandbox effectiveness

- Total egress attempts blocked across sandboxed runs: **84**
- Per run:
  - Opus4.7 on ibex-e2e: 8 attempts blocked
  - GLM5.1 on veer-el2-block: 6 attempts blocked
  - Qwen27B on nvdla-full: 6 attempts blocked
  - GPT5.5 on nvdla-e2e: 4 attempts blocked
  - Opus4.7 on nvdla-full: 4 attempts blocked
  - Opus4.7 on openpiton-full: 4 attempts blocked
  - Opus4.7 on veer-el2-block: 4 attempts blocked
  - Gemini3.1Pro on ibex-full: 4 attempts blocked
  - Gemini3.1Pro on nvdla-full: 4 attempts blocked
  - Qwen27B on nvdla-e2e: 4 attempts blocked

## Per-task results (legit runs)

### coralnpu-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 570 min | ? | `dns-block-opus-coralnpu-e2e` |
| GPT5.5 | 1.000 | 156 min | $32.97 | `codex-gpt55-xhigh-coralnpu-e2e` |
| DeepSeek | 0.033 | 295 min | $16.22 | `sandboxed-deepseek-coralnpu-e2e` |
| Kimi | 0.000 | 122 min | $3.02 | `sandboxed-kimi-coralnpu-e2e` |
| GLM5.1 | 0.000 | 100 min | $0.73 | `openrouter-glm-5.1-coralnpu-e2e` |
| Qwen27B | 0.000 | 114 min | ? | `openrouter-qwen3.6-27b-coralnpu-e2e` |

### coralnpu-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| GPT5.5 | 0.418 | 264 min | $80.34 | `codex-gpt55-xhigh-coralnpu-full` |
| Opus4.7 | 0.089 | 70 min | ? | `dns-block-opus-coralnpu-full` |
| QwenMax | 0.030 | 116 min | ? | `openrouter-qwen3.6-max-coralnpu-full` |
| DeepSeek | 0.030 | 204 min | $11.12 | `sandboxed-deepseek-coralnpu-full` |
| Kimi | 0.023 | 137 min | $5.46 | `sandboxed-kimi-coralnpu-full` |
| Qwen27B | 0.003 | 102 min | ? | `openrouter-qwen3.6-27b-coralnpu-full` |
| GLM5.1 | 0.003 | 92 min | $0.33 | `openrouter-glm-5.1-coralnpu-full` |

### ibex-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 14 min | ? | `dns-block-opus-ibex-e2e` |
| GPT5.5 | 1.000 | 14 min | $2.47 | `codex-gpt55-xhigh-ibex-e2e` |
| GLM5.1 | 1.000 | 155 min | $9.83 | `openrouter-glm-5.1-ibex-e2e` |
| Sonnet4.6 | 0.000 | 610 min | ? | `sandboxed-sonnet-ibex-e2e` |
| DeepSeek | 0.000 | 7 min | $0.01 | `sandboxed-deepseek-ibex-e2e` |
| Kimi | 0.000 | 94 min | $3.82 | `sandboxed-kimi-ibex-e2e` |
| Qwen27B | 0.000 | 15 min | ? | `openrouter-qwen3.6-27b-ibex-e2e` |

### ibex-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| GPT5.5 | 1.000 | 15 min | $5.17 | `codex-gpt55-xhigh-ibex-full` |
| Gemini3.1Pro | 1.000 | 30 min | $2.19 | `openrouter-gemini-3.1-pro-ibex-full` |
| Opus4.7 | 0.200 | 25 min | ? | `dns-block-opus-ibex-full` |
| QwenMax | 0.000 | 65 min | ? | `openrouter-qwen3.6-max-ibex-full` |
| DeepSeek | 0.000 | 48 min | $1.46 | `sandboxed-deepseek-ibex-full` |
| Kimi | 0.000 | 21 min | $0.36 | `sandboxed-kimi-ibex-full` |
| GLM5.1 | 0.000 | 82 min | $3.88 | `openrouter-glm-5.1-ibex-full` |
| Qwen27B | 0.000 | 40 min | ? | `openrouter-qwen3.6-27b-ibex-full` |

### nvdla-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Kimi | 1.000 | 89 min | $3.36 | `sandboxed-kimi-nvdla-e2e` |
| Sonnet4.6 | 0.857 | 606 min | ? | `sandboxed-sonnet-nvdla-e2e` |
| DeepSeek | 0.143 | 155 min | $5.57 | `sandboxed-deepseek-nvdla-e2e` |
| Opus4.7 | 0.000 | 3 min | ? | `dns-block-opus-nvdla-e2e` |
| GPT5.5 | 0.000 | 670 min | $53.08 | `codex-gpt55-xhigh-nvdla-e2e` |
| Gemini3.1Pro | 0.000 | 8 min | $0.31 | `openrouter-gemini-3.1-pro-nvdla-e2e` |
| GLM5.1 | 0.000 | 14 min | $0.39 | `openrouter-glm-5.1-nvdla-e2e` |
| Qwen27B | 0.000 | 38 min | ? | `openrouter-qwen3.6-27b-nvdla-e2e` |

### nvdla-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| GPT5.5 | 1.000 | 44 min | $6.86 | `codex-gpt55-xhigh-nvdla-full` |
| Kimi | 1.000 | 178 min | $8.44 | `sandboxed-kimi-nvdla-full` |
| Gemini3.1Pro | 0.667 | 40 min | $3.22 | `openrouter-gemini-3.1-pro-nvdla-full` |
| Opus4.7 | 0.417 | 28 min | ? | `dns-block-opus-nvdla-full` |
| DeepSeek | 0.000 | 225 min | $7.17 | `sandboxed-deepseek-nvdla-full` |
| GLM5.1 | 0.000 | 29 min | $0.43 | `openrouter-glm-5.1-nvdla-full` |
| Qwen27B | 0.000 | 57 min | ? | `openrouter-qwen3.6-27b-nvdla-full` |

### openpiton-e2e
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| DeepSeek | 1.000 | 146 min | $1.63 | `sandboxed-deepseek-openpiton-e2e-r2` |
| Opus4.7 | 0.000 | 34 min | ? | `dns-block-opus-openpiton-e2e` |
| GPT5.5 | 0.000 | 56 min | $2.84 | `codex-gpt55-xhigh-openpiton-e2e` |
| Gemini3.1Pro | 0.000 | 106 min | $0.01 | `openrouter-gemini-3.1-pro-openpiton-e2e` |
| Sonnet4.6 | 0.000 | 26 min | ? | `sandboxed-sonnet-openpiton-e2e` |
| Kimi | 0.000 | 95 min | $0.60 | `sandboxed-kimi-openpiton-e2e-r2` |
| GLM5.1 | 0.000 | 98 min | ? | `openrouter-glm-5.1-openpiton-e2e` |
| Qwen27B | 0.000 | 96 min | ? | `openrouter-qwen3.6-27b-openpiton-e2e` |

### openpiton-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 81 min | ? | `dns-block-opus-openpiton-full` |
| Kimi | 0.056 | 294 min | $4.09 | `sandboxed-kimi-openpiton-full-r2` |
| Gemini3.1Pro | 0.000 | 105 min | $0.03 | `openrouter-gemini-3.1-pro-openpiton-full` |
| DeepSeek | 0.000 | 187 min | $4.59 | `sandboxed-deepseek-openpiton-full-r2` |
| GLM5.1 | 0.000 | 57 min | $0.21 | `openrouter-glm-5.1-openpiton-full` |
| Qwen27B | 0.000 | 97 min | ? | `openrouter-qwen3.6-27b-openpiton-full` |

### pulp-common-cells-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 0.000 | 19 min | ? | `dns-block-opus-pulp-common-cells-full` |
| GPT5.5 | 0.000 | 24 min | $5.58 | `codex-gpt55-xhigh-pulp-common-cells-full` |
| Sonnet4.6 | 0.000 | 39 min | ? | `sandboxed-sonnet-pulp-common-cells-full` |
| DeepSeek | 0.000 | 134 min | $1.58 | `sandboxed-deepseek-pulp-common-cells-full-r2` |
| Kimi | 0.000 | 44 min | $2.70 | `sandboxed-kimi-pulp-common-cells-full` |
| GLM5.1 | 0.000 | 35 min | $2.65 | `openrouter-glm-5.1-pulp-common-cells-full` |
| Qwen27B | 0.000 | 6 min | ? | `openrouter-qwen3.6-27b-pulp-common-cells-full` |

### secworks-aes-full
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 19 min | ? | `dns-block-opus-secworks-aes-full` |
| GPT5.5 | 1.000 | 17 min | $3.58 | `codex-gpt55-xhigh-secworks-aes-full` |
| Gemini3.1Pro | 1.000 | 15 min | $1.49 | `openrouter-gemini-3.1-pro-secworks-aes-full` |
| Sonnet4.6 | 1.000 | 59 min | ? | `sandboxed-sonnet-secworks-aes-full` |
| QwenMax | 0.000 | 8 min | ? | `openrouter-qwen3.6-max-secworks-aes-full` |
| DeepSeek | 0.000 | 4 min | $0.02 | `sandboxed-deepseek-secworks-aes-full` |
| Kimi | 0.000 | 4 min | $0.08 | `sandboxed-kimi-secworks-aes-full` |
| GLM5.1 | 0.000 | 11 min | $0.29 | `openrouter-glm-5.1-secworks-aes-full` |
| Qwen27B | 0.000 | 1 min | ? | `openrouter-qwen3.6-27b-secworks-aes-full` |

### veer-el2-block
| Model | Reward | Runtime | Cost | Job |
|---|---|---|---|---|
| Opus4.7 | 1.000 | 18 min | ? | `dns-block-opus-veer-el2-block` |
| GPT5.5 | 1.000 | 52 min | $8.59 | `codex-gpt55-xhigh-veer-el2-block` |
| Gemini3.1Pro | 0.000 | 6 min | $0.03 | `openrouter-gemini-3.1-pro-veer-el2-block` |
| Sonnet4.6 | 0.000 | 66 min | ? | `sandboxed-sonnet-veer-el2-block` |
| QwenMax | 0.000 | 5 min | ? | `openrouter-qwen3.6-max-veer-el2-block` |
| DeepSeek | 0.000 | 94 min | $0.63 | `sandboxed-deepseek-veer-el2-block` |
| Kimi | 0.000 | 11 min | $0.37 | `sandboxed-kimi-veer-el2-block` |
| GLM5.1 | 0.000 | 112 min | $3.49 | `openrouter-glm-5.1-veer-el2-block` |
| Qwen27B | 0.000 | 102 min | ? | `openrouter-qwen3.6-27b-veer-el2-block` |
