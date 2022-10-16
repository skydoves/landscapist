/*
 * Copyright (C) 2015 The Android Open Source Project
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

/* Fixed-point precision after vertical pass -- 16 bit data minus 1 sign and 1
 * integer (bicubic has a little overshoot).  It would also be possible to add
 * a temporary DC bias to eliminate the sign bit for more precision, but that's
 * extra arithmetic.
 */
.set VERTBITS, 14

/* The size of the scratch buffer in which we store our vertically convolved
 * intermediates.
 */
.set CHUNKSHIFT, 7       /* 5 tests better for uchar4, but 7 is necessary for ridiculous (10:1) scale factors */
.set CHUNKSIZE, (1 << CHUNKSHIFT)

/* The number of components processed in a single iteration of the innermost
 * loop.
 */
.set VECSHIFT, 3
.set VECSIZE, (1<<VECSHIFT)

/* Read four different lines (except at edges where addresses may be clamped,
 * which is why we don't simply take base and stride registers), and multiply
 * and accumulate them by the coefficients in v3[0..3], leaving the results in
 * v12.  This gives eight 16-bit results representing a horizontal line of 2-8
 * input pixels (depending on number of components per pixel) to be fed into
 * the horizontal scaling pass.
 *
 * Input coefficients are 16-bit unsigned fixed-point (although [0] and [3] are
 * known to represent negative values and VMLS is used to implement this).
 * Output is VERTBITS signed fixed-point, which must leave room for a little
 * v12.  This gives eight 16-bit results.
 */
.macro vert8, dstlo=v12.4h, dsthi=v12.8h
        ld1         {v8.8b}, [x4], #8
        ld1         {v9.8b}, [x5], #8
        ld1         {v10.8b}, [x6], #8
        ld1         {v11.8b}, [x7], #8
        uxtl        v8.8h, v8.8b
        uxtl        v9.8h, v9.8b
        uxtl        v10.8h, v10.8b
        uxtl        v11.8h, v11.8b
        umull       v12.4s, v9.4h, v3.h[1]
        umull2      v13.4s, v9.8h, v3.h[1]
        umlsl       v12.4s, v8.4h, v3.h[0]
        umlsl2      v13.4s, v8.8h, v3.h[0]
        umlal       v12.4s, v10.4h, v3.h[2]
        umlal2      v13.4s, v10.8h, v3.h[2]
        umlsl       v12.4s, v11.4h, v3.h[3]
        umlsl2      v13.4s, v11.8h, v3.h[3]

        /* Shift by 8 (bits per pixel), plus 16 (the fixed-point multiplies),
         * minus VERTBITS (the number of fraction bits we want to keep from
         * here on).
         */
        sqshrn      \dstlo, v12.4s, #8 + (16 - VERTBITS)
        sqshrn2     \dsthi, v13.4s, #8 + (16 - VERTBITS)
.endm

/* As above, but only four 16-bit results into v12hi.
 */
.macro vert4, dst=v12.8h
        ld1         {v8.s}[0], [x4], #4
        ld1         {v9.s}[0], [x5], #4
        ld1         {v10.s}[0], [x6], #4
        ld1         {v11.s}[0], [x7], #4
        uxtl        v8.8h, v8.8b
        uxtl        v9.8h, v9.8b
        uxtl        v10.8h, v10.8b
        uxtl        v11.8h, v11.8b
        umull       v12.4s, v9.4h, v3.h[1]
        umlsl       v12.4s, v8.4h, v3.h[0]
        umlal       v12.4s, v10.4h, v3.h[2]
        umlsl       v12.4s, v11.4h, v3.h[3]
.ifc \dst,v12.8h
        sqshrn2     \dst, v12.4s, #8 + (16 - VERTBITS)
.else
        sqshrn      \dst, v12.4s, #8 + (16 - VERTBITS)
.endif
.endm


