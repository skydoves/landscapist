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
#define PRIVATE(f) .text; .align 4; .type f,#function; f: .fnstart
#define END(f) .fnend; .size f, .-f;

#define ARCH_ARM_USE_BLUR_PRELOAD

.eabi_attribute 25,1 @Tag_ABI_align8_preserved
.arm

/* Number of fractional bits to preserve in intermediate results.  The
 * intermediate storage is 16-bit, and we started with 8 bit data (the integer
 * part), so this should be between 0 and 8.
 */
.set FRACTION_BITS, 7

.set MAX_R, 25


/* A quick way of making a line of code conditional on some other condition.
 * Use `.set cc, 1` or `.set cc, 0` to enable or disable lines prefixed with
 * `ifcc`:
 */
.macro ifcc zzz:vararg
.if cc
            \zzz
.endif
.endm

/* It's not always clear that prefetching is beneficial and this needs further
 * testing on different cores, so it's made switchable here.
 */
#if defined(ARCH_ARM_USE_BLUR_PRELOAD)
#define VERTPLD(...) pld [__VA_ARGS__]
#else
#define VERTPLD(...) nop
#endif

/* Fetch 16 columns of bytes (regardless of image format), convolve these
 * vertically, and leave them in the register file.  If working near the top or
 * bottom of an image then clamp the addressing while loading the data in.
 *
 * The convolution is fully unrolled for windows up to max_r, with the
 * outermost edges calculated first.  This way it's possible to branch directly
 * into the relevant part of the code for an arbitrary convolution radius.  Two
 * variants of the loop are produced; one eliminates the clamping code for a
 * slight speed advantage.
 *
 * Where the macro is called with reg=x, the specified register is taken to
 * contain a pre-calculated pointer into one of the two loops.
 *
 * Input:
 *      r1 -- src
 *      r2 -- pitch
 *      r5 -- r
 *      r6 -- rup (r, unless clipped to top of source image)
 *      r7 -- rdn (r, unless clipped to bottom of source image)
 *      r12 -- switch index
 *      q0-q3 -- coefficient table
 * Output:
 *      r1 += 16
 *      q10,q11 -- 16 convolved columns
 * Modifies:
 *      r10 = upper row pointer
 *      r11 = lower row pointer
 *      q12-q15 = temporary sums
 */
