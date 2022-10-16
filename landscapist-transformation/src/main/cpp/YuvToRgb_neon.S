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

/* Perform the actual YuvToRGB conversion in a macro, from register to
 * register.  This macro will be called from within several different wrapper
 * variants for different data layouts.  Y data starts in q8, but with the even
 * and odd bytes split into d16 and d17 respectively.  U and V are in d20
 * and d21.  Working constants are pre-loaded into q13-q15, and q3 is
 * pre-loaded with a constant 0xff alpha channel.
 *
 * The complicated arithmetic is the result of refactoring the original
 * equations to avoid 16-bit overflow without losing any precision.
 */
.macro yuvkern
        vmov.i8     d15, #149

        vmull.u8    q1, d16, d15        // g0 = y0 * 149
        vmull.u8    q5, d17, d15        // g1 = y1 * 149

        vmov.i8     d14, #50
        vmov.i8     d15, #104
        vmull.u8    q8, d20, d14        // g2 = u * 50 + v * 104
        vmlal.u8    q8, d21, d15

        vshr.u8     d14, d21, #1
        vaddw.u8    q0, q1, d14         // r0 = y0 * 149 + (v >> 1)
        vaddw.u8    q4, q5, d14         // r1 = y1 * 149 + (v >> 1)

        vshll.u8    q7, d20, #2
        vadd.u16    q2, q1, q7          // b0 = y0 * 149 + (u << 2)
        vadd.u16    q6, q5, q7          // b1 = y1 * 149 + (u << 2)

        vmov.i8     d14, #204
        vmov.i8     d15, #254
        vmull.u8    q11, d21, d14       // r2 = v * 204
        vmull.u8    q12, d20, d15       // b2 = u * 254

        vhadd.u16   q0, q11             // r0 = (r0 + r2) >> 1
        vhadd.u16   q4, q11             // r1 = (r1 + r2) >> 1
        vqadd.u16   q1, q14             // g0 = satu16(g0 + (-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        vqadd.u16   q5, q14             // g1 = satu16(g1 + (-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        vhadd.u16   q2, q12             // b0 = (b0 + b2) >> 1
        vhadd.u16   q6, q12             // b1 = (b1 + b2) >> 1

        vqsub.u16   q0, q13             // r0 = satu16(r0 - (16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        vqsub.u16   q4, q13             // r1 = satu16(r1 - (16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        vqsub.u16   q1, q8              // g0 = satu16(g0 - g2)
        vqsub.u16   q5, q8              // g1 = satu16(g1 - g2)
        vqsub.u16   q2, q15             // b0 = satu16(b0 - (16 * 149 + (128 << 2) + 128 * 254) >> 1)
        vqsub.u16   q6, q15             // b1 = satu16(b1 - (16 * 149 + (128 << 2) + 128 * 254) >> 1)

        vqrshrn.u16 d0, q0, #6
        vqrshrn.u16 d1, q1, #7
        vqrshrn.u16 d2, q4, #6
        vqrshrn.u16 d3, q5, #7
        vqrshrn.u16 d4, q2, #6
        vqrshrn.u16 d5, q6, #6

        vzip.u8     q0, q1
        vzip.u8     d4, d5
.endm

/* Define the wrapper code which will load and store the data, iterate the
 * correct number of times, and safely handle the remainder at the end of the
 * loop.  Some sections of code are switched out depending on the data packing
 * being handled.
 */
.macro wrap_line kernel, interleaved=0, swapuv=0

        movw        r5, #((16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        vdup.i16    q13, r5
        movw        r5, #((-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        vdup.i16    q14, r5
        movw        r5, #((16 * 149 + (128 << 2) + 128 * 254) >> 1)
        vdup.i16    q15, r5

        vmov.i8     q3, #0xff

        subs        r2, #16
        bhs         1f
        b           2f

        .align 4