/* During horizontal resize having CHUNKSIZE input available means being able
 * to produce a varying amount of output, depending on the phase of the data.
 * This function calculates the minimum number of VECSIZE chunks extracted from
 * a CHUNKSIZE window (x1), and the threshold value for when the count will be
 * one higher than that (x0).
 * These work out, conveniently, to be the quotient and remainder from:
 *      (CHUNKSIZE + xinc * VECSIZE - 1) / (xinc * VECSIZE)
 *
 * The two values are packed together in a uint64_t for convenience; and
 * they are, in fact, used this way as an arithmetic short-cut later on.
 */
/* uint64_t rsdIntrinsicResize_oscctl_K(uint32_t xinc) */
ENTRY(rsdIntrinsicResize_oscctl_K)
        lsl         x2, x0, #VECSHIFT
        mov         x0, #(CHUNKSIZE << 16) - 1
        add         x0, x0, x2
        udiv        x1, x0, x2
        msub        x0, x1, x2, x0
        add         x0, x0, x1, LSL #32
        ret
END(rsdIntrinsicResize_oscctl_K)

/* Iterate to generate the uchar1, uchar2, and uchar4 versions of the code.
 * For the most part the vertical pass (the outer loop) is the same for all
 * versions.  Exceptions are handled in-line with conditional assembly.
 */
.irp comp, 1, 2, 4
.if \comp == 1
.set COMPONENT_SHIFT, 0
.elseif \comp == 2
.set COMPONENT_SHIFT, 1
.elseif \comp == 4
.set COMPONENT_SHIFT, 2
.else
.error "Unknown component count"
.endif
.set COMPONENT_COUNT, (1 << COMPONENT_SHIFT)
.set LOOP_OUTPUT_SIZE, (VECSIZE * COMPONENT_COUNT)

.set BUFFER_SIZE, (CHUNKSIZE * 2 + 4) * COMPONENT_COUNT * 2

/* void rsdIntrinsicResizeB1_K(
 *             uint8_t * restrict dst,          // x0
 *             size_t count,                    // x1
 *             uint32_t xf,                     // x2
 *             uint32_t xinc,                   // x3
 *             uint8_t const * restrict srcn,   // x4
 *             uint8_t const * restrict src0,   // x5
 *             uint8_t const * restrict src1,   // x6
 *             uint8_t const * restrict src2,   // x7
 *             size_t xclip,                    // [sp,#0]  -> [sp,#80] -> x12
 *             size_t avail,                    // [sp,#8]  -> [sp,#88] -> x11
 *             uint64_t osc_ctl,                // [sp,#16] -> [sp,#96] -> x10
 *             int32 const *yr,                 // [sp,#24] -> [sp,#104] -> v4   (copied to v3   for scalar access)
 */
