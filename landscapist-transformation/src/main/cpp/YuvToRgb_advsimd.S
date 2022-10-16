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
#define END(f) .size f, .-f;

/* Perform the actual YuvToRGB conversion in a macro, from register to
 * register.  This macro will be called from within several different wrapper
 * variants for different data layouts.  Y data starts with the even and odd
 * bytes split into the low parts of v8 and v9 respectively.  U and V are in
 * v10 and v11.  Working constants are pre-loaded into v24-v31, and v3 and v7
 * are pre-loaded with a constant 0xff alpha channel.
 *
 * The complicated arithmetic is the result of refactoring the original
 * equations to avoid 16-bit overflow without losing any precision.
 */
.macro yuvkern, regu=v10, regv=v11
        /* v0   out R_lo / even R_lo accumulator
         * v1   out G_lo / even G_lo accumulator
         * v2   out B_lo / even B_lo accumulator
         * v3   out A_lo / const 0xff*ff
         * v4   out R_hi / even R_hi accumulator
         * v5   out G_hi / even G_hi accumulator
         * v6   out B_hi / even B_hi accumulator
         * v7   out A_hi / const 0xff*ff
         * v8   even Y   / G_lo luma tmp
         * v9   odd Y    / G_lo luma tmp
         * \regu in U
         * \regv in V
         * v12  R_lo luma tmp
         * v13  B_lo luma tmp
         * v14  R_hi luma tmp
         * v15  B_hi luma tmp
         * v16  odd R_lo accumulator
         * v17  odd G_lo accumulator
         * v18  odd B_lo accumulator
         * v19  multiplier extra bits low
         * v20  odd R_hi accumulator
         * v21  odd G_hi accumulator
         * v22  odd B_hi accumulator
         * v23  multiplier extra bits high
         * v24  constant 149
         * v25  constant 50
         * v26  constant 104
         * v27  constant 204
         * v28  constant 254
         * v29  constant ((16 * 149 + (128 >> 1) + 128 * 204) >> 1)
         * v30  constant ((-16 * 149 + 128 * 50 + 128 * 104) >> 0)
         * v31  constant ((16 * 149 + (128 << 2) + 128 * 254) >> 1)
         */

        umull       v1.8h,  v8.8b,  v24.8b      // g0 = y0 * 149
        umull       v17.8h, v9.8b,  v24.8b      // g1 = y1 * 149
        umull2      v5.8h,  v8.16b, v24.16b     // g0_hi = y0_hi * 149
        umull2      v21.8h, v9.16b, v24.16b     // g1_hi = y1_hi * 149

        umull       v8.8h, \regu\().8b, v25.8b     // g2 = u * 50 + v * 104
        umlal       v8.8h, \regv\().8b, v26.8b
        umull2      v9.8h, \regu\().16b, v25.16b   // g2_hi = u_hi * 50 + v_hi * 104
        umlal2      v9.8h, \regv\().16b, v26.16b

        ushr        v19.16b, \regv\().16b, #1
        uaddw       v0.8h,  v1.8h,  v19.8b      // r0 = g0 + (v >> 1)
        uaddw       v16.8h, v17.8h, v19.8b      // r1 = g1 + (v >> 1)

        uaddw2      v4.8h,  v5.8h,  v19.16b     // r0_hi = g0_hi + (v_hi >> 1)
        uaddw2      v20.8h, v21.8h, v19.16b     // r1_hi = g1_hi + (v_hi >> 1)

        ushll       v19.8h, \regu\().8b,  #2
        ushll2      v23.8h, \regu\().16b, #2
        add         v2.8h,  v1.8h,  v19.8h      // b0 = g0 + (u << 2)
        add         v18.8h, v17.8h, v19.8h      // b1 = g1 + (u << 2)

        add         v6.8h,  v5.8h,  v23.8h      // b0_hi = g0_hi + (u_hi << 2)
        add         v22.8h, v21.8h, v23.8h      // b1_hi = g1_hi + (u_hi << 2)

        umull       v12.8h, \regv\().8b, v27.8b    // r2 = v * 204
        umull       v13.8h, \regu\().8b, v28.8b    // b2 = u * 254

        umull2      v14.8h, \regv\().16b, v27.16b  // r2_hi = v_hi * 204
        umull2      v15.8h, \regu\().16b, v28.16b  // b2_hi = u_hi * 254

        uhadd       v0.8h,  v0.8h,  v12.8h      // r0 = (r0 + r2) >> 1
        uhadd       v16.8h, v16.8h, v12.8h      // r1 = (r1 + r2) >> 1
        uqadd       v1.8h,  v1.8h,  v30.8h      // g0 = satu16(g0 + (-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        uqadd       v17.8h, v17.8h, v30.8h      // g1 = satu16(g1 + (-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        uhadd       v2.8h,  v2.8h,  v13.8h      // b0 = (b0 + b2) >> 1
        uhadd       v18.8h, v18.8h, v13.8h      // b1 = (b1 + b2) >> 1

        uhadd       v4.8h,  v4.8h,  v14.8h      // r0_hi = (r0_hi + r2_hi) >> 1
        uhadd       v20.8h, v20.8h, v14.8h      // r1_hi = (r1_hi + r2_hi) >> 1
        uqadd       v5.8h,  v5.8h,  v30.8h      // g0_hi = satu16(g0_hi + (-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        uqadd       v21.8h, v21.8h, v30.8h      // g1_hi = satu16(g1_hi + (-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        uhadd       v6.8h,  v6.8h,  v15.8h      // b0_hi = (b0_hi + b2_hi) >> 1
        uhadd       v22.8h, v22.8h, v15.8h      // b1_hi = (b1_hi + b2_hi) >> 1

        uqsub       v0.8h,  v0.8h,  v29.8h      // r0 = satu16(r0 - (16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        uqsub       v16.8h, v16.8h, v29.8h      // r1 = satu16(r1 - (16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        uqsub       v1.8h,  v1.8h,  v8.8h       // g0 = satu16(g0 - g2)
        uqsub       v17.8h, v17.8h, v8.8h       // g1 = satu16(g1 - g2)
        uqsub       v2.8h,  v2.8h,  v31.8h      // b0 = satu16(b0 - (16 * 149 + (128 << 2) + 128 * 254) >> 1)
        uqsub       v18.8h, v18.8h, v31.8h      // b1 = satu16(b1 - (16 * 149 + (128 << 2) + 128 * 254) >> 1)

        uqsub       v4.8h,  v4.8h,  v29.8h      // r0_hi = satu16(r0_hi - (16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        uqsub       v20.8h, v20.8h, v29.8h      // r1_hi = satu16(r1_hi - (16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        uqsub       v5.8h,  v5.8h,  v9.8h       // g0_hi = satu16(g0_hi - g2_hi)
        uqsub       v21.8h, v21.8h, v9.8h       // g1_hi = satu16(g1_hi - g2_hi)
        uqsub       v6.8h,  v6.8h,  v31.8h      // b0_hi = satu16(b0_hi - (16 * 149 + (128 << 2) + 128 * 254) >> 1)
        uqsub       v22.8h, v22.8h, v31.8h      // b1_hi = satu16(b1_hi - (16 * 149 + (128 << 2) + 128 * 254) >> 1)

        uqrshrn     v0.8b,  v0.8h,  #6
        uqrshrn     v16.8b, v16.8h, #6
        uqrshrn     v1.8b,  v1.8h,  #7
        uqrshrn     v17.8b, v17.8h, #7
        uqrshrn     v2.8b,  v2.8h,  #6
        uqrshrn     v18.8b, v18.8h, #6

        uqrshrn     v4.8b,  v4.8h,  #6
        uqrshrn     v20.8b, v20.8h, #6
        uqrshrn     v5.8b,  v5.8h,  #7
        uqrshrn     v21.8b, v21.8h, #7
        uqrshrn     v6.8b,  v6.8h,  #6
        uqrshrn     v22.8b, v22.8h, #6

        zip1        v0.16b, v0.16b, v16.16b
        zip1        v1.16b, v1.16b, v17.16b
        zip1        v2.16b, v2.16b, v18.16b

        zip1        v4.16b, v4.16b, v20.16b
        zip1        v5.16b, v5.16b, v21.16b
        zip1        v6.16b, v6.16b, v22.16b
