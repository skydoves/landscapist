/*
 * Copyright (C) 2013-2014 The Android Open Source Project
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

#define BLEND_LIST(X) \
    X(0, CLEAR) \
    X(1, SRC) \
    X(2, DST) \
    X(3, SRC_OVER) \
    X(4, DST_OVER) \
    X(5, SRC_IN) \
    X(6, DST_IN) \
    X(7, SRC_OUT) \
    X(8, DST_OUT) \
    X(9, SRC_ATOP) \
    X(10, DST_ATOP) \
    X(11, XOR) \
    X(12, MULTIPLY) \
    X(13, ADD) \
    X(14, SUBTRACT)

/* This operation was not enabled in the original RenderScript. We could
 * enable it.
 *
 *  X(15, DIFFERENCE) \
 */

/* For every blend operation supported, define a macro with just the arithmetic
 * component.  The rest can be handled later on.
 *
 * At entry q0-q3 contain the RGBA data from the destination buffer, and q8-q11
 * contain the data from the source buffer.  Both have already been split out
 * into one colour component per register (if necessary).  q3 and q11 contain
 * the alpha components.
 *
 * At the same time as defining the assembly macro, define a corresponding
 * preprocessor macro indicating any other requirements.
 *    zipped=0 -- The macro does not require the RGBA components to be
 *                separated.
 *    lddst=0  -- The macro does not require data from the destination buffer.
 *    ldsrc=0  -- The macro does not require data from the source buffer.
 *    nowrap=1 -- The macro requires no wrapper at all, and should simply be
 *                inserted without any surrounding load/store or loop code.
 */

#define params_CLEAR zipped=0, lddst=0, ldsrc=0
.macro blend_kernel_CLEAR
        movi    v0.16b, #0
        movi    v1.16b, #0
        movi    v2.16b, #0
        movi    v3.16b, #0
.endm

#define params_SRC zipped=0, lddst=0
.macro blend_kernel_SRC
        mov     v0.16b, v8.16b
        mov     v1.16b, v9.16b
        mov     v2.16b, v10.16b
        mov     v3.16b, v11.16b
.endm

#define params_DST nowrap=1
.macro blend_kernel_DST
        /* nop */
.endm

#define params_SRC_OVER zipped=1
.macro blend_kernel_SRC_OVER
        mvn         v7.16b, v11.16b

        umull2      v12.8h, v7.16b, v0.16b
        umull       v0.8h,  v7.8b,  v0.8b
        umull2      v13.8h, v7.16b, v1.16b
        umull       v1.8h,  v7.8b,  v1.8b
        umull2      v14.8h, v7.16b, v2.16b
        umull       v2.8h,  v7.8b,  v2.8b
        umull2      v15.8h, v7.16b, v3.16b
        umull       v3.8h,  v7.8b,  v3.8b

        rshrn       v4.8b,  v0.8h,  #8
        rshrn2      v4.16b, v12.8h, #8
        rshrn       v5.8b,  v1.8h,  #8
        rshrn2      v5.16b, v13.8h, #8
        rshrn       v6.8b,  v2.8h,  #8
        rshrn2      v6.16b, v14.8h, #8
        rshrn       v7.8b,  v3.8h,  #8
        rshrn2      v7.16b, v15.8h, #8

        uaddw       v0.8h,  v0.8h,  v4.8b
        uaddw2      v12.8h, v12.8h, v4.16b
        uaddw       v1.8h,  v1.8h,  v5.8b
        uaddw2      v13.8h, v13.8h, v5.16b
        uaddw       v2.8h,  v2.8h,  v6.8b
        uaddw2      v14.8h, v14.8h, v6.16b
        uaddw       v3.8h,  v3.8h,  v7.8b
        uaddw2      v15.8h, v15.8h, v7.16b

        rshrn       v0.8b,  v0.8h,  #8
        rshrn2      v0.16b, v12.8h, #8
        rshrn       v1.8b,  v1.8h,  #8
        rshrn2      v1.16b, v13.8h, #8
        rshrn       v2.8b,  v2.8h,  #8
        rshrn2      v2.16b, v14.8h, #8
        rshrn       v3.8b,  v3.8h,  #8
        rshrn2      v3.16b, v15.8h, #8

        uqadd       v0.16b, v0.16b, v8.16b
        uqadd       v1.16b, v1.16b, v9.16b
        uqadd       v2.16b, v2.16b, v10.16b
        uqadd       v3.16b, v3.16b, v11.16b
