# Secworks AES — Restore the RTL Implementation

You are in `/app`, a **FuseSoC + Icarus Verilog** repository for the
**secworks AES crypto core** — a well-tested Verilog implementation of the
AES symmetric block cipher (NIST FIPS 197) supporting 128-bit and 256-bit
keys.

## Repository Structure

```
aes.core              — FuseSoC core descriptor (defines filesets and targets)
src/
  rtl/                — RTL implementation files (STRIPPED — see below)
    aes.v             — Top-level wrapper with register-file bus interface
    aes_core.v        — AES core: orchestrates key expansion + cipher
    aes_key_mem.v     — Key memory: key expansion (10/14 rounds)
    aes_encipher_block.v — Encryption datapath (ShiftRows, SubBytes, MixColumns, AddRoundKey)
    aes_inv_sbox.v    — Inverse S-box (combinational lookup table)
    aes_sbox.v        — Forward S-box (combinational lookup table)
    aes_decipher_block.v — Decryption datapath (inverse operations)
  tb/                 — Testbenches (KEPT — do not modify)
    tb_aes.v          — Top-level wrapper testbench (register bus interface)
    tb_aes_core.v     — Core-level testbench (direct signal interface)
    tb_aes_key_mem.v  — Key memory testbench
    tb_aes_encipher_block.v — Encipher block testbench
    tb_aes_decipher_block.v — Decipher block testbench
  model/python/       — Reference Python model (useful for debugging)
    aes.py            — Pure-Python AES implementation
    aes_key_gen.py    — Python key expansion reference
README.md             — Core description and usage notes
```

## What Has Been Stripped

All **RTL implementation files** under `src/rtl/` have been emptied (zero
bytes). The `.core` file and all testbenches are intact.

**Stripped** (you must implement these):
- `src/rtl/aes.v` — register-file wrapper with chip-select/write-enable bus
- `src/rtl/aes_core.v` — core FSM coordinating key expansion and cipher ops
- `src/rtl/aes_key_mem.v` — key schedule memory (Rijndael key expansion)
- `src/rtl/aes_encipher_block.v` — round-based encryption datapath
- `src/rtl/aes_decipher_block.v` — round-based decryption datapath
- `src/rtl/aes_sbox.v` — 8-bit S-box (256-entry ROM lookup)
- `src/rtl/aes_inv_sbox.v` — 8-bit inverse S-box (256-entry ROM lookup)

**Kept** (do not modify):
- All testbenches in `src/tb/`
- `aes.core` (FuseSoC descriptor)
- `README.md`, `LICENSE`
- `src/model/python/` (reference Python model — read-only reference)

## Your Task

Implement the seven RTL files so that all five FuseSoC testbench targets pass:

1. `tb_aes` — 20 NIST ECB test vectors (encrypt + decrypt, 128-bit and 256-bit keys)
2. `tb_aes_core` — 20 NIST ECB test vectors via the direct core interface
3. `tb_aes_key_mem` — key expansion for 9 different keys (128-bit and 256-bit)
4. `tb_aes_encipher_block` — 8 NIST encryption test vectors
5. `tb_aes_decipher_block` — 8 NIST decryption test vectors

## Scoring

Reward is **proportional across test cases**: `passed_test_cases / total_test_cases`.

Each testbench prints a summary line like:
```
*** All 20 test cases completed successfully
```
or on failure:
```
*** 20 tests completed - 3 test cases did not complete successfully.
```

Total test cases across all testbenches (green baseline): **~65** individual
test cases (20 + 20 + 9 + 8 + 8 respectively, where key-mem tests cover
multiple round keys per key).

Getting a subset of modules correct still earns partial credit.

## Environment

- **Icarus Verilog (`iverilog`)** and **FuseSoC** are pre-installed.
- The AES core is already registered in the FuseSoC library as `secworks:crypto:aes`.
- `/app` is a fresh git repo — use it freely.

## Useful Commands

```bash
# Run the top-level testbench (most comprehensive)
fusesoc run --target=tb_aes secworks:crypto:aes

# Run the core-level testbench
fusesoc run --target=tb_aes_core secworks:crypto:aes

# Run the key memory testbench
fusesoc run --target=tb_aes_key_mem secworks:crypto:aes

# Run the encipher block testbench
fusesoc run --target=tb_aes_encipher_block secworks:crypto:aes

# Run the decipher block testbench
fusesoc run --target=tb_aes_decipher_block secworks:crypto:aes

# Show core targets and filesets
fusesoc core show secworks:crypto:aes

# Compile manually with iverilog (useful for faster iteration)
# Top-level:
iverilog -Wall -o /tmp/tb_aes.sim \
    src/tb/tb_aes.v \
    src/rtl/aes.v src/rtl/aes_core.v src/rtl/aes_key_mem.v \
    src/rtl/aes_sbox.v src/rtl/aes_inv_sbox.v \
    src/rtl/aes_encipher_block.v src/rtl/aes_decipher_block.v \
  && /tmp/tb_aes.sim 2>&1 | tail -5

# Encipher block only:
iverilog -Wall -o /tmp/enc.sim \
    src/tb/tb_aes_encipher_block.v \
    src/rtl/aes_encipher_block.v src/rtl/aes_sbox.v \
  && /tmp/enc.sim 2>&1 | tail -5

# Decipher block only:
iverilog -Wall -o /tmp/dec.sim \
    src/tb/tb_aes_decipher_block.v \
    src/rtl/aes_decipher_block.v src/rtl/aes_inv_sbox.v \
  && /tmp/dec.sim 2>&1 | tail -5

# Key memory only:
iverilog -Wall -o /tmp/keymem.sim \
    src/tb/tb_aes_key_mem.v \
    src/rtl/aes_key_mem.v src/rtl/aes_sbox.v \
  && /tmp/keymem.sim 2>&1 | tail -5
```

## Implementation Guidance

The module hierarchy is:
```
aes (top wrapper)
  └── aes_core
        ├── aes_key_mem
        │     └── aes_sbox (shared, used for key expansion SubWord)
        ├── aes_encipher_block
        │     └── aes_sbox (shared via sboxw/new_sboxw ports)
        └── aes_decipher_block
              └── aes_inv_sbox (internal)
```

The S-box and inverse S-box are pure combinational modules: they take a 32-bit
input word (`sboxw`) and output a 32-bit substituted word (`new_sboxw`), applying
the 8-bit AES S-box substitution to each byte.

Key interfaces to implement (derived from testbench port connections):
- `aes`: bus interface with `clk, reset_n, cs, we, address[7:0], write_data[31:0], read_data[31:0]`
- `aes_core`: `clk, reset_n, encdec, init, next, ready, key[255:0], keylen, block[127:0], result[127:0], result_valid`
- `aes_key_mem`: `clk, reset_n, key[255:0], keylen, init, round[3:0], round_key[127:0], ready, sboxw[31:0], new_sboxw[31:0]`
- `aes_encipher_block`: `clk, reset_n, next, keylen, round[3:0], round_key[127:0], sboxw[31:0], new_sboxw[31:0], block[127:0], new_block[127:0], ready`
- `aes_decipher_block`: `clk, reset_n, next, keylen, round[3:0], round_key[127:0], block[127:0], new_block[127:0], ready`

Refer to `README.md` for the register map used by the `aes` top-level wrapper,
and to `src/model/python/` for the reference AES algorithm implementation.

NIST test vectors (from FIPS 197 and SP 800-38A) are embedded in the
testbenches — use them to verify correctness incrementally.