.macro fetch, max_r=MAX_R, labelc=1, labelnc=2, reg=r12 /*{{{*/
  .ifc \reg,r12 ; .set cc, 1 ; .else ; .set cc, 0 ; .endif

            vld1.8      {d30,d31}, [r1]
            mls         r10, r2, r6, r1

            vmovl.u8    q14, d30
            VERTPLD(r1, #32)
            vmovl.u8    q15, d31
  .if \max_r < 16 // approximate
    ifcc    adr         \reg, 1f
  .else
    ifcc    ldr         \reg, 2f
1:  ifcc    add         \reg, \reg, pc
  .endif

            vmull.u16   q12, d28, d0[0]
    ifcc    sub         \reg, r5, LSL #6
            vmull.u16   q13, d29, d0[0]
            mla         r11, r2, r7, r1
            vmull.u16   q14, d30, d0[0]
            add         r1, r1, #16
            vmull.u16   q15, d31, d0[0]
            bx          \reg

     ifcc   .align 2
  2: ifcc   .word       1f-1b-8

  /* This version of the vertical fetch loop body is used away from the edges
   * of the source image.  The pointers start at the top and bottom source rows
   * and work their way towards the centre on each iteration.  This way the
   * number of taps used can be controlled by jumping directly into the middle
   * of the loop and running to completion.
   * If the loop body changes size then the code which calculates the address of
   * the initial iteration must be updated to accordingly.
   */
  .macro vertfetch_noclamp i, dreg
    .if 0 < \i && \i <= \max_r
            vld1.8      {d20,d21}, [r10], r2
            vld1.8      {d22,d23}, [r11]
            sub         r11, r11, r2
            vswp        d21, d22
            VERTPLD(r10, #32)
            vaddl.u8    q10, d20, d21
            vaddl.u8    q11, d22, d23
            vmlal.u16   q12, d20, \dreg
            VERTPLD(r11, #32)
            vmlal.u16   q13, d21, \dreg
            vmlal.u16   q14, d22, \dreg
            vmlal.u16   q15, d23, \dreg
    .endif
  .endm

  /* This version of the vertical fetch loop body is used near the edges of the
   * source image, where one or both of the accesses may start with a clamped
   * value, and the row addresses only begin to change after some number of
   * iterations before the end.
   * If the loop body changes size then the code which calculates the address of
   * the initial iteration must be updated to accordingly.
   */
  .macro vertfetch_clamped i, dreg
    .if 0 < \i && \i <= \max_r
            vld1.8      {d20,d21}, [r10]
            vld1.8      {d22,d23}, [r11]
            cmp         r6, #\i
            vswp        d21, d22
            VERTPLD(r10, #32)
            vaddl.u8    q10, d20, d21
            addhs       r10, r10, r2
            vaddl.u8    q11, d22, d23
            cmp         r7, #\i
            vmlal.u16   q12, d20, \dreg
            VERTPLD(r11, #32)
            vmlal.u16   q13, d21, \dreg
            subhs       r11, r11, r2
            vmlal.u16   q14, d22, \dreg
            nop
            vmlal.u16   q15, d23, \dreg
    .endif
  .endm

  /* Entry into this unrolled loop is computed as a negative index from
   * \labelc at the end of the block.
   */
  .align 4
  vertfetch_clamped 27, d6[3]
  vertfetch_clamped 26, d6[2]
  vertfetch_clamped 25, d6[1]
  vertfetch_clamped 24, d6[0]
  vertfetch_clamped 23, d5[3]
  vertfetch_clamped 22, d5[2]
  vertfetch_clamped 21, d5[1]
  vertfetch_clamped 20, d5[0]
  vertfetch_clamped 19, d4[3]
  vertfetch_clamped 18, d4[2]
  vertfetch_clamped 17, d4[1]
  vertfetch_clamped 16, d4[0]
  vertfetch_clamped 15, d3[3]
  vertfetch_clamped 14, d3[2]
  vertfetch_clamped 13, d3[1]
  vertfetch_clamped 12, d3[0]
  vertfetch_clamped 11, d2[3]
  vertfetch_clamped 10, d2[2]
  vertfetch_clamped  9, d2[1]
  vertfetch_clamped  8, d2[0]
  vertfetch_clamped  7, d1[3]
  vertfetch_clamped  6, d1[2]
  vertfetch_clamped  5, d1[1]
  vertfetch_clamped  4, d1[0]
  vertfetch_clamped  3, d0[3]
  vertfetch_clamped  2, d0[2]
  vertfetch_clamped  1, d0[1]
  vertfetch_clamped  0, d0[0]
  1:
  \labelc : b 2f    /* done with clamped loop, skip over non-clamped loop */

  /* Entry into this unrolled loop is computed as a negative index from
   * \labelnc at the end of the block.
   */
  .align 4
  vertfetch_noclamp 27, d6[3]
  vertfetch_noclamp 26, d6[2]
  vertfetch_noclamp 25, d6[1]
  vertfetch_noclamp 24, d6[0]
  vertfetch_noclamp 23, d5[3]
  vertfetch_noclamp 22, d5[2]
  vertfetch_noclamp 21, d5[1]
  vertfetch_noclamp 20, d5[0]
  vertfetch_noclamp 19, d4[3]
  vertfetch_noclamp 18, d4[2]
  vertfetch_noclamp 17, d4[1]
  vertfetch_noclamp 16, d4[0]
  vertfetch_noclamp 15, d3[3]
  vertfetch_noclamp 14, d3[2]
  vertfetch_noclamp 13, d3[1]
  vertfetch_noclamp 12, d3[0]
  vertfetch_noclamp 11, d2[3]
  vertfetch_noclamp 10, d2[2]
  vertfetch_noclamp  9, d2[1]
  vertfetch_noclamp  8, d2[0]
  vertfetch_noclamp  7, d1[3]
  vertfetch_noclamp  6, d1[2]
  vertfetch_noclamp  5, d1[1]
  vertfetch_noclamp  4, d1[0]
  vertfetch_noclamp  3, d0[3]
  vertfetch_noclamp  2, d0[2]
  vertfetch_noclamp  1, d0[1]
  vertfetch_noclamp  0, d0[0]
  \labelnc :

  .purgem vertfetch_clamped
  .purgem vertfetch_noclamp

  2:        vqrshrn.u32 d20, q12, #16 - FRACTION_BITS
            vqrshrn.u32 d21, q13, #16 - FRACTION_BITS
            vqrshrn.u32 d22, q14, #16 - FRACTION_BITS
            vqrshrn.u32 d23, q15, #16 - FRACTION_BITS
.endm /*}}}*/

/* Some portion of the convolution window (as much as will fit, and all of it
 * for the uchar1 cases) is kept in the register file to avoid unnecessary
 * memory accesses.  This forces the horizontal loops to be unrolled because
 * there's no indexed addressing into the register file.
 *
 * As in the fetch macro, the operations are ordered from outside to inside, so
 * that jumping into the middle of the block bypasses the unwanted window taps.
 *
 * There are several variants of the macro because of the fixed offets of the
 * taps -- the wider the maximum radius the further the centre tap is from the
 * most recently fetched data.  This means that pre-filling the window requires
 * more data that won't be used and it means that rotating the window involves
 * more mov operations.
 *
 * When the buffer gets too big the buffer at [r9] is used.
 *
 * Input:
 *      q4-q11 -- convoltion window
 *      r9 -- pointer to additional convolution window data
 * Output:
 *      r9 -- updated buffer pointer (if used)
 *      d31 -- result to be stored
 * Modifies:
 *      r12 -- temp buffer pointer
 *      q12-q13 -- temporaries for load and vext operations.
 *      q14-q15 -- intermediate sums
 */
#define TUNED_LIST1 8, 16
.macro hconv1_8/*{{{*/
            vmull.u16   q14, d18, d0[0]
            vmull.u16   q15, d19, d0[0]

            ldr         r12, [pc, r5, LSL #2]
            add         pc, pc, r12
            bkpt
    100:    .word 101f-100b
            .word 102f-100b
            .word 103f-100b
            .word 104f-100b
            .word 105f-100b
            .word 106f-100b
            .word 107f-100b
            .word 108f-100b
    108:    vmlal.u16   q14, d16, d2[0]
            vmlal.u16   q15, d17, d2[0]
            vmlal.u16   q14, d20, d2[0]
            vmlal.u16   q15, d21, d2[0]
    107:    vext.u16    q12, q8, q9, #1
            vext.u16    q13, q9, q10, #7
            vmlal.u16   q14, d24, d1[3]
            vmlal.u16   q15, d25, d1[3]
            vmlal.u16   q14, d26, d1[3]
            vmlal.u16   q15, d27, d1[3]
    106:    vext.u16    q12, q8, q9, #2
            vext.u16    q13, q9, q10, #6
            vmlal.u16   q14, d24, d1[2]
            vmlal.u16   q15, d25, d1[2]
            vmlal.u16   q14, d26, d1[2]
            vmlal.u16   q15, d27, d1[2]
    105:    vext.u16    q12, q8, q9, #3
            vext.u16    q13, q9, q10, #5
            vmlal.u16   q14, d24, d1[1]
            vmlal.u16   q15, d25, d1[1]
            vmlal.u16   q14, d26, d1[1]
            vmlal.u16   q15, d27, d1[1]
    104:    //vext.u16    q12, q8, q9, #4
            //vext.u16    q13, q9, q10, #4
            vmlal.u16   q14, d17, d1[0]
            vmlal.u16   q15, d18, d1[0]
            vmlal.u16   q14, d19, d1[0]
            vmlal.u16   q15, d20, d1[0]
    103:    vext.u16    q12, q8, q9, #5
            vext.u16    q13, q9, q10, #3
            vmlal.u16   q14, d24, d0[3]
            vmlal.u16   q15, d25, d0[3]
            vmlal.u16   q14, d26, d0[3]
            vmlal.u16   q15, d27, d0[3]
    102:    vext.u16    q12, q8, q9, #6
            vext.u16    q13, q9, q10, #2
            vmlal.u16   q14, d24, d0[2]
            vmlal.u16   q15, d25, d0[2]
            vmlal.u16   q14, d26, d0[2]
            vmlal.u16   q15, d27, d0[2]
    101:    vext.u16    q12, q8, q9, #7
            vext.u16    q13, q9, q10, #1
            vmlal.u16   q14, d24, d0[1]
            vmlal.u16   q15, d25, d0[1]
            vmlal.u16   q14, d26, d0[1]
            vmlal.u16   q15, d27, d0[1]

            vqrshrn.u32 d28, q14, #16
            vqrshrn.u32 d29, q15, #16
            vqrshrn.u16 d31, q14, #FRACTION_BITS

            vmov        q8, q9
            vmov        q9, q10
            vmov        q10, q11
.endm/*}}}*/

.macro hconv1_16/*{{{*/
            vmull.u16   q14, d16, d0[0]
            vmull.u16   q15, d17, d0[0]

            ldr         r12, [pc, r5, LSL #2]
            add         pc, pc, r12
            bkpt
    100:    .word 101f-100b
            .word 102f-100b
            .word 103f-100b
            .word 104f-100b
            .word 105f-100b
            .word 106f-100b
            .word 107f-100b
            .word 108f-100b
            .word 109f-100b
            .word 110f-100b
            .word 111f-100b
            .word 112f-100b
            .word 113f-100b
            .word 114f-100b
            .word 115f-100b
            .word 116f-100b
    116:    //vext.u16    q12, q6, q7, #0
            //vext.u16    q13, q10, q11, #0
            vmlal.u16   q14, d12, d4[0]
            vmlal.u16   q15, d13, d4[0]
            vmlal.u16   q14, d20, d4[0]
            vmlal.u16   q15, d21, d4[0]
    115:    vext.u16    q12, q6, q7, #1
            vext.u16    q13, q9, q10, #7
            vmlal.u16   q14, d24, d3[3]
            vmlal.u16   q15, d25, d3[3]
            vmlal.u16   q14, d26, d3[3]
            vmlal.u16   q15, d27, d3[3]
    114:    vext.u16    q12, q6, q7, #2
            vext.u16    q13, q9, q10, #6
            vmlal.u16   q14, d24, d3[2]
            vmlal.u16   q15, d25, d3[2]
            vmlal.u16   q14, d26, d3[2]
            vmlal.u16   q15, d27, d3[2]
    113:    vext.u16    q12, q6, q7, #3
            vext.u16    q13, q9, q10, #5
            vmlal.u16   q14, d24, d3[1]
            vmlal.u16   q15, d25, d3[1]
            vmlal.u16   q14, d26, d3[1]
            vmlal.u16   q15, d27, d3[1]
    112:    //vext.u16    q12, q6, q7, #4
            //vext.u16    q13, q9, q10, #4
            vmlal.u16   q14, d13, d3[0]
            vmlal.u16   q15, d14, d3[0]
            vmlal.u16   q14, d19, d3[0]
            vmlal.u16   q15, d20, d3[0]
    111:    vext.u16    q12, q6, q7, #5
            vext.u16    q13, q9, q10, #3
            vmlal.u16   q14, d24, d2[3]
            vmlal.u16   q15, d25, d2[3]
            vmlal.u16   q14, d26, d2[3]
            vmlal.u16   q15, d27, d2[3]
    110:    vext.u16    q12, q6, q7, #6
            vext.u16    q13, q9, q10, #2
            vmlal.u16   q14, d24, d2[2]
            vmlal.u16   q15, d25, d2[2]
            vmlal.u16   q14, d26, d2[2]
            vmlal.u16   q15, d27, d2[2]
    109:    vext.u16    q12, q6, q7, #7
            vext.u16    q13, q9, q10, #1
            vmlal.u16   q14, d24, d2[1]
            vmlal.u16   q15, d25, d2[1]
            vmlal.u16   q14, d26, d2[1]
            vmlal.u16   q15, d27, d2[1]
    108:    //vext.u16    q12, q7, q8, #0
            //vext.u16    q13, q9, q10, #0
            vmlal.u16   q14, d14, d2[0]
            vmlal.u16   q15, d15, d2[0]
            vmlal.u16   q14, d18, d2[0]
            vmlal.u16   q15, d19, d2[0]
    107:    vext.u16    q12, q7, q8, #1
            vext.u16    q13, q8, q9, #7
            vmlal.u16   q14, d24, d1[3]
            vmlal.u16   q15, d25, d1[3]
            vmlal.u16   q14, d26, d1[3]
            vmlal.u16   q15, d27, d1[3]
    106:    vext.u16    q12, q7, q8, #2
            vext.u16    q13, q8, q9, #6
            vmlal.u16   q14, d24, d1[2]
            vmlal.u16   q15, d25, d1[2]
            vmlal.u16   q14, d26, d1[2]
            vmlal.u16   q15, d27, d1[2]
    105:    vext.u16    q12, q7, q8, #3
            vext.u16    q13, q8, q9, #5
            vmlal.u16   q14, d24, d1[1]
            vmlal.u16   q15, d25, d1[1]
            vmlal.u16   q14, d26, d1[1]
            vmlal.u16   q15, d27, d1[1]
    104:    //vext.u16    q12, q7, q8, #4
            //vext.u16    q13, q8, q9, #4
            vmlal.u16   q14, d15, d1[0]
            vmlal.u16   q15, d16, d1[0]
            vmlal.u16   q14, d17, d1[0]
            vmlal.u16   q15, d18, d1[0]
    103:    vext.u16    q12, q7, q8, #5
            vext.u16    q13, q8, q9, #3
            vmlal.u16   q14, d24, d0[3]
            vmlal.u16   q15, d25, d0[3]
            vmlal.u16   q14, d26, d0[3]
            vmlal.u16   q15, d27, d0[3]
    102:    vext.u16    q12, q7, q8, #6
            vext.u16    q13, q8, q9, #2
            vmlal.u16   q14, d24, d0[2]
            vmlal.u16   q15, d25, d0[2]
            vmlal.u16   q14, d26, d0[2]
            vmlal.u16   q15, d27, d0[2]
    101:    vext.u16    q12, q7, q8, #7
            vext.u16    q13, q8, q9, #1
            vmlal.u16   q14, d24, d0[1]
            vmlal.u16   q15, d25, d0[1]
            vmlal.u16   q14, d26, d0[1]
            vmlal.u16   q15, d27, d0[1]

            vqrshrn.u32 d28, q14, #16
            vqrshrn.u32 d29, q15, #16
            vqrshrn.u16 d31, q14, #FRACTION_BITS

            vmov        q6, q7
            vmov        q7, q8
            vmov        q8, q9
            vmov        q9, q10
            vmov        q10, q11
.endm/*}}}*/

.macro hconv1_25/*{{{*/
            vext.u16    q12, q6, q7, #7
            vmull.u16   q14, d24, d0[0]
            vmull.u16   q15, d25, d0[0]

            ldr         r12, [pc, r5, LSL #2]
            add         pc, pc, r12
            bkpt
    100:    .word 101f-100b
            .word 102f-100b
            .word 103f-100b
            .word 104f-100b
            .word 105f-100b
            .word 106f-100b
            .word 107f-100b
            .word 108f-100b
            .word 109f-100b
            .word 110f-100b
            .word 111f-100b
            .word 112f-100b
            .word 113f-100b
            .word 114f-100b
            .word 115f-100b
            .word 116f-100b
            .word 117f-100b
            .word 118f-100b
            .word 119f-100b
            .word 120f-100b
            .word 121f-100b
            .word 122f-100b
            .word 123f-100b
            .word 124f-100b
            .word 125f-100b
    125:    vext.u16    q12, q3, q4, #6
            vext.u16    q13, q10, q11, #0
            vmlal.u16   q14, d24, d6[1]
            vmlal.u16   q15, d25, d6[1]
            vmlal.u16   q14, d26, d6[1]
            vmlal.u16   q15, d27, d6[1]
    124:    vext.u16    q12, q3, q4, #7
            vext.u16    q13, q9, q10, #7
            vmlal.u16   q14, d24, d6[0]
            vmlal.u16   q15, d25, d6[0]
            vmlal.u16   q14, d26, d6[0]
            vmlal.u16   q15, d27, d6[0]
    123:    vext.u16    q12, q4, q5, #0
            vext.u16    q13, q9, q10, #6
            vmlal.u16   q14, d24, d5[3]
            vmlal.u16   q15, d25, d5[3]
            vmlal.u16   q14, d26, d5[3]
            vmlal.u16   q15, d27, d5[3]
    122:    vext.u16    q12, q4, q5, #1
            vext.u16    q13, q9, q10, #5
            vmlal.u16   q14, d24, d5[2]
            vmlal.u16   q15, d25, d5[2]
            vmlal.u16   q14, d26, d5[2]
            vmlal.u16   q15, d27, d5[2]
    121:    vext.u16    q12, q4, q5, #2
            vext.u16    q13, q9, q10, #4
            vmlal.u16   q14, d24, d5[1]
            vmlal.u16   q15, d25, d5[1]
            vmlal.u16   q14, d26, d5[1]
            vmlal.u16   q15, d27, d5[1]
    120:    vext.u16    q12, q4, q5, #3
            vext.u16    q13, q9, q10, #3
            vmlal.u16   q14, d24, d5[0]
            vmlal.u16   q15, d25, d5[0]
            vmlal.u16   q14, d26, d5[0]
            vmlal.u16   q15, d27, d5[0]
    119:    vext.u16    q12, q4, q5, #4
            vext.u16    q13, q9, q10, #2
            vmlal.u16   q14, d24, d4[3]
            vmlal.u16   q15, d25, d4[3]
            vmlal.u16   q14, d26, d4[3]
            vmlal.u16   q15, d27, d4[3]
    118:    vext.u16    q12, q4, q5, #5
            vext.u16    q13, q9, q10, #1
            vmlal.u16   q14, d24, d4[2]
            vmlal.u16   q15, d25, d4[2]
            vmlal.u16   q14, d26, d4[2]
            vmlal.u16   q15, d27, d4[2]
    117:    vext.u16    q12, q4, q5, #6
            vext.u16    q13, q9, q10, #0
            vmlal.u16   q14, d24, d4[1]
            vmlal.u16   q15, d25, d4[1]
            vmlal.u16   q14, d26, d4[1]
            vmlal.u16   q15, d27, d4[1]
    116:    vext.u16    q12, q4, q5, #7
            vext.u16    q13, q8, q9, #7
            vmlal.u16   q14, d24, d4[0]
            vmlal.u16   q15, d25, d4[0]
            vmlal.u16   q14, d26, d4[0]
            vmlal.u16   q15, d27, d4[0]
    115:    vext.u16    q12, q5, q6, #0
            vext.u16    q13, q8, q9, #6
            vmlal.u16   q14, d24, d3[3]
            vmlal.u16   q15, d25, d3[3]
            vmlal.u16   q14, d26, d3[3]
            vmlal.u16   q15, d27, d3[3]
    114:    vext.u16    q12, q5, q6, #1
            vext.u16    q13, q8, q9, #5
            vmlal.u16   q14, d24, d3[2]
            vmlal.u16   q15, d25, d3[2]
            vmlal.u16   q14, d26, d3[2]
            vmlal.u16   q15, d27, d3[2]
    113:    vext.u16    q12, q5, q6, #2
            vext.u16    q13, q8, q9, #4
            vmlal.u16   q14, d24, d3[1]
            vmlal.u16   q15, d25, d3[1]
            vmlal.u16   q14, d26, d3[1]
            vmlal.u16   q15, d27, d3[1]
    112:    vext.u16    q12, q5, q6, #3
            vext.u16    q13, q8, q9, #3
            vmlal.u16   q14, d24, d3[0]
            vmlal.u16   q15, d25, d3[0]
            vmlal.u16   q14, d26, d3[0]
            vmlal.u16   q15, d27, d3[0]
    111:    vext.u16    q12, q5, q6, #4
            vext.u16    q13, q8, q9, #2
            vmlal.u16   q14, d24, d2[3]
            vmlal.u16   q15, d25, d2[3]
            vmlal.u16   q14, d26, d2[3]
            vmlal.u16   q15, d27, d2[3]
    110:    vext.u16    q12, q5, q6, #5
            vext.u16    q13, q8, q9, #1
            vmlal.u16   q14, d24, d2[2]
            vmlal.u16   q15, d25, d2[2]
            vmlal.u16   q14, d26, d2[2]
            vmlal.u16   q15, d27, d2[2]
    109:    vext.u16    q12, q5, q6, #6
            vext.u16    q13, q8, q9, #0
            vmlal.u16   q14, d24, d2[1]
            vmlal.u16   q15, d25, d2[1]
            vmlal.u16   q14, d26, d2[1]
            vmlal.u16   q15, d27, d2[1]
    108:    vext.u16    q12, q5, q6, #7
            vext.u16    q13, q7, q8, #7
            vmlal.u16   q14, d24, d2[0]
            vmlal.u16   q15, d25, d2[0]
            vmlal.u16   q14, d26, d2[0]
            vmlal.u16   q15, d27, d2[0]
    107:    vext.u16    q12, q6, q7, #0
            vext.u16    q13, q7, q8, #6
            vmlal.u16   q14, d24, d1[3]
            vmlal.u16   q15, d25, d1[3]
            vmlal.u16   q14, d26, d1[3]
            vmlal.u16   q15, d27, d1[3]
    106:    vext.u16    q12, q6, q7, #1
            vext.u16    q13, q7, q8, #5
            vmlal.u16   q14, d24, d1[2]
            vmlal.u16   q15, d25, d1[2]
            vmlal.u16   q14, d26, d1[2]
            vmlal.u16   q15, d27, d1[2]
    105:    vext.u16    q12, q6, q7, #2
            vext.u16    q13, q7, q8, #4
            vmlal.u16   q14, d24, d1[1]
            vmlal.u16   q15, d25, d1[1]
            vmlal.u16   q14, d26, d1[1]
            vmlal.u16   q15, d27, d1[1]
    104:    vext.u16    q12, q6, q7, #3
            vext.u16    q13, q7, q8, #3
            vmlal.u16   q14, d24, d1[0]
            vmlal.u16   q15, d25, d1[0]
            vmlal.u16   q14, d26, d1[0]
            vmlal.u16   q15, d27, d1[0]
    103:    vext.u16    q12, q6, q7, #4
            vext.u16    q13, q7, q8, #2
            vmlal.u16   q14, d24, d0[3]
            vmlal.u16   q15, d25, d0[3]
            vmlal.u16   q14, d26, d0[3]
            vmlal.u16   q15, d27, d0[3]
    102:    vext.u16    q12, q6, q7, #5
            vext.u16    q13, q7, q8, #1
            vmlal.u16   q14, d24, d0[2]
            vmlal.u16   q15, d25, d0[2]
            vmlal.u16   q14, d26, d0[2]
            vmlal.u16   q15, d27, d0[2]
    101:    vext.u16    q12, q6, q7, #6
            vext.u16    q13, q7, q8, #0
            vmlal.u16   q14, d24, d0[1]
            vmlal.u16   q15, d25, d0[1]
            vmlal.u16   q14, d26, d0[1]
            vmlal.u16   q15, d27, d0[1]

            vqrshrn.u32 d28, q14, #16
            vqrshrn.u32 d29, q15, #16
            vqrshrn.u16 d31, q14, #FRACTION_BITS

            vmov        d7, d9
            vmov        q4, q5
            vmov        q5, q6
            vmov        q6, q7
            vmov        q7, q8
            vmov        q8, q9
            vmov        q9, q10
            vmov        q10, q11
.endm/*}}}*/

#define TUNED_LIST4 6, 12
.macro hconv4_6/*{{{*/
            vmull.u16   q14, d14, d0[0]
            vmull.u16   q15, d15, d0[0]

            ldr         r12, [pc, r5, LSL #2]
            add         pc, pc, r12
            bkpt
    100:    .word 101f-100b
            .word 102f-100b
            .word 103f-100b
            .word 104f-100b
            .word 105f-100b
            .word 106f-100b
    106:    vmlal.u16   q14, d8,  d1[2]
            vmlal.u16   q15, d9,  d1[2]
            vmlal.u16   q14, d20, d1[2]
            vmlal.u16   q15, d21, d1[2]
    105:    vmlal.u16   q14, d9,  d1[1]
            vmlal.u16   q15, d10, d1[1]
            vmlal.u16   q14, d19, d1[1]
            vmlal.u16   q15, d20, d1[1]
    104:    vmlal.u16   q14, d10, d1[0]
            vmlal.u16   q15, d11, d1[0]
            vmlal.u16   q14, d18, d1[0]
            vmlal.u16   q15, d19, d1[0]
    103:    vmlal.u16   q14, d11, d0[3]
            vmlal.u16   q15, d12, d0[3]
            vmlal.u16   q14, d17, d0[3]
            vmlal.u16   q15, d18, d0[3]
    102:    vmlal.u16   q14, d12, d0[2]
            vmlal.u16   q15, d13, d0[2]
            vmlal.u16   q14, d16, d0[2]
            vmlal.u16   q15, d17, d0[2]
    101:    vmlal.u16   q14, d13, d0[1]
            vmlal.u16   q15, d14, d0[1]
            vmlal.u16   q14, d15, d0[1]
            vmlal.u16   q15, d16, d0[1]

            vqrshrn.u32 d28, q14, #16
            vqrshrn.u32 d29, q15, #16
            vqrshrn.u16 d31, q14, #FRACTION_BITS

            vmov        q4, q5
            vmov        q5, q6
            vmov        q6, q7
            vmov        q7, q8
            vmov        q8, q9
            vmov        q9, q10
            vmov        q10, q11
.endm/*}}}*/

.macro hconv4_12/*{{{*/
            vmull.u16   q14, d8, d0[0]
            vmull.u16   q15, d9, d0[0]

            ldr         r12, [pc, r5, LSL #2]
            add         pc, pc, r12
            bkpt
    100:    .word 101f-100b
            .word 102f-100b
            .word 103f-100b
            .word 104f-100b
            .word 105f-100b
            .word 106f-100b
            .word 107f-100b
            .word 108f-100b
            .word 109f-100b
            .word 110f-100b
            .word 111f-100b
            .word 112f-100b
    112:    add         r12, r9, #0x1a0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d3[0]
            vmlal.u16   q15, d25, d3[0]
            vmlal.u16   q14, d20, d3[0]
            vmlal.u16   q15, d21, d3[0]
    111:    add         r12, r9, #0x1a8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12:64]
            vmlal.u16   q14, d24, d2[3]
            vmlal.u16   q15, d25, d2[3]
            vmlal.u16   q14, d19, d2[3]
            vmlal.u16   q15, d20, d2[3]
    110:    add         r12, r9, #0x1b0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d2[2]
            vmlal.u16   q15, d25, d2[2]
            vmlal.u16   q14, d18, d2[2]
            vmlal.u16   q15, d19, d2[2]
    109:    add         r12, r9, #0x1b8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12:64]
            vmlal.u16   q14, d24, d2[1]
            vmlal.u16   q15, d25, d2[1]
            vmlal.u16   q14, d17, d2[1]
            vmlal.u16   q15, d18, d2[1]
    108:    add         r12, r9, #0x1c0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d2[0]
            vmlal.u16   q15, d25, d2[0]
            vmlal.u16   q14, d16, d2[0]
            vmlal.u16   q15, d17, d2[0]
    107:    add         r12, r9, #0x1c8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12:64]
            vmlal.u16   q14, d24, d1[3]
            vmlal.u16   q15, d25, d1[3]
            vmlal.u16   q14, d15, d1[3]
            vmlal.u16   q15, d16, d1[3]
    106:    add         r12, r9, #0x1d0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d1[2]
            vmlal.u16   q15, d25, d1[2]
            vmlal.u16   q14, d14, d1[2]
            vmlal.u16   q15, d15, d1[2]
    105:    add         r12, r9, #0x1d8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12:64]
            vmlal.u16   q14, d24, d1[1]
            vmlal.u16   q15, d25, d1[1]
            vmlal.u16   q14, d13, d1[1]
            vmlal.u16   q15, d14, d1[1]
    104:    add         r12, r9, #0x1e0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d1[0]
            vmlal.u16   q15, d25, d1[0]
            vmlal.u16   q14, d12, d1[0]
            vmlal.u16   q15, d13, d1[0]
    103:    add         r12, r9, #0x1e8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12:64]
            vmlal.u16   q14, d24, d0[3]
            vmlal.u16   q15, d25, d0[3]
            vmlal.u16   q14, d11, d0[3]
            vmlal.u16   q15, d12, d0[3]
    102:    add         r12, r9, #0x1f0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d0[2]
            vmlal.u16   q15, d25, d0[2]
            vmlal.u16   q14, d10, d0[2]
            vmlal.u16   q15, d11, d0[2]
    101:    add         r12, r9, #0x1f8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]
            vmlal.u16   q14, d24, d0[1]
            vmlal.u16   q15, d8,  d0[1]
            vmlal.u16   q14, d9,  d0[1]
            vmlal.u16   q15, d10, d0[1]

            vqrshrn.u32 d28, q14, #16
            vqrshrn.u32 d29, q15, #16
            vqrshrn.u16 d31, q14, #FRACTION_BITS

            vst1.u8     {q4}, [r9:128]!
            bic         r9, r9, #0x200
            vmov        q4, q5
            vmov        q5, q6
            vmov        q6, q7
            vmov        q7, q8
            vmov        q8, q9
            vmov        q9, q10
            vmov        q10, q11
.endm/*}}}*/

.macro hconv4_25/*{{{*/
            add         r12, r9, #0x198
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12:64]
            vmull.u16   q14, d24, d0[0]
            vmull.u16   q15, d25, d0[0]

            ldr         r12, [pc, r5, LSL #2]
            add         pc, pc, r12
            bkpt
    100:    .word 101f-100b
            .word 102f-100b
            .word 103f-100b
            .word 104f-100b
            .word 105f-100b
            .word 106f-100b
            .word 107f-100b
            .word 108f-100b
            .word 109f-100b
            .word 110f-100b
            .word 111f-100b
            .word 112f-100b
            .word 113f-100b
            .word 114f-100b
            .word 115f-100b
            .word 116f-100b
            .word 117f-100b
            .word 118f-100b
            .word 119f-100b
            .word 120f-100b
            .word 121f-100b
            .word 122f-100b
            .word 123f-100b
            .word 124f-100b
            .word 125f-100b
    125:    add         r12, r9, #0x0d0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d6[1]
            vmlal.u16   q15, d25, d6[1]
            vmlal.u16   q14, d20, d6[1]
            vmlal.u16   q15, d21, d6[1]
    124:    add         r12, r9, #0x0d8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
            vmlal.u16   q14, d24, d6[0]
            vmlal.u16   q15, d25, d6[0]
            vmlal.u16   q14, d19, d6[0]
            vmlal.u16   q15, d20, d6[0]
    123:    add         r12, r9, #0x0e0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d5[3]
            vmlal.u16   q15, d25, d5[3]
            vmlal.u16   q14, d18, d5[3]
            vmlal.u16   q15, d19, d5[3]
    122:    add         r12, r9, #0x0e8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
            vmlal.u16   q14, d24, d5[2]
            vmlal.u16   q15, d25, d5[2]
            vmlal.u16   q14, d17, d5[2]
            vmlal.u16   q15, d18, d5[2]
    121:    add         r12, r9, #0x0f0
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d5[1]
            vmlal.u16   q15, d25, d5[1]
            vmlal.u16   q14, d16, d5[1]
            vmlal.u16   q15, d17, d5[1]
    120:    add         r12, r9, #0x0f8
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
            vmlal.u16   q14, d24, d5[0]
            vmlal.u16   q15, d25, d5[0]
            vmlal.u16   q14, d15, d5[0]
            vmlal.u16   q15, d16, d5[0]
    119:    add         r12, r9, #0x100
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d4[3]
            vmlal.u16   q15, d25, d4[3]
            vmlal.u16   q14, d14, d4[3]
            vmlal.u16   q15, d15, d4[3]
    118:    add         r12, r9, #0x108
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
            vmlal.u16   q14, d24, d4[2]
            vmlal.u16   q15, d25, d4[2]
            vmlal.u16   q14, d13, d4[2]
            vmlal.u16   q15, d14, d4[2]
    117:    add         r12, r9, #0x110
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d4[1]
            vmlal.u16   q15, d25, d4[1]
            vmlal.u16   q14, d12, d4[1]
            vmlal.u16   q15, d13, d4[1]
    116:    add         r12, r9, #0x118
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
            vmlal.u16   q14, d24, d4[0]
            vmlal.u16   q15, d25, d4[0]
            vmlal.u16   q14, d11, d4[0]
            vmlal.u16   q15, d12, d4[0]
    115:    add         r12, r9, #0x120
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d3[3]
            vmlal.u16   q15, d25, d3[3]
            vmlal.u16   q14, d10, d3[3]
            vmlal.u16   q15, d11, d3[3]
    114:    add         r12, r9, #0x128
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
            vmlal.u16   q14, d24, d3[2]
            vmlal.u16   q15, d25, d3[2]
            vmlal.u16   q14, d9,  d3[2]
            vmlal.u16   q15, d10, d3[2]
    113:    add         r12, r9, #0x130
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
            vmlal.u16   q14, d24, d3[1]
            vmlal.u16   q15, d25, d3[1]
            vmlal.u16   q14, d8,  d3[1]
            vmlal.u16   q15, d9,  d3[1]
    112:    add         r12, r9, #0x138
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
                                            add         r12, r9, #0x1f8
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26}, [r12:64]
            vmlal.u16   q14, d24, d3[0]
            vmlal.u16   q15, d25, d3[0]
            vmlal.u16   q14, d26, d3[0]   @ Could be d7, without the load, right?
            vmlal.u16   q15, d8,  d3[0]
    111:    add         r12, r9, #0x140
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
                                            add         r12, r9, #0x1f0
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26,d27}, [r12:128]
            vmlal.u16   q14, d24, d2[3]
            vmlal.u16   q15, d25, d2[3]
            vmlal.u16   q14, d26, d2[3]
            vmlal.u16   q15, d27, d2[3]
    110:    add         r12, r9, #0x148
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
                                            add         r12, r9, #0x1e8
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26}, [r12:64]!
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d27}, [r12:64]
            vmlal.u16   q14, d24, d2[2]
            vmlal.u16   q15, d25, d2[2]
            vmlal.u16   q14, d26, d2[2]
            vmlal.u16   q15, d27, d2[2]
    109:    add         r12, r9, #0x150
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
                                            add         r12, r9, #0x1e0
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26,d27}, [r12:128]
            vmlal.u16   q14, d24, d2[1]
            vmlal.u16   q15, d25, d2[1]
            vmlal.u16   q14, d26, d2[1]
            vmlal.u16   q15, d27, d2[1]
    108:    add         r12, r9, #0x158
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
                                            add         r12, r9, #0x1d8
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26}, [r12:64]!
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d27}, [r12:64]
            vmlal.u16   q14, d24, d2[0]
            vmlal.u16   q15, d25, d2[0]
            vmlal.u16   q14, d26, d2[0]
            vmlal.u16   q15, d27, d2[0]
    107:    add         r12, r9, #0x160
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
                                            add         r12, r9, #0x1d0
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26,d27}, [r12:128]
            vmlal.u16   q14, d24, d1[3]
            vmlal.u16   q15, d25, d1[3]
            vmlal.u16   q14, d26, d1[3]
            vmlal.u16   q15, d27, d1[3]
    106:    add         r12, r9, #0x168
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
                                            add         r12, r9, #0x1c8
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26}, [r12:64]!
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d27}, [r12:64]
            vmlal.u16   q14, d24, d1[2]
            vmlal.u16   q15, d25, d1[2]
            vmlal.u16   q14, d26, d1[2]
            vmlal.u16   q15, d27, d1[2]
    105:    add         r12, r9, #0x170
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
                                            add         r12, r9, #0x1c0
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26,d27}, [r12:128]
            vmlal.u16   q14, d24, d1[1]
            vmlal.u16   q15, d25, d1[1]
            vmlal.u16   q14, d26, d1[1]
            vmlal.u16   q15, d27, d1[1]
    104:    add         r12, r9, #0x178
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
                                            add         r12, r9, #0x1b8
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26}, [r12:64]!
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d27}, [r12:64]
            vmlal.u16   q14, d24, d1[0]
            vmlal.u16   q15, d25, d1[0]
            vmlal.u16   q14, d26, d1[0]
            vmlal.u16   q15, d27, d1[0]
    103:    add         r12, r9, #0x180
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]
                                            add         r12, r9, #0x1b0
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26,d27}, [r12:128]
            vmlal.u16   q14, d24, d0[3]
            vmlal.u16   q15, d25, d0[3]
            vmlal.u16   q14, d26, d0[3]
            vmlal.u16   q15, d27, d0[3]
    102:    add         r12, r9, #0x188
            bic         r12, r12, #0x200
            vld1.u16    {d24}, [r12:64]!
            bic         r12, r12, #0x200
            vld1.u16    {d25}, [r12]
                                            add         r12, r9, #0x1a8
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d26}, [r12:64]!
                                            bic         r12, r12, #0x200
                                            vld1.u16    {d27}, [r12:64]
            vmlal.u16   q14, d24, d0[2]
            vmlal.u16   q15, d25, d0[2]
            vmlal.u16   q14, d26, d0[2]
            vmlal.u16   q15, d27, d0[2]
    101:    add         r12, r9, #0x190
            bic         r12, r12, #0x200
            vld1.u16    {d24,d25}, [r12:128]!
            bic         r12, r12, #0x200
            vld1.u16    {d26,d27}, [r12:128]
            vmlal.u16   q14, d24, d0[1]
            vmlal.u16   q15, d25, d0[1]
            vmlal.u16   q14, d26, d0[1]
            vmlal.u16   q15, d27, d0[1]

            vqrshrn.u32 d28, q14, #16
            vqrshrn.u32 d29, q15, #16
            vqrshrn.u16 d31, q14, #FRACTION_BITS

            vst1.u8     {q4}, [r9:128]!
            bic         r9, r9, #0x200
            vmov        q4, q5
            vmov        q5, q6
            vmov        q6, q7
            vmov        q7, q8
            vmov        q8, q9
            vmov        q9, q10
            vmov        q10, q11