.endm

#define params_DST_OVER zipped=1
.macro blend_kernel_DST_OVER
        mvn         v7.16b, v3.16b

        umull2      v12.8h, v7.16b, v8.16b
        umull       v8.8h,  v7.8b,  v8.8b
        umull2      v13.8h, v7.16b, v9.16b
        umull       v9.8h,  v7.8b,  v9.8b
        umull2      v14.8h, v7.16b, v10.16b
        umull       v10.8h, v7.8b,  v10.8b
        umull2      v15.8h, v7.16b, v11.16b
        umull       v11.8h, v7.8b,  v11.8b

        rshrn       v4.8b,  v8.8h,  #8
        rshrn2      v4.16b, v12.8h, #8
        rshrn       v5.8b,  v9.8h,  #8
        rshrn2      v5.16b, v13.8h, #8
        rshrn       v6.8b,  v10.8h, #8
        rshrn2      v6.16b, v14.8h, #8
        rshrn       v7.8b,  v11.8h, #8
        rshrn2      v7.16b, v15.8h, #8

        uaddw       v8.8h,  v8.8h,  v4.8b
        uaddw2      v12.8h, v12.8h, v4.16b
        uaddw       v9.8h,  v9.8h,  v5.8b
        uaddw2      v13.8h, v13.8h, v5.16b
        uaddw       v10.8h, v10.8h, v6.8b
        uaddw2      v14.8h, v14.8h, v6.16b
        uaddw       v11.8h, v11.8h, v7.8b
        uaddw2      v15.8h, v15.8h, v7.16b

        rshrn       v8.8b,  v8.8h,  #8
        rshrn2      v8.16b, v12.8h, #8
        rshrn       v9.8b,  v9.8h,  #8
        rshrn2      v9.16b, v13.8h, #8
        rshrn       v10.8b,  v10.8h, #8
        rshrn2      v10.16b, v14.8h, #8
        rshrn       v11.8b,  v11.8h, #8
        rshrn2      v11.16b, v15.8h, #8

        uqadd       v0.16b, v0.16b, v8.16b
        uqadd       v1.16b, v1.16b, v9.16b
        uqadd       v2.16b, v2.16b, v10.16b
        uqadd       v3.16b, v3.16b, v11.16b
.endm

#define params_SRC_IN zipped=1
.macro blend_kernel_SRC_IN
        umull2      v12.8h, v3.16b, v8.16b
        umull       v0.8h,  v3.8b,  v8.8b
        umull2      v13.8h, v3.16b, v9.16b
        umull       v1.8h,  v3.8b,  v9.8b
        umull2      v14.8h, v3.16b, v10.16b
        umull       v2.8h,  v3.8b,  v10.8b
        umull2      v15.8h, v3.16b, v11.16b
        umull       v3.8h,  v3.8b,  v11.8b

        rshrn       v4.8b,  v0.8h,  #8
        rshrn2      v4.16b, v12.8h, #8
        rshrn       v5.8b,  v1.8h,  #8
        rshrn2      v5.16b, v13.8h, #8
        rshrn       v6.8b,  v2.8h,  #8
        rshrn2      v6.16b, v14.8h, #8
        rshrn       v7.8b,  v3.8h,  #8
        rshrn2      v7.16b, v15.8h, #8

        uaddw       v0.8h,  v0.8h,  v4.8b
        uaddw2      v12.8h, v12.8h, v4.16b
        uaddw       v1.8h,  v1.8h,  v5.8b
        uaddw2      v13.8h, v13.8h, v5.16b
        uaddw       v2.8h,  v2.8h,  v6.8b
        uaddw2      v14.8h, v14.8h, v6.16b
        uaddw       v3.8h,  v3.8h,  v7.8b
        uaddw2      v15.8h, v15.8h, v7.16b

        rshrn       v0.8b,  v0.8h,  #8
        rshrn2      v0.16b, v12.8h, #8
        rshrn       v1.8b,  v1.8h,  #8
        rshrn2      v1.16b, v13.8h, #8
        rshrn       v2.8b,  v2.8h,  #8
        rshrn2      v2.16b, v14.8h, #8
        rshrn       v3.8b,  v3.8h,  #8
        rshrn2      v3.16b, v15.8h, #8
.endm

