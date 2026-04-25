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

#include "spi_master.h"

#include <algorithm>
#include <cstring>
#include <vector>

SpiMaster::SpiMaster(FtdiInterface* ftdi) : ftdi_(ftdi) {}

void SpiMaster::cs_low() {
  uint8_t cmd[3] = {0x80, 0x00, 0x0b};
  ftdi_->write_data(cmd, 3);
}

void SpiMaster::cs_high() {
  uint8_t cmd[3] = {0x80, 0x08, 0x0b};
  ftdi_->write_data(cmd, 3);
}

void SpiMaster::mpsse_write_bytes(const uint8_t* buf, int n) {
  uint8_t hdr[3] = {0x11,
                    static_cast<uint8_t>((n - 1) & 0xFF),
                    static_cast<uint8_t>((n - 1) >> 8)};
  ftdi_->write_data(hdr, 3);
  ftdi_->write_data(buf, n);
}

void SpiMaster::send_spi_header(uint8_t opcode, uint32_t addr,
                                uint16_t beats_minus1) {
  uint8_t hdr[7] = {
      opcode,
      static_cast<uint8_t>(addr >> 24),
      static_cast<uint8_t>(addr >> 16),
      static_cast<uint8_t>(addr >> 8),
      static_cast<uint8_t>(addr),
      static_cast<uint8_t>(beats_minus1 >> 8),
      static_cast<uint8_t>(beats_minus1),
  };
  mpsse_write_bytes(hdr, 7);
}

void SpiMaster::v2_write_lines(uint32_t addr, const uint8_t* data,
                                int num_beats) {
  cs_low();
  send_spi_header(0x02, addr, static_cast<uint16_t>(num_beats - 1));
  mpsse_write_bytes(data, num_beats * 16);
  cs_high();
  // Drain: wait for pipeline flush after CS deassert.
  uint8_t drain;
  while (ftdi_->read_data(&drain, 1) == 1 && drain == 0xFE) {}
}

bool SpiMaster::v2_read_lines(uint32_t addr, int num_beats, uint8_t* out) {
  size_t window = static_cast<size_t>(num_beats) * kBytesPerBeat +
                  kInitialLatencyPaddingBytes;

  cs_low();
  send_spi_header(0x01, addr, static_cast<uint16_t>(num_beats - 1));
  // Issue MPSSE read commands (max 65535 bytes per command).
  size_t to_req = window;
  while (to_req > 0) {
    size_t chunk = std::min(to_req, static_cast<size_t>(65535));
    uint8_t rc[3] = {0x20,
                     static_cast<uint8_t>((chunk - 1) & 0xFF),
                     static_cast<uint8_t>((chunk - 1) >> 8)};
    ftdi_->write_data(rc, 3);
    to_req -= chunk;
  }
  cs_high();

  std::vector<uint8_t> buf(window);
  size_t received = 0;
  while (received < window) {
    int n = ftdi_->read_data(buf.data() + received,
                             static_cast<int>(window - received));
    if (n <= 0) break;
    received += static_cast<size_t>(n);
  }
  if (received < window) return false;

  // Scan for sync tokens and extract data.
  size_t pos = 0;
  for (int beat = 0; beat < num_beats; beat++) {
    bool found = false;
    while (pos + 16 < window) {
      if (buf[pos] == 0xFE) {
        std::memcpy(out + beat * 16, buf.data() + pos + 1, 16);
        pos += 17;
        found = true;
        break;
      }
      pos++;
    }
    if (!found) return false;
  }
  return true;
}

void SpiMaster::write_word(uint32_t addr, uint32_t value) {
  uint32_t line_addr = addr & ~static_cast<uint32_t>(0xF);
  uint8_t line[16];
  v2_read_lines(line_addr, 1, line);

  uint32_t offset = addr & 0xFu;
  line[offset]     = static_cast<uint8_t>(value);
  line[offset + 1] = static_cast<uint8_t>(value >> 8);
  line[offset + 2] = static_cast<uint8_t>(value >> 16);
  line[offset + 3] = static_cast<uint8_t>(value >> 24);

  v2_write_lines(line_addr, line, 1);
}

void SpiMaster::v2_write_data(uint32_t addr, const uint8_t* data, size_t len) {
  if (len == 0) return;

  uint32_t start      = addr;
  uint32_t end        = addr + static_cast<uint32_t>(len);
  uint32_t beat_start = start & ~static_cast<uint32_t>(0xF);
  uint32_t beat_end   = (end + 15u) & ~static_cast<uint32_t>(0xF);
  int total_beats     = static_cast<int>((beat_end - beat_start) / 16);

  std::vector<uint8_t> buf(static_cast<size_t>(total_beats) * 16);

  bool partial_first = (start & 0xFu) != 0;
  bool partial_last  = (end & 0xFu) != 0;

  // RMW: read partial-edge beats before overwriting.
  if (partial_first) {
    v2_read_lines(beat_start, 1, buf.data());
  }
  if (partial_last && !(partial_first && total_beats == 1)) {
    uint32_t last_addr =
        beat_start + static_cast<uint32_t>((total_beats - 1) * 16);
    v2_read_lines(last_addr, 1,
                  buf.data() + static_cast<size_t>(total_beats - 1) * 16);
  }

  std::memcpy(buf.data() + (start - beat_start), data, len);

  int beats_done   = 0;
  uint32_t cur_addr = beat_start;
  while (beats_done < total_beats) {
    int chunk =
        std::min(total_beats - beats_done, kMaxBeatsPerTransaction);
    v2_write_lines(cur_addr, buf.data() + static_cast<size_t>(beats_done) * 16,
                   chunk);
    beats_done += chunk;
    cur_addr += static_cast<uint32_t>(chunk) * 16u;
  }
}

bool SpiMaster::v2_read_data(uint32_t addr, size_t len, uint8_t* out) {
  if (len == 0) return true;

  uint32_t start      = addr;
  uint32_t end        = addr + static_cast<uint32_t>(len);
  uint32_t beat_start = start & ~static_cast<uint32_t>(0xF);
  uint32_t beat_end   = (end + 15u) & ~static_cast<uint32_t>(0xF);
  int total_beats     = static_cast<int>((beat_end - beat_start) / 16);

  std::vector<uint8_t> buf(static_cast<size_t>(total_beats) * 16);

  int beats_done    = 0;
  uint32_t cur_addr = beat_start;
  while (beats_done < total_beats) {
    int chunk =
        std::min(total_beats - beats_done, kMaxBeatsPerTransaction);
    if (!v2_read_lines(cur_addr, chunk,
                       buf.data() + static_cast<size_t>(beats_done) * 16)) {
      return false;
    }
    beats_done += chunk;
    cur_addr += static_cast<uint32_t>(chunk) * 16u;
  }

  std::memcpy(out, buf.data() + (start - beat_start), len);
  return true;
}

void SpiMaster::device_reset() {
  uint8_t assert_rst[3]   = {0x80, 0x08, 0x8b};
  uint8_t deassert_rst[3] = {0x80, 0x88, 0x8b};
  uint8_t float_rst[3]    = {0x80, 0x08, 0x0b};
  ftdi_->write_data(assert_rst, 3);
  ftdi_->write_data(deassert_rst, 3);
  ftdi_->write_data(float_rst, 3);
}
