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

#define ENTRY(f) .text; .align 4; .globl f; .type f,#function; f: .fnstart
#define END(f) .fnend; .size f, .-f;

.eabi_attribute 25,1 @Tag_ABI_align8_preserved
.arm

/* Fixed-point precision after vertical pass -- 16 bit data minus 1 sign and 1
 * integer (bicubic has a little overshoot).  It would also be possible to add
 * a temporary DC bias to eliminate the sign bit for more precision, but that's
 * extra arithmetic.
 */
.set VERTBITS, 14

/* The size of the scratch buffer in which we store our vertically convolved
 * intermediates.
 */
.set CHUNKSHIFT, 7
.set CHUNKSIZE, (1 << CHUNKSHIFT)

/* The number of components processed in a single iteration of the innermost
 * loop.
 */
.set VECSHIFT, 3
.set VECSIZE, (1<<VECSHIFT)

/* Read four different lines (except at edges where addresses may be clamped,
 * which is why we don't simply take base and stride registers), and multiply
 * and accumulate them by the coefficients in d6[0..3], leaving the results in
 * q12.  This gives eight 16-bit results representing a horizontal line of 2-8
 * input pixels (depending on number of components per pixel) to be fed into
 * the horizontal scaling pass.
 *
 * Input coefficients are 16-bit unsigned fixed-point (although [0] and [3] are
 * known to represent negative values and VMLS is used to implement this).
 * Output is VERTBITS signed fixed-point, which must leave room for a little
 * bit of overshoot beyond [0,1.0).
 */
.macro vert8, dstlo=d24, dsthi=d25
        vld1.u8     d16, [r4]!
        vld1.u8     d18, [r5]!
        vld1.u8     d20, [r6]!
        vld1.u8     d22, [r7]!
        vmovl.u8    q8, d16
        vmovl.u8    q9, d18
        vmovl.u8    q10, d20
        vmovl.u8    q11, d22
        vmull.u16   q12, d18, d6[1]
        vmull.u16   q13, d19, d6[1]
        vmlsl.u16   q12, d16, d6[0]
        vmlsl.u16   q13, d17, d6[0]
        vmlal.u16   q12, d20, d6[2]
        vmlal.u16   q13, d21, d6[2]
        vmlsl.u16   q12, d22, d6[3]
        vmlsl.u16   q13, d23, d6[3]

        /* Shift by 8 (bits per pixel), plus 16 (the fixed-point multiplies),
         * minus VERTBITS (the number of fraction bits we want to keep from
         * here on).
         */
        vqshrn.s32  \dstlo, q12, #8 + 16 - VERTBITS
        vqshrn.s32  \dsthi, q13, #8 + 16 - VERTBITS
.endm

/* As above, but only four 16-bit results into d25.
 */
.macro vert4
        vld1.u32    d16[0], [r4]!
        vld1.u32    d18[0], [r5]!
        vld1.u32    d20[0], [r6]!
        vld1.u32    d22[0], [r7]!
        vmovl.u8    q8, d16
        vmovl.u8    q9, d18
        vmovl.u8    q10, d20
        vmovl.u8    q11, d22
        vmull.u16   q12, d18, d6[1]
        vmlsl.u16   q12, d16, d6[0]
        vmlal.u16   q12, d20, d6[2]
        vmlsl.u16   q12, d22, d6[3]
        vqshrn.s32  d25, q12, #8 + 16 - VERTBITS
.endm


/* During horizontal resize having CHUNKSIZE input available means being able
 * to produce a varying amount of output, depending on the phase of the data.
 * This function calculates the minimum number of VECSIZE chunks extracted from
 * a CHUNKSIZE window (r1), and the threshold value for when the count will be
 * one higher than that (r0).
 * These work out, conveniently, to be the quotient and remainder from:
 *      (CHUNKSIZE + xinc * VECSIZE - 1) / (xinc * VECSIZE)
 *
 * The two values can be packed together in a uint64_t for convenience; and
 * they are, in fact, used this way as an arithmetic short-cut later on.
 */

/* uint64_t rsdIntrinsicResize_oscctl_K(uint32_t xinc); */
ENTRY(rsdIntrinsicResize_oscctl_K)
        lsl         r2, r0, #VECSHIFT
        movw        r0, #:lower16:(CHUNKSIZE << 16) - 1
        movt        r0, #:upper16:(CHUNKSIZE << 16) - 1
        add         r0, r0, r2