ENTRY(rsdIntrinsicResizeB\comp\()_K)
            sub         x8, sp, #48
            sub         sp, sp, #80
            st1         {v8.1d - v11.1d}, [sp]
            st1         {v12.1d - v15.1d}, [x8]
            str         x19, [x8, #32]

            /* align the working buffer on the stack to make it easy to use bit
             * twiddling for address calculations.
             */
            sub         x12, sp, #BUFFER_SIZE
            bic         x12, x12, #(1 << (CHUNKSHIFT + 1 + COMPONENT_SHIFT + 1)) - 1

            ldr         x8, [sp,#104]           // yr
            adrp        x9, intrinsic_resize_consts
            add         x9, x9, :lo12:intrinsic_resize_consts
            ld1         {v4.4s}, [x8]
            ld1         {v5.8h}, [x9]
            sqxtun      v4.4h, v4.4s            // yr
            dup         v6.8h, w2
            dup         v7.8h, w3
            mla         v6.8h, v5.8h, v7.8h     // vxf
            shl         v7.8h, v7.8h, #VECSHIFT // vxinc

            /* Compute starting condition for oscillator used to compute ahead
             * of time how many iterations are possible before needing to
             * refill the working buffer.  This is based on the fixed-point
             * index of the last element in the vector of pixels processed in
             * each iteration, counting up until it would overflow.
             */
            sub         x8, x2, x3
            lsl         x9, x3, #VECSHIFT
            add         x8, x8, x9

            ldr         x10, [sp,#96]           // osc_ctl
            ldp         x13,x11, [sp,#80]       // xclip, avail

            mov         x19, sp
            mov         sp, x12

            /* x4-x7 contain pointers to the four lines of input to be
             * convolved.  These pointers have been clamped vertically and
             * horizontally (which is why it's not a simple row/stride pair),
             * and the xclip argument (now in x13) indicates how many pixels
             * from true the x position of the pointer is.  This value should
             * be 0, 1, or 2 only.
             *
             * Start by placing four pixels worth of input at the far end of
             * the buffer.  As many as two of these may be clipped, so four
             * pixels are fetched, and then the first pixel is duplicated and
             * the data shifted according to xclip.  The source pointers are
             * then also adjusted according to xclip so that subsequent fetches
             * match.
             */
            mov         v3.8b, v4.8b  /* make y coeffs available for vert4 and vert8 macros */
            sub         x14, x12, x13, LSL #(COMPONENT_SHIFT + 1)
            add         x15, x12, #(2 * CHUNKSIZE - 4) * COMPONENT_COUNT * 2
            add         x14, x14, #4 * COMPONENT_COUNT * 2
.if \comp == 1
            vert4       v12.4h
            dup         v11.4h, v12.h[0]
            st1         {v11.4h,v12.4h}, [x12]
            ld1         {v12.4h}, [x14]
            st1         {v12.4h}, [x15]
.elseif \comp == 2
            vert8
            dup         v11.4s, v12.s[0]
            st1         {v11.8h,v12.8h}, [x12]
            ld1         {v12.8h}, [x14]
            st1         {v12.8h}, [x15]
.elseif \comp == 4
            vert8       v14.4h, v14.8h
            vert8       v15.4h, v15.8h
            dup         v12.2d, v14.d[0]
            dup         v13.2d, v14.d[0]
            st1         {v12.8h,v13.8h}, [x12], #32
            st1         {v14.8h,v15.8h}, [x12]
            sub         x12, x12, #32
            ld1         {v11.8h,v12.8h}, [x14]
            st1         {v11.8h,v12.8h}, [x15]
.endif
            /* Count off four pixels into the working buffer.
             */
            sub         x11, x11, #4
            /* Incoming pointers were to the first _legal_ pixel.  Four pixels
             * were read unconditionally, but some may have been discarded by
             * xclip, so we rewind the pointers to compensate.
             */
            sub         x4, x4, x13, LSL #(COMPONENT_SHIFT)
            sub         x5, x5, x13, LSL #(COMPONENT_SHIFT)
            sub         x6, x6, x13, LSL #(COMPONENT_SHIFT)
            sub         x7, x7, x13, LSL #(COMPONENT_SHIFT)

            /* First tap starts where we just pre-filled, at the end of the
             * buffer.
             */
            add         x2, x2, #(CHUNKSIZE * 2 - 4) << 16

            /* Use overflowing arithmetic to implement wraparound array
             * indexing.
             */
            lsl         x2, x2, #(47 - CHUNKSHIFT)
            lsl         x3, x3, #(47 - CHUNKSHIFT)


            /* Start of outermost loop.
             * Fetch CHUNKSIZE pixels into scratch buffer, then calculate the
             * number of iterations of the inner loop that can be performed and
             * get into that.
             *
             * The fill is complicated by the possibility of running out of
             * input before the scratch buffer is filled.  If this isn't a risk
             * then it's handled by the simple loop at 2:, otherwise the
             * horrible loop at 3:.
             */
1:          mov         v3.8b, v4.8b            /* put y scaling coefficients somewhere handy */
            subs        x11, x11, #CHUNKSIZE
            bge         2f                      /* if at least CHUNKSIZE are available... */
            add         x11, x11, #CHUNKSIZE    /* if they're not... */
            b           4f
            /* basic fill loop, processing 8 bytes at a time until there are
             * fewer than eight bytes available.
             */
3:          vert8
            sub         x11, x11, #8 / COMPONENT_COUNT
            st1         {v12.8h}, [x12], #16
4:          cmp         x11, #8 / COMPONENT_COUNT - 1
            bgt         3b
.if \comp == 4
            blt         3f
            /* The last pixel (four bytes) if necessary */
            vert4
.else
            cmp         x11, #1
            blt         3f
            /* The last pixels if necessary */
            sub         x4, x4, #8
            sub         x5, x5, #8
            sub         x6, x6, #8
            sub         x7, x7, #8
            add         x4, x4, x11, LSL #(COMPONENT_SHIFT)
            add         x5, x5, x11, LSL #(COMPONENT_SHIFT)
            add         x6, x6, x11, LSL #(COMPONENT_SHIFT)
            add         x7, x7, x11, LSL #(COMPONENT_SHIFT)
            vert8
            sub         x11, sp, x11, LSL #(COMPONENT_SHIFT + 1)
            sub         sp, sp, #32
            sub         x11, x11, #16
.if \comp == 1
            dup         v13.8h, v12.h[7]
.elseif \comp == 2
            dup         v13.4s, v12.s[3]
.endif
            st1         {v12.8h,v13.8h}, [sp]
            ld1         {v12.8h}, [x11]
            add         sp, sp, #32
            b           4f
.endif
            /* Keep filling until we get to the end of this chunk of the buffer */
3:
.if \comp == 1
            dup         v12.8h, v12.h[7]
.elseif \comp == 2
            dup         v12.4s, v12.s[3]
.elseif \comp == 4
            dup         v12.2d, v12.d[1]
.endif
4:          st1         {v12.8h}, [x12], #16
            tst         x12, #(CHUNKSIZE - 1) * COMPONENT_COUNT * 2
            bne         3b
            b           4f

.align 4
2:          /* Quickly pull a chunk of data into the working buffer.
             */
            vert8
            st1         {v12.8h}, [x12], #16
            vert8
            st1         {v12.8h}, [x12], #16
            tst         x12, #(CHUNKSIZE - 1) * COMPONENT_COUNT * 2
            bne         2b
            cmp         x11, #0
            bne         3f
4:          /* if we end with 0 pixels left we'll have nothing handy to spread
             * across to the right, so we rewind a bit.
             */
            mov         x11, #1
            sub         x4, x4, #COMPONENT_COUNT
            sub         x5, x5, #COMPONENT_COUNT
            sub         x6, x6, #COMPONENT_COUNT
            sub         x7, x7, #COMPONENT_COUNT
3:          /* copy four taps (width of cubic window) to far end for overflow
             * address handling
             */
            sub         x13, x12, #CHUNKSIZE * COMPONENT_COUNT * 2
            eor         x12, x13, #CHUNKSIZE * COMPONENT_COUNT * 2
.if \comp == 1
            ld1         {v14.4h}, [x13]
.elseif \comp == 2
            ld1         {v14.8h}, [x13]
.elseif \comp == 4
            ld1         {v14.8h,v15.8h}, [x13]
.endif
            add         x13, x12, #CHUNKSIZE * COMPONENT_COUNT * 2
.if \comp == 1
            st1         {v14.4h}, [x13]
.elseif \comp == 2
            st1         {v14.8h}, [x13]
.elseif \comp == 4
            st1         {v14.8h,v15.8h}, [x13]
.endif
            /* The high 32-bits of x10 contains the maximum possible iteration
             * count, but if x8 is greater than the low 32-bits of x10 then
             * this indicates that the count must be reduced by one for this
             * iteration to avoid reading past the end of the available data.
             */
            sub         x13, x10, x8
            lsr         x13, x13, #32

            madd        x8, x13, x9, x8
            sub         x8, x8, #(CHUNKSIZE << 16)

            /* prefer to count pixels, rather than vectors, to clarify the tail
             * store case on exit.
             */
            lsl         x13, x13, #VECSHIFT
            cmp         x13, x1
            csel        x13, x1, x13, gt

            sub         x1, x1, x13

            lsl         x13, x13, #COMPONENT_SHIFT

            mov         w14, #0x8000
            movi        v30.8h, #3
            dup         v31.8h, w14

            cmp         x13, #0
            bgt         3f
            cmp         x1, #0
            bgt         1b     /* an extreme case where we shouldn't use code in this structure */
            b           9f

            .align 4
2:          /* Inner loop continues here, but starts at 3:, see end of loop
             * below for explanation. */
.if LOOP_OUTPUT_SIZE == 4
            st1         {v8.s}[0], [x0], #4
.elseif LOOP_OUTPUT_SIZE == 8
            st1         {v8.8b}, [x0], #8
.elseif LOOP_OUTPUT_SIZE == 16
            st1         {v8.16b}, [x0], #16
.elseif LOOP_OUTPUT_SIZE == 32
            st1         {v8.16b,v9.16b}, [x0], #32
.endif
            /* Inner loop:  here the four x coefficients for each tap are
             * calculated in vector code, and the addresses are calculated in
             * scalar code, and these calculations are interleaved.
             */
3:          ushr        v8.8h, v6.8h, #1            // sxf
            lsr         x14, x2, #(63 - CHUNKSHIFT)
            sqrdmulh    v9.8h, v8.8h, v8.8h         // sxf**2
            add         x2, x2, x3
            sqrdmulh    v10.8h, v9.8h, v8.8h        // sxf**3
            lsr         x15, x2, #(63 - CHUNKSHIFT)
            sshll       v11.4s, v9.4h, #2
            sshll2      v12.4s, v9.8h, #2
            add         x2, x2, x3
            smlsl       v11.4s, v10.4h, v30.4h
            smlsl2      v12.4s, v10.8h, v30.8h
            lsr         x16, x2, #(63 - CHUNKSHIFT)

            shadd       v0.8h, v10.8h, v8.8h
            add         x2, x2, x3
            sub         v0.8h, v9.8h, v0.8h
            lsr         x17, x2, #(63 - CHUNKSHIFT)

            saddw       v1.4s, v11.4s, v9.4h
            saddw2      v13.4s, v12.4s, v9.8h
            add         x2, x2, x3
            shrn        v1.4h, v1.4s, #1
            shrn2       v1.8h, v13.4s, #1
            add         x14, sp, x14, LSL #(COMPONENT_SHIFT + 1)
            sub         v1.8h, v1.8h, v31.8h
            add         x15, sp, x15, LSL #(COMPONENT_SHIFT + 1)

            saddw       v2.4s, v11.4s, v8.4h
            saddw2      v13.4s, v12.4s, v8.8h
            add         x16, sp, x16, LSL #(COMPONENT_SHIFT + 1)
            shrn        v2.4h, v2.4s, #1
            shrn2       v2.8h, v13.4s, #1
            add         x17, sp, x17, LSL #(COMPONENT_SHIFT + 1)
            neg         v2.8h, v2.8h

            shsub       v3.8h, v10.8h, v9.8h

            /* increment the x fractional parts (oveflow is ignored, as the
             * scalar arithmetic shadows this addition with full precision).
             */
            add         v6.8h, v6.8h, v7.8h

            /* At this point we have four pointers in x8-x11, pointing to the
             * four taps in the scratch buffer that must be convolved together
             * to produce an output pixel (one output pixel per pointer).
             * These pointers usually overlap, but their spacing is irregular
             * so resolving the redundancy through L1 is a pragmatic solution.
             *
             * The scratch buffer is made of signed 16-bit data, holding over
             * some extra precision, and overshoot, from the vertical pass.
             *
             * We also have the 16-bit unsigned fixed-point weights for each
             * of the four taps in v0 - v3.  That's eight pixels worth of
             * coefficients when we have only four pointers, so calculations
             * for four more pixels are interleaved with the fetch and permute
             * code for each variant in the following code.
             *
             * The data arrangement is less than ideal for any pixel format,
             * but permuting loads help to mitigate most of the problems.
             *
             * Note also that the two outside taps of a bicubic are negative,
             * but these coefficients are unsigned.  The sign is hard-coded by
             * use of multiply-and-subtract operations.
             */
.if \comp == 1
            /* The uchar 1 case.
             * Issue one lanewise ld4.h to load four consecutive pixels from
             * one pointer (one pixel) into four different registers; then load
             * four consecutive s16 values from the next pointer (pixel) into
             * the next lane of those four registers, etc., so that we finish
             * with v12 - v15 representing the four taps, and each lane
             * representing a separate pixel.
             *
             * The first ld4 uses a splat to avoid any false dependency on
             * the previous state of the register.
             */
            ld4r        {v12.8h,v13.8h,v14.8h,v15.8h}, [x14]
            lsr         x14, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.h,v13.h,v14.h,v15.h}[1], [x15]
            add         x14, sp, x14, LSL #(COMPONENT_SHIFT + 1)
            lsr         x15, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.h,v13.h,v14.h,v15.h}[2], [x16]
            add         x15, sp, x15, LSL #(COMPONENT_SHIFT + 1)
            lsr         x16, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.h,v13.h,v14.h,v15.h}[3], [x17]
            add         x16, sp, x16, LSL #(COMPONENT_SHIFT + 1)
            lsr         x17, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.h,v13.h,v14.h,v15.h}[4], [x14]
            add         x17, sp, x17, LSL #(COMPONENT_SHIFT + 1)
            ld4         {v12.h,v13.h,v14.h,v15.h}[5], [x15]
            ld4         {v12.h,v13.h,v14.h,v15.h}[6], [x16]
            ld4         {v12.h,v13.h,v14.h,v15.h}[7], [x17]

            smull       v8.4s, v12.4h, v0.4h
            smull2      v9.4s, v12.8h, v0.8h
            smlsl       v8.4s, v13.4h, v1.4h
            smlsl2      v9.4s, v13.8h, v1.8h
            smlsl       v8.4s, v14.4h, v2.4h
            smlsl2      v9.4s, v14.8h, v2.8h
            smlal       v8.4s, v15.4h, v3.4h
            smlal2      v9.4s, v15.8h, v3.8h

            subs        x13, x13, #LOOP_OUTPUT_SIZE

            sqrshrn     v8.4h, v8.4s, #15
            sqrshrn2    v8.8h, v9.4s, #15

            sqrshrun    v8.8b, v8.8h, #VERTBITS - 8