.endm/*}}}*/

/* Dedicated function wrapper for the fetch macro, for the cases where
 * performance isn't that important, to keep code size down.
 */
PRIVATE(fetch_generic_asm)
            push        {r10,r11}
            fetch
            pop         {r10,r11}
            bx          lr
END(fetch_generic_asm)


/* Fetch the next (16 - (r10 & 15)) columns of data, avoiding reading memory
 * beyond that limit, and filling the rest of the vector with the last legal
 * pixel.
 * Result is in q10 and q11.  q8 and q9 are filled with the first legal pixel.
 * Note: This function can read beyond the right edge of input if the image is
 * narrower than 16 bytes.
 */
PRIVATE(fetch_clampleft1)
            push        {r12,lr}
            bl          fetch_generic_asm
            vdup.u16    q8, d20[0]
            vdup.u16    q9, d20[0]
            ands        r12, r10, #15
            beq         1f
            sub         r1, r1, r12
            sub         r10, r10, r12
            sub         sp, sp, #32
            vst1.u16    {q10,q11}, [sp]
            sub         r12, sp, r12, LSL #1
            sub         sp, sp, #32
            vst1.u16    {q8,q9}, [sp]
            vld1.u16    {q10,q11}, [r12]
            add         sp, sp, #64
1:          pop         {r12,pc}
END(fetch_clampleft1)