#if defined(ARCH_ARM_USE_UDIV)
        udiv        r1, r0, r2
        mls         r0, r1, r2, r0
#else
        clz         r3, r2
        clz         r1, r0
        subs        r3, r3, r1
        movlt       r3, #0
        mov         r1, #1
        lsl         r2, r2, r3
        lsl         r3, r1, r3
        mov         r1, #0
1:      cmp         r2, r0
        addls       r1, r3
        subls       r0, r2
        lsrs        r3, r3, #1
        lsr         r2, r2, #1
        bne         1b
#endif
        bx          lr
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
.set OSC_STORE, (BUFFER_SIZE + 0)
.set OSCSTEP_STORE, (BUFFER_SIZE + 4)
.set OSCCTL_STORE, (BUFFER_SIZE + 8)
.set AVAIL_STORE, (BUFFER_SIZE + 16)
.set SP_STORE, (BUFFER_SIZE + 24)   /* should be +20, but rounded up to make a legal constant somewhere */

/* void rsdIntrinsicResizeB\comp\()_K(
 *             uint8_t * restrict dst,          // r0
 *             size_t count,                    // r1
 *             uint32_t xf,                     // r2
 *             uint32_t xinc,                   // r3
 *             uint8_t const * restrict srcn,   // [sp]     -> [sp,#104] -> r4
 *             uint8_t const * restrict src0,   // [sp,#4]  -> [sp,#108] -> r5
 *             uint8_t const * restrict src1,   // [sp,#8]  -> [sp,#112] -> r6
 *             uint8_t const * restrict src2,   // [sp,#12] -> [sp,#116] -> r7
 *             size_t xclip,                    // [sp,#16] -> [sp,#120]
 *             size_t avail,                    // [sp,#20] -> [sp,#124] -> lr
 *             uint64_t osc_ctl,                // [sp,#24] -> [sp,#128]
 *             int32_t const *yr);              // [sp,#32] -> [sp,#136] -> d8 (copied to d6 for scalar access)
 */