.endm

/* Define the wrapper code which will load and store the data, iterate the
 * correct number of times, and safely handle the remainder at the end of the
 * loop.  Some sections of code are switched out depending on the data packing
 * being handled.
 */
.macro wrap_line kernel, interleaved=0, swapuv=0
        movi        v24.16b, #149
        movi        v25.16b, #50
        movi        v26.16b, #104
        movi        v27.16b, #204
        movi        v28.16b, #254
        mov         w5, #((16 * 149 + (128 >> 1) + 128 * 204) >> 1)
        dup         v29.8h, w5
        mov         w5, #((-16 * 149 + 128 * 50 + 128 * 104) >> 0)
        dup         v30.8h, w5
        mov         w5, #((16 * 149 + (128 << 2) + 128 * 254) >> 1)
        dup         v31.8h, w5

        movi        v3.16b, #0xff
        movi        v7.16b, #0xff

        subs        x2, x2, #32
        bhs         1f
        b           2f

        .align 4
1:      ld2         {v8.16b,v9.16b}, [x1], #32
  .if \interleaved
        ld2         {v10.16b,v11.16b}, [x3], #32
  .else
        ld1         {v10.16b}, [x3], #16
        ld1         {v11.16b}, [x4], #16
  .endif

  .if \swapuv
        \kernel regu=v11, regv=v10
  .else
        \kernel
  .endif

        subs        x2, x2, #32

        st4         {v0.16b - v3.16b}, [x0], #64
        st4         {v4.16b - v7.16b}, [x0], #64

        bhs         1b

