/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define SNIP_START(x) \
    .globl x; x:

#define SNIP_END(x) \
    .globl x##_end; x##_end: \
    .globl x##_len; x##_len: \
    .word x##_end-x

SNIP_START(_N_ColorMatrix_prefix_i)
    stmfd           sp!, {r4, lr}
    vpush           {q4-q7}
    vld1.16 {q2}, [r2]!
    vld1.16 {q3}, [r2]!
    vld1.32 {d8[],d9[]}, [r2]!
    vld1.32 {d10[],d11[]}, [r2]!
    vld1.32 {d12[],d13[]}, [r2]!
    vld1.32 {d14[],d15[]}, [r2]!
    veor q0, q0
    veor q1, q1
    veor q9, q9
    veor q10, q10
    veor q11, q11
SNIP_END(_N_ColorMatrix_prefix_i)

SNIP_START(_N_ColorMatrix_prefix_f)
    stmfd           sp!, {r4, lr}
    vpush           {q4-q7}
    add r2, #48
    vld1.32 {q4}, [r2]!
    vld1.32 {q5}, [r2]!
    vld1.32 {q6}, [r2]!
    vld1.32 {q7}, [r2]!
    vld1.32 {d16[],d17[]}, [r2]!
    vld1.32 {d18[],d19[]}, [r2]!
    vld1.32 {d20[],d21[]}, [r2]!
    vld1.32 {d22[],d23[]}, [r2]!
    veor q1, q1
    veor q2, q2
    veor q3, q3
SNIP_END(_N_ColorMatrix_prefix_f)

SNIP_START(_N_ColorMatrix_postfix1)
    subs r3, r3, #1
    #bne 1b
SNIP_END(_N_ColorMatrix_postfix1)

SNIP_START(_N_ColorMatrix_postfix2)

    #mov r0, #0
    #ldr r0, [r0]

    #vqadd.s32 q0,q0,q0
    #vadd.f32 q0,q0,q0
    #vmul.f32 q0,q0,d0[0]
    #vmla.f32 q0,q0,d0[0]
    #vmov q0, q0


    vpop            {q4-q7}
    ldmfd           sp!, {r4, lr}
    bx              lr
SNIP_END(_N_ColorMatrix_postfix2)

SNIP_START(_N_ColorMatrix_load_u8_4)
    vld4.8 {d0[0],d1[0],d2[0],d3[0]}, [r1]!
    vld4.8 {d0[1],d1[1],d2[1],d3[1]}, [r1]!
    vld4.8 {d0[2],d1[2],d2[2],d3[2]}, [r1]!
    vld4.8 {d0[3],d1[3],d2[3],d3[3]}, [r1]!
SNIP_END(_N_ColorMatrix_load_u8_4)

SNIP_START(_N_ColorMatrix_load_u8_3)
    vld4.8 {d0[0],d1[0],d2[0],d3[0]}, [r1]!
    vld4.8 {d0[1],d1[1],d2[1],d3[1]}, [r1]!
    vld4.8 {d0[2],d1[2],d2[2],d3[2]}, [r1]!
    vld4.8 {d0[3],d1[3],d2[3],d3[3]}, [r1]!
    veor d3, d3
SNIP_END(_N_ColorMatrix_load_u8_3)

SNIP_START(_N_ColorMatrix_load_u8_2)
    vld2.8 {d0[0],d1[0]}, [r1]!
    vld2.8 {d0[1],d1[1]}, [r1]!
    vld2.8 {d0[2],d1[2]}, [r1]!
    vld2.8 {d0[3],d1[3]}, [r1]!
    veor d2, d2
    veor d3, d3
SNIP_END(_N_ColorMatrix_load_u8_2)

SNIP_START(_N_ColorMatrix_load_u8_1)
    vld1.32 {d0[0]}, [r1]!
    veor d1, d1
    veor d2, d2
    veor d3, d3
SNIP_END(_N_ColorMatrix_load_u8_1)