PRIVATE(fetch_clampleft4)
            push        {r12,lr}
            bl          fetch_generic_asm
            vmov.u16    d16, d20
            vmov.u16    d17, d20
            vmov.u16    d18, d20
            vmov.u16    d19, d20
            ands        r12, r10, #15
            beq         1f
            sub         r1, r1, r12
            sub         r10, r10, r12
            sub         sp, sp, #32
            vst1.u16    {q10-q11}, [sp]
            sub         r12, sp, r12, LSL #1
            sub         sp, sp, #32
            vst1.u16    {q8,q9}, [sp]
            vld1.u16    {q10,q11}, [r12]
            add         sp, sp, #64
1:          pop         {r12,pc}
END(fetch_clampleft4)

/* Fetch only the next (r11 & 15) (where 0 means 16) columns of data, avoiding
 * reading memory beyond that limit, and filling the rest of the vector with
 * the last legal pixel.
 * Result is in q10 and q11.  q12 and q13 are filled with the last legal pixel.
 * Note: This function can read beyond the left edge of input if the image is
 * narrower than 16 bytes.
 */
PRIVATE(fetch_clampright1)
            push        {r12, lr}
            rsb         r12, r11, #0
            ands        r12, r12, #15
            beq         1f
            sub         r1, r1, r12
            bl          fetch_generic_asm
            vdup.u16    q12, d23[3]
            vdup.u16    q13, d23[3]
            rsb         r12, r11, #0
            and         r12, r12, #15
            sub         sp, sp, #32
            vst1.u16    {q12,q13}, [sp]
            sub         sp, sp, #32
            add         r12, sp, r12, LSL #1
            vst1.u16    {q10,q11}, [sp]
            vld1.u16    {q10,q11}, [r12]
            add         sp, sp, #64
            pop         {r12,pc}