1:      vld2.u8     {d16,d17}, [r1]!
        pld         [r1, #256]
  .if \interleaved
        vld2.u8     {d20,d21}, [r3]!
    .if \swapuv
        vswp        d20, d21
    .endif
        pld         [r3, #256]
  .else
        vld1.u8     d20, [r3]!
        vld1.u8     d21, [r4]!
        pld         [r3, #128]
        pld         [r4, #128]
  .endif

        \kernel

        subs        r2, #16

        vst4.u8     {d0,d2,d4,d6}, [r0]!
        vst4.u8     {d1,d3,d5,d7}, [r0]!

        bhs         1b

2:      adds        r2, #16
        beq         2f

        /* To handle the tail portion of the data (something less than 16
         * bytes) load small power-of-two chunks into working registers.  It
         * doesn't matter where they end up in the register; the same process
         * will store them back out using the same positions and the
         * interaction between neighbouring pixels is constrained to odd
         * boundaries where the load operations don't interfere.
         */
        vmov.i8     q8, #0
        vmov.i8     q10, #0

        tst         r2, #8
        beq         1f
        vld1.u8     d17, [r1]!
  .if \interleaved
        vld1.u8     d21, [r3]!
  .else
        vld1.u32    d20[1], [r3]!
        vld1.u32    d21[1], [r4]!
  .endif

1:      tst         r2, #4
        beq         1f
        vld1.u32    d16[1], [r1]!
  .if \interleaved
        vld1.u32    d20[1], [r3]!
  .else
        vld1.u16    d20[1], [r3]!
        vld1.u16    d21[1], [r4]!
  .endif
1:      tst         r2, #2
        beq         1f
        vld1.u16    d16[1], [r1]!
  .if \interleaved
        vld1.u16    d20[1], [r3]!
  .else
        vld1.u8     d20[1], [r3]!
        vld1.u8     d21[1], [r4]!
  .endif
1:      tst         r2, #1
        beq         1f
        vld1.u8     d16[1], [r1]!
  .if \interleaved
        vld1.u16    d20[0], [r3]!
  .else
        vld1.u8     d20[0], [r3]!
        vld1.u8     d21[0], [r4]!
  .endif

        /* One small impediment in the process above is that some of the load
         * operations can't perform byte-wise structure deinterleaving at the
         * same time as loading only part of a register.  So the data is loaded
         * linearly and unpacked manually at this point if necessary.
         */
1:      vuzp.8      d16, d17
  .if \interleaved
        vuzp.8      d20, d21
    .if \swapuv
        vswp        d20, d21
    .endif
  .endif

        \kernel

        /* As above but with the output; structured stores for partial vectors
         * aren't available, so the data is re-packed first and stored linearly.
         */
        vzip.8  q0, q2
        vzip.8  q1, q3
        vzip.8  q0, q1
        vzip.8  q2, q3

1:      tst         r2, #8
        beq         1f
        vst1.u8     {d4,d5,d6,d7}, [r0]!

1:      tst         r2, #4
        beq         1f
        vst1.u8     {d2,d3}, [r0]!
1:      tst         r2, #2
        beq         1f
        vst1.u8     d1, [r0]!
1:      tst         r2, #1
        beq         2f
        vst1.u32    d0[1], [r0]!
2:
.endm


/*  void rsdIntrinsicYuv2_K(
 *          void *out,          // r0
 *          void const *yin,    // r1
 *          void const *uin,    // r2
 *          void const *vin,    // r3
 *          size_t xstart,      // [sp]
 *          size_t xend);       // [sp+#4]
 */
ENTRY(rsdIntrinsicYuv2_K)
        push        {r4,r5}
        ldr         r5, [sp, #8]
        mov         r4, r3
        mov         r3, r2
        ldr         r2, [sp, #12]

        add         r0, r5, LSL #2
        add         r1, r5
        add         r3, r5, LSR #1
        add         r4, r5, LSR #1
        sub         r2, r5

        vpush       {d8-d15}

        wrap_line yuvkern, 0

        vpop        {d8-d15}
        pop         {r4,r5}
        bx lr
END(rsdIntrinsicYuv2_K)

/*  void rsdIntrinsicYuv_K(
 *          void *out,          // r0
 *          void const *yin,    // r1
 *          void const *uvin,   // r2
 *          size_t xstart,      // r3
 *          size_t xend);       // [sp]
 */
ENTRY(rsdIntrinsicYuv_K)
        push        {r4,r5}
        bic         r4, r3, #1
        add         r3, r2, r4
        ldr         r2, [sp, #8]

        add         r0, r4, LSL #2
        add         r1, r4
        sub         r2, r4

        vpush       {d8-d15}

        wrap_line yuvkern, 1, 1

        vpop        {d8-d15}
        pop         {r4,r5}
        bx lr
END(rsdIntrinsicYuv_K)

/*  void rsdIntrinsicYuvR_K(
 *          void *out,          // r0
 *          void const *yin,    // r1
 *          void const *uvin,   // r2
 *          size_t xstart,      // r3
 *          size_t xend);       // [sp]
 */
ENTRY(rsdIntrinsicYuvR_K)
        push        {r4,r5}
        bic         r4, r3, #1
        add         r3, r2, r4
        ldr         r2, [sp, #8]

        add         r0, r4, LSL #2
        add         r1, r4
        sub         r2, r4

        vpush       {d8-d15}

        wrap_line yuvkern, 1

        vpop        {d8-d15}
        pop         {r4,r5}
        bx lr
END(rsdIntrinsicYuvR_K)