.elseif \comp == 2
            /* The uchar2 case:
             * This time load pairs of values into adjacent lanes in v12 - v15
             * by aliasing them as u32 data; leaving room for only four pixels,
             * so the process has to be done twice.  This also means that the
             * coefficient registers fail to align with the coefficient data
             * (eight separate pixels), so that has to be doubled-up to match.
             */
            ld4r        {v12.4s,v13.4s,v14.4s,v15.4s}, [x14]
            lsr         x14, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.s,v13.s,v14.s,v15.s}[1], [x15]
            add         x14, sp, x14, LSL #(COMPONENT_SHIFT + 1)
            lsr         x15, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.s,v13.s,v14.s,v15.s}[2], [x16]
            add         x15, sp, x15, LSL #(COMPONENT_SHIFT + 1)
            lsr         x16, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld4         {v12.s,v13.s,v14.s,v15.s}[3], [x17]
            add         x16, sp, x16, LSL #(COMPONENT_SHIFT + 1)
            lsr         x17, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3

            /* double-up coefficients to align with component pairs */
            zip1        v16.8h, v0.8h, v0.8h
            add         x17, sp, x17, LSL #(COMPONENT_SHIFT + 1)
            zip1        v17.8h, v1.8h, v1.8h
            zip1        v18.8h, v2.8h, v2.8h
            zip1        v19.8h, v3.8h, v3.8h

            smull       v8.4s, v12.4h, v16.4h
            smull2      v9.4s, v12.8h, v16.8h
            smlsl       v8.4s, v13.4h, v17.4h
            smlsl2      v9.4s, v13.8h, v17.8h
            smlsl       v8.4s, v14.4h, v18.4h
            smlsl2      v9.4s, v14.8h, v18.8h
            smlal       v8.4s, v15.4h, v19.4h
            smlal2      v9.4s, v15.8h, v19.8h

            sqrshrn     v8.4h, v8.4s, #15
            sqrshrn2    v8.8h, v9.4s, #15

            ld4r        {v12.4s,v13.4s,v14.4s,v15.4s}, [x14]
            ld4         {v12.s,v13.s,v14.s,v15.s}[1], [x15]
            ld4         {v12.s,v13.s,v14.s,v15.s}[2], [x16]
            ld4         {v12.s,v13.s,v14.s,v15.s}[3], [x17]

            /* double-up coefficients to align with component pairs */
            zip2        v16.8h, v0.8h, v0.8h
            zip2        v17.8h, v1.8h, v1.8h
            zip2        v18.8h, v2.8h, v2.8h
            zip2        v19.8h, v3.8h, v3.8h

            smull       v10.4s, v12.4h, v16.4h
            smull2      v11.4s, v12.8h, v16.8h
            smlsl       v10.4s, v13.4h, v17.4h
            smlsl2      v11.4s, v13.8h, v17.8h
            smlsl       v10.4s, v14.4h, v18.4h
            smlsl2      v11.4s, v14.8h, v18.8h
            smlal       v10.4s, v15.4h, v19.4h
            smlal2      v11.4s, v15.8h, v19.8h

            subs        x13, x13, #LOOP_OUTPUT_SIZE

            sqrshrn     v9.4h, v10.4s, #15
            sqrshrn2    v9.8h, v11.4s, #15

            sqrshrun     v8.8b, v8.8h, #VERTBITS - 8
            sqrshrun2    v8.16b, v9.8h, #VERTBITS - 8