1:          bl          fetch_generic_asm
            vdup.u16    q12, d23[3]
            vdup.u16    q13, d23[3]
            pop         {r12,pc}
END(fetch_clampright1)

PRIVATE(fetch_clampright4)
            push        {r12, lr}
            rsb         r12, r11, #0
            ands        r12, r12, #15
            beq         1f
            sub         r1, r1, r12
            bl          fetch_generic_asm
            vmov.u16    d24, d23
            vmov.u16    d25, d23
            vmov.u16    d26, d23
            vmov.u16    d27, d23
            rsb         r12, r11, #0
            and         r12, r12, #15
            sub         sp, sp, #32
            vst1.u16    {q12-q13}, [sp]
            sub         sp, sp, #32
            add         r12, sp, r12, LSL #1
            vst1.u16    {q10,q11}, [sp]
            vld1.u16    {q10,q11}, [r12]
            add         sp, sp, #64
            pop         {r12,pc}
1:          bl          fetch_generic_asm
            vmov.u16    d24, d23
            vmov.u16    d25, d23
            vmov.u16    d26, d23
            vmov.u16    d27, d23
            pop         {r12,pc}
END(fetch_clampright4)

/* Given values in q10 and q11, and an index in r11, sweep the (r11 & 15)th
 * value across to fill the rest of the register pair.  Used for filling the
 * right hand edge of the window when reading too close to the right hand edge
 * of the image.
 * Also returns a dup-ed copy of the last element in q12 for the tail-fill
 * case (this happens incidentally in common path, but must be done
 * deliberately in the fast-out path).
 */