SNIP_START(_N_ColorMatrix_load_u8f_4)
    vld4.8 {d0[0],d1[0],d2[0],d3[0]}, [r1]!
    vld4.8 {d0[1],d1[1],d2[1],d3[1]}, [r1]!
    vld4.8 {d0[2],d1[2],d2[2],d3[2]}, [r1]!
    vld4.8 {d0[3],d1[3],d2[3],d3[3]}, [r1]!
    vmovl.u8 q3, d3
    vmovl.u8 q2, d2
    vmovl.u8 q1, d1
    vmovl.u8 q0, d0
    vmovl.u16 q3, d6
    vmovl.u16 q2, d4
    vmovl.u16 q1, d2
    vmovl.u16 q0, d0
    vcvt.f32.s32 q3, q3
    vcvt.f32.s32 q2, q2
    vcvt.f32.s32 q1, q1
    vcvt.f32.s32 q0, q0
SNIP_END(_N_ColorMatrix_load_u8f_4)

SNIP_START(_N_ColorMatrix_load_u8f_3)
    vld4.8 {d0[0],d1[0],d2[0],d3[0]}, [r1]!
    vld4.8 {d0[1],d1[1],d2[1],d3[1]}, [r1]!
    vld4.8 {d0[2],d1[2],d2[2],d3[2]}, [r1]!
    vld4.8 {d0[3],d1[3],d2[3],d3[3]}, [r1]!
    vmovl.u8 q2, d2
    vmovl.u8 q1, d1
    vmovl.u8 q0, d0
    vmovl.u16 q2, d4
    vmovl.u16 q1, d2
    vmovl.u16 q0, d0
    vcvt.f32.s32 q2, q2
    vcvt.f32.s32 q1, q1
    vcvt.f32.s32 q0, q0
    veor q3, q3
SNIP_END(_N_ColorMatrix_load_u8f_3)

SNIP_START(_N_ColorMatrix_load_u8f_2)
    vld2.8 {d0[0],d1[0]}, [r1]!
    vld2.8 {d0[1],d1[1]}, [r1]!
    vld2.8 {d0[2],d1[2]}, [r1]!
    vld2.8 {d0[3],d1[3]}, [r1]!
    vmovl.u8 q1, d1
    vmovl.u8 q0, d0
    vmovl.u16 q1, d2
    vmovl.u16 q0, d0
    vcvt.f32.s32 q1, q1
    vcvt.f32.s32 q0, q0
    veor q2, q2
    veor q3, q3
SNIP_END(_N_ColorMatrix_load_u8f_2)

SNIP_START(_N_ColorMatrix_load_u8f_1)
    vld1.32 {d0[0]}, [r1]!
    vmovl.u8 q0, d0
    vmovl.u16 q0, d0
    vcvt.f32.s32 q0, q0
    veor q1, q1
    veor q2, q2
    veor q3, q3
SNIP_END(_N_ColorMatrix_load_u8f_1)

SNIP_START(_N_ColorMatrix_load_f32_4)
    vld4.32 {d0[0],d2[0],d4[0],d6[0]}, [r1]!
    vld4.32 {d0[1],d2[1],d4[1],d6[1]}, [r1]!
    vld4.32 {d1[0],d3[0],d5[0],d7[0]}, [r1]!
    vld4.32 {d1[1],d3[1],d5[1],d7[1]}, [r1]!
SNIP_END(_N_ColorMatrix_load_f32_4)

SNIP_START(_N_ColorMatrix_load_f32_3)
    vld3.32 {d0[0],d2[0],d4[0]}, [r1]!
    add r1, r1, #4
    vld3.32 {d0[1],d2[1],d4[1]}, [r1]!
    add r1, r1, #4
    vld3.32 {d1[0],d3[0],d5[0]}, [r1]!
    add r1, r1, #4
    vld3.32 {d1[1],d3[1],d5[1]}, [r1]!
    add r1, r1, #4
    veor q3, q3
SNIP_END(_N_ColorMatrix_load_f32_3)

