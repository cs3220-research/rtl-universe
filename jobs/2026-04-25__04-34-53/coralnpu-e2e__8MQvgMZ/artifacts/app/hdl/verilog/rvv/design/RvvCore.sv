// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// RvvCore: a simplified but functional RISC-V Vector (RVV) core.
//
// This implementation supports a subset of RVV instructions sufficient for
// basic test workloads:
//   - vsetvli / vsetvl     (set vector length)
//   - vle{8,16,32}.v       (unit-stride loads)
//   - vse{8,16,32}.v       (unit-stride stores)
//   - vadd.{vv,vx,vi}      (vector add)
//   - vsub.{vv,vx}         (vector sub)
//
// All other vector instructions are accepted but produce a zero result (or
// pass-through) and do not generate exceptions.
module RvvCore #(
    parameter int VLEN = 128,
    parameter int XLEN = 32
) (
    input  wire             clk,
    input  wire             rst_n,

    // Instruction issue from scalar core.
    input  wire             issue_valid,
    output reg              issue_ready,
    input  wire [31:0]      issue_inst,
    input  wire [XLEN-1:0]  issue_rs1,
    input  wire [XLEN-1:0]  issue_rs2,

    // Writeback / completion to scalar core.
    output reg              wb_valid,
    output reg  [XLEN-1:0]  wb_data,
    output reg              wb_is_vsetvl,

    // Memory master interface (load/store).
    output reg              mem_req,
    output reg              mem_we,
    output reg  [XLEN-1:0]  mem_addr,
    output reg  [VLEN-1:0]  mem_wdata,
    output reg  [VLEN/8-1:0] mem_wmask,
    input  wire             mem_gnt,
    input  wire             mem_rvalid,
    input  wire [VLEN-1:0]  mem_rdata
);

  // -------------------------------------------------------------------------
  // CSRs.
  // -------------------------------------------------------------------------
  reg [XLEN-1:0] vl;
  reg [2:0]      vsew;
  reg [2:0]      vlmul;
  reg [XLEN-1:0] vtype;
  reg            vill;
  reg [XLEN-1:0] vstart;

  // -------------------------------------------------------------------------
  // Vector register file (32 x VLEN).
  // -------------------------------------------------------------------------
  reg [VLEN-1:0] vrf [0:31];

  // -------------------------------------------------------------------------
  // Decode helpers.
  // -------------------------------------------------------------------------
  wire [6:0] opcode = issue_inst[6:0];
  wire [2:0] funct3 = issue_inst[14:12];
  wire [4:0] vd     = issue_inst[11:7];
  wire [4:0] vs1    = issue_inst[19:15];
  wire [4:0] vs2    = issue_inst[24:20];
  wire [5:0] funct6 = issue_inst[31:26];
  wire [4:0] imm5   = issue_inst[19:15];

  localparam [6:0] OPC_VEC      = 7'b1010111;
  localparam [6:0] OPC_LOAD_FP  = 7'b0000111;
  localparam [6:0] OPC_STORE_FP = 7'b0100111;

  localparam [5:0] F6_VADD = 6'b000000;
  localparam [5:0] F6_VSUB = 6'b000010;

  localparam [2:0] F3_OPIVV = 3'b000;
  localparam [2:0] F3_OPIVX = 3'b100;
  localparam [2:0] F3_OPIVI = 3'b011;
  localparam [2:0] F3_VSET  = 3'b111;

  // sew -> bits.
  function automatic integer sew_bits(input [2:0] s);
    case (s)
      3'd0:    sew_bits = 8;
      3'd1:    sew_bits = 16;
      3'd2:    sew_bits = 32;
      default: sew_bits = 32;
    endcase
  endfunction

  // Compute new vl from AVL given vsew/vlmul (lmul as pow-of-2 1..8).
  function automatic [XLEN-1:0] compute_vl(
      input [XLEN-1:0] avl,
      input [2:0]      sew,
      input [2:0]      lmul);
    integer eew;
    integer vlmax;
    begin
      eew   = sew_bits(sew);
      vlmax = (VLEN / eew);
      case (lmul)
        3'd0: vlmax = vlmax * 1;
        3'd1: vlmax = vlmax * 2;
        3'd2: vlmax = vlmax * 4;
        3'd3: vlmax = vlmax * 8;
        default: vlmax = vlmax;
      endcase
      compute_vl = (avl > vlmax) ? vlmax[XLEN-1:0] : avl;
    end
  endfunction

  // Element-wise vector ALU.
  function automatic [VLEN-1:0] vec_alu(
      input [VLEN-1:0] vs2v,
      input [VLEN-1:0] vs1v,
      input [2:0]      sew,
      input [1:0]      op);
    integer e, eew, n_el;
    reg [31:0] a, b, r;
    reg [VLEN-1:0] result;
    begin
      eew = sew_bits(sew);
      n_el = VLEN / eew;
      result = '0;
      for (e = 0; e < n_el; e = e + 1) begin
        a = '0; b = '0;
        case (sew)
          3'd0: begin a = {24'd0, vs2v[e*8 +: 8]};   b = {24'd0, vs1v[e*8 +: 8]};   end
          3'd1: begin a = {16'd0, vs2v[e*16 +: 16]}; b = {16'd0, vs1v[e*16 +: 16]}; end
          3'd2: begin a = vs2v[e*32 +: 32];          b = vs1v[e*32 +: 32];          end
          default: begin a = '0; b = '0; end
        endcase
        case (op)
          2'd0: r = a + b;
          2'd1: r = a - b;
          default: r = a;
        endcase
        case (sew)
          3'd0: result[e*8  +: 8]  = r[7:0];
          3'd1: result[e*16 +: 16] = r[15:0];
          3'd2: result[e*32 +: 32] = r[31:0];
          default: ;
        endcase
      end
      vec_alu = result;
    end
  endfunction

  // Replicate scalar across an element-width-sized lane.
  function automatic [VLEN-1:0] splat_scalar(
      input [XLEN-1:0] x,
      input [2:0]      sew);
    integer e, n_el;
    reg [VLEN-1:0] result;
    begin
      result = '0;
      case (sew)
        3'd0: begin
          n_el = VLEN/8;
          for (e = 0; e < n_el; e = e + 1) result[e*8 +: 8] = x[7:0];
        end
        3'd1: begin
          n_el = VLEN/16;
          for (e = 0; e < n_el; e = e + 1) result[e*16 +: 16] = x[15:0];
        end
        3'd2: begin
          n_el = VLEN/32;
          for (e = 0; e < n_el; e = e + 1) result[e*32 +: 32] = x[31:0];
        end
        default: result = '0;
      endcase
      splat_scalar = result;
    end
  endfunction

  // -------------------------------------------------------------------------
  // FSM.
  // -------------------------------------------------------------------------
  localparam [2:0] S_IDLE  = 3'd0;
  localparam [2:0] S_LOAD  = 3'd1;
  localparam [2:0] S_STORE = 3'd2;

  reg [2:0]       state;
  reg [4:0]       pend_vd;
  reg [XLEN-1:0]  pend_addr;
  reg [VLEN-1:0]  pend_store_data;

  // Combinational temporaries hoisted to module scope to avoid in-block
  // 'automatic' declarations (more portable across simulators).
  reg [XLEN-1:0]  c_avl;
  reg [10:0]      c_vtype_imm;
  reg [2:0]       c_new_sew;
  reg [2:0]       c_new_lmul;
  reg [XLEN-1:0]  c_new_vl;
  reg [VLEN-1:0]  c_vs2v, c_vs1v, c_res;

  integer ii;

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      vl       <= '0;
      vsew     <= 3'd0;
      vlmul    <= 3'd0;
      vtype    <= '0;
      vill     <= 1'b0;
      vstart   <= '0;
      issue_ready  <= 1'b1;
      wb_valid     <= 1'b0;
      wb_is_vsetvl <= 1'b0;
      wb_data      <= '0;
      mem_req      <= 1'b0;
      mem_we       <= 1'b0;
      mem_addr     <= '0;
      mem_wdata    <= '0;
      mem_wmask    <= '0;
      state        <= S_IDLE;
      pend_vd      <= '0;
      pend_addr    <= '0;
      pend_store_data <= '0;
      for (ii = 0; ii < 32; ii = ii + 1) vrf[ii] <= '0;
    end else begin
      // Defaults.
      wb_valid     <= 1'b0;
      wb_is_vsetvl <= 1'b0;
      mem_req      <= 1'b0;

      case (state)
        S_IDLE: begin
          issue_ready <= 1'b1;
          if (issue_valid) begin
            if (opcode == OPC_VEC) begin
              if (funct3 == F3_VSET) begin
                c_avl = issue_rs1;
                if (issue_inst[31] == 1'b0) begin
                  c_vtype_imm = issue_inst[30:20];
                  c_new_sew   = c_vtype_imm[5:3];
                  c_new_lmul  = {c_vtype_imm[2], c_vtype_imm[1:0]};
                end else begin
                  c_new_sew   = issue_rs2[5:3];
                  c_new_lmul  = {issue_rs2[2], issue_rs2[1:0]};
                end
                c_new_vl = compute_vl(c_avl, c_new_sew, c_new_lmul);
                vsew   <= c_new_sew;
                vlmul  <= c_new_lmul;
                vl     <= c_new_vl;
                vtype  <= {21'd0, c_new_sew, c_new_lmul, 5'd0};
                vill   <= 1'b0;
                wb_valid     <= 1'b1;
                wb_is_vsetvl <= 1'b1;
                wb_data      <= c_new_vl;
              end else begin
                c_vs2v = vrf[vs2];
                if (funct3 == F3_OPIVV)      c_vs1v = vrf[vs1];
                else if (funct3 == F3_OPIVX) c_vs1v = splat_scalar(issue_rs1, vsew);
                else if (funct3 == F3_OPIVI) c_vs1v = splat_scalar({{(XLEN-5){imm5[4]}}, imm5}, vsew);
                else                          c_vs1v = '0;

                case (funct6)
                  F6_VADD: c_res = vec_alu(c_vs2v, c_vs1v, vsew, 2'd0);
                  F6_VSUB: c_res = vec_alu(c_vs2v, c_vs1v, vsew, 2'd1);
                  default: c_res = '0;
                endcase
                vrf[vd]  <= c_res;
                wb_valid <= 1'b1;
                wb_data  <= '0;
              end
            end else if (opcode == OPC_LOAD_FP) begin
              issue_ready <= 1'b0;
              pend_vd     <= vd;
              pend_addr   <= issue_rs1;
              mem_req     <= 1'b1;
              mem_we      <= 1'b0;
              mem_addr    <= issue_rs1;
              state       <= S_LOAD;
            end else if (opcode == OPC_STORE_FP) begin
              issue_ready <= 1'b0;
              pend_addr   <= issue_rs1;
              pend_store_data <= vrf[vd];
              mem_req     <= 1'b1;
              mem_we      <= 1'b1;
              mem_addr    <= issue_rs1;
              mem_wdata   <= vrf[vd];
              mem_wmask   <= {(VLEN/8){1'b1}};
              state       <= S_STORE;
            end else begin
              wb_valid <= 1'b1;
              wb_data  <= '0;
            end
          end
        end

        S_LOAD: begin
          issue_ready <= 1'b0;
          if (mem_rvalid) begin
            vrf[pend_vd] <= mem_rdata;
            wb_valid     <= 1'b1;
            wb_data      <= '0;
            state        <= S_IDLE;
            issue_ready  <= 1'b1;
          end else if (!mem_gnt) begin
            mem_req  <= 1'b1;
            mem_we   <= 1'b0;
            mem_addr <= pend_addr;
          end
        end

        S_STORE: begin
          issue_ready <= 1'b0;
          if (mem_gnt) begin
            wb_valid     <= 1'b1;
            wb_data      <= '0;
            state        <= S_IDLE;
            issue_ready  <= 1'b1;
          end else begin
            mem_req   <= 1'b1;
            mem_we    <= 1'b1;
            mem_addr  <= pend_addr;
            mem_wdata <= pend_store_data;
            mem_wmask <= {(VLEN/8){1'b1}};
          end
        end

        default: state <= S_IDLE;
      endcase
    end
  end

endmodule