2:      adds        x2, x2, #32
        beq         2f

        /* To handle the tail portion of the data (something less than 32
         * bytes) load small power-of-two chunks into working registers.  It
         * doesn't matter where they end up in the register; the same process
         * will store them back out using the same positions and the
         * interaction between neighbouring pixels is constrained to odd
         * boundaries where the load operations don't interfere.
         */
        movi        v8.8b, #0
        movi        v9.8b, #0
        movi        v10.8b, #0
        movi        v11.8b, #0

        tbz         x2, #4, 1f
        ld1         {v9.16b}, [x1], #16
  .if \interleaved
        ld1         {v11.16b}, [x3], #16
  .else
        ld1         {v10.d}[1], [x3], #8
        ld1         {v11.d}[1], [x4], #8
  .endif
1:      tbz         x2, #3, 1f
        ld1         {v8.d}[1], [x1], #8
  .if \interleaved
        ld1         {v10.d}[1], [x3], #8
  .else
        ld1         {v10.s}[1], [x3], #4
        ld1         {v11.s}[1], [x4], #4
  .endif
1:      tbz         x2, #2, 1f
        ld1         {v8.s}[1], [x1], #4
  .if \interleaved
        ld1         {v10.s}[1], [x3], #4
  .else
        ld1         {v10.h}[1], [x3], #2
        ld1         {v11.h}[1], [x4], #2
  .endif
1:      tbz         x2, #1, 1f
        ld1         {v8.h}[1], [x1], #2
  .if \interleaved
        ld1         {v10.h}[1], [x3], #2
  .else
        ld1         {v10.b}[1], [x3], #1
        ld1         {v11.b}[1], [x4], #1
  .endif
1:      tbz         x2, #0, 1f
        ld1         {v8.b}[1], [x1], #1
  .if \interleaved
        ld1         {v10.h}[0], [x3], #2
  .else
        ld1         {v10.b}[0], [x3], #1
        ld1         {v11.b}[0], [x4], #1
  .endif

        /* One small impediment in the process above is that some of the load
         * operations can't perform byte-wise structure deinterleaving at the
         * same time as loading only part of a register.  So the data is loaded
         * linearly and unpacked manually at this point if necessary.
         */