.elseif \comp == 4
            /* The uchar4 case.
             * This case is comparatively painless because four s16s are the
             * smallest addressable unit for a vmul-by-scalar.  Rather than
             * permute the data, simply arrange the multiplies to suit the way
             * the data comes in.  That's a lot of data, though, so things
             * progress in pairs of pixels at a time.
             */
            ld1         {v12.8h,v13.8h}, [x14]
            lsr         x14, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld1         {v14.8h,v15.8h}, [x15]
            add         x14, sp, x14, LSL #(COMPONENT_SHIFT + 1)
            lsr         x15, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3

            smull       v8.4s, v12.4h, v0.h[0]
            smull       v9.4s, v14.4h, v0.h[1]
            smlsl2      v8.4s, v12.8h, v1.h[0]
            smlsl2      v9.4s, v14.8h, v1.h[1]
            smlsl       v8.4s, v13.4h, v2.h[0]
            smlsl       v9.4s, v15.4h, v2.h[1]
            smlal2      v8.4s, v13.8h, v3.h[0]
            smlal2      v9.4s, v15.8h, v3.h[1]

            /* And two more...  */
            ld1         {v12.8h,v13.8h}, [x16]
            add         x15, sp, x15, LSL #(COMPONENT_SHIFT + 1)
            lsr         x16, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3
            ld1         {v14.8h,v15.8h}, [x17]
            add         x16, sp, x16, LSL #(COMPONENT_SHIFT + 1)
            lsr         x17, x2, #(63 - CHUNKSHIFT)
            add         x2, x2, x3

            sqrshrn     v8.4h, v8.4s, #15
            add         x17, sp, x17, LSL #(COMPONENT_SHIFT + 1)
            sqrshrn2    v8.8h, v9.4s, #15

            smull       v10.4s, v12.4h, v0.h[2]
            smull       v11.4s, v14.4h, v0.h[3]
            smlsl2      v10.4s, v12.8h, v1.h[2]
            smlsl2      v11.4s, v14.8h, v1.h[3]
            smlsl       v10.4s, v13.4h, v2.h[2]
            smlsl       v11.4s, v15.4h, v2.h[3]
            smlal2      v10.4s, v13.8h, v3.h[2]
            smlal2      v11.4s, v15.8h, v3.h[3]

            sqrshrn     v9.4h, v10.4s, #15
            sqrshrn2    v9.8h, v11.4s, #15

            sqrshrun     v8.8b, v8.8h, #VERTBITS - 8
            sqrshrun2    v8.16b, v9.8h, #VERTBITS - 8

            /* And two more...  */
            ld1         {v12.8h,v13.8h}, [x14]
            ld1         {v14.8h,v15.8h}, [x15]

            smull       v10.4s, v12.4h, v0.h[4]
            smull       v11.4s, v14.4h, v0.h[5]
            smlsl2      v10.4s, v12.8h, v1.h[4]
            smlsl2      v11.4s, v14.8h, v1.h[5]
            smlsl       v10.4s, v13.4h, v2.h[4]
            smlsl       v11.4s, v15.4h, v2.h[5]
            smlal2      v10.4s, v13.8h, v3.h[4]
            smlal2      v11.4s, v15.8h, v3.h[5]

            /* And two more...  */
            ld1         {v12.8h,v13.8h}, [x16]
            ld1         {v14.8h,v15.8h}, [x17]

            subs        x13, x13, #LOOP_OUTPUT_SIZE

            sqrshrn     v9.4h, v10.4s, #15
            sqrshrn2    v9.8h, v11.4s, #15

            smull       v10.4s, v12.4h, v0.h[6]
            smull       v11.4s, v14.4h, v0.h[7]
            smlsl2      v10.4s, v12.8h, v1.h[6]
            smlsl2      v11.4s, v14.8h, v1.h[7]
            smlsl       v10.4s, v13.4h, v2.h[6]
            smlsl       v11.4s, v15.4h, v2.h[7]
            smlal2      v10.4s, v13.8h, v3.h[6]
            smlal2      v11.4s, v15.8h, v3.h[7]

            sqrshrn     v10.4h, v10.4s, #15
            sqrshrn2    v10.8h, v11.4s, #15

            sqrshrun     v9.8b, v9.8h, #VERTBITS - 8
            sqrshrun2    v9.16b, v10.8h, #VERTBITS - 8