SNIP_START(_N_ColorMatrix_load_f32_2)
    vld2.32 {d0[0],d2[0]}, [r1]!
    vld2.32 {d0[1],d2[1]}, [r1]!
    vld2.32 {d1[0],d3[0]}, [r1]!
    vld2.32 {d1[1],d3[1]}, [r1]!
    veor q2, q2
    veor q3, q3
SNIP_END(_N_ColorMatrix_load_f32_2)

SNIP_START(_N_ColorMatrix_load_f32_1)
    vld1.32 {q0}, [r1]!
    veor q1, q1
    veor q2, q2
    veor q3, q3
SNIP_END(_N_ColorMatrix_load_f32_1)


SNIP_START(_N_ColorMatrix_store_u8_4)
#mov r0, #0
    vst4.8 {d0[0],d1[0],d2[0],d3[0]}, [r0]!
    vst4.8 {d0[1],d1[1],d2[1],d3[1]}, [r0]!
    vst4.8 {d0[2],d1[2],d2[2],d3[2]}, [r0]!
    vst4.8 {d0[3],d1[3],d2[3],d3[3]}, [r0]!
SNIP_END(_N_ColorMatrix_store_u8_4)

SNIP_START(_N_ColorMatrix_store_u8_2)
    vst2.8 {d0[0],d1[0]}, [r0]!
    vst2.8 {d0[1],d1[1]}, [r0]!
    vst2.8 {d0[2],d1[2]}, [r0]!
    vst2.8 {d0[3],d1[3]}, [r0]!
SNIP_END(_N_ColorMatrix_store_u8_2)

SNIP_START(_N_ColorMatrix_store_u8_1)
    vst1.32 {d0[0]}, [r0]!
SNIP_END(_N_ColorMatrix_store_u8_1)


SNIP_START(_N_ColorMatrix_store_f32u_4)
    vcvt.s32.f32 q0, q0
    vcvt.s32.f32 q1, q1
    vcvt.s32.f32 q2, q2
    vcvt.s32.f32 q3, q3
    vqmovn.s32 d0, q0
    vqmovn.s32 d2, q1
    vqmovn.s32 d4, q2
    vqmovn.s32 d6, q3
    vqmovun.s16 d0, q0
    vqmovun.s16 d1, q1
    vqmovun.s16 d2, q2
    vqmovun.s16 d3, q3
    vst4.8 {d0[0],d1[0],d2[0],d3[0]}, [r0]!
    vst4.8 {d0[1],d1[1],d2[1],d3[1]}, [r0]!
    vst4.8 {d0[2],d1[2],d2[2],d3[2]}, [r0]!
    vst4.8 {d0[3],d1[3],d2[3],d3[3]}, [r0]!

    #mov r0, #0
    #ldr r0, [r0]

SNIP_END(_N_ColorMatrix_store_f32u_4)

SNIP_START(_N_ColorMatrix_store_f32u_2)
    vcvt.s32.f32 q0, q0
    vcvt.s32.f32 q1, q1
    vqmovn.s32 d0, q0
    vqmovn.s32 d2, q1
    vqmovun.s16 d0, q0
    vqmovun.s16 d1, q1
    vst2.8 {d0[0],d1[0]}, [r0]!
    vst2.8 {d0[1],d1[1]}, [r0]!
    vst2.8 {d0[2],d1[2]}, [r0]!
    vst2.8 {d0[3],d1[3]}, [r0]!
SNIP_END(_N_ColorMatrix_store_f32u_2)

SNIP_START(_N_ColorMatrix_store_f32u_1)
    vcvt.s32.f32 q0, q0
    vqmovn.s32 d0, q0
    vqmovun.s16 d0, q0
    vst1.32 {d0[0]}, [r0]!
SNIP_END(_N_ColorMatrix_store_f32u_1)

SNIP_START(_N_ColorMatrix_store_f32_4)
    vst4.32 {d0[0],d2[0],d4[0],d6[0]}, [r0]!
    vst4.32 {d0[1],d2[1],d4[1],d6[1]}, [r0]!
    vst4.32 {d1[0],d3[0],d5[0],d7[0]}, [r0]!
    vst4.32 {d1[1],d3[1],d5[1],d7[1]}, [r0]!
