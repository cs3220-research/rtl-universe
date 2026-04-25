// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <cstddef>
#include <cstdint>

// Abstract FTDI hardware interface (for testability).
class FtdiInterface {
 public:
  virtual ~FtdiInterface() = default;
  virtual int write_data(const uint8_t* buf, int size) = 0;
  virtual int read_data(uint8_t* buf, int size) = 0;
  virtual int purge_buffers() = 0;
};

// SPI master over FTDI MPSSE, implementing the Spi2TLULv2 host protocol.
class SpiMaster {
 public:
  // Bytes per read beat: 1 sync byte (0xFE) + 16 data bytes.
  static constexpr size_t kBytesPerBeat = 17;
  // Extra bytes to read ahead to absorb FPGA CDC latency.
  static constexpr size_t kInitialLatencyPaddingBytes = 2048;
  // Max beats per single SPI transaction (16-bit beats-1 field).
  static constexpr int kMaxBeatsPerTransaction = 65535;

  explicit SpiMaster(FtdiInterface* ftdi);

  // Send a v2 write frame: opcode=0x02, addr (BE), beats-1 (BE), then
  // num_beats*16 bytes of data.  Waits for the drain byte after CS high.
  void v2_write_lines(uint32_t addr, const uint8_t* data, int num_beats);

  // Send a v2 read frame and collect the response.
  // Returns true if all num_beats sync tokens were found and data extracted.
  bool v2_read_lines(uint32_t addr, int num_beats, uint8_t* out);

  // Read-modify-write a 32-bit word at addr (must be 4-byte aligned, within
  // a 16-byte aligned line).
  void write_word(uint32_t addr, uint32_t value);

  // Write arbitrary bytes using RMW for partial 16-byte beats.
  void v2_write_data(uint32_t addr, const uint8_t* data, size_t len);

  // Read arbitrary bytes (aligned to 16-byte beat boundaries, trimmed).
  bool v2_read_data(uint32_t addr, size_t len, uint8_t* out);

  // Assert then release the hardware reset pin (3 MPSSE GPIO writes).
  void device_reset();

 private:
  FtdiInterface* ftdi_;

  // MPSSE helpers
  void cs_low();
  void cs_high();
  void mpsse_write_bytes(const uint8_t* buf, int n);
  void send_spi_header(uint8_t opcode, uint32_t addr, uint16_t beats_minus1);
};