.endif
            bgt         2b      /* continue inner loop */
            /* The inner loop has already been limited to ensure that none of
             * the earlier iterations could overfill the output, so the store
             * appears within the loop but after the conditional branch (at the
             * top).  At the end, provided it won't overfill, perform the final
             * store here.  If it would, then break out to the tricky tail case
             * instead.
             */
            blt         1f
            /* Store the amount of data appropriate to the configuration of the
             * instance being assembled.
             */
.if LOOP_OUTPUT_SIZE == 4
            st1         {v8.s}[0], [x0], #4
.elseif LOOP_OUTPUT_SIZE == 8
            st1         {v8.8b}, [x0], #8
.elseif LOOP_OUTPUT_SIZE == 16
            st1         {v8.16b}, [x0], #16
.elseif LOOP_OUTPUT_SIZE == 32
            st1         {v8.16b,v9.16b}, [x0], #32
.endif
            b           1b              /* resume outer loop */
            /* Partial tail store case:
             * Different versions of the code need different subsets of the
             * following partial stores.  Here the number of components and the
             * size of the chunk of data produced by each inner loop iteration
             * is tested to figure out whether or not each phrase is relevant.
             */
.if 16 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 16
1:          tst         x13, #16
            beq         1f
            st1         {v8.16b}, [x0], #16
            mov         v8.16b, v9.16b
.endif
.if 8 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 8
1:          tst         x13, #8
            beq         1f
            st1         {v8.8b}, [x0], #8
            ext         v8.16b, v8.16b, v8.16b, #8
.endif
.if 4 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 4
1:          tst         x13, #4
            beq         1f
            st1         {v8.s}[0], [x0], #4
            ext         v8.8b, v8.8b, v8.8b, #4
.endif
.if 2 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 2
1:          tst         x13, #2
            beq         1f
            st1         {v8.h}[0], [x0], #2
            ext         v8.8b, v8.8b, v8.8b, #2
.endif
.if 1 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 1
1:          tst         x13, #1
            beq         1f
            st1         {v8.b}[0], [x0], #1
.endif
1:
9:          mov         sp, x19
            ld1         {v8.1d - v11.1d}, [sp], #32
            ld1         {v12.1d - v15.1d}, [sp], #32
            ldr         x19, [sp], #16
            ret
END(rsdIntrinsicResizeB\comp\()_K)
.endr

.rodata
intrinsic_resize_consts:          .hword      0, 1, 2, 3, 4, 5, 6, 7