1:      mov         v12.16b, v8.16b
        uzp1        v8.16b, v12.16b, v9.16b
        uzp2        v9.16b, v12.16b, v9.16b
  .if \interleaved
        mov         v12.16b, v10.16b
        uzp1        v10.16b, v12.16b, v11.16b
        uzp2        v11.16b, v12.16b, v11.16b
  .endif

  .if \swapuv
        \kernel regu=v11, regv=v10
  .else
        \kernel
  .endif

        /* As above but with the output; structured stores for partial vectors
         * aren't available, so the data is re-packed first and stored linearly.
         */
        zip1        v16.16b, v0.16b, v2.16b
        zip2        v18.16b, v0.16b, v2.16b
        zip1        v17.16b, v1.16b, v3.16b
        zip2        v19.16b, v1.16b, v3.16b
        zip1        v0.16b, v16.16b, v17.16b
        zip2        v1.16b, v16.16b, v17.16b
        zip1        v2.16b, v18.16b, v19.16b
        zip2        v3.16b, v18.16b, v19.16b

        /* Luckily v4-v7 don't need to be unzipped because the complete set of
         * four and can be stored using st4. */

        tbz         x2, #4, 1f
        st4         {v4.16b - v7.16b}, [x0], #64
1:      tbz         x2, #3, 1f
        st1         {v2.16b,v3.16b}, [x0], #32
1:      tbz         x2, #2, 1f
        st1         {v1.16b}, [x0], #16
1:      tbz         x2, #1, 1f
        st1         {v0.d}[1], [x0], #8
1:      tbz         x2, #0, 2f
        st1         {v0.s}[1], [x0], #4
2:
.endm


/*  void rsdIntrinsicYuv2_K(
 *          void *out,          // x0
 *          void const *yin,    // x1
 *          void const *uin,    // x2
 *          void const *vin,    // x3
 *          size_t xstart,      // x4
 *          size_t xend);       // x5
 */
ENTRY(rsdIntrinsicYuv2_K)
        lsr         x6, x4, #1
        add         x0, x0, x4, LSL #2
        add         x1, x1, x4
        add         x4, x3, x6
        add         x3, x2, x6
        sub         x2, x5, x6, LSL #1

        sub         x6, sp, #32
        sub         sp, sp, #64
        st1         {v8.1d - v11.1d}, [sp]
        st1         {v12.1d - v15.1d}, [x6]

        wrap_line yuvkern, 0

        ld1         {v8.1d - v11.1d}, [sp], #32
        ld1         {v12.1d - v15.1d}, [sp], #32
        ret
END(rsdIntrinsicYuv2_K)

/*  void rsdIntrinsicYuv_K(
 *          void *out,          // x0
 *          void const *yin,    // x1
 *          void const *uvin,   // x2
 *          size_t xstart,      // x3
 *          size_t xend);       // x4
 */
ENTRY(rsdIntrinsicYuv_K)
        bic         x5, x3, #1
        add         x0, x0, x5, LSL #2
        add         x1, x1, x5
        add         x3, x2, x5
        sub         x2, x4, x5

        sub         x5, sp, #32
        sub         sp, sp, #64
        st1         {v8.1d - v11.1d}, [sp]
        st1         {v12.1d - v15.1d}, [x5]

        wrap_line yuvkern, 1, 1

        ld1         {v8.1d - v11.1d}, [sp], #32
        ld1         {v12.1d - v15.1d}, [sp], #32
        ret
END(rsdIntrinsicYuv_K)

/*  void rsdIntrinsicYuvR_K(
 *          void *out,          // x0
 *          void const *yin,    // x1
 *          void const *uvin,   // x2
 *          size_t xstart,      // x3
 *          size_t xend);       // x4
 */
ENTRY(rsdIntrinsicYuvR_K)
        bic         x5, x3, #1
        add         x0, x0, x5, LSL #2
        add         x1, x1, x5
        add         x3, x2, x5
        sub         x2, x4, x5

        sub         x5, sp, #32
        sub         sp, sp, #64
        st1         {v8.1d - v11.1d}, [sp]
        st1         {v12.1d - v15.1d}, [x5]

        wrap_line yuvkern, 1

        ld1         {v8.1d - v11.1d}, [sp], #32
        ld1         {v12.1d - v15.1d}, [sp], #32
        ret
END(rsdIntrinsicYuvR_K)
