/*
 * Copyright (C) 2014 The Android Open Source Project
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

#define ENTRY(f) .text; .align 4; .globl f; .type f,#function; f: .fnstart
#define END(f) .fnend; .size f, .-f;

.eabi_attribute 25,1 @Tag_ABI_align8_preserved
.arm

.macro lanepair dst, src, xr0, xr1, yr0, yr1, zr0, zr1

            vmov        r6, r7, \src

            add         r6, r6, r3
            add         r7, r7, r3

            vld1.u8     d16, [r6], r4
            vld1.u8     d17, [r7], r4

            vld1.u8     d18, [r6], r5
            vld1.u8     d19, [r7], r5

            vdup.u8     d6, \yr0
            vdup.u8     d7, \yr1
            /* Y interpolate, front, lanes 0 and 1 -> q12 and q13 */
            vshll.u8    q12, d16, #8
            vshll.u8    q13, d17, #8
            vmlsl.u8    q12, d16, d6
            vmlsl.u8    q13, d17, d7
            vmlal.u8    q12, d18, d6
            vmlal.u8    q13, d19, d7

            vld1.u8     d18, [r6]
            vld1.u8     d19, [r7]

            sub         r6, r6, r4
            sub         r7, r7, r4

            vld1.u8     d16, [r6]
            vld1.u8     d17, [r7]

            /* Y interpolate, rear, lanes 0 and 1 -> q14 and q15 */
            vshll.u8    q14, d16, #8
            vshll.u8    q15, d17, #8
            vmlsl.u8    q14, d16, d6
            vmlsl.u8    q15, d17, d7
            vmlal.u8    q14, d18, d6
            vmlal.u8    q15, d19, d7

            /* Z interpolate, lane 0 q12/q14 -> q10 */
            vshll.u16   q8, d24, #8
            vshll.u16   q9, d25, #8
            vmlsl.u16   q8, d24, \zr0
            vmlsl.u16   q9, d25, \zr0
            vmlal.u16   q8, d28, \zr0
            vmlal.u16   q9, d29, \zr0
            vrshrn.u32  d20, q8, #8
            vrshrn.u32  d21, q9, #8

            /* Z interpolate, lane 1 q13/q15 -> q11 */
            vshll.u16   q8, d26, #8
            vshll.u16   q9, d27, #8
            vmlsl.u16   q8, d26, \zr1
            vmlsl.u16   q9, d27, \zr1
            vmlal.u16   q8, d30, \zr1
            vmlal.u16   q9, d31, \zr1
            vrshrn.u32  d22, q8, #8
            vrshrn.u32  d23, q9, #8

            /* X interpolate, lanes 0 and 1 q10,q11 -> q14 */
            vshll.u16   q8, d20, #8
            vshll.u16   q9, d22, #8
            vmlsl.u16   q8, d20, \xr0
            vmlsl.u16   q9, d22, \xr1
            vmlal.u16   q8, d21, \xr0
            vmlal.u16   q9, d23, \xr1
            vshrn.u32   d28, q8, #8
            vshrn.u32   d29, q9, #8

            /* pack lanes 0-1 -> d12 */
            vqrshrn.u16  \dst, q14, #8
.endm

/* void rsdIntrinsic3DLUT_K(
 *          void *dst,          // r0
 *          void const *in,     // r1
 *          size_t count,       // r2
 *          void const *lut,    // r3
 *          int32_t pitchy,     // [sp]
 *          int32_t pitchz,     // [sp+#4]
 *          int dimx,           // [sp+#8]
 *          int dimy,           // [sp+#12]
 *          int dimz);          // [sp+#16]
 */
