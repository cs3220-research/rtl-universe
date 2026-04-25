// Copyright 2025 Google LLC
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

package top_racl_pkg;

  parameter int unsigned NrRaclPolicies  = 1;
  parameter int unsigned NrRaclBits      = 1;
  parameter int unsigned NrCtnUidBits    = 1;
  parameter bit          ErrorRsp        = 1'b0;

  // RACL Policy selector bits (ceil log2 of NrRaclPolicies)
  parameter int unsigned RaclPolicySelLen = 1;

  typedef logic [RaclPolicySelLen-1:0] racl_policy_sel_t;
  typedef logic [NrRaclBits-1:0]       racl_role_t;
  typedef logic [NrCtnUidBits-1:0]     ctn_uid_t;
  typedef logic [(2**NrRaclBits)-1:0]  racl_role_vec_t;

  // RACL policy: read and write role vectors
  typedef struct packed {
    racl_role_vec_t read_perm;
    racl_role_vec_t write_perm;
  } racl_policy_t;

  typedef racl_policy_t [NrRaclPolicies-1:0] racl_policy_vec_t;

  // Default (allow-all) policy
  parameter racl_policy_t RACL_POLICY_DEFAULT = '{
    read_perm : '1,
    write_perm: '1
  };

  // RACL information carried on a bus transaction
  typedef struct packed {
    racl_role_t role;
    ctn_uid_t   ctn_uid;
  } racl_role_info_t;

endpackage