ENTRY(rsdIntrinsicResizeB\comp\()_K)
            push        {r4,r5,r6,r7,r8,r9,r10,r11,r12,lr}
            vpush       {d8-d15}

            /* align the working buffer on the stack to make it easy to use bit
             * twiddling for address calculations and bounds tests.
             */
            sub         r12, sp, #BUFFER_SIZE + 32
            mov         lr, sp
            bfc         r12, #0, #CHUNKSHIFT + 1 + COMPONENT_SHIFT + 1
            mov         sp, r12
            str         lr, [sp,#SP_STORE]

            ldr         r8, [lr,#136]           // yr
            adr         r9, 8f
            vld1.s32    {q4}, [r8]
            vld1.s16    {q5}, [r9]
            vqmovun.s32 d8, q4                  // yr
            vdup.s16    q6, r2
            vdup.s16    q7, r3
            vmla.s16    q6, q5, q7              // vxf
            vshl.s16    q7, q7, #VECSHIFT       // vxinc

            ldrd        r4,r5, [lr,#104]        // srcn, src0
            ldrd        r6,r7, [lr,#112]        // src1, src2

            /* Compute starting condition for oscillator used to compute ahead
             * of time how many iterations are possible before needing to
             * refill the working buffer.  This is based on the fixed-point
             * index of the last element in the vector of pixels processed in
             * each iteration, counting up until it would overflow.
             */
            sub         r8, r2, r3
            mov         r9, r3, LSL #VECSHIFT
            add         r8, r8, r9

            ldrd        r10,r11, [lr,#128]      // osc_ctl

            str         r8, [sp,#OSC_STORE]
            str         r9, [sp,#OSCSTEP_STORE]
            str         r10, [sp,#OSCCTL_STORE]
            str         r11, [sp,#OSCCTL_STORE+4]
            ldrd        r10,r11, [lr,#120]      // xclip,avail


            /* r4-r7 contain pointers to the four lines of input to be
             * convolved.  These pointers have been clamped vertically and
             * horizontally (which is why it's not a simple row/stride pair),
             * and the xclip argument (now in r10) indicates how many pixels
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
            vmov        d6, d8  /* make y coeffs available for vert4 and vert8 macros */

            sub         r8, r12, r10, LSL #COMPONENT_SHIFT + 1
            add         r9, r12, #(2 * CHUNKSIZE - 4) * COMPONENT_COUNT * 2
            add         r8, r8, #4 * COMPONENT_COUNT * 2
.if \comp == 1
            vert4
            vdup.s16    d24, d25[0]
            vst1.s16    {q12}, [r12]
            vld1.s16    {d24}, [r8]
            vst1.s16    {d24}, [r9]
.elseif \comp == 2
            vert8
            vdup.u32    q11, d24[0]
            vst1.s16    {q11,q12}, [r12]
            vld1.s16    {q12}, [r8]
            vst1.s16    {q12}, [r9]
.elseif \comp == 4
            vert8       d28, d29
            vert8       d30, d31
            vmov.u64    d24, d28
            vmov.u64    d25, d28
            vmov.u64    d26, d28
            vmov.u64    d27, d28
            vst1.s16    {q12,q13}, [r12]!
            vst1.s16    {q14,q15}, [r12]
            sub         r12, r12, #32
            vld1.s16    {q11,q12}, [r8]
            vst1.s16    {q11,q12}, [r9]
.endif
            /* Count off four pixels into the working buffer, and move count to
             * its new home.
             */
            sub         lr, r11, #4
            /* Incoming pointers were to the first _legal_ pixel.  Four pixels
             * were read unconditionally, but some may have been discarded by
             * xclip, so we rewind the pointers to compensate.
             */
            sub         r4, r4, r10, LSL #COMPONENT_SHIFT
            sub         r5, r5, r10, LSL #COMPONENT_SHIFT
            sub         r6, r6, r10, LSL #COMPONENT_SHIFT
            sub         r7, r7, r10, LSL #COMPONENT_SHIFT

            /* First tap starts where we just pre-filled, at the end of the
             * buffer.
             */
            add         r2, r2, #(CHUNKSIZE * 2 - 4) << 16

            /* Use overflowing arithmetic to implement wraparound array
             * indexing.
             */
            mov         r2, r2, LSL #(15 - CHUNKSHIFT)
            mov         r3, r3, LSL #(15 - CHUNKSHIFT)

            str         lr, [sp,#AVAIL_STORE]

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
1:          ldr         lr, [sp,#AVAIL_STORE]   /* get number of pixels available */
            vmov        d6, d8              /* put y scaling coefficients somewhere handy */
            subs        lr, #CHUNKSIZE
            bge         2f                  /* if at least CHUNKSIZE are available... */
            add         lr, #CHUNKSIZE      /* if they're not... */
            b           4f
            /* ..just sneaking a literal in here after this unconditional branch.. */
8:          .hword      0, 1, 2, 3, 4, 5, 6, 7
            /* basic fill loop, processing 8 bytes at a time until there are
             * fewer than eight bytes available.
             */
3:          vert8
            sub         lr, lr, #8 / COMPONENT_COUNT
            vst1.s16    {q12}, [r12]!
4:          cmp         lr, #8 / COMPONENT_COUNT - 1
            bgt         3b
.if \comp == 4
            blt         3f
            /* The last pixel (four bytes) if necessary */
            vert4
.else
            cmp         lr, #1
            blt         3f
            /* The last pixels if necessary */
            sub         r4, r4, #8
            sub         r5, r5, #8
            sub         r6, r6, #8
            sub         r7, r7, #8
            add         r4, r4, lr, LSL #COMPONENT_SHIFT
            add         r5, r5, lr, LSL #COMPONENT_SHIFT
            add         r6, r6, lr, LSL #COMPONENT_SHIFT
            add         r7, r7, lr, LSL #COMPONENT_SHIFT
            vert8
            sub         lr, sp, lr, LSL #COMPONENT_SHIFT + 1
            sub         sp, sp, #32
            sub         lr, lr, #16
.if \comp == 1
            vdup.s16    q13, d25[3]
.elseif \comp == 2
            vdup.u32    q13, d25[1]
.endif
            vst1.s16    {q12,q13}, [sp]
            vld1.s16    {q12}, [lr]
            add         sp, sp, #32
            b           4f
.endif
            /* Keep filling until we get to the end of this chunk of the buffer */
3:
.if \comp == 1
            vdup.s16    q12, d25[3]
.elseif \comp == 2
            vdup.u32    q12, d25[1]
.elseif \comp == 4
            vmov.u64    d24, d25
.endif
4:          vst1.s16    {q12}, [r12]!
            tst         r12, #(CHUNKSIZE - 1) * COMPONENT_COUNT * 2
            bne         3b
            b           4f

.align 4
2:          /* Quickly pull a chunk of data into the working buffer.
             */
            vert8
            vst1.s16    {q12}, [r12]!
            vert8
            vst1.s16    {q12}, [r12]!
            tst         r12, #(CHUNKSIZE - 1) * COMPONENT_COUNT * 2
            bne         2b
            cmp         lr, #0
            bne         3f
4:          /* if we end with 0 pixels left we'll have nothing handy to spread
             * across to the right, so we rewind a bit.
             */
            mov         lr, #1
            sub         r4, r4, #COMPONENT_COUNT
            sub         r5, r5, #COMPONENT_COUNT
            sub         r6, r6, #COMPONENT_COUNT
            sub         r7, r7, #COMPONENT_COUNT
3:          str         lr, [sp,#AVAIL_STORE]       /* done with available pixel count */
            add         lr, sp, #OSC_STORE
            ldrd        r8,r9, [lr,#0]              /* need osc, osc_step soon */
            ldrd        r10,r11, [lr,#OSCCTL_STORE-OSC_STORE] /* need osc_ctl too */

            /* copy four taps (width of cubic window) to far end for overflow
             * address handling
             */
            sub         lr, r12, #CHUNKSIZE * COMPONENT_COUNT * 2
            eor         r12, lr, #CHUNKSIZE * COMPONENT_COUNT * 2
.if \comp == 1
            vld1.s16    {d28}, [lr]
.elseif \comp == 2
            vld1.s16    {q14}, [lr]
.elseif \comp == 4
            vld1.s16    {q14,q15}, [lr]
.endif
            add         lr, r12, #CHUNKSIZE * COMPONENT_COUNT * 2
.if \comp == 1
            vst1.s16    {d28}, [lr]
.elseif \comp == 2
            vst1.s16    {q14}, [lr]
.elseif \comp == 4
            vst1.s16    {q14,q15}, [lr]
.endif
            /* r11 contains the maximum possible iteration count, but if r8 is
             * greater than r10 then this indicates that the count must be
             * reduced by one for this iteration to avoid reading past the end
             * of the available data.
             */
            cmp             r10, r8
            sbc         lr, r11, #0

            mla         r8, lr, r9, r8
            sub         r8, r8, #(CHUNKSIZE << 16)

            str         r8, [sp,#OSC_STORE]         /* done with osc */

            /* prefer to count pixels, rather than vectors, to clarify the tail
             * store case on exit.
             */
            mov         lr, lr, LSL #VECSHIFT
            cmp         lr, r1
            movgt       lr, r1

            sub         r1, r1, lr

            mov         lr, lr, LSL #COMPONENT_SHIFT

            vmov.i16    d10, #3
            vmov.i16    d11, #0x8000

            cmp         lr, #0
            bgt         3f
            cmp         r1, #0
            bgt         1b     /* an extreme case where we shouldn't use code in this structure */
            b           9f

            .align 4
2:          /* Inner loop continues here, but starts at 3:, see end of loop
             * below for explanation. */
.if LOOP_OUTPUT_SIZE == 4
            vst1.u32    {d16[0]}, [r0]!
.elseif LOOP_OUTPUT_SIZE == 8
            vst1.u8     {d16}, [r0]!
.elseif LOOP_OUTPUT_SIZE == 16
            vst1.u8     {q8}, [r0]!
.elseif LOOP_OUTPUT_SIZE == 32
            vst1.u8     {q8,q9}, [r0]!
.endif
            /* Inner loop:  here the four x coefficients for each tap are
             * calculated in vector code, and the addresses are calculated in
             * scalar code, and these calculations are interleaved.
             */
3:          vshr.u16    q8, q6, #1
            mov         r8, r2, LSR #(31 - CHUNKSHIFT)
            vqrdmulh.s16 q9, q8, q8
            add         r2, r2, r3
            vqrdmulh.s16 q10, q9, q8
            mov         r9, r2, LSR #(31 - CHUNKSHIFT)
            vshll.s16   q11, d18, #2
            vshll.s16   q12, d19, #2
            add         r2, r2, r3
            vmlsl.s16   q11, d20, d10
            vmlsl.s16   q12, d21, d10
            mov         r10, r2, LSR #(31 - CHUNKSHIFT)

            vhadd.s16   q0, q10, q8
            add         r2, r2, r3
            vsub.s16    q0, q9, q0
            mov         r11, r2, LSR #(31 - CHUNKSHIFT)

            vaddw.s16   q1, q11, d18
            vaddw.s16   q13, q12, d19
            add         r2, r2, r3
            vshrn.s32   d2, q1, #1
            vshrn.s32   d3, q13, #1
            add         r8, sp, r8, LSL #(COMPONENT_SHIFT + 1)
            vsub.s16    d2, d2, d11
            vsub.s16    d3, d3, d11 // TODO: find a wider d11 and use q-reg operation
            add         r9, sp, r9, LSL #(COMPONENT_SHIFT + 1)

            vaddw.s16   q2, q11, d16
            vaddw.s16   q13, q12, d17
            add         r10, sp, r10, LSL #(COMPONENT_SHIFT + 1)
            vshrn.s32   d4, q2, #1
            vshrn.s32   d5, q13, #1
            add         r11, sp, r11, LSL #(COMPONENT_SHIFT + 1)
            vneg.s16    q2, q2

            vhsub.s16   q3, q10, q9

            /* increment the x fractional parts (oveflow is ignored, as the
             * scalar arithmetic shadows this addition with full precision).
             */
            vadd.s16    q6, q6, q7

            /* At this point we have four pointers in r8-r11, pointing to the
             * four taps in the scratch buffer that must be convolved together
             * to produce an output pixel (one output pixel per pointer).
             * These pointers usually overlap, but their spacing is irregular
             * so resolving the redundancy through L1 is a pragmatic solution.
             *
             * The scratch buffer is made of signed 16-bit data, holding over
             * some extra precision, and overshoot, from the vertical pass.
             *
             * We also have the 16-bit unsigned fixed-point weights for each
             * of the four taps in q0 - q3.  That's eight pixels worth of
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
             * Issue one lanewise vld4.s16 to load four consecutive pixels from
             * one pointer (one pixel) into four different registers; then load
             * four consecutive s16 values from the next pointer (pixel) into
             * the next lane of those four registers, etc., so that we finish
             * with q12 - q15 representing the four taps, and each lane
             * representing a separate pixel.
             *
             * The first vld4 uses a splat to avoid any false dependency on
             * the previous state of the register.
             */
            vld4.s16    {d24[],d26[],d28[],d30[]}, [r8]
            mov         r8, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.s16    {d24[1],d26[1],d28[1],d30[1]}, [r9]
            add         r8, sp, r8, LSL #(COMPONENT_SHIFT + 1)
            mov         r9, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.s16    {d24[2],d26[2],d28[2],d30[2]}, [r10]
            add         r9, sp, r9, LSL #(COMPONENT_SHIFT + 1)
            mov         r10, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.s16    {d24[3],d26[3],d28[3],d30[3]}, [r11]
            add         r10, sp, r10, LSL #(COMPONENT_SHIFT + 1)
            mov         r11, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.s16    {d25[],d27[],d29[],d31[]}, [r8]
            add         r11, sp, r11, LSL #(COMPONENT_SHIFT + 1)
            vld4.s16    {d25[1],d27[1],d29[1],d31[1]}, [r9]
            vld4.s16    {d25[2],d27[2],d29[2],d31[2]}, [r10]
            vld4.s16    {d25[3],d27[3],d29[3],d31[3]}, [r11]

            vmull.s16   q8, d24, d0
            vmull.s16   q9, d25, d1
            vmlsl.s16   q8, d26, d2
            vmlsl.s16   q9, d27, d3
            vmlsl.s16   q8, d28, d4
            vmlsl.s16   q9, d29, d5
            vmlal.s16   q8, d30, d6
            vmlal.s16   q9, d31, d7

            subs        lr, lr, #LOOP_OUTPUT_SIZE

            vqrshrn.s32 d16, q8, #15
            vqrshrn.s32 d17, q9, #15

            vqrshrun.s16 d16, q8, #VERTBITS - 8
.elseif \comp == 2
            /* The uchar2 case:
             * This time load pairs of values into adjacent lanes in q12 - q15
             * by aliasing them as u32 data; leaving room for only four pixels,
             * so the process has to be done twice.  This also means that the
             * coefficient registers fail to align with the coefficient data
             * (eight separate pixels), so that has to be doubled-up to match.
             */
            vld4.u32    {d24[],d26[],d28[],d30[]}, [r8]
            mov         r8, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.u32    {d24[1],d26[1],d28[1],d30[1]}, [r9]
            add         r8, sp, r8, LSL #(COMPONENT_SHIFT + 1)
            mov         r9, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.u32    {d25[],d27[],d29[],d31[]}, [r10]
            add         r9, sp, r9, LSL #(COMPONENT_SHIFT + 1)
            mov         r10, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld4.u32    {d25[1],d27[1],d29[1],d31[1]}, [r11]
            add         r10, sp, r10, LSL #(COMPONENT_SHIFT + 1)
            mov         r11, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3

            /* double-up coefficients to align with component pairs */
            vmov        d20, d0
            add         r11, sp, r11, LSL #(COMPONENT_SHIFT + 1)
            vmov        d21, d2
            vmov        d22, d4
            vmov        d23, d6
            vzip.s16    d0, d20
            vzip.s16    d2, d21
            vzip.s16    d4, d22
            vzip.s16    d6, d23

            vmull.s16   q8, d24, d0
            vmull.s16   q9, d25, d20
            vmlsl.s16   q8, d26, d2
            vmlsl.s16   q9, d27, d21
            vmlsl.s16   q8, d28, d4
            vmlsl.s16   q9, d29, d22
            vmlal.s16   q8, d30, d6
            vmlal.s16   q9, d31, d23

            vqrshrn.s32 d16, q8, #15
            vqrshrn.s32 d17, q9, #15

            vld4.u32    {d24[],d26[],d28[],d30[]}, [r8]
            vld4.u32    {d24[1],d26[1],d28[1],d30[1]}, [r9]
            vld4.u32    {d25[],d27[],d29[],d31[]}, [r10]
            vld4.u32    {d25[1],d27[1],d29[1],d31[1]}, [r11]

            /* double-up coefficients to align with component pairs */
            vmov        d0, d1
            vmov        d2, d3
            vmov        d4, d5
            vmov        d6, d7
            vzip.s16    d0, d1
            vzip.s16    d2, d3
            vzip.s16    d4, d5
            vzip.s16    d6, d7

            vmull.s16   q10, d24, d0
            vmull.s16   q11, d25, d1
            vmlsl.s16   q10, d26, d2
            vmlsl.s16   q11, d27, d3
            vmlsl.s16   q10, d28, d4
            vmlsl.s16   q11, d29, d5
            vmlal.s16   q10, d30, d6
            vmlal.s16   q11, d31, d7

            subs        lr, lr, #LOOP_OUTPUT_SIZE

            vqrshrn.s32 d18, q10, #15
            vqrshrn.s32 d19, q11, #15

            vqrshrun.s16 d16, q8, #VERTBITS - 8
            vqrshrun.s16 d17, q9, #VERTBITS - 8
.elseif \comp == 4
            /* The uchar4 case.
             * This case is comparatively painless because four s16s are the
             * smallest addressable unit for a vmul-by-scalar.  Rather than
             * permute the data, simply arrange the multiplies to suit the way
             * the data comes in.  That's a lot of data, though, so things
             * progress in pairs of pixels at a time.
             */
            vld1.s16    {q12,q13}, [r8]
            mov         r8, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld1.s16    {q14,q15}, [r9]
            add         r8, sp, r8, LSL #(COMPONENT_SHIFT + 1)
            mov         r9, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3

            vmull.s16   q8, d24, d0[0]
            vmull.s16   q9, d28, d0[1]
            vmlsl.s16   q8, d25, d2[0]
            vmlsl.s16   q9, d29, d2[1]
            vmlsl.s16   q8, d26, d4[0]
            vmlsl.s16   q9, d30, d4[1]
            vmlal.s16   q8, d27, d6[0]
            vmlal.s16   q9, d31, d6[1]

            /* And two more...  */
            vld1.s16    {q12,q13}, [r10]
            add         r9, sp, r9, LSL #(COMPONENT_SHIFT + 1)
            mov         r10, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3
            vld1.s16    {q14,q15}, [r11]
            add         r10, sp, r10, LSL #(COMPONENT_SHIFT + 1)
            mov         r11, r2, LSR #(31 - CHUNKSHIFT)
            add         r2, r2, r3

            vqrshrn.s32 d16, q8, #15
            add         r11, sp, r11, LSL #(COMPONENT_SHIFT + 1)
            vqrshrn.s32 d17, q9, #15

            vmull.s16   q10, d24, d0[2]
            vmull.s16   q11, d28, d0[3]
            vmlsl.s16   q10, d25, d2[2]
            vmlsl.s16   q11, d29, d2[3]
            vmlsl.s16   q10, d26, d4[2]
            vmlsl.s16   q11, d30, d4[3]
            vmlal.s16   q10, d27, d6[2]
            vmlal.s16   q11, d31, d6[3]

            vqrshrn.s32 d18, q10, #15
            vqrshrn.s32 d19, q11, #15

            vqrshrun.s16 d16, q8, #VERTBITS - 8
            vqrshrun.s16 d17, q9, #VERTBITS - 8

            /* And two more...  */
            vld1.s16    {q12,q13}, [r8]
            vld1.s16    {q14,q15}, [r9]

            vmull.s16   q10, d24, d1[0]
            vmull.s16   q11, d28, d1[1]
            vmlsl.s16   q10, d25, d3[0]
            vmlsl.s16   q11, d29, d3[1]
            vmlsl.s16   q10, d26, d5[0]
            vmlsl.s16   q11, d30, d5[1]
            vmlal.s16   q10, d27, d7[0]
            vmlal.s16   q11, d31, d7[1]

            /* And two more...  */
            vld1.s16    {q12,q13}, [r10]
            vld1.s16    {q14,q15}, [r11]

            subs        lr, lr, #LOOP_OUTPUT_SIZE

            vqrshrn.s32 d18, q10, #15
            vqrshrn.s32 d19, q11, #15

            vmull.s16   q10, d24, d1[2]
            vmull.s16   q11, d28, d1[3]
            vmlsl.s16   q10, d25, d3[2]
            vmlsl.s16   q11, d29, d3[3]
            vmlsl.s16   q10, d26, d5[2]
            vmlsl.s16   q11, d30, d5[3]
            vmlal.s16   q10, d27, d7[2]
            vmlal.s16   q11, d31, d7[3]

            vqrshrn.s32 d20, q10, #15
            vqrshrn.s32 d21, q11, #15

            vqrshrun.s16 d18, q9, #VERTBITS - 8
            vqrshrun.s16 d19, q10, #VERTBITS - 8
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
            vst1.u32    {d16[0]}, [r0]!
.elseif LOOP_OUTPUT_SIZE == 8
            vst1.u8     {d16}, [r0]!
.elseif LOOP_OUTPUT_SIZE == 16
            vst1.u8     {q8}, [r0]!
.elseif LOOP_OUTPUT_SIZE == 32
            vst1.u8     {q8,q9}, [r0]!
.endif
            b           1b              /* resume outer loop */
            /* Partial tail store case:
             * Different versions of the code need different subsets of the
             * following partial stores.  Here the number of components and the
             * size of the chunk of data produced by each inner loop iteration
             * is tested to figure out whether or not each phrase is relevant.
             */
.if 16 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 16
1:          tst         lr, #16
            beq         1f
            vst1.u8     {q8}, [r0]!
            vmov        q8, q9
.endif
.if 8 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 8
1:          tst         lr, #8
            beq         1f
            vst1.u8     {d16}, [r0]!
            vmov.u8     d16, d17
.endif
.if 4 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 4
1:          tst         lr, #4
            beq         1f
            vst1.u32    {d16[0]}, [r0]!
            vext.u32    d16, d16, d16, #1
.endif
.if 2 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 2
1:          tst         lr, #2
            beq         1f
            vst1.u16    {d16[0]}, [r0]!
            vext.u16    d16, d16, d16, #1
.endif
.if 1 < LOOP_OUTPUT_SIZE && COMPONENT_COUNT <= 1
1:          tst         lr, #1
            beq         1f
            vst1.u8     {d16[0]}, [r0]!
.endif
1:
9:          ldr         sp, [sp,#SP_STORE]
            vpop        {d8-d15}
            pop         {r4,r5,r6,r7,r8,r9,r10,r11,r12,pc}
END(rsdIntrinsicResizeB\comp\()_K)
.endr