#define params_DST_IN zipped=1
.macro blend_kernel_DST_IN
        umull2      v12.8h, v0.16b, v11.16b
        umull       v0.8h,  v0.8b,  v11.8b
        umull2      v13.8h, v1.16b, v11.16b
        umull       v1.8h,  v1.8b,  v11.8b
        umull2      v14.8h, v2.16b, v11.16b
        umull       v2.8h,  v2.8b,  v11.8b
        umull2      v15.8h, v3.16b, v11.16b
        umull       v3.8h,  v3.8b,  v11.8b

        rshrn       v4.8b,  v0.8h,  #8
        rshrn2      v4.16b, v12.8h, #8
        rshrn       v5.8b,  v1.8h,  #8
        rshrn2      v5.16b, v13.8h, #8
        rshrn       v6.8b,  v2.8h,  #8
        rshrn2      v6.16b, v14.8h, #8
        rshrn       v7.8b,  v3.8h,  #8
        rshrn2      v7.16b, v15.8h, #8

        uaddw       v0.8h,  v0.8h,  v4.8b
        uaddw2      v12.8h, v12.8h, v4.16b
        uaddw       v1.8h,  v1.8h,  v5.8b
        uaddw2      v13.8h, v13.8h, v5.16b
        uaddw       v2.8h,  v2.8h,  v6.8b
        uaddw2      v14.8h, v14.8h, v6.16b
        uaddw       v3.8h,  v3.8h,  v7.8b
        uaddw2      v15.8h, v15.8h, v7.16b

        rshrn       v0.8b,  v0.8h,  #8
        rshrn2      v0.16b, v12.8h, #8
        rshrn       v1.8b,  v1.8h,  #8
        rshrn2      v1.16b, v13.8h, #8
        rshrn       v2.8b,  v2.8h,  #8
        rshrn2      v2.16b, v14.8h, #8
        rshrn       v3.8b,  v3.8h,  #8
        rshrn2      v3.16b, v15.8h, #8
.endm

#define params_SRC_OUT zipped=1
.macro blend_kernel_SRC_OUT
        mvn         v3.16b, v3.16b
        blend_kernel_SRC_IN
.endm


#define params_DST_OUT zipped=1
.macro blend_kernel_DST_OUT
        mvn         v11.16b, v11.16b
        blend_kernel_DST_IN
.endm

#define params_SRC_ATOP zipped=1
.macro blend_kernel_SRC_ATOP
        mvn         v11.16b, v11.16b

        umull2      v12.8h, v11.16b, v0.16b
        umull       v0.8h,  v11.8b,  v0.8b
        umull2      v13.8h, v11.16b, v1.16b
        umull       v1.8h,  v11.8b,  v1.8b
        umull2      v14.8h, v11.16b, v2.16b
        umull       v2.8h,  v11.8b,  v2.8b

        umull2      v4.8h,  v3.16b, v8.16b
        umull       v8.8h,  v3.8b,  v8.8b
        umull2      v5.8h,  v3.16b, v9.16b
        umull       v9.8h,  v3.8b,  v9.8b
        umull2      v6.8h,  v3.16b, v10.16b
        umull       v10.8h, v3.8b,  v10.8b

        uqadd       v12.8h, v12.8h, v4.8h
        uqadd       v0.8h,  v0.8h,  v8.8h
        uqadd       v13.8h, v13.8h, v5.8h
        uqadd       v1.8h,  v1.8h,  v9.8h
        uqadd       v14.8h, v14.8h, v6.8h
        uqadd       v2.8h,  v2.8h,  v10.8h

        urshr       v8.8h,  v0.8h,  #8
        urshr       v4.8h,  v12.8h, #8
        urshr       v9.8h,  v1.8h,  #8
        urshr       v5.8h,  v13.8h, #8
        urshr       v10.8h, v2.8h,  #8
        urshr       v6.8h,  v14.8h, #8

        uqadd       v0.8h,  v0.8h,  v8.8h
        uqadd       v12.8h, v12.8h, v4.8h
        uqadd       v1.8h,  v1.8h,  v9.8h
        uqadd       v13.8h, v13.8h, v5.8h
        uqadd       v2.8h,  v2.8h,  v10.8h
        uqadd       v14.8h, v14.8h, v6.8h

        uqrshrn     v0.8b,  v0.8h,  #8
        uqrshrn2    v0.16b, v12.8h, #8
        uqrshrn     v1.8b,  v1.8h,  #8
        uqrshrn2    v1.16b, v13.8h, #8
        uqrshrn     v2.8b,  v2.8h,  #8
        uqrshrn2    v2.16b, v14.8h, #8
.endm

