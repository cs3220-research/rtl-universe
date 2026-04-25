# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

load("@rules_hdl//verilator:defs.bzl", "verilator_cc_library")
load("@rules_hdl//verilog:providers.bzl", "verilog_library")

package(default_visibility = ["//visibility:public"])

CORE_MINI_AXI_VOPTS = [
    "-DUSE_GENERIC",
    "-Wno-UNOPTFLAT",
    "-Wno-ASCRANGE",
    "-Wno-WIDTHEXPAND",
    "-Wno-WIDTHTRUNC",
    "-Wno-UNSIGNED",
    "--pins-bv",
    "2",
]

RVV_CORE_MINI_AXI_VOPTS = [
    "-DUSE_GENERIC",
    "-DTB_SUPPORT",
    "-DVLEN_128",
    "-Wno-WIDTH",
    "-Wno-CASEINCOMPLETE",
    "-Wno-LATCH",
    "-Wno-SIDEEFFECT",
    "-Wno-MULTIDRIVEN",
    "-Wno-UNOPTFLAT",
    "-Wno-BLKANDNBLK",
    "-Wno-CASEX",
    "-Wno-ASCRANGE",
    "-Wno-WIDTHEXPAND",
    "-Wno-WIDTHTRUNC",
    "-Wno-UNSIGNED",
    "-Ihdl/verilog/rvv/inc",
    "-Ihdl/verilog/rvv/design/FPnew/common_cells/inc",
    "--pins-bv",
    "2",
]

# ===========================================================================
# CoreMiniAxi
# ===========================================================================

genrule(
    name = "core_mini_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:CoreMiniAxi.sv",
        "//hdl/generated:VCoreMiniAxi_parameters.h",
    ],
    outs = [
        "CoreMiniAxi.sv",
        "VCoreMiniAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:CoreMiniAxi.sv) $(location CoreMiniAxi.sv) && " +
          "cp $(location //hdl/generated:VCoreMiniAxi_parameters.h) $(location VCoreMiniAxi_parameters.h)",
)

verilog_library(
    name = "core_mini_axi_cc_library_verilog",
    srcs = [":CoreMiniAxi.sv"],
)