ENTRY(rsdIntrinsic3DLUT_K)
            push        {r4,r5,r6,r7}
            ldr         r4, [sp, #16]
            ldr         r5, [sp, #20]
            ldr         r6, [sp, #24]
            ldr         r7, [sp, #28]
            ldr         r12, [sp, #32]
            vpush       {d8-d15}

            vmov.u8     d8, #1
            vmov.u16    d8[0], r6
            vmov.u16    d8[1], r7
            vmov.u16    d8[2], r12
            vmov        d9, r4, r5

            subs        r2, #8
            bge         2f
            cmp         r2, #-8
            ble         9f
            b           4f

            .align 6
1:          vst4.u8     {d12,d13,d14,d15}, [r0]!
/* r0  = dst
 * r1  = src
 * r2  = count
 * r3  = lut
 * r4  = pitchy
 * r5  = pitchz
 * r6 = offset0
 * r7 = offset1
 */
2:          vld4.u8     {d0,d2,d4,d6}, [r1]!
3:          vmov        d10, d6
/* q0,q1,q2,q5 source data
 * q4 dimensions and pitches
 * q3, scratch register for scalar access
 */
            vmov        q3, q4
            vmovl.u8    q0, d0
            vmovl.u8    q1, d2
            vmovl.u8    q2, d4
            vmul.u16    q0, q0, d6[0]
            vmul.u16    q1, q1, d6[1]
            vmul.u16    q2, q2, d6[2]

/* vrsra.u16 below would be more accurate, but this can result in a dim.0 case
 * where we try to read from the limit of the array and the limit +1 to
 * interpolate, even though the fractional component is zero.  Strictly this is
 * correct, except for the llegal access problem.
 */
            vsra.u16    q0, q0, #8
            vsra.u16    q1, q1, #8
            vsra.u16    q2, q2, #8

            vshr.u16    q12, q0, #8
            vshr.u16    q13, q1, #8
            vshr.u16    q14, q2, #8

            vbic.u16    q0, #0xff00
            vmovn.u16   d2, q1
            vbic.u16    q2, #0xff00

/* q0,d2,q2 fractional offset
 * q12,q13,q14 integer offset
 */

            vshll.u16   q6, d24, #2
            vshll.u16   q7, d25, #2
            vmovl.u16   q8, d26
            vmovl.u16   q9, d27
            vmovl.u16   q10, d28
            vmovl.u16   q11, d29
            vmla.s32    q6, q8,  d9[0]
            vmla.s32    q7, q9,  d9[0]
            vmla.s32    q6, q10, d9[1]
            vmla.s32    q7, q11, d9[1]

/* q6,q7 list of table offsets */

        /* lanes 0 and 1 */
            lanepair dst=d12, src=d12, xr0=d0[0], xr1=d0[1], yr0=d2[0], yr1=d2[1], zr0=d4[0], zr1=d4[1]

        /* lanes 2 and 3 */
            lanepair dst=d13, src=d13, xr0=d0[2], xr1=d0[3], yr0=d2[2], yr1=d2[3], zr0=d4[2], zr1=d4[3]

        /* lanes 4 and 5 */
            lanepair dst=d14, src=d14, xr0=d1[0], xr1=d1[1], yr0=d2[4], yr1=d2[5], zr0=d5[0], zr1=d5[1]

        /* lanes 6 and 7 */
            lanepair dst=d15, src=d15, xr0=d1[2], xr1=d1[3], yr0=d2[6], yr1=d2[7], zr0=d5[2], zr1=d5[3]

            vuzp.u8     d12, d13
            vuzp.u8     d14, d15
            vuzp.u8     d12, d14
            vuzp.u8     d13, d15

            subs        r2, r2, #8
            vmov.u8     d15, d10

            bge         1b

            cmp         r2, #-8
            blt         1f

            vst4.u8     {d12,d13,d14,d15}, [r0]!

            beq         9f

            /* fill the vector with a safe value */
4:          vld1.u32    {d0[]}, [r1]
            vmov        d2, d0
            vmov        d4, d0
            vmov        d6, d0
            tst         r2, #4
            beq         2f
            vld1.u32    {d0}, [r1]!
            vld1.u32    {d2}, [r1]!
2:          tst         r2, #2
            beq         2f
            vld1.u32    {d4}, [r1]!
2:          tst         r2, #1
            beq         2f
            vld1.u32    {d6[0]}, [r1]!
2:          vuzp.8      d0, d2
            vuzp.8      d4, d6
            vuzp.8      d0, d4
            vuzp.8      d2, d6
            b           3b

1:          vzip.8      d12, d14
            vzip.8      d13, d15
            vzip.8      d12, d13
            vzip.8      d14, d15
            tst         r2, #4
            beq         2f
            vst1.u32    {d12,d13}, [r0]!
2:          tst         r2, #2
            beq         2f
            vst1.u32    {d14}, [r0]!
2:          tst         r2, #1
            beq         9f
            vst1.u32    {d15[0]}, [r0]!

9:          mov         r0, #0
            vpop        {d8-d15}
            pop         {r4,r5,r6,r7}
            bx lr
END(rsdIntrinsic3DLUT_K)