#define params_DST_ATOP zipped=1
.macro blend_kernel_DST_ATOP
        mvn         v3.16b, v3.16b

        umull2      v12.8h, v11.16b, v0.16b
        umull       v0.8h,  v11.8b,  v0.8b
        umull2      v13.8h, v11.16b, v1.16b
        umull       v1.8h,  v11.8b,  v1.8b
        umull2      v14.8h, v11.16b, v2.16b
        umull       v2.8h,  v11.8b,  v2.8b

        umull2      v4.8h,  v3.16b, v8.16b
        umull       v8.8h,  v3.8b,  v8.8b
        umull2      v5.8h,  v3.16b, v9.16b
        umull       v9.8h,  v3.8b,  v9.8b
        umull2      v6.8h,  v3.16b, v10.16b
        umull       v10.8h, v3.8b,  v10.8b

        uqadd       v12.8h, v12.8h, v4.8h
        uqadd       v0.8h,  v0.8h,  v8.8h
        uqadd       v13.8h, v13.8h, v5.8h
        uqadd       v1.8h,  v1.8h,  v9.8h
        uqadd       v14.8h, v14.8h, v6.8h
        uqadd       v2.8h,  v2.8h,  v10.8h

        urshr       v8.8h,  v0.8h,  #8
        urshr       v4.8h,  v12.8h, #8
        urshr       v9.8h,  v1.8h,  #8
        urshr       v5.8h,  v13.8h, #8
        urshr       v10.8h, v2.8h,  #8
        urshr       v6.8h,  v14.8h, #8

        uqadd       v0.8h,  v0.8h,  v8.8h
        uqadd       v12.8h, v12.8h, v4.8h
        uqadd       v1.8h,  v1.8h,  v9.8h
        uqadd       v13.8h, v13.8h, v5.8h
        uqadd       v2.8h,  v2.8h,  v10.8h
        uqadd       v14.8h, v14.8h, v6.8h

        uqrshrn     v0.8b,  v0.8h,  #8
        uqrshrn2    v0.16b, v12.8h, #8
        uqrshrn     v1.8b,  v1.8h,  #8
        uqrshrn2    v1.16b, v13.8h, #8
        uqrshrn     v2.8b,  v2.8h,  #8
        uqrshrn2    v2.16b, v14.8h, #8

        mov         v3.16b, v11.16b
.endm

#define params_MULTIPLY zipped=0
.macro blend_kernel_MULTIPLY
        umull2      v12.8h, v0.16b, v8.16b
        umull       v0.8h,  v0.8b,  v8.8b
        umull2      v13.8h, v1.16b, v9.16b
        umull       v1.8h,  v1.8b,  v9.8b
        umull2      v14.8h, v2.16b, v10.16b
        umull       v2.8h,  v2.8b,  v10.8b
        umull2      v15.8h, v3.16b, v11.16b
        umull       v3.8h,  v3.8b,  v11.8b

        rshrn       v4.8b,  v0.8h,  #8
        rshrn2      v4.16b, v12.8h, #8
        rshrn       v5.8b,  v1.8h,  #8
        rshrn2      v5.16b, v13.8h, #8
        rshrn       v6.8b,  v2.8h,  #8
        rshrn2      v6.16b, v14.8h, #8
        rshrn       v7.8b,  v3.8h,  #8
        rshrn2      v7.16b, v15.8h, #8

        uaddw       v0.8h,  v0.8h,  v4.8b
        uaddw2      v12.8h, v12.8h, v4.16b
        uaddw       v1.8h,  v1.8h,  v5.8b
        uaddw2      v13.8h, v13.8h, v5.16b
        uaddw       v2.8h,  v2.8h,  v6.8b
        uaddw2      v14.8h, v14.8h, v6.16b
        uaddw       v3.8h,  v3.8h,  v7.8b
        uaddw2      v15.8h, v15.8h, v7.16b

        rshrn       v0.8b,  v0.8h,  #8
        rshrn2      v0.16b, v12.8h, #8
        rshrn       v1.8b,  v1.8h,  #8
        rshrn2      v1.16b, v13.8h, #8
        rshrn       v2.8b,  v2.8h,  #8
        rshrn2      v2.16b, v14.8h, #8
        rshrn       v3.8b,  v3.8h,  #8
        rshrn2      v3.16b, v15.8h, #8
.endm

#define params_ADD zipped=0
.macro blend_kernel_ADD
        uqadd    v0.16b, v0.16b, v8.16b
        uqadd    v1.16b, v1.16b, v9.16b
        uqadd    v2.16b, v2.16b, v10.16b
        uqadd    v3.16b, v3.16b, v11.16b