SNIP_END(_N_ColorMatrix_store_f32_4)

SNIP_START(_N_ColorMatrix_store_f32_3)
    vst4.32 {d0[0],d2[0],d4[0],d6[0]}, [r0]!
    vst4.32 {d0[1],d2[1],d4[1],d6[1]}, [r0]!
    vst4.32 {d1[0],d3[0],d5[0],d7[0]}, [r0]!
    vst4.32 {d1[1],d3[1],d5[1],d7[1]}, [r0]!
SNIP_END(_N_ColorMatrix_store_f32_3)

SNIP_START(_N_ColorMatrix_store_f32_2)
    vst2.32 {d0[0],d2[0]}, [r0]!
    vst2.32 {d0[1],d2[1]}, [r0]!
    vst2.32 {d1[0],d3[0]}, [r0]!
    vst2.32 {d1[1],d3[1]}, [r0]!
SNIP_END(_N_ColorMatrix_store_f32_2)

SNIP_START(_N_ColorMatrix_store_f32_1)
    vst1.32 {q0}, [r0]!
SNIP_END(_N_ColorMatrix_store_f32_1)


SNIP_START(_N_ColorMatrix_unpack_u8_4)
    vmovl.u8 q12, d0  /* R */
    vmovl.u8 q13, d1  /* G */
    vmovl.u8 q14, d2  /* B */
    vmovl.u8 q15, d3  /* A */
SNIP_END(_N_ColorMatrix_unpack_u8_4)

SNIP_START(_N_ColorMatrix_unpack_u8_3)
    vmovl.u8 q12, d0  /* R */
    vmovl.u8 q13, d1  /* G */
    vmovl.u8 q14, d2  /* B */
    veor q15, q15
SNIP_END(_N_ColorMatrix_unpack_u8_3)

SNIP_START(_N_ColorMatrix_unpack_u8_2)
    vmovl.u8 q12, d0  /* R */
    vmovl.u8 q13, d1  /* G */
    veor q14, q14
    veor q15, q15
SNIP_END(_N_ColorMatrix_unpack_u8_2)

SNIP_START(_N_ColorMatrix_unpack_u8_1)
    vmovl.u8 q12, d0  /* R */
    veor q13, q13
    veor q14, q14
    veor q15, q15
SNIP_END(_N_ColorMatrix_unpack_u8_1)

SNIP_START(_N_ColorMatrix_pack_u8_4)
    vqrshrn.s32 d24, q8, #8
    vqrshrn.s32 d26, q9, #8
    vqrshrn.s32 d28, q10, #8
    vqrshrn.s32 d30, q11, #8
    vqmovun.s16 d0, q12
    vqmovun.s16 d1, q13
    vqmovun.s16 d2, q14
    vqmovun.s16 d3, q15
SNIP_END(_N_ColorMatrix_pack_u8_4)

SNIP_START(_N_ColorMatrix_pack_u8_3)
    vqrshrn.s32 d24, q8, #8
    vqrshrn.s32 d26, q9, #8
    vqrshrn.s32 d28, q10, #8
    vqmovun.s16 d0, q12
    vqmovun.s16 d1, q13
    vqmovun.s16 d2, q14
SNIP_END(_N_ColorMatrix_pack_u8_3)

SNIP_START(_N_ColorMatrix_pack_u8_2)
    vqrshrn.s32 d24, q8, #8
    vqrshrn.s32 d26, q9, #8
    vqmovun.s16 d0, q12
    vqmovun.s16 d1, q13
SNIP_END(_N_ColorMatrix_pack_u8_2)

SNIP_START(_N_ColorMatrix_pack_u8_1)
    vqrshrn.s32 d24, q8, #8
    vqmovun.s16 d0, q12
SNIP_END(_N_ColorMatrix_pack_u8_1)

SNIP_START(_N_ColorMatrix_dot)
    vmov.u8 d1, d0
    vmov.u8 d2, d0
SNIP_END(_N_ColorMatrix_dot)