PRIVATE(prefill_sweepright1)
            ands        r12, r11, #15
            beq         1f
            sub         r12, r12, #1
            sub         sp, sp, #64
            vst1.u16    {q10,q11}, [sp]
            add         r12, sp, r12, LSL #1
            vld1.u16    {d24[],d25[]}, [r12]
            vld1.u16    {d26[],d27[]}, [r12]
            vst1.u16    {q12,q13}, [r12]
            vld1.u16    {q10,q11}, [sp]
            add         sp, sp, #64
            bx          lr
1:          vdup.u16    q12, d23[3]
            vdup.u16    q13, d23[3]
            bx          lr
END(prefill_sweepright1)

PRIVATE(prefill_sweepright4)
            ands        r12, r11, #15
            beq         1f
            sub         r12, r12, #4
            sub         sp, sp, #64
            vst1.u16    {q10,q11}, [sp]
            add         r12, sp, r12, LSL #1
            vld1.u64    {d24}, [r12]
            vld1.u64    {d25}, [r12]
            vld1.u64    {d26}, [r12]
            vld1.u64    {d27}, [r12]
            vst1.u16    {q12,q13}, [r12]
            vld1.u16    {q10,q11}, [sp]
            add         sp, sp, #64
            bx          lr
1:          vmov.u16    d24, d23
            vmov.u16    d25, d23
            vmov.u16    d26, d23
            vmov.u16    d27, d23
            bx          lr
END(prefill_sweepright4)

/* The main loop keeps a sliding window of data that has already been convolved
 * in the vertical axis for the current line.  This usually stays in the
 * register file, but spills to memory for large windows.  The first thing that
 * needs to be done at start-up is to fill this window with image data, taking
 * into account the padding needed if the left or right edges of the image fall
 * within this window.
 */

/* Because the window is in the register file writes to it cannot be indexed
 * by another register.  Consequently the fill loops are unrolled to address
 * the registers directly.  This macro distinguishes between writes to the
 * register file and writes to the spill buffer (indicated by a destination
 * register named xx).
 */
.macro prefill_out ra, rb, sra, srb, srb_hi
  .ifc \ra,xx
    .ifc \rb,xx
            vst1.u16    {\sra,\srb}, [r9:128]!
    .else
            /* this case is used only for the last tap of uchar1 r=25 */
            /* discard \sra */
            vmov.u16    \rb, \srb_hi
    .endif
  .else
    .ifnc \ra,\sra
            vmov.u16    \ra, \sra
    .endif
    .ifnc \rb,\srb
            vmov.u16    \rb, \srb
    .endif
  .endif
.endm

/* This macro provides the list of registers representing the window, and the
 * cases where the register file is too small and a spill buffer is used
 * instead.
 * Since several specialisations of each function are generated, this also
 * culls superfluous iterations, and sets the variable `i` for subsequent
 * macros indicating the current index into the window.
 */
.macro prefill_list, macro, nextmacro, max_r, step, label
  .macro ifneeded macro, nextmacro, line, nextline, ra, rb, step, label
    .if windowsize >= (\line * 16)
      .set i, windowsize - (\line * 16)