verilator_cc_library(
    name = "core_mini_axi_cc_library",
    module = ":core_mini_axi_cc_library_verilog",
    module_top = "CoreMiniAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "core_mini_axi_cc_library_cc",
    module = ":core_mini_axi_cc_library_verilog",
    module_top = "CoreMiniAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# CoreMiniVerificationAxi
# ===========================================================================

genrule(
    name = "core_mini_verification_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:CoreMiniVerificationAxi.sv",
        "//hdl/generated:VCoreMiniVerificationAxi_parameters.h",
    ],
    outs = [
        "CoreMiniVerificationAxi.sv",
        "VCoreMiniVerificationAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:CoreMiniVerificationAxi.sv) $(location CoreMiniVerificationAxi.sv) && " +
          "cp $(location //hdl/generated:VCoreMiniVerificationAxi_parameters.h) $(location VCoreMiniVerificationAxi_parameters.h)",
)

verilog_library(
    name = "core_mini_verification_axi_cc_library_verilog",
    srcs = [":CoreMiniVerificationAxi.sv"],
)

verilator_cc_library(
    name = "core_mini_verification_axi_cc_library",
    module = ":core_mini_verification_axi_cc_library_verilog",
    module_top = "CoreMiniVerificationAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "core_mini_verification_axi_cc_library_cc",
    module = ":core_mini_verification_axi_cc_library_verilog",
    module_top = "CoreMiniVerificationAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# CoreMiniHighmemAxi
# ===========================================================================

genrule(
    name = "core_mini_highmem_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:CoreMiniHighmemAxi.sv",
        "//hdl/generated:VCoreMiniHighmemAxi_parameters.h",
    ],
    outs = [
        "CoreMiniHighmemAxi.sv",
        "VCoreMiniHighmemAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:CoreMiniHighmemAxi.sv) $(location CoreMiniHighmemAxi.sv) && " +
          "cp $(location //hdl/generated:VCoreMiniHighmemAxi_parameters.h) $(location VCoreMiniHighmemAxi_parameters.h)",
)

verilog_library(
    name = "core_mini_highmem_axi_cc_library_verilog",
    srcs = [":CoreMiniHighmemAxi.sv"],
)

verilator_cc_library(
    name = "core_mini_highmem_axi_cc_library",
    module = ":core_mini_highmem_axi_cc_library_verilog",
    module_top = "CoreMiniHighmemAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "core_mini_highmem_axi_cc_library_cc",
    module = ":core_mini_highmem_axi_cc_library_verilog",
    module_top = "CoreMiniHighmemAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# CoreMini_ITCM512KB_DTCM512KBAxi
# ===========================================================================

genrule(
    name = "core_mini_itcm512kb_dtcm512kb_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:CoreMini_ITCM512KB_DTCM512KBAxi.sv",
        "//hdl/generated:VCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h",
    ],
    outs = [
        "CoreMini_ITCM512KB_DTCM512KBAxi.sv",
        "VCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:CoreMini_ITCM512KB_DTCM512KBAxi.sv) $(location CoreMini_ITCM512KB_DTCM512KBAxi.sv) && " +
          "cp $(location //hdl/generated:VCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h) $(location VCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h)",
)

verilog_library(
    name = "core_mini_itcm512kb_dtcm512kb_axi_cc_library_verilog",
    srcs = [":CoreMini_ITCM512KB_DTCM512KBAxi.sv"],
)

verilator_cc_library(
    name = "core_mini_itcm512kb_dtcm512kb_axi_cc_library",
    module = ":core_mini_itcm512kb_dtcm512kb_axi_cc_library_verilog",
    module_top = "CoreMini_ITCM512KB_DTCM512KBAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "core_mini_itcm512kb_dtcm512kb_axi_cc_library_cc",
    module = ":core_mini_itcm512kb_dtcm512kb_axi_cc_library_verilog",
    module_top = "CoreMini_ITCM512KB_DTCM512KBAxi",
    vopts = CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# RvvCoreMiniAxi
# ===========================================================================

genrule(
    name = "rvv_core_mini_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:RvvCoreMiniAxi.sv",
        "//hdl/generated:VRvvCoreMiniAxi_parameters.h",
    ],
    outs = [
        "RvvCoreMiniAxi.sv",
        "VRvvCoreMiniAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:RvvCoreMiniAxi.sv) $(location RvvCoreMiniAxi.sv) && " +
          "cp $(location //hdl/generated:VRvvCoreMiniAxi_parameters.h) $(location VRvvCoreMiniAxi_parameters.h)",
)

verilog_library(
    name = "rvv_core_mini_axi_cc_library_verilog",
    srcs = [":RvvCoreMiniAxi.sv"],
)

verilator_cc_library(
    name = "rvv_core_mini_axi_cc_library",
    module = ":rvv_core_mini_axi_cc_library_verilog",
    module_top = "RvvCoreMiniAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "rvv_core_mini_axi_cc_library_cc",
    module = ":rvv_core_mini_axi_cc_library_verilog",
    module_top = "RvvCoreMiniAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# RvvCoreMiniVerificationAxi
# ===========================================================================

genrule(
    name = "rvv_core_mini_verification_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:RvvCoreMiniVerificationAxi.sv",
        "//hdl/generated:VRvvCoreMiniVerificationAxi_parameters.h",
    ],
    outs = [
        "RvvCoreMiniVerificationAxi.sv",
        "VRvvCoreMiniVerificationAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:RvvCoreMiniVerificationAxi.sv) $(location RvvCoreMiniVerificationAxi.sv) && " +
          "cp $(location //hdl/generated:VRvvCoreMiniVerificationAxi_parameters.h) $(location VRvvCoreMiniVerificationAxi_parameters.h)",
)

verilog_library(
    name = "rvv_core_mini_verification_axi_cc_library_verilog",
    srcs = [":RvvCoreMiniVerificationAxi.sv"],
)

verilator_cc_library(
    name = "rvv_core_mini_verification_axi_cc_library",
    module = ":rvv_core_mini_verification_axi_cc_library_verilog",
    module_top = "RvvCoreMiniVerificationAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "rvv_core_mini_verification_axi_cc_library_cc",
    module = ":rvv_core_mini_verification_axi_cc_library_verilog",
    module_top = "RvvCoreMiniVerificationAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# RvvCoreMiniHighmemAxi
# ===========================================================================

genrule(
    name = "rvv_core_mini_highmem_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:RvvCoreMiniHighmemAxi.sv",
        "//hdl/generated:VRvvCoreMiniHighmemAxi_parameters.h",
    ],
    outs = [
        "RvvCoreMiniHighmemAxi.sv",
        "VRvvCoreMiniHighmemAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:RvvCoreMiniHighmemAxi.sv) $(location RvvCoreMiniHighmemAxi.sv) && " +
          "cp $(location //hdl/generated:VRvvCoreMiniHighmemAxi_parameters.h) $(location VRvvCoreMiniHighmemAxi_parameters.h)",
)

verilog_library(
    name = "rvv_core_mini_highmem_axi_cc_library_verilog",
    srcs = [":RvvCoreMiniHighmemAxi.sv"],
)

verilator_cc_library(
    name = "rvv_core_mini_highmem_axi_cc_library",
    module = ":rvv_core_mini_highmem_axi_cc_library_verilog",
    module_top = "RvvCoreMiniHighmemAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "rvv_core_mini_highmem_axi_cc_library_cc",
    module = ":rvv_core_mini_highmem_axi_cc_library_verilog",
    module_top = "RvvCoreMiniHighmemAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# RvvCoreMini_ITCM512KB_DTCM512KBAxi
# ===========================================================================

genrule(
    name = "rvv_core_mini_itcm512kb_dtcm512kb_axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:RvvCoreMini_ITCM512KB_DTCM512KBAxi.sv",
        "//hdl/generated:VRvvCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h",
    ],
    outs = [
        "RvvCoreMini_ITCM512KB_DTCM512KBAxi.sv",
        "VRvvCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:RvvCoreMini_ITCM512KB_DTCM512KBAxi.sv) $(location RvvCoreMini_ITCM512KB_DTCM512KBAxi.sv) && " +
          "cp $(location //hdl/generated:VRvvCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h) $(location VRvvCoreMini_ITCM512KB_DTCM512KBAxi_parameters.h)",
)

verilog_library(
    name = "rvv_core_mini_itcm512kb_dtcm512kb_axi_cc_library_verilog",
    srcs = [":RvvCoreMini_ITCM512KB_DTCM512KBAxi.sv"],
)

verilator_cc_library(
    name = "rvv_core_mini_itcm512kb_dtcm512kb_axi_cc_library",
    module = ":rvv_core_mini_itcm512kb_dtcm512kb_axi_cc_library_verilog",
    module_top = "RvvCoreMini_ITCM512KB_DTCM512KBAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = True,
)

verilator_cc_library(
    name = "rvv_core_mini_itcm512kb_dtcm512kb_axi_cc_library_cc",
    module = ":rvv_core_mini_itcm512kb_dtcm512kb_axi_cc_library_verilog",
    module_top = "RvvCoreMini_ITCM512KB_DTCM512KBAxi",
    vopts = RVV_CORE_MINI_AXI_VOPTS,
    systemc = False,
)

# ===========================================================================
# RvvCoreMiniTlul (for completeness)
# ===========================================================================

genrule(
    name = "rvv_core_mini_tlul_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:RvvCoreMiniTlul.sv",
        "//hdl/generated:VRvvCoreMiniTlul_parameters.h",
    ],
    outs = [
        "RvvCoreMiniTlul.sv",
        "VRvvCoreMiniTlul_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:RvvCoreMiniTlul.sv) $(location RvvCoreMiniTlul.sv) && " +
          "cp $(location //hdl/generated:VRvvCoreMiniTlul_parameters.h) $(location VRvvCoreMiniTlul_parameters.h)",
)

verilog_library(
    name = "rvv_core_mini_tlul_cc_library_verilog",
    srcs = [":RvvCoreMiniTlul.sv"],
)

filegroup(
    name = "rvv_core_mini_tlul_verilog",
    srcs = [":rvv_core_mini_tlul_cc_library_verilog"],
)

# ===========================================================================
# Core (for completeness / verilator_sim non-scored tests)
# ===========================================================================

genrule(
    name = "core_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:Core.sv",
        "//hdl/generated:VCore_parameters.h",
    ],
    outs = [
        "Core.sv",
        "VCore_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:Core.sv) $(location Core.sv) && " +
          "cp $(location //hdl/generated:VCore_parameters.h) $(location VCore_parameters.h)",
)

verilog_library(
    name = "core_cc_library_verilog",
    srcs = [":Core.sv"],
)

verilator_cc_library(
    name = "core_cc_library",
    module = ":core_cc_library_verilog",
    module_top = "Core",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)

# ===========================================================================
# CoreScalar
# ===========================================================================

genrule(
    name = "core_scalar_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:CoreScalar.sv",
        "//hdl/generated:VCoreScalar_parameters.h",
    ],
    outs = [
        "CoreScalar.sv",
        "VCoreScalar_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:CoreScalar.sv) $(location CoreScalar.sv) && " +
          "cp $(location //hdl/generated:VCoreScalar_parameters.h) $(location VCoreScalar_parameters.h)",
)

verilog_library(
    name = "core_scalar_cc_library_verilog",
    srcs = [":CoreScalar.sv"],
)

verilator_cc_library(
    name = "core_scalar_cc_library",
    module = ":core_scalar_cc_library_verilog",
    module_top = "CoreScalar",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)

# ===========================================================================
# CoreMini
# ===========================================================================

genrule(
    name = "core_mini_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:CoreMini.sv",
        "//hdl/generated:VCoreMini_parameters.h",
    ],
    outs = [
        "CoreMini.sv",
        "VCoreMini_parameters.h",
    ],
    cmd = "cp $(location //hdl/generated:CoreMini.sv) $(location CoreMini.sv) && " +
          "cp $(location //hdl/generated:VCoreMini_parameters.h) $(location VCoreMini_parameters.h)",
)

verilog_library(
    name = "core_mini_cc_library_verilog",
    srcs = [":CoreMini.sv"],
)

verilator_cc_library(
    name = "core_mini_cc_library",
    module = ":core_mini_cc_library_verilog",
    module_top = "CoreMini",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)

# ===========================================================================
# DBus2AxiV2
# ===========================================================================

genrule(
    name = "dbus2axi_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:DBus2AxiV2.sv",
    ],
    outs = [
        "DBus2AxiV2.sv",
    ],
    cmd = "cp $(location //hdl/generated:DBus2AxiV2.sv) $(location DBus2AxiV2.sv)",
)

verilog_library(
    name = "dbus2axi_cc_library_verilog",
    srcs = [":DBus2AxiV2.sv"],
)

verilator_cc_library(
    name = "dbus2axi_cc_library",
    module = ":dbus2axi_cc_library_verilog",
    module_top = "DBus2AxiV2",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)

# ===========================================================================
# L1DCache
# ===========================================================================

genrule(
    name = "l1dcache_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:L1DCache.sv",
    ],
    outs = [
        "L1DCache.sv",
    ],
    cmd = "cp $(location //hdl/generated:L1DCache.sv) $(location L1DCache.sv)",
)

verilog_library(
    name = "l1dcache_cc_library_verilog",
    srcs = [":L1DCache.sv"],
)

verilator_cc_library(
    name = "l1dcache_cc_library",
    module = ":l1dcache_cc_library_verilog",
    module_top = "L1DCache",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)

# ===========================================================================
# L1DCacheBank
# ===========================================================================

genrule(
    name = "l1dcachebank_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:L1DCacheBank.sv",
    ],
    outs = [
        "L1DCacheBank.sv",
    ],
    cmd = "cp $(location //hdl/generated:L1DCacheBank.sv) $(location L1DCacheBank.sv)",
)

verilog_library(
    name = "l1dcachebank_cc_library_verilog",
    srcs = [":L1DCacheBank.sv"],
)

verilator_cc_library(
    name = "l1dcachebank_cc_library",
    module = ":l1dcachebank_cc_library_verilog",
    module_top = "L1DCacheBank",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)

# ===========================================================================
# L1ICache
# ===========================================================================

genrule(
    name = "l1icache_cc_library_emit_verilog",
    srcs = [
        "//hdl/generated:L1ICache.sv",
    ],
    outs = [
        "L1ICache.sv",
    ],
    cmd = "cp $(location //hdl/generated:L1ICache.sv) $(location L1ICache.sv)",
)

verilog_library(
    name = "l1icache_cc_library_verilog",
    srcs = [":L1ICache.sv"],
)

verilator_cc_library(
    name = "l1icache_cc_library",
    module = ":l1icache_cc_library_verilog",
    module_top = "L1ICache",
    vopts = ["--pins-bv", "2"],
    systemc = True,
)
