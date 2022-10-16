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

#define ENTRY(f) .text; .align 4; .globl f; .type f,#function; f:
#define PRIVATE(f) .text; .align 4; .type f,#function; f:
#define END(f) .size f, .-f;

//#define ARCH_ARM64_USE_BLUR_PRELOAD

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
#if defined(ARCH_ARM64_USE_BLUR_PRELOAD)
#define VERTPLD(...) prfm        PLDL1KEEP, [__VA_ARGS__]
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
 *      x1 -- src
 *      x2 -- pitch
 *      x5 -- r
 *      x6 -- rup (r, unless clipped to top of source image)
 *      x7 -- rdn (r, unless clipped to bottom of source image)
 *      x12 -- switch index
 *      v0-v3 -- coefficient table
 *      x13 = -pitch
 *      x15 = top-row in
 *      x19 = bottom-row in
 * Output:
 *      x1 += 16
 *      v10,v11 -- 16 convolved columns
 * Modifies:
 *      x10 = upper row pointer
 *      x11 = lower row pointer
 *      v12-v15 = temporary sums
 */
.macro fetch, max_r=MAX_R, labelc=1, labelnc=2, reg=x12 /*{{{*/
  .ifc \reg,x12 ; .set cc, 1 ; .else ; .set cc, 0 ; .endif

            ld1         {v15.16b}, [x1], #16
            mov         x10, x15

            uxtl        v14.8h, v15.8b
            VERTPLD(x1, #16)
            uxtl2       v15.8h, v15.16b
  .if \max_r < 16 // approximate
    ifcc    adr         \reg, 1f
  .else
    ifcc    adrp        \reg, 1f
    ifcc    add         \reg, \reg, #:lo12:1f
  .endif

            umull       v12.4s, v14.4h, v0.h[0]
    ifcc    sub         \reg, \reg, x5, LSL #6
            umull2      v13.4s, v14.8h, v0.h[0]
            mov         x11, x19
            umull       v14.4s, v15.4h, v0.h[0]
    ifcc    add         \reg, \reg, x5, LSL #3
            umull2      v15.4s, v15.8h, v0.h[0]
            br          \reg

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
            ld1         {v10.16b}, [x10], x2
            ld1         {v11.16b}, [x11], x13
            uaddl       v16.8h, v10.8b, v11.8b
            uaddl2      v11.8h, v10.16b, v11.16b
            umlal       v12.4s, v16.4h, \dreg
            umlal2      v13.4s, v16.8h, \dreg
            VERTPLD(x10, #32)
            umlal       v14.4s, v11.4h, \dreg
            VERTPLD(x11, #32)
            umlal2      v15.4s, v11.8h, \dreg
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
            ld1         {v10.16b}, [x10], x2
            cmp         x6, #\i
            ld1         {v11.16b}, [x11], x13
            csel        x10, x15, x10, lo
            uaddl       v16.8h, v10.8b, v11.8b
            cmp         x7, #\i
            uaddl2      v11.8h, v10.16b, v11.16b
            csel        x11, x19, x11, lo
            umlal       v12.4s, v16.4h, \dreg
            umlal2      v13.4s, v16.8h, \dreg
            VERTPLD(x10, #32)
            umlal       v14.4s, v11.4h, \dreg
            VERTPLD(x11, #32)
            umlal2      v15.4s, v11.8h, \dreg
    .endif
  .endm

  /* Entry into this unrolled loop is computed as a negative index from
   * \labelc at the end of the block.
   */
  .align 4
  vertfetch_clamped 27, v3.h[3]
  vertfetch_clamped 26, v3.h[2]
  vertfetch_clamped 25, v3.h[1]
  vertfetch_clamped 24, v3.h[0]
  vertfetch_clamped 23, v2.h[7]
  vertfetch_clamped 22, v2.h[6]
  vertfetch_clamped 21, v2.h[5]
  vertfetch_clamped 20, v2.h[4]
  vertfetch_clamped 19, v2.h[3]
  vertfetch_clamped 18, v2.h[2]
  vertfetch_clamped 17, v2.h[1]
  vertfetch_clamped 16, v2.h[0]
  vertfetch_clamped 15, v1.h[7]
  vertfetch_clamped 14, v1.h[6]
  vertfetch_clamped 13, v1.h[5]
  vertfetch_clamped 12, v1.h[4]
  vertfetch_clamped 11, v1.h[3]
  vertfetch_clamped 10, v1.h[2]
  vertfetch_clamped  9, v1.h[1]
  vertfetch_clamped  8, v1.h[0]
  vertfetch_clamped  7, v0.h[7]
  vertfetch_clamped  6, v0.h[6]
  vertfetch_clamped  5, v0.h[5]
  vertfetch_clamped  4, v0.h[4]
  vertfetch_clamped  3, v0.h[3]
  vertfetch_clamped  2, v0.h[2]
  vertfetch_clamped  1, v0.h[1]
  vertfetch_clamped  0, v0.h[0]
  1:
  \labelc : b 2f    /* done with clamped loop, skip over non-clamped loop */

  /* Entry into this unrolled loop is computed as a negative index from
   * \labelnc at the end of the block.
   */
  .align 4
  vertfetch_noclamp 27, v3.h[3]
  vertfetch_noclamp 26, v3.h[2]
  vertfetch_noclamp 25, v3.h[1]
  vertfetch_noclamp 24, v3.h[0]
  vertfetch_noclamp 23, v2.h[7]
  vertfetch_noclamp 22, v2.h[6]
  vertfetch_noclamp 21, v2.h[5]
  vertfetch_noclamp 20, v2.h[4]
  vertfetch_noclamp 19, v2.h[3]
  vertfetch_noclamp 18, v2.h[2]
  vertfetch_noclamp 17, v2.h[1]
  vertfetch_noclamp 16, v2.h[0]
  vertfetch_noclamp 15, v1.h[7]
  vertfetch_noclamp 14, v1.h[6]
  vertfetch_noclamp 13, v1.h[5]
  vertfetch_noclamp 12, v1.h[4]
  vertfetch_noclamp 11, v1.h[3]
  vertfetch_noclamp 10, v1.h[2]
  vertfetch_noclamp  9, v1.h[1]
  vertfetch_noclamp  8, v1.h[0]
  vertfetch_noclamp  7, v0.h[7]
  vertfetch_noclamp  6, v0.h[6]
  vertfetch_noclamp  5, v0.h[5]
  vertfetch_noclamp  4, v0.h[4]
  vertfetch_noclamp  3, v0.h[3]
  vertfetch_noclamp  2, v0.h[2]
  vertfetch_noclamp  1, v0.h[1]
  vertfetch_noclamp  0, v0.h[0]
  \labelnc :

  .purgem vertfetch_clamped
  .purgem vertfetch_noclamp

  2:        uqrshrn     v10.4h, v12.4s, #16 - FRACTION_BITS
            add         x15, x15, #16
            uqrshrn2    v10.8h, v13.4s, #16 - FRACTION_BITS
            add         x19, x19, #16
            uqrshrn     v11.4h, v14.4s, #16 - FRACTION_BITS
            uqrshrn2    v11.8h, v15.4s, #16 - FRACTION_BITS
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
 * When the buffer gets too big the buffer at [x9] is used.
 *
 * Input:
 *      v16-v31,v4-v11 -- convoltion window
 *      x9 -- pointer to additional convolution window data
 * Output:
 *      x9 -- updated buffer pointer (if used)
 *      d31 -- result to be stored
 * Modifies:
 *      x12 -- temp buffer pointer
 *      v12-v13 -- temporaries for load and vext operations.
 *      v14-v15 -- intermediate sums
 */
#define TUNED_LIST1 8, 16
.macro hconv1_8/*{{{*/

.rodata
    200:    .hword -4
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .hword 107f-100f
            .hword 108f-100f
            .align      4
.text
            umull       v14.4s, v9.4h, v0.h[0]
            umull2      v15.4s, v9.8h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    108:    umlal       v14.4s, v8.4h, v1.h[0]
            umlal2      v15.4s, v8.8h, v1.h[0]
            umlal       v14.4s, v10.4h, v1.h[0]
            umlal2      v15.4s, v10.8h, v1.h[0]
    107:    ext         v12.16b, v8.16b, v9.16b, #1*2
            ext         v13.16b, v9.16b, v10.16b, #7*2
            umlal       v14.4s, v12.4h, v0.h[7]
            umlal2      v15.4s, v12.8h, v0.h[7]
            umlal       v14.4s, v13.4h, v0.h[7]
            umlal2      v15.4s, v13.8h, v0.h[7]
    106:    ext         v12.16b, v8.16b, v9.16b, #2*2
            ext         v13.16b, v9.16b, v10.16b, #6*2
            umlal       v14.4s, v12.4h, v0.h[6]
            umlal2      v15.4s, v12.8h, v0.h[6]
            umlal       v14.4s, v13.4h, v0.h[6]
            umlal2      v15.4s, v13.8h, v0.h[6]
    105:    ext         v12.16b, v8.16b, v9.16b, #3*2
            ext         v13.16b, v9.16b, v10.16b, #5*2
            umlal       v14.4s, v12.4h, v0.h[5]
            umlal2      v15.4s, v12.8h, v0.h[5]
            umlal       v14.4s, v13.4h, v0.h[5]
            umlal2      v15.4s, v13.8h, v0.h[5]
    104:    //ext         v12.16b, v8.16b, v9.16b, #4*2
            //ext         v13.16b, v9.16b, v10.16b, #4*2
            umlal2      v14.4s, v8.8h, v0.h[4]
            umlal       v15.4s, v9.4h, v0.h[4]
            umlal2      v14.4s, v9.8h, v0.h[4]
            umlal       v15.4s, v10.4h, v0.h[4]
    103:    ext         v12.16b, v8.16b, v9.16b, #5*2
            ext         v13.16b, v9.16b, v10.16b, #3*2
            umlal       v14.4s, v12.4h, v0.h[3]
            umlal2      v15.4s, v12.8h, v0.h[3]
            umlal       v14.4s, v13.4h, v0.h[3]
            umlal2      v15.4s, v13.8h, v0.h[3]
    102:    ext         v12.16b, v8.16b, v9.16b, #6*2
            ext         v13.16b, v9.16b, v10.16b, #2*2
            umlal       v14.4s, v12.4h, v0.h[2]
            umlal2      v15.4s, v12.8h, v0.h[2]
            umlal       v14.4s, v13.4h, v0.h[2]
            umlal2      v15.4s, v13.8h, v0.h[2]
    101:    ext         v12.16b, v8.16b, v9.16b, #7*2
            ext         v13.16b, v9.16b, v10.16b, #1*2
            umlal       v14.4s, v12.4h, v0.h[1]
            umlal2      v15.4s, v12.8h, v0.h[1]
            umlal       v14.4s, v13.4h, v0.h[1]
            umlal2      v15.4s, v13.8h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

.macro hconv1_16/*{{{*/
.rodata
   200:     .hword -4
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .hword 107f-100f
            .hword 108f-100f
            .hword 109f-100f
            .hword 110f-100f
            .hword 111f-100f
            .hword 112f-100f
            .hword 113f-100f
            .hword 114f-100f
            .hword 115f-100f
            .hword 116f-100f
            .align 4

.text
            umull       v14.4s, v8.4h, v0.h[0]
            umull2      v15.4s, v8.8h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    116:    //ext         v12.16b, v6.16b, v7.16b, #0*2
            //ext         v13.16b, v10.16b, v11.16b, #0*2
            umlal       v14.4s, v6.4h, v2.h[0]
            umlal2      v15.4s, v6.8h, v2.h[0]
            umlal       v14.4s, v10.4h, v2.h[0]
            umlal2      v15.4s, v10.8h, v2.h[0]
    115:    ext         v12.16b, v6.16b, v7.16b, #1*2
            ext         v13.16b, v9.16b, v10.16b, #7*2
            umlal       v14.4s, v12.4h, v1.h[7]
            umlal2      v15.4s, v12.8h, v1.h[7]
            umlal       v14.4s, v13.4h, v1.h[7]
            umlal2      v15.4s, v13.8h, v1.h[7]
    114:    ext         v12.16b, v6.16b, v7.16b, #2*2
            ext         v13.16b, v9.16b, v10.16b, #6*2
            umlal       v14.4s, v12.4h, v1.h[6]
            umlal2      v15.4s, v12.8h, v1.h[6]
            umlal       v14.4s, v13.4h, v1.h[6]
            umlal2      v15.4s, v13.8h, v1.h[6]
    113:    ext         v12.16b, v6.16b, v7.16b, #3*2
            ext         v13.16b, v9.16b, v10.16b, #5*2
            umlal       v14.4s, v12.4h, v1.h[5]
            umlal2      v15.4s, v12.8h, v1.h[5]
            umlal       v14.4s, v13.4h, v1.h[5]
            umlal2      v15.4s, v13.8h, v1.h[5]
    112:    //ext         v12.16b, v6.16b, v7.16b, #4*2
            //ext         v13.16b, v9.16b, v10.16b, #4*2
            umlal2      v14.4s, v6.8h, v1.h[4]
            umlal       v15.4s, v7.4h, v1.h[4]
            umlal2      v14.4s, v9.8h, v1.h[4]
            umlal       v15.4s, v10.4h, v1.h[4]
    111:    ext         v12.16b, v6.16b, v7.16b, #5*2
            ext         v13.16b, v9.16b, v10.16b, #3*2
            umlal       v14.4s, v12.4h, v1.h[3]
            umlal2      v15.4s, v12.8h, v1.h[3]
            umlal       v14.4s, v13.4h, v1.h[3]
            umlal2      v15.4s, v13.8h, v1.h[3]
    110:    ext         v12.16b, v6.16b, v7.16b, #6*2
            ext         v13.16b, v9.16b, v10.16b, #2*2
            umlal       v14.4s, v12.4h, v1.h[2]
            umlal2      v15.4s, v12.8h, v1.h[2]
            umlal       v14.4s, v13.4h, v1.h[2]
            umlal2      v15.4s, v13.8h, v1.h[2]
    109:    ext         v12.16b, v6.16b, v7.16b, #7*2
            ext         v13.16b, v9.16b, v10.16b, #1*2
            umlal       v14.4s, v12.4h, v1.h[1]
            umlal2      v15.4s, v12.8h, v1.h[1]
            umlal       v14.4s, v13.4h, v1.h[1]
            umlal2      v15.4s, v13.8h, v1.h[1]
    108:    //ext         v12.16b, v7.16b, v8.16b, #0*2
            //ext         v13.16b, v9.16b, v10.16b, #0*2
            umlal       v14.4s, v7.4h, v1.h[0]
            umlal2      v15.4s, v7.8h, v1.h[0]
            umlal       v14.4s, v9.4h, v1.h[0]
            umlal2      v15.4s, v9.8h, v1.h[0]
    107:    ext         v12.16b, v7.16b, v8.16b, #1*2
            ext         v13.16b, v8.16b, v9.16b, #7*2
            umlal       v14.4s, v12.4h, v0.h[7]
            umlal2      v15.4s, v12.8h, v0.h[7]
            umlal       v14.4s, v13.4h, v0.h[7]
            umlal2      v15.4s, v13.8h, v0.h[7]
    106:    ext         v12.16b, v7.16b, v8.16b, #2*2
            ext         v13.16b, v8.16b, v9.16b, #6*2
            umlal       v14.4s, v12.4h, v0.h[6]
            umlal2      v15.4s, v12.8h, v0.h[6]
            umlal       v14.4s, v13.4h, v0.h[6]
            umlal2      v15.4s, v13.8h, v0.h[6]
    105:    ext         v12.16b, v7.16b, v8.16b, #3*2
            ext         v13.16b, v8.16b, v9.16b, #5*2
            umlal       v14.4s, v12.4h, v0.h[5]
            umlal2      v15.4s, v12.8h, v0.h[5]
            umlal       v14.4s, v13.4h, v0.h[5]
            umlal2      v15.4s, v13.8h, v0.h[5]
    104:    //ext         v12.16b, v7.16b, v8.16b, #4*2
            //ext         v13.16b, v8.16b, v9.16b, #4*2
            umlal2      v14.4s, v7.8h, v0.h[4]
            umlal       v15.4s, v8.4h, v0.h[4]
            umlal2      v14.4s, v8.8h, v0.h[4]
            umlal       v15.4s, v9.4h, v0.h[4]
    103:    ext         v12.16b, v7.16b, v8.16b, #5*2
            ext         v13.16b, v8.16b, v9.16b, #3*2
            umlal       v14.4s, v12.4h, v0.h[3]
            umlal2      v15.4s, v12.8h, v0.h[3]
            umlal       v14.4s, v13.4h, v0.h[3]
            umlal2      v15.4s, v13.8h, v0.h[3]
    102:    ext         v12.16b, v7.16b, v8.16b, #6*2
            ext         v13.16b, v8.16b, v9.16b, #2*2
            umlal       v14.4s, v12.4h, v0.h[2]
            umlal2      v15.4s, v12.8h, v0.h[2]
            umlal       v14.4s, v13.4h, v0.h[2]
            umlal2      v15.4s, v13.8h, v0.h[2]
    101:    ext         v12.16b, v7.16b, v8.16b, #7*2
            ext         v13.16b, v8.16b, v9.16b, #1*2
            umlal       v14.4s, v12.4h, v0.h[1]
            umlal2      v15.4s, v12.8h, v0.h[1]
            umlal       v14.4s, v13.4h, v0.h[1]
            umlal2      v15.4s, v13.8h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            mov         v6.16b, v7.16b
            mov         v7.16b, v8.16b
            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

.macro hconv1_25/*{{{*/
.rodata
   200:     .hword -4
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .hword 107f-100f
            .hword 108f-100f
            .hword 109f-100f
            .hword 110f-100f
            .hword 111f-100f
            .hword 112f-100f
            .hword 113f-100f
            .hword 114f-100f
            .hword 115f-100f
            .hword 116f-100f
            .hword 117f-100f
            .hword 118f-100f
            .hword 119f-100f
            .hword 120f-100f
            .hword 121f-100f
            .hword 122f-100f
            .hword 123f-100f
            .hword 124f-100f
            .hword 125f-100f
            .align 4
.text
            ext         v12.16b, v6.16b, v7.16b, #7*2
            umull       v14.4s, v12.4h, v0.h[0]
            umull2      v15.4s, v12.8h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    125:    ext         v12.16b, v31.16b, v4.16b, #6*2
            ext         v13.16b, v10.16b, v11.16b, #0*2
            umlal       v14.4s, v12.4h, v3.h[1]
            umlal2      v15.4s, v12.8h, v3.h[1]
            umlal       v14.4s, v13.4h, v3.h[1]
            umlal2      v15.4s, v13.8h, v3.h[1]
    124:    ext         v12.16b, v31.16b, v4.16b, #7*2
            ext         v13.16b, v9.16b, v10.16b, #7*2
            umlal       v14.4s, v12.4h, v3.h[0]
            umlal2      v15.4s, v12.8h, v3.h[0]
            umlal       v14.4s, v13.4h, v3.h[0]
            umlal2      v15.4s, v13.8h, v3.h[0]
    123:    ext         v12.16b, v4.16b, v5.16b, #0*2
            ext         v13.16b, v9.16b, v10.16b, #6*2
            umlal       v14.4s, v12.4h, v2.h[7]
            umlal2      v15.4s, v12.8h, v2.h[7]
            umlal       v14.4s, v13.4h, v2.h[7]
            umlal2      v15.4s, v13.8h, v2.h[7]
    122:    ext         v12.16b, v4.16b, v5.16b, #1*2
            ext         v13.16b, v9.16b, v10.16b, #5*2
            umlal       v14.4s, v12.4h, v2.h[6]
            umlal2      v15.4s, v12.8h, v2.h[6]
            umlal       v14.4s, v13.4h, v2.h[6]
            umlal2      v15.4s, v13.8h, v2.h[6]
    121:    ext         v12.16b, v4.16b, v5.16b, #2*2
            ext         v13.16b, v9.16b, v10.16b, #4*2
            umlal       v14.4s, v12.4h, v2.h[5]
            umlal2      v15.4s, v12.8h, v2.h[5]
            umlal       v14.4s, v13.4h, v2.h[5]
            umlal2      v15.4s, v13.8h, v2.h[5]
    120:    ext         v12.16b, v4.16b, v5.16b, #3*2
            ext         v13.16b, v9.16b, v10.16b, #3*2
            umlal       v14.4s, v12.4h, v2.h[4]
            umlal2      v15.4s, v12.8h, v2.h[4]
            umlal       v14.4s, v13.4h, v2.h[4]
            umlal2      v15.4s, v13.8h, v2.h[4]
    119:    ext         v12.16b, v4.16b, v5.16b, #4*2
            ext         v13.16b, v9.16b, v10.16b, #2*2
            umlal       v14.4s, v12.4h, v2.h[3]
            umlal2      v15.4s, v12.8h, v2.h[3]
            umlal       v14.4s, v13.4h, v2.h[3]
            umlal2      v15.4s, v13.8h, v2.h[3]
    118:    ext         v12.16b, v4.16b, v5.16b, #5*2
            ext         v13.16b, v9.16b, v10.16b, #1*2
            umlal       v14.4s, v12.4h, v2.h[2]
            umlal2      v15.4s, v12.8h, v2.h[2]
            umlal       v14.4s, v13.4h, v2.h[2]
            umlal2      v15.4s, v13.8h, v2.h[2]
    117:    ext         v12.16b, v4.16b, v5.16b, #6*2
            ext         v13.16b, v9.16b, v10.16b, #0*2
            umlal       v14.4s, v12.4h, v2.h[1]
            umlal2      v15.4s, v12.8h, v2.h[1]
            umlal       v14.4s, v13.4h, v2.h[1]
            umlal2      v15.4s, v13.8h, v2.h[1]
    116:    ext         v12.16b, v4.16b, v5.16b, #7*2
            ext         v13.16b, v8.16b, v9.16b, #7*2
            umlal       v14.4s, v12.4h, v2.h[0]
            umlal2      v15.4s, v12.8h, v2.h[0]
            umlal       v14.4s, v13.4h, v2.h[0]
            umlal2      v15.4s, v13.8h, v2.h[0]
    115:    ext         v12.16b, v5.16b, v6.16b, #0*2
            ext         v13.16b, v8.16b, v9.16b, #6*2
            umlal       v14.4s, v12.4h, v1.h[7]
            umlal2      v15.4s, v12.8h, v1.h[7]
            umlal       v14.4s, v13.4h, v1.h[7]
            umlal2      v15.4s, v13.8h, v1.h[7]
    114:    ext         v12.16b, v5.16b, v6.16b, #1*2
            ext         v13.16b, v8.16b, v9.16b, #5*2
            umlal       v14.4s, v12.4h, v1.h[6]
            umlal2      v15.4s, v12.8h, v1.h[6]
            umlal       v14.4s, v13.4h, v1.h[6]
            umlal2      v15.4s, v13.8h, v1.h[6]
    113:    ext         v12.16b, v5.16b, v6.16b, #2*2
            ext         v13.16b, v8.16b, v9.16b, #4*2
            umlal       v14.4s, v12.4h, v1.h[5]
            umlal2      v15.4s, v12.8h, v1.h[5]
            umlal       v14.4s, v13.4h, v1.h[5]
            umlal2      v15.4s, v13.8h, v1.h[5]
    112:    ext         v12.16b, v5.16b, v6.16b, #3*2
            ext         v13.16b, v8.16b, v9.16b, #3*2
            umlal       v14.4s, v12.4h, v1.h[4]
            umlal2      v15.4s, v12.8h, v1.h[4]
            umlal       v14.4s, v13.4h, v1.h[4]
            umlal2      v15.4s, v13.8h, v1.h[4]
    111:    ext         v12.16b, v5.16b, v6.16b, #4*2
            ext         v13.16b, v8.16b, v9.16b, #2*2
            umlal       v14.4s, v12.4h, v1.h[3]
            umlal2      v15.4s, v12.8h, v1.h[3]
            umlal       v14.4s, v13.4h, v1.h[3]
            umlal2      v15.4s, v13.8h, v1.h[3]
    110:    ext         v12.16b, v5.16b, v6.16b, #5*2
            ext         v13.16b, v8.16b, v9.16b, #1*2
            umlal       v14.4s, v12.4h, v1.h[2]
            umlal2      v15.4s, v12.8h, v1.h[2]
            umlal       v14.4s, v13.4h, v1.h[2]
            umlal2      v15.4s, v13.8h, v1.h[2]
    109:    ext         v12.16b, v5.16b, v6.16b, #6*2
            ext         v13.16b, v8.16b, v9.16b, #0*2
            umlal       v14.4s, v12.4h, v1.h[1]
            umlal2      v15.4s, v12.8h, v1.h[1]
            umlal       v14.4s, v13.4h, v1.h[1]
            umlal2      v15.4s, v13.8h, v1.h[1]
    108:    ext         v12.16b, v5.16b, v6.16b, #7*2
            ext         v13.16b, v7.16b, v8.16b, #7*2
            umlal       v14.4s, v12.4h, v1.h[0]
            umlal2      v15.4s, v12.8h, v1.h[0]
            umlal       v14.4s, v13.4h, v1.h[0]
            umlal2      v15.4s, v13.8h, v1.h[0]
    107:    ext         v12.16b, v6.16b, v7.16b, #0*2
            ext         v13.16b, v7.16b, v8.16b, #6*2
            umlal       v14.4s, v12.4h, v0.h[7]
            umlal2      v15.4s, v12.8h, v0.h[7]
            umlal       v14.4s, v13.4h, v0.h[7]
            umlal2      v15.4s, v13.8h, v0.h[7]
    106:    ext         v12.16b, v6.16b, v7.16b, #1*2
            ext         v13.16b, v7.16b, v8.16b, #5*2
            umlal       v14.4s, v12.4h, v0.h[6]
            umlal2      v15.4s, v12.8h, v0.h[6]
            umlal       v14.4s, v13.4h, v0.h[6]
            umlal2      v15.4s, v13.8h, v0.h[6]
    105:    ext         v12.16b, v6.16b, v7.16b, #2*2
            ext         v13.16b, v7.16b, v8.16b, #4*2
            umlal       v14.4s, v12.4h, v0.h[5]
            umlal2      v15.4s, v12.8h, v0.h[5]
            umlal       v14.4s, v13.4h, v0.h[5]
            umlal2      v15.4s, v13.8h, v0.h[5]
    104:    ext         v12.16b, v6.16b, v7.16b, #3*2
            ext         v13.16b, v7.16b, v8.16b, #3*2
            umlal       v14.4s, v12.4h, v0.h[4]
            umlal2      v15.4s, v12.8h, v0.h[4]
            umlal       v14.4s, v13.4h, v0.h[4]
            umlal2      v15.4s, v13.8h, v0.h[4]
    103:    ext         v12.16b, v6.16b, v7.16b, #4*2
            ext         v13.16b, v7.16b, v8.16b, #2*2
            umlal       v14.4s, v12.4h, v0.h[3]
            umlal2      v15.4s, v12.8h, v0.h[3]
            umlal       v14.4s, v13.4h, v0.h[3]
            umlal2      v15.4s, v13.8h, v0.h[3]
    102:    ext         v12.16b, v6.16b, v7.16b, #5*2
            ext         v13.16b, v7.16b, v8.16b, #1*2
            umlal       v14.4s, v12.4h, v0.h[2]
            umlal2      v15.4s, v12.8h, v0.h[2]
            umlal       v14.4s, v13.4h, v0.h[2]
            umlal2      v15.4s, v13.8h, v0.h[2]
    101:    ext         v12.16b, v6.16b, v7.16b, #6*2
            ext         v13.16b, v7.16b, v8.16b, #0*2
            umlal       v14.4s, v12.4h, v0.h[1]
            umlal2      v15.4s, v12.8h, v0.h[1]
            umlal       v14.4s, v13.4h, v0.h[1]
            umlal2      v15.4s, v13.8h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            mov         v31.16b, v4.16b
            mov         v4.16b, v5.16b
            mov         v5.16b, v6.16b
            mov         v6.16b, v7.16b
            mov         v7.16b, v8.16b
            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

#define TUNED_LIST4 6, 12, 20
.macro hconv4_6/*{{{*/
.rodata
   200:     .hword -4
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .align      4
.text
            umull       v14.4s, v7.4h, v0.h[0]
            umull2      v15.4s, v7.8h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    106:    umlal       v14.4s, v4.4h,  v0.h[6]
            umlal2      v15.4s, v4.8h,  v0.h[6]
            umlal       v14.4s, v10.4h, v0.h[6]
            umlal2      v15.4s, v10.8h, v0.h[6]
    105:    umlal2      v14.4s, v4.8h,  v0.h[5]
            umlal       v15.4s, v5.4h, v0.h[5]
            umlal2      v14.4s, v9.8h, v0.h[5]
            umlal       v15.4s, v10.4h, v0.h[5]
    104:    umlal       v14.4s, v5.4h, v0.h[4]
            umlal2      v15.4s, v5.8h, v0.h[4]
            umlal       v14.4s, v9.4h, v0.h[4]
            umlal2      v15.4s, v9.8h, v0.h[4]
    103:    umlal2      v14.4s, v5.8h, v0.h[3]
            umlal       v15.4s, v6.4h, v0.h[3]
            umlal2      v14.4s, v8.8h, v0.h[3]
            umlal       v15.4s, v9.4h, v0.h[3]
    102:    umlal       v14.4s, v6.4h, v0.h[2]
            umlal2      v15.4s, v6.8h, v0.h[2]
            umlal       v14.4s, v8.4h, v0.h[2]
            umlal2      v15.4s, v8.8h, v0.h[2]
    101:    umlal2      v14.4s, v6.8h, v0.h[1]
            umlal       v15.4s, v7.4h, v0.h[1]
            umlal2      v14.4s, v7.8h, v0.h[1]
            umlal       v15.4s, v8.4h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            mov         v4.16b, v5.16b
            mov         v5.16b, v6.16b
            mov         v6.16b, v7.16b
            mov         v7.16b, v8.16b
            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

.macro hconv4_12/*{{{*/
.rodata
   200:     .hword -4 //Might need to remove these...
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .hword 107f-100f
            .hword 108f-100f
            .hword 109f-100f
            .hword 110f-100f
            .hword 111f-100f
            .hword 112f-100f
            .align 4
.text
            umull       v14.4s, v4.4h, v0.h[0]
            umull2      v15.4s, v4.8h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    112:    umlal       v14.4s, v26.4h, v1.h[4]
            umlal2      v15.4s, v26.8h, v1.h[4]
            umlal       v14.4s, v10.4h, v1.h[4]
            umlal2      v15.4s, v10.8h, v1.h[4]
    111:    umlal2      v14.4s, v26.8h, v1.h[3]
            umlal       v15.4s, v27.4h, v1.h[3]
            umlal2      v14.4s, v9.8h, v1.h[3]
            umlal       v15.4s, v10.4h, v1.h[3]
    110:    umlal       v14.4s, v27.4h, v1.h[2]
            umlal2      v15.4s, v27.8h, v1.h[2]
            umlal       v14.4s, v9.4h, v1.h[2]
            umlal2      v15.4s, v9.8h, v1.h[2]
    109:    umlal2      v14.4s, v27.8h, v1.h[1]
            umlal       v15.4s, v28.4h, v1.h[1]
            umlal2      v14.4s, v8.8h, v1.h[1]
            umlal       v15.4s, v9.4h, v1.h[1]
    108:    umlal       v14.4s, v28.4h, v1.h[0]
            umlal2      v15.4s, v28.8h, v1.h[0]
            umlal       v14.4s, v8.4h, v1.h[0]
            umlal2      v15.4s, v8.8h, v1.h[0]
    107:    umlal2      v14.4s, v28.8h, v0.h[7]
            umlal       v15.4s, v29.4h, v0.h[7]
            umlal2      v14.4s, v7.8h, v0.h[7]
            umlal       v15.4s, v8.4h, v0.h[7]
    106:    umlal       v14.4s, v29.4h, v0.h[6]
            umlal2      v15.4s, v29.8h, v0.h[6]
            umlal       v14.4s, v7.4h, v0.h[6]
            umlal2      v15.4s, v7.8h, v0.h[6]
    105:    umlal2      v14.4s, v29.8h, v0.h[5]
            umlal       v15.4s, v30.4h, v0.h[5]
            umlal2      v14.4s, v6.8h, v0.h[5]
            umlal       v15.4s, v7.4h, v0.h[5]
    104:    umlal       v14.4s, v30.4h, v0.h[4]
            umlal2      v15.4s, v30.8h, v0.h[4]
            umlal       v14.4s, v6.4h, v0.h[4]
            umlal2      v15.4s, v6.8h, v0.h[4]
    103:    umlal2      v14.4s, v30.8h, v0.h[3]
            umlal       v15.4s, v31.4h, v0.h[3]
            umlal2      v14.4s, v5.8h, v0.h[3]
            umlal       v15.4s, v6.4h, v0.h[3]
    102:    umlal       v14.4s, v31.4h, v0.h[2]
            umlal2      v15.4s, v31.8h, v0.h[2]
            umlal       v14.4s, v5.4h, v0.h[2]
            umlal2      v15.4s, v5.8h, v0.h[2]
    101:    umlal2      v14.4s, v31.8h, v0.h[1]
            umlal       v15.4s, v4.4h,  v0.h[1]
            umlal2      v14.4s, v4.8h,  v0.h[1]
            umlal       v15.4s, v5.4h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            mov         v26.16b, v27.16b
            mov         v27.16b, v28.16b
            mov         v28.16b, v29.16b
            mov         v29.16b, v30.16b
            mov         v30.16b, v31.16b
            mov         v31.16b, v4.16b
            mov         v4.16b, v5.16b
            mov         v5.16b, v6.16b
            mov         v6.16b, v7.16b
            mov         v7.16b, v8.16b
            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

.macro hconv4_20/*{{{*/
.rodata
   200:     .hword -4
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .hword 107f-100f
            .hword 108f-100f
            .hword 109f-100f
            .hword 110f-100f
            .hword 111f-100f
            .hword 112f-100f
            .hword 113f-100f
            .hword 114f-100f
            .hword 115f-100f
            .hword 116f-100f
            .hword 117f-100f
            .hword 118f-100f
            .hword 119f-100f
            .hword 120f-100f
            .align 4
.text
            umull       v14.4s, v28.4h, v0.h[0]
            umull2      v15.4s, v28.8h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    120:    umlal       v14.4s, v18.4h, v2.h[4]
            umlal2      v15.4s, v18.8h, v2.h[4]
            umlal       v14.4s, v10.4h, v2.h[4]
            umlal2      v15.4s, v10.8h, v2.h[4]
    119:    umlal2      v14.4s, v18.8h, v2.h[3]
            umlal       v15.4s, v19.4h, v2.h[3]
            umlal2      v14.4s, v9.8h,  v2.h[3]
            umlal       v15.4s, v10.4h, v2.h[3]
    118:    umlal       v14.4s, v19.4h, v2.h[2]
            umlal2      v15.4s, v19.8h, v2.h[2]
            umlal       v14.4s, v9.4h,  v2.h[2]
            umlal2      v15.4s, v9.8h,  v2.h[2]
    117:    umlal2      v14.4s, v19.8h, v2.h[1]
            umlal       v15.4s, v20.4h, v2.h[1]
            umlal2      v14.4s, v8.8h,  v2.h[1]
            umlal       v15.4s, v9.4h,  v2.h[1]
    116:    umlal       v14.4s, v20.4h, v2.h[0]
            umlal2      v15.4s, v20.8h, v2.h[0]
            umlal       v14.4s, v8.4h,  v2.h[0]
            umlal2      v15.4s, v8.8h,  v2.h[0]
    115:    umlal2      v14.4s, v20.8h, v1.h[7]
            umlal       v15.4s, v21.4h, v1.h[7]
            umlal2      v14.4s, v7.8h,  v1.h[7]
            umlal       v15.4s, v8.4h,  v1.h[7]
    114:    umlal       v14.4s, v21.4h, v1.h[6]
            umlal2      v15.4s, v21.8h, v1.h[6]
            umlal       v14.4s, v7.4h,  v1.h[6]
            umlal2      v15.4s, v7.8h,  v1.h[6]
    113:    umlal2      v14.4s, v21.8h, v1.h[5]
            umlal       v15.4s, v22.4h, v1.h[5]
            umlal2      v14.4s, v6.8h,  v1.h[5]
            umlal       v15.4s, v7.4h,  v1.h[5]
    112:    umlal       v14.4s, v22.4h, v1.h[4]
            umlal2      v15.4s, v22.8h, v1.h[4]
            umlal       v14.4s, v6.4h,  v1.h[4]
            umlal2      v15.4s, v6.8h,  v1.h[4]
    111:    umlal2      v14.4s, v22.8h, v1.h[3]
            umlal       v15.4s, v23.4h, v1.h[3]
            umlal2      v14.4s, v5.8h,  v1.h[3]
            umlal       v15.4s, v6.4h,  v1.h[3]
    110:    umlal       v14.4s, v23.4h, v1.h[2]
            umlal2      v15.4s, v23.8h, v1.h[2]
            umlal       v14.4s, v5.4h,  v1.h[2]
            umlal2      v15.4s, v5.8h,  v1.h[2]
    109:    umlal2      v14.4s, v23.8h, v1.h[1]
            umlal       v15.4s, v24.4h, v1.h[1]
            umlal2      v14.4s, v4.8h,  v1.h[1]
            umlal       v15.4s, v5.4h,  v1.h[1]
    108:    umlal       v14.4s, v24.4h, v1.h[0]
            umlal2      v15.4s, v24.8h, v1.h[0]
            umlal       v14.4s, v4.4h,  v1.h[0]
            umlal2      v15.4s, v4.8h,  v1.h[0]
    107:    umlal2      v14.4s, v24.8h, v0.h[7]
            umlal       v15.4s, v25.4h, v0.h[7]
            umlal2      v14.4s, v31.8h, v0.h[7]
            umlal       v15.4s, v4.4h,  v0.h[7]
    106:    umlal       v14.4s, v25.4h, v0.h[6]
            umlal2      v15.4s, v25.8h, v0.h[6]
            umlal       v14.4s, v31.4h, v0.h[6]
            umlal2      v15.4s, v31.8h, v0.h[6]
    105:    umlal2      v14.4s, v25.8h, v0.h[5]
            umlal       v15.4s, v26.4h, v0.h[5]
            umlal2      v14.4s, v30.8h, v0.h[5]
            umlal       v15.4s, v31.4h, v0.h[5]
    104:    umlal       v14.4s, v26.4h, v0.h[4]
            umlal2      v15.4s, v26.8h, v0.h[4]
            umlal       v14.4s, v30.4h, v0.h[4]
            umlal2      v15.4s, v30.8h, v0.h[4]
    103:    umlal2      v14.4s, v26.8h, v0.h[3]
            umlal       v15.4s, v27.4h, v0.h[3]
            umlal2      v14.4s, v29.8h, v0.h[3]
            umlal       v15.4s, v30.4h, v0.h[3]
    102:    umlal       v14.4s, v27.4h, v0.h[2]
            umlal2      v15.4s, v27.8h, v0.h[2]
            umlal       v14.4s, v29.4h, v0.h[2]
            umlal2      v15.4s, v29.8h, v0.h[2]
    101:    umlal2      v14.4s, v27.8h, v0.h[1]
            umlal       v15.4s, v28.4h, v0.h[1]
            umlal2      v14.4s, v28.8h, v0.h[1]
            umlal       v15.4s, v29.4h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            mov         v18.16b, v19.16b
            mov         v19.16b, v20.16b
            mov         v20.16b, v21.16b
            mov         v21.16b, v22.16b
            mov         v22.16b, v23.16b
            mov         v23.16b, v24.16b
            mov         v24.16b, v25.16b
            mov         v25.16b, v26.16b
            mov         v26.16b, v27.16b
            mov         v27.16b, v28.16b
            mov         v28.16b, v29.16b
            mov         v29.16b, v30.16b
            mov         v30.16b, v31.16b
            mov         v31.16b, v4.16b
            mov         v4.16b, v5.16b
            mov         v5.16b, v6.16b
            mov         v6.16b, v7.16b
            mov         v7.16b, v8.16b
            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

.macro hconv4_25/*{{{*/
.rodata
   200:     .hword -4
            .hword 101f-100f
            .hword 102f-100f
            .hword 103f-100f
            .hword 104f-100f
            .hword 105f-100f
            .hword 106f-100f
            .hword 107f-100f
            .hword 108f-100f
            .hword 109f-100f
            .hword 110f-100f
            .hword 111f-100f
            .hword 112f-100f
            .hword 113f-100f
            .hword 114f-100f
            .hword 115f-100f
            .hword 116f-100f
            .hword 117f-100f
            .hword 118f-100f
            .hword 119f-100f
            .hword 120f-100f
            .hword 121f-100f
            .hword 122f-100f
            .hword 123f-100f
            .hword 124f-100f
            .hword 125f-100f
            .align 4
.text
            umull2      v14.4s, v25.8h, v0.h[0]
            umull       v15.4s, v26.4h, v0.h[0]

            adrp        x16, 200b
            add         x16, x16, :lo12:200b
            ldrsh       x12, [x16, x5, LSL #1]
            adr         x16, 100f
            add         x12, x12, x16
    100:    br          x12
    125:    ld1         {v12.8h}, [x9]
            umlal       v14.4s, v12.4h, v3.h[1]
            umlal2      v15.4s, v12.8h, v3.h[1]
            umlal       v14.4s, v10.4h, v3.h[1]
            umlal2      v15.4s, v10.8h, v3.h[1]
    124:    add         x12, x9, #0x08
            bic         x12, x12, #0x40
            ld1         {v12.4h}, [x12], #8
            bic         x12, x12, #0x40
            ld1         {v13.4h}, [x12]
            umlal       v14.4s, v12.4h, v3.h[0]
            umlal       v15.4s, v13.4h, v3.h[0]
            umlal2      v14.4s, v9.8h,  v3.h[0]
            umlal       v15.4s, v10.4h, v3.h[0]
    123:    add         x12, x9, #0x10
            bic         x12, x12, #0x40
            ld1         {v12.8h}, [x12]
            umlal       v14.4s, v12.4h, v2.h[7]
            umlal2      v15.4s, v12.8h, v2.h[7]
            umlal       v14.4s, v9.4h,  v2.h[7]
            umlal2      v15.4s, v9.8h,  v2.h[7]
    122:    add         x12, x9, #0x18
            bic         x12, x12, #0x40
            ld1         {v12.4h}, [x12], #8
            bic         x12, x12, #0x40
            ld1         {v13.4h}, [x12]
            umlal       v14.4s, v12.4h, v2.h[6]
            umlal       v15.4s, v13.4h, v2.h[6]
            umlal2      v14.4s, v8.8h,  v2.h[6]
            umlal       v15.4s, v9.4h,  v2.h[6]
    121:    add         x12, x9, #0x20
            bic         x12, x12, #0x40
            ld1         {v12.8h}, [x12]
            umlal       v14.4s, v12.4h, v2.h[5]
            umlal2      v15.4s, v12.8h, v2.h[5]
            umlal       v14.4s, v8.4h,  v2.h[5]
            umlal2      v15.4s, v8.8h,  v2.h[5]
    120:    add         x12, x9, #0x28
            bic         x12, x12, #0x40
            ld1         {v12.4h}, [x12], #8
            bic         x12, x12, #0x40
            ld1         {v13.4h}, [x12]
            umlal       v14.4s, v12.4h, v2.h[4]
            umlal       v15.4s, v13.4h, v2.h[4]
            umlal2      v14.4s, v7.8h,  v2.h[4]
            umlal       v15.4s, v8.4h,  v2.h[4]
    119:    add         x12, x9, #0x30
            bic         x12, x12, #0x40
            ld1         {v12.8h}, [x12]
            umlal       v14.4s, v12.4h, v2.h[3]
            umlal2      v15.4s, v12.8h, v2.h[3]
            umlal       v14.4s, v7.4h,  v2.h[3]
            umlal2      v15.4s, v7.8h,  v2.h[3]
    118:    add         x12, x9, #0x38
            bic         x12, x12, #0x40
            ld1         {v12.4h}, [x12]
            umlal       v14.4s, v12.4h, v2.h[2]
            umlal       v15.4s, v17.4h, v2.h[2]
            umlal2      v14.4s, v6.8h,  v2.h[2]
            umlal       v15.4s, v7.4h,  v2.h[2]
    117:    umlal       v14.4s, v17.4h, v2.h[1]
            umlal2      v15.4s, v17.8h, v2.h[1]
            umlal       v14.4s, v6.4h,  v2.h[1]
            umlal2      v15.4s, v6.8h,  v2.h[1]
    116:    umlal2      v14.4s, v17.8h, v2.h[0]
            umlal       v15.4s, v18.4h, v2.h[0]
            umlal2      v14.4s, v5.8h,  v2.h[0]
            umlal       v15.4s, v6.4h,  v2.h[0]
    115:    umlal       v14.4s, v18.4h, v1.h[7]
            umlal2      v15.4s, v18.8h, v1.h[7]
            umlal       v14.4s, v5.4h,  v1.h[7]
            umlal2      v15.4s, v5.8h,  v1.h[7]
    114:    umlal2      v14.4s, v18.8h, v1.h[6]
            umlal       v15.4s, v19.4h, v1.h[6]
            umlal2      v14.4s, v4.8h,  v1.h[6]
            umlal       v15.4s, v5.4h,  v1.h[6]
    113:    umlal       v14.4s, v19.4h, v1.h[5]
            umlal2      v15.4s, v19.8h, v1.h[5]
            umlal       v14.4s, v4.4h,  v1.h[5]
            umlal2      v15.4s, v4.8h,  v1.h[5]
    112:    umlal2      v14.4s, v19.8h, v1.h[4]
            umlal       v15.4s, v20.4h, v1.h[4]
            umlal2      v14.4s, v31.8h, v1.h[4]
            umlal       v15.4s, v4.4h,  v1.h[4]
    111:    umlal       v14.4s, v20.4h, v1.h[3]
            umlal2      v15.4s, v20.8h, v1.h[3]
            umlal       v14.4s, v31.4h, v1.h[3]
            umlal2      v15.4s, v31.8h, v1.h[3]
    110:    umlal2      v14.4s, v20.8h, v1.h[2]
            umlal       v15.4s, v21.4h, v1.h[2]
            umlal2      v14.4s, v30.8h, v1.h[2]
            umlal       v15.4s, v31.4h, v1.h[2]
    109:    umlal       v14.4s, v21.4h, v1.h[1]
            umlal2      v15.4s, v21.8h, v1.h[1]
            umlal       v14.4s, v30.4h, v1.h[1]
            umlal2      v15.4s, v30.8h, v1.h[1]
    108:    umlal2      v14.4s, v21.8h, v1.h[0]
            umlal       v15.4s, v22.4h, v1.h[0]
            umlal2      v14.4s, v29.8h, v1.h[0]
            umlal       v15.4s, v30.4h, v1.h[0]
    107:    umlal       v14.4s, v22.4h, v0.h[7]
            umlal2      v15.4s, v22.8h, v0.h[7]
            umlal       v14.4s, v29.4h, v0.h[7]
            umlal2      v15.4s, v29.8h, v0.h[7]
    106:    umlal2      v14.4s, v22.8h, v0.h[6]
            umlal       v15.4s, v23.4h, v0.h[6]
            umlal2      v14.4s, v28.8h, v0.h[6]
            umlal       v15.4s, v29.4h, v0.h[6]
    105:    umlal       v14.4s, v23.4h, v0.h[5]
            umlal2      v15.4s, v23.8h, v0.h[5]
            umlal       v14.4s, v28.4h, v0.h[5]
            umlal2      v15.4s, v28.8h, v0.h[5]
    104:    umlal2      v14.4s, v23.8h, v0.h[4]
            umlal       v15.4s, v24.4h, v0.h[4]
            umlal2      v14.4s, v27.8h, v0.h[4]
            umlal       v15.4s, v28.4h, v0.h[4]
    103:    umlal       v14.4s, v24.4h, v0.h[3]
            umlal2      v15.4s, v24.8h, v0.h[3]
            umlal       v14.4s, v27.4h, v0.h[3]
            umlal2      v15.4s, v27.8h, v0.h[3]
    102:    umlal2      v14.4s, v24.8h, v0.h[2]
            umlal       v15.4s, v25.4h, v0.h[2]
            umlal2      v14.4s, v26.8h, v0.h[2]
            umlal       v15.4s, v27.4h, v0.h[2]
    101:    umlal       v14.4s, v25.4h, v0.h[1]
            umlal2      v15.4s, v25.8h, v0.h[1]
            umlal       v14.4s, v26.4h, v0.h[1]
            umlal2      v15.4s, v26.8h, v0.h[1]

            uqrshrn     v14.4h, v14.4s, #16
            uqrshrn2    v14.8h, v15.4s, #16
            uqrshrn     v15.8b, v14.8h, #FRACTION_BITS

            st1         {v17.16b}, [x9], #16
            bic         x9, x9, #0x40
            mov         v17.16b, v18.16b
            mov         v18.16b, v19.16b
            mov         v19.16b, v20.16b
            mov         v20.16b, v21.16b
            mov         v21.16b, v22.16b
            mov         v22.16b, v23.16b
            mov         v23.16b, v24.16b
            mov         v24.16b, v25.16b
            mov         v25.16b, v26.16b
            mov         v26.16b, v27.16b
            mov         v27.16b, v28.16b
            mov         v28.16b, v29.16b
            mov         v29.16b, v30.16b
            mov         v30.16b, v31.16b
            mov         v31.16b, v4.16b
            mov         v4.16b, v5.16b
            mov         v5.16b, v6.16b
            mov         v6.16b, v7.16b
            mov         v7.16b, v8.16b
            mov         v8.16b, v9.16b
            mov         v9.16b, v10.16b
            mov         v10.16b, v11.16b
.endm/*}}}*/

/* Dedicated function wrapper for the fetch macro, for the cases where
 * performance isn't that important, to keep code size down.
 */
PRIVATE(fetch_generic_asm)
            stp         x10, x11, [sp, #-16]!
            fetch
            ldp         x10, x11, [sp], #16
            ret
END(fetch_generic_asm)


/* Fetch the next (16 - (x10 & 15)) columns of data, avoiding reading memory
 * beyond that limit, and filling the rest of the vector with the last legal
 * pixel.
 * Result is in v10 and v11.  v8 and v9 are filled with the first legal pixel.
 * Note: This function can read beyond the right edge of input if the image is
 * narrower than 16 bytes.
 */
PRIVATE(fetch_clampleft1)
            stp         x29, x30, [sp, #-16]!
            bl          fetch_generic_asm
            dup         v8.8h, v10.h[0]
            dup         v9.8h, v10.h[0]
            ands        x12, x10, #15
            beq         1f
            sub         x1, x1, x12
            sub         x15, x15, x12
            sub         x19, x19, x12
            sub         x10, x10, x12
            sub         x12, sp, x12, LSL #1
            sub         sp, sp, #64
            sub         x12, x12, #32
            st1         {v8.8h, v9.8h, v10.8h,v11.8h}, [sp]
            ld1         {v10.8h,v11.8h}, [x12]
            add         sp, sp, #64
1:          ldp         x29, x30, [sp], #16
            ret
END(fetch_clampleft1)

PRIVATE(fetch_clampleft4)
            stp         x29, x30, [sp, #-16]!
            bl          fetch_generic_asm
            dup         v8.2d, v10.d[0]
            dup         v9.2d, v10.d[0]
            ands        x12, x10, #15
            beq         1f
            sub         x1, x1, x12
            sub         x15, x15, x12
            sub         x19, x19, x12
            sub         x10, x10, x12
            sub         x12, sp, x12, LSL #1
            sub         sp, sp, #64
            sub         x12, x12, #32
            st1         {v8.8h, v9.8h, v10.8h,v11.8h}, [sp]
            ld1         {v10.8h,v11.8h}, [x12]
            add         sp, sp, #64
1:          ldp         x29, x30, [sp], #16
            ret
END(fetch_clampleft4)

/* Fetch only the next (x11 & 15) (where 0 means 16) columns of data, avoiding
 * reading memory beyond that limit, and filling the rest of the vector with
 * the last legal pixel.
 * Result is in v10 and v11.  v12 and v13 are filled with the last legal pixel.
 * Note: This function can read beyond the left edge of input if the image is
 * narrower than 16 bytes.
 */
PRIVATE(fetch_clampright1)
            stp         x29, x30, [sp, #-16]!
            sub         x12, xzr, x11
            ands        x12, x12, #15
            beq         1f
            sub         x1, x1, x12
            sub         x15, x15, x12
            sub         x19, x19, x12
            bl          fetch_generic_asm
            dup         v12.8h, v11.h[7]
            dup         v13.8h, v11.h[7]
            sub         x12, xzr, x11
            and         x12, x12, #15
            sub         sp, sp, #64
            add         x12, sp, x12, LSL #1
            st1         {v10.8h,v11.8h,v12.8h,v13.8h}, [sp]
            ld1         {v10.8h,v11.8h}, [x12]
            add         sp, sp, #64
            ldp         x29, x30, [sp], #16
            ret
1:          bl          fetch_generic_asm
            dup         v12.8h, v11.h[7]
            dup         v13.8h, v11.h[7]
            ldp         x29, x30, [sp], #16
            ret
END(fetch_clampright1)

PRIVATE(fetch_clampright4)
            stp         x29, x30, [sp, #-16]!
            sub         x12, xzr, x11
            ands        x12, x12, #15
            beq         1f
            sub         x1, x1, x12
            sub         x15, x15, x12
            sub         x19, x19, x12
            bl          fetch_generic_asm
            dup         v12.2d, v11.d[1]
            dup         v13.2d, v11.d[1]
            sub         x12, xzr, x11
            and         x12, x12, #15
            sub         sp, sp, #64
            add         x12, sp, x12, LSL #1
            st1         {v10.8h,v11.8h,v12.8h,v13.8h}, [sp]
            ld1         {v10.8h,v11.8h}, [x12]
            add         sp, sp, #64
            ldp         x29, x30, [sp], #16
            ret
1:          bl          fetch_generic_asm
            dup         v12.2d, v11.d[1]
            dup         v13.2d, v11.d[1]
            ldp         x29, x30, [sp], #16
            ret
END(fetch_clampright4)

/* Given values in v10 and v11, and an index in x11, sweep the (x11 & 15)th
 * value across to fill the rest of the register pair.  Used for filling the
 * right hand edge of the window when reading too close to the right hand edge
 * of the image.
 * Also returns a dup-ed copy of the last element in v12 for the tail-fill
 * case (this happens incidentally in common path, but must be done
 * deliberately in the fast-out path).
 */
PRIVATE(prefill_sweepright1)
            ands        x12, x11, #15
            beq         1f
            sub         x12, x12, #1
            sub         sp, sp, #64
            st1         {v10.8h,v11.8h}, [sp]
            add         x12, sp, x12, LSL #1
            ld1r        {v12.8h}, [x12]
            ld1r        {v13.8h}, [x12]
            st1         {v12.8h,v13.8h}, [x12]
            ld1         {v10.8h,v11.8h}, [sp]
            add         sp, sp, #64
            ret
1:          dup         v12.8h, v11.h[7]
            dup         v13.8h, v11.h[7]
            ret
END(prefill_sweepright1)

PRIVATE(prefill_sweepright4)
            ands        x12, x11, #15
            beq         1f
            sub         x12, x12, #4
            sub         sp, sp, #64
            st1         {v10.8h,v11.8h}, [sp]
            add         x12, sp, x12, LSL #1
            ld1r        {v12.2d}, [x12]
            st1         {v13.8h}, [x12]
            ld1         {v10.8h,v11.8h}, [sp]
            add         sp, sp, #64
            ret
1:          dup         v12.2d, v11.d[1]
            dup         v13.2d, v11.d[1]
            ret
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
.macro prefill_out ra, rb, sra, srb
  .ifc \ra,xx
    .ifc \rb,xx
            st1         {\sra,\srb}, [x9], #32
    .else
            bic         x9, x9, #0x40
            st1         {\sra}, [x9], #16
            mov         \rb, \srb
    .endif
  .else
    .ifnc \ra,\sra
            mov         \ra, \sra
    .endif
    .ifnc \rb,\srb
            mov         \rb, \srb
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
            ifneeded \macro \nextmacro, 13, 12, xx,      xx,      \step, \label
            ifneeded \macro \nextmacro, 12, 11, xx,      xx,      \step, \label
            ifneeded \macro \nextmacro, 11, 10, xx,      v17.16b, \step, \label
            ifneeded \macro \nextmacro, 10,  9, v18.16b, v19.16b, \step, \label
            ifneeded \macro \nextmacro,  9,  8, v20.16b, v21.16b, \step, \label
            ifneeded \macro \nextmacro,  8,  7, v22.16b, v23.16b, \step, \label
            ifneeded \macro \nextmacro,  7,  6, v24.16b, v25.16b, \step, \label
            ifneeded \macro \nextmacro,  6,  5, v26.16b, v27.16b, \step, \label
            ifneeded \macro \nextmacro,  5,  4, v28.16b, v29.16b, \step, \label
            ifneeded \macro \nextmacro,  4,  3, v30.16b, v31.16b, \step, \label
            ifneeded \macro \nextmacro,  3,  2, v4.16b,  v5.16b,  \step, \label
            ifneeded \macro \nextmacro,  2,  1, v6.16b,  v7.16b,  \step, \label
            ifneeded \macro \nextmacro,  1,  0, v8.16b,  v9.16b,  \step, \label
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
            cmp         x10, #i+16
            blo         \next
            prefill_out \ra, \rb, v8.16b, v9.16b
.endm

/* leftedge: The very first non-fill or partial-fill chunk from the image is
 * already loaded (as it was used to calculate the left padding value), so
 * store it here, and then drop into the regular load/store cycle in the next
 * macro.
 */
.macro prefill_leftedge, next, after, ra, rb, step
1:          prefill_out \ra, \rb, v10.16b, v11.16b
            b           \after
.endm

/* dofetch: Copy chunks of the image into the window without any complications
 * from edge conditions.
 */
.macro prefill_dofetch, next, after, ra, rb, step
            cmp         x11, #i+16
            bls         \next
            bl          fetch_generic_asm
            prefill_out \ra, \rb, v10.16b, v11.16b
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
            cmp         x11, #i
            bls         \next
            bl          fetch_clampright\step
            prefill_out \ra, \rb, v10.16b, v11.16b
            b           \after
.endm

/* rightfill: The rest of the window is simply filled with right padding from
 * v12 and v13.
 */
.macro prefill_rightfill, next, after, ra, rb, step
            prefill_out \ra, \rb, v12.16b, v13.16b
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
 *      x1 -- src
 *      x2 -- pitch
 *      x3 -- count
 *      x4 -- available image data right of src pointer
 *      x5 -- r
 *      x6 -- rup
 *      x7 -- rdn
 *      x8 -- available image data left of src pointer
 *      x9 -- buffer (if needed)
 *      x13 = -pitch
 *      x15 = top-row in
 *      x19 = bottom-row in
 * Output:
 *      x4 -= min(inlen, count + windowsize - centertap)
 *      x1 += min(inlen, count + windowsize - centertap)
 *      x15 += min(inlen, count + windowsize - centertap)
 *      x19 += min(inlen, count + windowsize - centertap)
 * Modifies:
 *      x10 -- fill start index in the window
 *      x11 -- fill stop index in the window
 *      x12 -- scratch
 */
.macro prefill step=1, max_r=25, label=xx
.set windowsize, (((\max_r + \max_r) * \step + 15) & ~15)
.set centertap, (windowsize - \max_r * \step)
            mov         x10, #centertap
            subs        x10, x10, x8
            csel        x10, xzr, x10, lo

            subs        x11, x4, #windowsize - centertap
            csel        x11, xzr, x11, hs
            add         x11, x11, #windowsize

            /* x10 indicates where in the window legal image data begins.
             * x11 indicates where in the window legal image date ends.
             * When starting near the centre of a large image these would be
             * zero and windowsize respectively, but when starting near the
             * edges this can change.
             * When starting on the leftmost pixel, x10 will be centertap.
             * When starting on the rightmost pixel, x11 will be centertap+1.
             */

            /* x4 indicates how much data there is between the current pointers
             * and the right edge of the image.  The pointers currently point
             * to the data needed at centertap.  The subsequent code will
             * consume (windowsize - x10) data, but only the data from
             * centertap to windowsize comes out of x4's budget.
             */
1:          subs        x4, x4, #windowsize - centertap
            csel        x4, xzr, x4, lo

            /* And the pointers need to rewind to the start of the window.
             */
            sub         x1, x1, #centertap
            sub         x15, x15, #centertap
            sub         x19, x19, #centertap

            /* Unless x8 indicated that there wasn't that much data available.
             */
            add         x1, x1, x10
            add         x15, x15, x10
            add         x19, x19, x10

            /* Get the first chunk, and add padding to align it to the window
             * if necessary.
             */
            bl          fetch_clampleft\step

            /* Sometimes the start and the end of the window are in the same
             * chunk.  In that case both ends need filler at the outset.
             */
            sub         x12, x11, #1
            eor         x12,  x10, x12
            cmp         x12, #16
            bhs         1f
            bl          prefill_sweepright\step

            /* Iterate through all the points in the window and fill them in
             * with padding or image data as needed.
             */
1:          prefill_body \max_r, \step, \label
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
 *      x0 = dst
 *      x1 = src
 *      x2 = pitch
 *      x3 = count
 *      x4 = inlen
 *      x5 = r
 *      x6 = rup
 *      x7 = rdn
 *      x9 = buffer
 *      x13 = -pitch
 *      x15 = top-row in
 *      x19 = bottom-row in
 * Modifies
 *      x8 = fetch code pointer
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
            add         x12, x3, #15
            bic         x12, x12, #15
            cmp         x4, x12
            csel        x4, x12, x4, hi

            /* First calculate the entry-point into the internal fetch logic.
             * This is done so the same function can service several kernel
             * sizes.
             */
            adrp        x8, \labelnc
            add         x8, x8, #:lo12:\labelnc
            sub         x8, x8, x5, LSL #5
            sub         x8, x8, x5, LSL #3
            cmp         x5, x6
            ccmp        x5, x7, #0, eq
            beq         5f

            /* if (r != rup || r != rdn) then the address-clamping table should
             * be used rather than the short-cut version.
             */
            adrp        x8, \labelc
            add         x8, x8, #:lo12:\labelc
            sub         x8, x8, x5, LSL #6
            add         x8, x8, x5, LSL #3
            b           5f

            /* Main loop: ... */
            .align  4
3:          /* first perform a vertical convolution from memory to get the next
             * 16 taps of the horizontal window into the register file...
             */
            fetch max_r=\max_r, labelc=\labelc, labelnc=\labelnc, reg=x8

            /* ...then perform a horizontal convolution on that window to
             * produce eight output bytes, and slide the window along.
             * This has to be done twice to match the 16-way vertical pass.
             * It would be preferable to have twice the work done in \core, but
             * that would demand yet another variant on those macros and would
             * perturb the register allocation severely.
             */
            \core
            st1         {v15.8b}, [x0], #8
            \core
            st1         {v15.8b}, [x0], #8

            sub         x3, x3, #16
5:          subs        x4, x4, #16
            bhi         3b
            /* Here there's 16 or fewer bytes available before the edge of the
             * source image.  x4 holds that count minus 16 (because it was
             * decremented before the first iteration ran).  The last read may
             * not be a whole chunk, and beyond that a fill value must be used.
             *
             * Of course, none of that matters if there's no more output to
             * produce...
             */
            cbz         x3, 5f

            /* Oh well. */
            adds        x4, x4, #16
            bne         1f
  .if \step==1
            dup         v10.8h, v9.h[7]
            dup         v11.8h, v9.h[7]
  .else
            dup         v10.2d, v9.d[1]
            dup         v11.2d, v9.d[1]
  .endif
            b           3f

            /* To avoid reading past end of input, rewind pointers by (16-x4)
             * to ensure that they're exactly 16 bytes from the edge.
             */
1:          mov         x11, x4
            bl          fetch_clampright\step
            /* Now to put this padding to use, perform any remaining
             * iterations.  This is done at half the rate of the main loop,
             * because there's no longer pressure from a 16-lane window filler.
             */
3:          \core
  .if \step==1
            dup         v11.8h, v11.h[7]
  .else
            dup         v11.2d, v11.d[1]
  .endif
            subs        x3, x3, #8
            blo         4f
            st1         {v15.8b}, [x0], #8
            bne         3b
            b           5f

            /* If the final iteration contained 0 < l < 8 values, then perform
             * a piecewise store of the final vector.
             */
4:          tbz         x3, #2, 1f
            st1         {v15.s}[0], [x0], #4
            ext         v15.8b, v15.8b, v15.8b, #4
1:          tbz         x3, #1, 1f
            st1         {v15.h}[0], [x0], #2
            ext         v15.8b, v15.8b, v15.8b, #2
1:          tbz         x3, #0, 5f
            st1         {v15.b}[0], [x0], #1
            ext         v15.8b, v15.8b, v15.8b, #1
5:          mov         x0, #0
.endm


.irp r, TUNED_LIST1, 25
PRIVATE(convolve1_\r)
            stp         x29,x30, [sp, #-16]!

            prefill     step=1, max_r=\r, label=.Lcnv1_\r

            conv_body   core=hconv1_\r, step=1, max_r=\r, labelc=.Lcnv1_\r, labelnc=.Lcnvnc1_\r

            ldp         x29,x30, [sp], #16
            ret
END(convolve1_\r)
.endr

.irp r, TUNED_LIST4, 25
PRIVATE(convolve4_\r)
            sub         x9, sp, #0x40
            stp         x29,x30, [sp, #-(16 + 0x40 + 0x80)]!
            bic         x9, x9, #0x7f

            /* x9 now points to a 0x40 byte buffer on the stack whose address
             * has the low 7 bits clear.  This allows easy address calculation
             * in the wrap-around cases.
             */

            prefill     step=4, max_r=\r, label=.Lcnv4_\r

            conv_body   core=hconv4_\r, step=4, max_r=\r, labelc=.Lcnv4_\r, labelnc=.Lcnvnc4_\r

            ldp         x29,x30, [sp], #(16 + 0x40 + 0x80)
            ret
END(convolve4_\r)
.endr

/* void rsdIntrinsicBlurU1_K(
 *                  void *out,      // x0
 *                  void *in,       // x1
 *                  size_t w,       // x2
 *                  size_t h,       // x3
 *                  size_t p,       // x4
 *                  size_t x,       // x5
 *                  size_t y,       // x6
 *                  size_t count,   // x7
 *                  size_t r,       // [sp]
 *                  uint16_t *tab); // [sp,#8]
 */
ENTRY(rsdIntrinsicBlurU1_K)
            stp         x19,x30, [sp, #-16]!
            sub         x8, sp, #32
            sub         sp, sp, #64
            st1         {v8.1d - v11.1d}, [sp]
            st1         {v12.1d - v15.1d}, [x8]
            mov         x8, x5          // x
            ldr         w5, [sp,#80]    // r
            sub         x9, x2, x8      // w - x
            sub         x10, x3, x6     // h - y
            mov         x2, x4          // pitch
            mov         x3, x7          // count
            sub         x7, x10, #1     // h - y - 1
            mov         x4, x9          // inlen = (w - x)

            ldr         x12, [sp, #88] // tab

            add         x1, x1, x8      // src += x

            cmp         x6, x5
            csel        x6, x5, x6, hs  // rup = min(r, y)
            cmp         x7, x5
            csel        x7, x5, x7, hs  // rdn = min(r, h - y - 1)

            sub         x13, xzr, x2    // -pitch
            msub        x15, x2, x6, x1
            madd        x19, x2, x7, x1

            ld1         {v0.8h,v1.8h}, [x12], #32
            ld1         {v2.8h,v3.8h}, [x12], #32

            adr         x30, 1f
  .irp r, TUNED_LIST1
            cmp         x5, #\r
            bls         convolve1_\r
  .endr
            b           convolve1_25

1:          ld1         {v8.1d - v11.1d}, [sp], #32
            ld1         {v12.1d - v15.1d}, [sp], #32
            ldp         x19,x30, [sp], #16
            ret
END(rsdIntrinsicBlurU1_K)

/* void rsdIntrinsicBlurU4_K(
 *                  void *out,      // x0
 *                  void *in,       // x1
 *                  size_t w,       // x2
 *                  size_t h,       // x3
 *                  size_t p,       // x4
 *                  size_t x,       // x5
 *                  size_t y,       // x6
 *                  size_t count,   // x7
 *                  size_t r,       // [sp]
 *                  uint16_t *tab); // [sp,#8]
 */
ENTRY(rsdIntrinsicBlurU4_K)
            stp         x19,x30, [sp, #-16]!
            sub         x8, sp, #32
            sub         sp, sp, #64
            st1         {v8.1d - v11.1d}, [sp]
            st1         {v12.1d - v15.1d}, [x8]
            lsl         x8, x5, #2      // x
            lsl         x2, x2, #2
            ldr         w5, [sp,#80]    // r
            sub         x9, x2, x8      // w - x
            sub         x10, x3, x6     // h - y
            mov         x2, x4          // pitch
            lsl         x3, x7, #2      // count
            sub         x7, x10, #1     // h - y - 1
            mov         x4, x9          // inlen = (w - x)

            ldr         x12, [sp, #88]

            add         x1, x1, x8      // in += x

            cmp         x6, x5
            csel        x6, x5, x6, hs  // rup = min(r, y)
            cmp         x7, x5
            csel        x7, x5, x7, hs  // rdn = min(r, h - y - 1)


            sub         x13, xzr, x2
            msub        x15, x2, x6, x1
            madd        x19, x2, x7, x1

            ld1         {v0.8h,v1.8h}, [x12], #32
            ld1         {v2.8h,v3.8h}, [x12], #32

            adr         x30, 1f
  .irp r, TUNED_LIST4
            cmp         x5, #\r
            bls         convolve4_\r
  .endr
            b           convolve4_25

1:          ld1         {v8.1d - v11.1d}, [sp], #32
            ld1         {v12.1d - v15.1d}, [sp], #32
            ldp         x19,x30, [sp], #16
            ret
END(rsdIntrinsicBlurU4_K)