\label\macro\line:
            prefill_\macro \label\nextmacro\line, \label\nextmacro\nextline, \ra, \rb, \step
    .endif
  .endm
  .if \step > 1
            ifneeded \macro \nextmacro, 13, 12, xx, xx,  \step, \label
            ifneeded \macro \nextmacro, 12, 11, xx, xx,  \step, \label
            ifneeded \macro \nextmacro, 11, 10, xx, xx,  \step, \label
            ifneeded \macro \nextmacro, 10,  9, xx, xx,  \step, \label
            ifneeded \macro \nextmacro,  9,  8, xx, xx,  \step, \label
            ifneeded \macro \nextmacro,  8,  7, xx, xx,  \step, \label
            ifneeded \macro \nextmacro,  7,  6, xx, xx,  \step, \label
            ifneeded \macro \nextmacro,  6,  5, xx, xx,  \step, \label
            ifneeded \macro \nextmacro,  5,  4, xx, xx,  \step, \label
            ifneeded \macro \nextmacro,  4,  3, xx, xx,  \step, \label
  .else
            /* q3 normally contains the coefficient table, but it's not fully
             * used.  In the uchar1, r=25 case the other half of q3 is used for
             * the last two window taps to avoid falling out to memory.
             */
            ifneeded \macro \nextmacro,  4,  3, xx, d7,   \step, \label
  .endif
            ifneeded \macro \nextmacro,  3,  2, q4, q5,   \step, \label
            ifneeded \macro \nextmacro,  2,  1, q6, q7,   \step, \label
            ifneeded \macro \nextmacro,  1,  0, q8, q9,   \step, \label

\label\macro\()0:
            b           \label\()_end
  .purgem ifneeded
.endm

/* These macros represent the possible stages of filling the window.
 * Each macro is unrolled enough times that it can fill the entire window
 * itself, but normally it will have to hand control to subsequent macros
 * part-way through and this is done using labels named \next and \after, where
 * \next is the next macro starting at the same window position and \after is
 * the next macro starting after the current window position.
 */

/* leftfill: v8 and v9 contain the left padding value.  While the window
 * extends outside of the image on the left-hand side, and at least 16 more
 * padding values are needed in the window, store v8 and v9 into the window.
 * Otherwise skip forward to storing image data.
 */
.macro prefill_leftfill, next, after, ra, rb, step
            cmp         r10, #i+16
            blo         \next
            prefill_out \ra, \rb, q8, q9, d19
.endm

/* leftedge: The very first non-fill or partial-fill chunk from the image is
 * already loaded (as it was used to calculate the left padding value), so
 * store it here, and then drop into the regular load/store cycle in the next
 * macro.
 */
.macro prefill_leftedge, next, after, ra, rb, step
1:          prefill_out \ra, \rb, q10, q11, d23
            b           \after
.endm

/* dofetch: Copy chunks of the image into the window without any complications
 * from edge conditions.
 */
.macro prefill_dofetch, next, after, ra, rb, step
            cmp         r11, #i+16
            bls         \next
            bl          fetch_generic_asm
            prefill_out \ra, \rb, q10, q11, d23
.endm

/* rightedge: The last fetch (currently in v10 and v11) may have gone beyond
 * the right-hand edge of the image.  In that case sweep the last valid pixel
 * across the rest of the chunk, and in either case prepare padding data in v12
 * and v13 for the next macro.  This is done in fetch_clampright.
 * This only happens once before going on to the next macro.
 * Sometimes leftedge also covers the rightedge case, in which case this has
 * to be skipped altogether.
 */
.macro prefill_rightedge, next, after, ra, rb, step
            cmp         r11, #i
            bls         \next
            bl          fetch_clampright\step
            prefill_out \ra, \rb, q10, q11, d23
            b           \after
.endm

/* rightfill: The rest of the window is simply filled with right padding from
 * v12 and v13.
 */
.macro prefill_rightfill, next, after, ra, rb, step
            prefill_out \ra, \rb, q12, q13, d25
.endm

/* Here all of the macros above are unrolled and laid out in the proper order.
 */
.macro prefill_body, max_r, step, label
            prefill_list leftfill,  leftedge,   \max_r, \step, \label
            prefill_list leftedge,  dofetch,    \max_r, \step, \label
            prefill_list dofetch,   rightedge,  \max_r, \step, \label
            prefill_list rightedge, rightfill,  \max_r, \step, \label
            prefill_list rightfill, oops,       \max_r, \step, \label
\label\()_end:
.endm

/* Fill the convolution window with context data.  The aim here is to load
 * exactly 2*r columns, and in the main loop to read as many columns as will be
 * written.  This is complicated by the window being divided into chunks at
 * register boundaries, and the need to handle cases when the input starts very
 * close to the left or right (or both) edges of the image and the need to fill
 * the spaces that leaves with left and right edge padding values.
 *
 * Input:
 *      r1 -- src
 *      r2 -- pitch
 *      r3 -- count
 *      r4 -- available image data right of src pointer
 *      r5 -- r
 *      r6 -- rup
 *      r7 -- rdn
 *      r8 -- available image data left of src pointer
 *      r9 -- buffer (if needed)
 * Output:
 *      r4 -= min(inlen, count + windowsize - centertap)
 *      r1 += min(inlen, count + windowsize - centertap)
 * Modifies:
 *      r10 -- fill start index in the window
 *      r11 -- fill stop index in the window
 *      r12 -- scratch
 */
.macro prefill step=1, max_r=25, label=xx
.set windowsize, (((\max_r + \max_r) * \step + 15) & ~15)
.set centertap, (windowsize - \max_r * \step)
            mov         r10, #centertap
            subs        r10, r10, r8
            movlo       r10, #0

            subs        r11, r4, #windowsize - centertap
            movhs       r11, #0
            add         r11, r11, #windowsize

            /* r10 indicates where in the window legal image data begins.
             * r11 indicates where in the window legal image date ends.
             * When starting near the centre of a large image these would be
             * zero and windowsize respectively, but when starting near the
             * edges this can change.
             * When starting on the leftmost pixel, r10 will be centertap.
             * When starting on the rightmost pixel, r11 will be centertap+1.
             */

            /* r4 indicates how much data there is between the current pointers
             * and the right edge of the image.  The pointers currently point
             * to the data needed at centertap.  The subsequent code will
             * consume (windowsize - r10) data, but only the data from
             * centertap to windowsize comes out of r4's budget.
             */
1:          subs        r4, r4, #windowsize - centertap
            movlo       r4, #0

            /* And the pointers need to rewind to the start of the window.
             */
            sub         r1, r1, #centertap

            /* Unless x8 indicated that there wasn't that much data available.
             */
            add         r1, r1, r10


            /* Get the first chunk, and add padding to align it to the window
             * if necessary.
             */
            bl          fetch_clampleft\step

            /* Sometimes the start and the end of the window are in the same
             * chunk.  In that case both ends need filler at the outset.
             */
            sub         r12, r11, #1
            eor         r12,  r10, r12
            cmp         r12, #16
            bllo        prefill_sweepright\step

            /* Iterate through all the points in the window and fill them in
             * with padding or image data as needed.
             */
            prefill_body \max_r, \step, \label
.endm

/* The main body of the convolve functions.  Having already pre-filled the
 * convolution window with 2*r input values, the logic settles into a regular
 * pattern of reading and writing at a 1:1 rate until either input or output
 * expires.  The input leads the output by r values, so when processing all the
 * way to the right-hand edge, or within r pixels of that edge, the input will
 * run out first.  In the case of very narrow images, or sub-windows starting
 * near the right edge, the input may already have run out while the
 * convolution window was being filled and this loop will start with a
 * zero-length input.
 *
 * Once the input runs out, the rest of the output must be processed by padding
 * the remainder of the window with pad value from the last valid pixel from
 * the source.
 *
 * Input:
 *      r0 = dst
 *      r1 = src
 *      r2 = pitch
 *      r3 = count
 *      r4 = inlen
 *      r5 = r
 *      r6 = rup
 *      r7 = rdn
 *      r9 = buffer
 * Modifies
 *      r8 = fetch code pointer
 */
