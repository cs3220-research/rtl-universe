// Copyright 2026 Google LLC
// SPDX-License-Identifier: Apache-2.0
//
// MultiFifo: a FIFO that accepts and produces up to N elements per cycle.
module MultiFifo #(
    parameter int W     = 32,
    parameter int DEPTH = 8,
    parameter int N     = 2
) (
    input  wire                       clk,
    input  wire                       rst_n,

    // Push side: up to N elements per cycle.
    input  wire [N-1:0]               push_valid,
    output wire [N-1:0]               push_ready,
    input  wire [N-1:0][W-1:0]        push_data,

    // Pop side: up to N elements per cycle.
    output wire [N-1:0]               pop_valid,
    input  wire [N-1:0]               pop_ready,
    output wire [N-1:0][W-1:0]        pop_data,

    output wire [$clog2(DEPTH+1)-1:0] count
);
  localparam int AW = (DEPTH > 1) ? $clog2(DEPTH) : 1;

  reg [W-1:0] mem [0:DEPTH-1];
  reg [AW:0]  wptr, rptr;

  wire [AW:0] occ = wptr - rptr;
  assign count    = occ;

  // Available slots / elements.
  wire [AW:0] free_slots = DEPTH - occ;

  // Push readiness: ready[i] means we have at least i+1 free slots for the
  // first i+1 elements.
  genvar gi;
  generate
    for (gi = 0; gi < N; gi = gi + 1) begin : g_push_ready
      assign push_ready[gi] = (free_slots > gi);
    end
  endgenerate

  // Pop validity: valid[i] means there are at least i+1 elements queued.
  generate
    for (gi = 0; gi < N; gi = gi + 1) begin : g_pop_valid
      assign pop_valid[gi] = (occ > gi);
      assign pop_data[gi]  = mem[(rptr + gi) % DEPTH];
    end
  endgenerate

  // Count actual pushes/pops this cycle (in-order).
  integer i;
  reg [$clog2(N+1)-1:0] n_push, n_pop;

  always @(*) begin
    n_push = 0;
    for (i = 0; i < N; i = i + 1) begin
      if (push_valid[i] && push_ready[i] && (n_push == i)) n_push = n_push + 1;
    end
    n_pop = 0;
    for (i = 0; i < N; i = i + 1) begin
      if (pop_valid[i] && pop_ready[i] && (n_pop == i)) n_pop = n_pop + 1;
    end
  end

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) begin
      wptr <= '0;
      rptr <= '0;
    end else begin
      for (i = 0; i < N; i = i + 1) begin
        if (i < n_push) begin
          mem[(wptr + i) % DEPTH] <= push_data[i];
        end
      end
      wptr <= wptr + n_push;
      rptr <= rptr + n_pop;
    end
  end

endmodule