.endm

#define params_SUBTRACT zipped=0
.macro blend_kernel_SUBTRACT
        uqsub    v0.16b, v0.16b, v8.16b
        uqsub    v1.16b, v1.16b, v9.16b
        uqsub    v2.16b, v2.16b, v10.16b
        uqsub    v3.16b, v3.16b, v11.16b
.endm

#define params_DIFFERENCE zipped=0
.macro blend_kernel_DIFFERENCE
        uabd    v0.16b, v0.16b, v8.16b
        uabd    v1.16b, v1.16b, v9.16b
        uabd    v2.16b, v2.16b, v10.16b
        uabd    v3.16b, v3.16b, v11.16b
.endm

#define params_XOR zipped=0
.macro blend_kernel_XOR
        eor     v0.16b, v0.16b, v8.16b
        eor     v1.16b, v1.16b, v9.16b
        eor     v2.16b, v2.16b, v10.16b
        eor     v3.16b, v3.16b, v11.16b
.endm


/* Define the wrapper code which will load and store the data, iterate the
 * correct number of times, and safely handle the remainder at the end of the
 * loop.  Various sections of assembly code are dropped or substituted for
 * simpler operations if they're not needed.
 */
.macro wrap_line kernel, nowrap=0, zipped=1, lddst=1, ldsrc=1, pld=1
.if \nowrap
        \kernel
.else
        sub     x3, sp, #32
        sub     sp, sp, #64
        st1     {v8.1d - v11.1d}, [sp]
        st1     {v12.1d - v15.1d}, [x3]
        subs    x2, x2, #64
        b       2f
.align 4
1:
  .if \lddst
    .if \zipped
        ld4     {v0.16b - v3.16b}, [x0]
    .else
        ld1     {v0.16b - v3.16b}, [x0]
    .endif
  .endif
  .if \ldsrc
    .if \zipped
        ld4     {v8.16b - v11.16b}, [x1], #64
    .else
        ld1     {v8.16b - v11.16b}, [x1], #64
    .endif
  .endif
  .if \pld