.macro conv_body core, step=1, max_r=25, labelc="", labelnc=""

            /* If x4 >= x3 then there's no need for clipping.  The main loop
             * needs to exit when either x3 or x4 runs out, so clamp x4 to be
             * no greater than x3 and use x4 for the loop.
             * However, if x4 comes out of the loop with less than 16 bytes
             * left, a partial read would be necessary to avoid reading beyond
             * the end of the image.  To avoid this, clamp x4 to the next
             * multiple of 16, which is still sufficient to force it out of the
             * loop but doesn't imply a rewind.
             */
            add         r12, r3, #15
            bic         r12, r12, #15
            cmp         r4, r12
            movhi       r4, r12

            /* First calculate the entry-point into the internal fetch logic.
             * This is done so the same function can service several kernel
             * sizes.
             */
            ldr         r8, 3f
1:          add         r8, r8, pc
            sub         r8, r5, LSL #5
            sub         r8, r5, LSL #4
            cmp         r5, r6
            cmpeq       r5, r7
            beq         5f

            /* if (r != rup || r != rdn) then the address-clamping table should
             * be used rather than the short-cut version.
             */
            ldr         r8, 3f+4
2:          add         r8, r8, pc
            sub         r8, r5, LSL #6
            b           5f
            .align 3
3:          .word       \labelnc-1b-8
            .word       \labelc-2b-8

            /* Main loop: ... */
            .align 4
3:          /* first perform a vertical convolution from memory to get the next
             * 16 taps of the horizontal window into the register file...
             */
            fetch max_r=\max_r, labelc=\labelc, labelnc=\labelnc, reg=r8

            /* ...then perform a horizontal convolution on that window to
             * produce eight output bytes, and slide the window along.
             * This has to be done twice to match the 16-way vertical pass.
             * It would be preferable to have twice the work done in \core, but
             * that would demand yet another variant on those macros and would
             * perturb the register allocation severely.
             */
            \core
            vst1.u8     {d31}, [r0]!
            \core
            vst1.u8     {d31}, [r0]!

            sub         r3, r3, #16
5:          subs        r4, r4, #16
            bhi         3b
            /* Here there's 16 or fewer bytes available before the edge of the
             * source image.  x4 holds that count minus 16 (because it was
             * decremented before the first iteration ran).  The last read may
             * not be a whole chunk, and beyond that a fill value must be used.
             *
             * Of course, none of that matters if there's no more output to
             * produce...
             */
            cmp         r3, #0
            beq         5f

            /* Oh well. */
            adds        r4, r4, #16
            bne         1f
  .if \step==1
            vdup.u16    q10, d19[3]
            vdup.u16    q11, d19[3]
  .else
            vmov.u64    d20, d19
            vmov.u64    d21, d19
            vmov.u64    d22, d19
            vmov.u64    d23, d19
  .endif
            b           3f

            /* To avoid reading past end of input, rewind pointers by (16-r4)
             * to ensure that they're exactly 16 bytes from the edge.
             */
1:          mov         r11, r4
            bl          fetch_clampright\step
            /* Now to put this padding to use, perform any remaining
             * iterations.  This is done at half the rate of the main loop,
             * because there's no longer pressure from a 16-lane window filler.
             */
3:          \core
  .if \step==1
            vdup.u16    q11, d23[3]
  .else
            vmov.u64    d22, d23
  .endif
            subs        r3, r3, #8
            blo         4f
            vst1.u8     {d31}, [r0]!
            bne         3b
            b           5f

            /* If the final iteration contained 0 < l < 8 values, then perform
             * a piecewise store of the final vector.
             */
4:          tst         r3, #4
            beq         1f
            vst1.u32    {d31[0]}, [r0]!
            vext.u8     d31, d31, d31, #4
1:          tst         r3, #2
            beq         1f
            vst1.u16    {d31[0]}, [r0]!
            vext.u8     d31, d31, d31, #2
1:          tst         r3, #1
            beq         5f
            vst1.u8     {d31[0]}, [r0]!
            vext.u8     d31, d31, d31, #1
5:          mov         r0, #0
.endm

.irp r, TUNED_LIST1, 25
PRIVATE(convolve1_\r)
            push        {r12,lr}

            prefill     step=1, max_r=\r, label=.Lcnv1_\r

            conv_body   core=hconv1_\r, step=1, max_r=\r, labelc=.Lcnv1_\r, labelnc=.Lcnvnc1_\r

            pop         {r12,pc}
END(convolve1_\r)
.endr

.irp r, TUNED_LIST4, 25
PRIVATE(convolve4_\r)
            push        {r12,lr}
            sub         r9, sp, #0x200
            sub         sp, sp, #0x200 + 0x400
            bic         r9, r9, #0x3fc

            /* r9 now points to a 0x200 byte buffer on the stack whose address
             * has the low 10 bits clear.  This allows easy address calculation
             * in the wrap-around cases.
             */

            prefill     step=4, max_r=\r, label=.Lcnv4_\r

            conv_body   core=hconv4_\r, step=4, max_r=\r, labelc=.Lcnv4_\r, labelnc=.Lcnvnc4_\r

            add         sp, sp, #0x200 + 0x400
            pop         {r12,pc}
END(convolve4_\r)
.endr

/* void rsdIntrinsicBlurU1_K(
 *                  void *out,      // r0
 *                  void *in,       // r1
 *                  size_t w,       // r2
 *                  size_t h,       // r3
 *                  size_t p,       // [sp]
 *                  size_t x,       // [sp,#4]
 *                  size_t y,       // [sp,#8]
 *                  size_t count,   // [sp,#12]
 *                  size_t r,       // [sp,#16]
 *                  uint16_t *tab); // [sp,#20]
 */
ENTRY(rsdIntrinsicBlurU1_K)
            push        {r4,r5,r6,r7,r8,r9,r10,r11,r12,lr}
            vpush       {d8-d15}
            ldr         r6, [sp,#112]   // y
            ldr         r8, [sp,#108]   // x
            ldr         r5, [sp,#120]   // r
            sub         r4, r2, r8      // inlen = w - x
            sub         r7, r3, r6      // h - y
            ldr         r2, [sp,#104]   // pitch
            ldr         r3, [sp,#116]   // count
            sub         r7, r7, #1      // h - y - 1

            ldr         r12, [sp,#124]

            add         r1, r1, r8      // src += x

            cmp         r6, r5
            movhi       r6, r5          // rup = min(r, y)
            cmp         r7, r5
            movhi       r7, r5          // rdn = min(r, h - y - 1)

            vld1.u16    {d0,d1,d2,d3}, [r12]!
            vld1.u16    {d4,d5,d6}, [r12]!

            adr         lr, 1f
  .irp r, TUNED_LIST1
            cmp         r5, #\r
            bls         convolve1_\r
  .endr
            b           convolve1_25

1:          vpop        {d8-d15}
            pop         {r4,r5,r6,r7,r8,r9,r10,r11,r12,pc}
END(rsdIntrinsicBlurU1_K)

/* void rsdIntrinsicBlurU4_K(
 *                  void *out,      // r0
 *                  void *in,       // r1
 *                  size_t w,       // r2
 *                  size_t h,       // r3
 *                  size_t p,       // [sp]
 *                  size_t x,       // [sp,#4]
 *                  size_t y,       // [sp,#8]
 *                  size_t count,   // [sp,#12]
 *                  size_t r,       // [sp,#16]
 *                  uint16_t *tab); // [sp,#20]
 */
ENTRY(rsdIntrinsicBlurU4_K)
            push        {r4,r5,r6,r7,r8,r9,r10,r11,r12,lr}
            vpush       {d8-d15}
            ldr         r6, [sp,#112]   // y
            ldr         r8, [sp,#108]   // x
            ldr         r5, [sp,#120]   // r
            lsl         r8, r8, #2
            rsb         r4, r8, r2, LSL #2 // inlen = (w - x)
            sub         r7, r3, r6      // h - y
            ldr         r2, [sp,#104]   // pitch
            ldr         r3, [sp,#116]   // count
            sub         r7, r7, #1      // h - y - 1
            lsl         r3, r3, #2      // count

            ldr         r12, [sp,#124]

            add         r1, r1, r8      // in += x

            cmp         r6, r5
            movhi       r6, r5          // rup = min(r, y)
            cmp         r7, r5
            movhi       r7, r5          // rdn = min(r, h - y - 1)

            vld1.u16    {d0,d1,d2,d3}, [r12]!
            vld1.u16    {d4,d5,d6}, [r12]!

            adr         lr, 1f
  .irp r, TUNED_LIST4
            cmp         r5, #\r
            bls         convolve4_\r
  .endr
            b           convolve4_25

1:          vpop        {d8-d15}
            pop         {r4,r5,r6,r7,r8,r9,r10,r11,r12,pc}
END(rsdIntrinsicBlurU4_K)