#if 0 /* TODO: test this on real hardware */
    .if \lddst ; prfm PLDL1STRM, [x0, #192] ; .endif
    .if \ldsrc ; prfm PLDL1STRM, [x1, #192] ; .endif
#endif
  .endif

        \kernel

        subs    x2, x2, #64
  .if \zipped
        st4     {v0.16b,v1.16b,v2.16b,v3.16b}, [x0], #64
  .else
        st1     {v0.16b,v1.16b,v2.16b,v3.16b}, [x0], #64
  .endif

2:      bge     1b
        adds    x2, x2, #64
        beq     2f

        /* To handle the tail portion of the data (something less than 64
         * bytes) load small power-of-two chunks into working registers.  It
         * doesn't matter where they end up in the register; the same process
         * will store them back out using the same positions and the operations
         * don't require data to interact with its neighbours.
         */
        movi    v0.16b, #0
        movi    v1.16b, #0
        movi    v2.16b, #0
        movi    v3.16b, #0

        movi    v8.16b, #0
        movi    v9.16b, #0
        movi    v10.16b, #0
        movi    v11.16b, #0

        tbz     x2, #5, 1f
  .if \lddst ; ld1     {v2.16b,v3.16b}, [x0], #32   ; .endif
  .if \ldsrc ; ld1     {v10.16b,v11.16b}, [x1], #32 ; .endif
1:      tbz     x2, #4, 1f
  .if \lddst ; ld1     {v1.16b}, [x0], #16  ; .endif
  .if \ldsrc ; ld1     {v9.16b}, [x1], #16  ; .endif
1:      tbz     x2, #3, 1f
  .if \lddst ; ld1     {v0.d}[1], [x0], #8 ; .endif
  .if \ldsrc ; ld1     {v8.d}[1], [x1], #8 ; .endif
1:      tbz     x2, #2, 1f
  .if \lddst ; ld1     {v0.s}[1], [x0], #4 ; .endif
  .if \ldsrc ; ld1     {v8.s}[1], [x1], #4 ; .endif
1:      tbz     x2, #1, 1f
  .if \lddst ; ld1     {v0.h}[1], [x0], #2 ; .endif
  .if \ldsrc ; ld1     {v8.h}[1], [x1], #2 ; .endif
1:      tbz     x2, #0, 1f
  .if \lddst ; ld1     {v0.b}[1], [x0], #1 ; .endif
  .if \ldsrc ; ld1     {v8.b}[1], [x1], #1 ; .endif
1:
  .if \lddst ; sub     x0, x0, x2           ; .endif

.if \zipped
        /* One small impediment in the process above is that some of the load
         * operations can't perform byte-wise structure deinterleaving at the
         * same time as loading only part of a register.  So the data is loaded
         * linearly and unpacked manually at this point.
         */
        uzp1    v4.16b, v0.16b, v1.16b
        uzp2    v5.16b, v0.16b, v1.16b
        uzp1    v6.16b, v2.16b, v3.16b
        uzp2    v7.16b, v2.16b, v3.16b
        uzp1    v0.16b, v4.16b, v6.16b
        uzp2    v2.16b, v4.16b, v6.16b
        uzp1    v1.16b, v5.16b, v7.16b
        uzp2    v3.16b, v5.16b, v7.16b

        uzp1    v4.16b, v8.16b, v9.16b
        uzp2    v5.16b, v8.16b, v9.16b
        uzp1    v6.16b, v10.16b, v11.16b
        uzp2    v7.16b, v10.16b, v11.16b
        uzp1    v8.16b, v4.16b, v6.16b
        uzp2    v10.16b, v4.16b, v6.16b
        uzp1    v9.16b, v5.16b, v7.16b
        uzp2    v11.16b, v5.16b, v7.16b

        \kernel

        zip1    v4.16b, v0.16b, v2.16b
        zip2    v6.16b, v0.16b, v2.16b
        zip1    v5.16b, v1.16b, v3.16b
        zip2    v7.16b, v1.16b, v3.16b
        zip1    v0.16b, v4.16b, v5.16b
        zip2    v1.16b, v4.16b, v5.16b
        zip1    v2.16b, v6.16b, v7.16b
        zip2    v3.16b, v6.16b, v7.16b
  .else
        \kernel
  .endif

        tbz     x2, #5, 1f
        st1     {v2.16b,v3.16b}, [x0], #32
1:      tbz     x2, #4, 1f
        st1     {v1.16b}, [x0], #16
1:      tbz     x2, #3, 1f
        st1     {v0.d}[1], [x0], #8
1:      tbz     x2, #2, 1f
        st1     {v0.s}[1], [x0], #4
1:      tbz     x2, #1, 1f
        st1     {v0.h}[1], [x0], #2
1:      tbz     x2, #0, 2f
        st1     {v0.b}[1], [x0], #1
2:      ld1     {v8.1d - v11.1d}, [sp], #32
        ld1     {v12.1d - v15.1d}, [sp], #32
.endif
        mov     x0, #0
        ret
.endm


/* produce list of blend_line_XX() functions; each function uses the wrap_line
 * macro, passing it the name of the operation macro it wants along with
 * optional parameters to remove unnecessary operations.
 */
#define BLEND_X(d, n) ENTRY(blend_line_##n) ; wrap_line blend_kernel_##n, params_##n ; END(blend_line_##n) ;
    BLEND_LIST(BLEND_X)
#undef BLEND_X

#define BLEND_X(d, n) .set tablesize, d+1 ;
    BLEND_LIST(BLEND_X)
#undef BLEND_X

/*  int rsdIntrinsicBlend_K(
 *          uchar4 *out,        // x0
 *          uchar4 const *in,   // x1
 *          int slot,           // x2
 *          size_t xstart,      // x3
 *          size_t xend);       // x4
 */
ENTRY(rsdIntrinsicBlend_K)
    adrp    x5, blendtable
    add     x5, x5, :lo12:blendtable
    cmp     w2, tablesize
    bhs     1f
    ldrsh   x6, [x5, w2, uxtw #1]
    add     x0, x0, w3, uxtw #2
    add     x1, x1, w3, uxtw #2
    sub     w2, w4, w3
    ubfiz   x2, x2, #2, #32 /* TODO: fix */
    cbz     x6, 1f
    adr     x5, 2f
    add     x6, x5, x6
2:  br      x6
1:  mov     x0, #-1
    ret

END(rsdIntrinsicBlend_K)

.rodata
.set off,0
blendtable:
#define BLEND_X(d, n) .rept d-off ; .hword 0 ; .endr ; .hword blend_line_##n - 2b ; .set off, d+1 ;
        BLEND_LIST(BLEND_X)
#undef BLEND_X
