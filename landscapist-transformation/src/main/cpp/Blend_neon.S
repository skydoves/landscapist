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

#define ENTRY(f) .text; .align 4; .globl f; .type f,#function; f: .fnstart
#define END(f) .fnend; .size f, .-f;

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
    X(14, MULTIPLY) \
    X(21, DIFFERENCE) \
    X(34, ADD) \
    X(35, SUBTRACT)

.eabi_attribute 25,1 @Tag_ABI_align8_preserved
.arm

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
        vmov.i8 q0, #0
        vmov.i8 q1, #0
        vmov.i8 q2, #0
        vmov.i8 q3, #0
.endm

#define params_SRC zipped=0, lddst=0
.macro blend_kernel_SRC
        vmov    q0, q8
        vmov    q1, q9
        vmov    q2, q10
        vmov    q3, q11
.endm

#define params_DST nowrap=1
.macro blend_kernel_DST
        /* nop */
.endm

#define params_SRC_OVER zipped=1
.macro blend_kernel_SRC_OVER
        vmvn        q7, q11

        vmull.u8    q12, d15, d1
        vmull.u8    q0,  d14, d0
        vmull.u8    q13, d15, d3
        vmull.u8    q1,  d14, d2
        vmull.u8    q14, d15, d5
        vmull.u8    q2,  d14, d4
        vmull.u8    q15, d15, d7
        vmull.u8    q3,  d14, d6

        vrshrn.u16  d8,  q0,  #8
        vrshrn.u16  d9,  q12, #8
        vrshrn.u16  d10, q1,  #8
        vrshrn.u16  d11, q13, #8
        vrshrn.u16  d12, q2,  #8
        vrshrn.u16  d13, q14, #8
        vrshrn.u16  d14, q3,  #8
        vrshrn.u16  d15, q15, #8

        vaddw.u8    q0,  d8
        vaddw.u8    q12, d9
        vaddw.u8    q1,  d10
        vaddw.u8    q13, d11
        vaddw.u8    q2,  d12
        vaddw.u8    q14, d13
        vaddw.u8    q3,  d14
        vaddw.u8    q15, d15

        vrshrn.u16  d0, q0,  #8
        vrshrn.u16  d1, q12, #8
        vrshrn.u16  d2, q1,  #8
        vrshrn.u16  d3, q13, #8
        vrshrn.u16  d4, q2,  #8
        vrshrn.u16  d5, q14, #8
        vrshrn.u16  d6, q3,  #8
        vrshrn.u16  d7, q15, #8

        vqadd.u8    q0, q8
        vqadd.u8    q1, q9
        vqadd.u8    q2, q10
        vqadd.u8    q3, q11
.endm

#define params_DST_OVER zipped=1
.macro blend_kernel_DST_OVER
        vmvn        q7, q3

        vmull.u8    q12, d15, d17
        vmull.u8    q8,  d14, d16
        vmull.u8    q13, d15, d19
        vmull.u8    q9,  d14, d18
        vmull.u8    q14, d15, d21
        vmull.u8    q10, d14, d20
        vmull.u8    q15, d15, d23
        vmull.u8    q11, d14, d22

        vrshrn.u16  d8,  q0,  #8
        vrshrn.u16  d9,  q12, #8
        vrshrn.u16  d10, q1,  #8
        vrshrn.u16  d11, q13, #8
        vrshrn.u16  d12, q2,  #8
        vrshrn.u16  d13, q14, #8
        vrshrn.u16  d14, q3,  #8
        vrshrn.u16  d15, q15, #8

        vaddw.u8    q8,  d8
        vaddw.u8    q12, d9
        vaddw.u8    q9,  d10
        vaddw.u8    q13, d11
        vaddw.u8    q10, d12
        vaddw.u8    q14, d13
        vaddw.u8    q11, d14
        vaddw.u8    q15, d15

        vrshrn.u16  d16, q8,  #8
        vrshrn.u16  d17, q12, #8
        vrshrn.u16  d18, q9,  #8
        vrshrn.u16  d19, q13, #8
        vrshrn.u16  d20, q10, #8
        vrshrn.u16  d21, q14, #8
        vrshrn.u16  d22, q11, #8
        vrshrn.u16  d23, q15, #8

        vqadd.u8    q0, q8
        vqadd.u8    q1, q9
        vqadd.u8    q2, q10
        vqadd.u8    q3, q11
.endm

#define params_SRC_IN zipped=1
.macro blend_kernel_SRC_IN
        vmull.u8    q12, d7, d17
        vmull.u8    q0,  d6, d16
        vmull.u8    q13, d7, d19
        vmull.u8    q1,  d6, d18
        vmull.u8    q14, d7, d21
        vmull.u8    q2,  d6, d20
        vmull.u8    q15, d7, d23
        vmull.u8    q3,  d6, d22

        vrshrn.u16  d8,  q0,  #8
        vrshrn.u16  d9,  q12, #8
        vrshrn.u16  d10, q1,  #8
        vrshrn.u16  d11, q13, #8
        vrshrn.u16  d12, q2,  #8
        vrshrn.u16  d13, q14, #8
        vrshrn.u16  d14, q3,  #8
        vrshrn.u16  d15, q15, #8

        vaddw.u8    q0,  d8
        vaddw.u8    q12, d9
        vaddw.u8    q1,  d10
        vaddw.u8    q13, d11
        vaddw.u8    q2,  d12
        vaddw.u8    q14, d13
        vaddw.u8    q3,  d14
        vaddw.u8    q15, d15

        vrshrn.u16  d0, q0,  #8
        vrshrn.u16  d1, q12, #8
        vrshrn.u16  d2, q1,  #8
        vrshrn.u16  d3, q13, #8
        vrshrn.u16  d4, q2,  #8
        vrshrn.u16  d5, q14, #8
        vrshrn.u16  d6, q3,  #8
        vrshrn.u16  d7, q15, #8
.endm

#define params_DST_IN zipped=1
.macro blend_kernel_DST_IN
        vmull.u8    q12, d1, d23
        vmull.u8    q0,  d0, d22
        vmull.u8    q13, d3, d23
        vmull.u8    q1,  d2, d22
        vmull.u8    q14, d5, d23
        vmull.u8    q2,  d4, d22
        vmull.u8    q15, d7, d23
        vmull.u8    q3,  d6, d22

        vrshrn.u16  d8,  q0,  #8
        vrshrn.u16  d9,  q12, #8
        vrshrn.u16  d10, q1,  #8
        vrshrn.u16  d11, q13, #8
        vrshrn.u16  d12, q2,  #8
        vrshrn.u16  d13, q14, #8
        vrshrn.u16  d14, q3,  #8
        vrshrn.u16  d15, q15, #8

        vaddw.u8    q0,  d8
        vaddw.u8    q12, d9
        vaddw.u8    q1,  d10
        vaddw.u8    q13, d11
        vaddw.u8    q2,  d12
        vaddw.u8    q14, d13
        vaddw.u8    q3,  d14
        vaddw.u8    q15, d15

        vrshrn.u16  d0, q0,  #8
        vrshrn.u16  d1, q12, #8
        vrshrn.u16  d2, q1,  #8
        vrshrn.u16  d3, q13, #8
        vrshrn.u16  d4, q2,  #8
        vrshrn.u16  d5, q14, #8
        vrshrn.u16  d6, q3,  #8
        vrshrn.u16  d7, q15, #8
.endm

#define params_SRC_OUT zipped=1
.macro blend_kernel_SRC_OUT
        vmvn        q3, q3
        blend_kernel_SRC_IN
.endm


#define params_DST_OUT zipped=1
.macro blend_kernel_DST_OUT
        vmvn        q11, q11
        blend_kernel_DST_IN
.endm

#define params_SRC_ATOP zipped=1
.macro blend_kernel_SRC_ATOP
        vmvn        q11, q11

        vmull.u8    q12, d23, d1
        vmull.u8    q0,  d22, d0
        vmull.u8    q13, d23, d3
        vmull.u8    q1,  d22, d2
        vmull.u8    q14, d23, d5
        vmull.u8    q2,  d22, d4

        vmull.u8    q4,  d7, d17
        vmull.u8    q8,  d6, d16
        vmull.u8    q5,  d7, d19
        vmull.u8    q9,  d6, d18
        vmull.u8    q6,  d7, d21
        vmull.u8    q10, d6, d20

        vqadd.u16   q12, q4
        vqadd.u16   q0,  q8
        vqadd.u16   q13, q5
        vqadd.u16   q1,  q9
        vqadd.u16   q14, q6
        vqadd.u16   q2,  q10

        vrshr.u16   q8,  q0,  #8
        vrshr.u16   q4,  q12, #8
        vrshr.u16   q9,  q1,  #8
        vrshr.u16   q5,  q13, #8
        vrshr.u16   q10, q2,  #8
        vrshr.u16   q6,  q14, #8

        vqadd.u16   q0,  q8
        vqadd.u16   q12, q4
        vqadd.u16   q1,  q9
        vqadd.u16   q13, q5
        vqadd.u16   q2,  q10
        vqadd.u16   q14, q6

        vqrshrn.u16 d0, q0,  #8
        vqrshrn.u16 d1, q12, #8
        vqrshrn.u16 d2, q1,  #8
        vqrshrn.u16 d3, q13, #8
        vqrshrn.u16 d4, q2,  #8
        vqrshrn.u16 d5, q14, #8
.endm

#define params_DST_ATOP zipped=1
.macro blend_kernel_DST_ATOP
        vmvn        q3, q3

        vmull.u8    q12, d23, d1
        vmull.u8    q0,  d22, d0
        vmull.u8    q13, d23, d3
        vmull.u8    q1,  d22, d2
        vmull.u8    q14, d23, d5
        vmull.u8    q2,  d22, d4

        vmull.u8    q4,  d7, d17
        vmull.u8    q8,  d6, d16
        vmull.u8    q5,  d7, d19
        vmull.u8    q9,  d6, d18
        vmull.u8    q6,  d7, d21
        vmull.u8    q10, d6, d20

        vqadd.u16   q12, q4
        vqadd.u16   q0,  q8
        vqadd.u16   q13, q5
        vqadd.u16   q1,  q9
        vqadd.u16   q14, q6
        vqadd.u16   q2,  q10

        vrshr.u16   q8,  q0,  #8
        vrshr.u16   q4,  q12, #8
        vrshr.u16   q9,  q1,  #8
        vrshr.u16   q5,  q13, #8
        vrshr.u16   q10, q2,  #8
        vrshr.u16   q6,  q14, #8

        vqadd.u16   q0,  q8
        vqadd.u16   q12, q4
        vqadd.u16   q1,  q9
        vqadd.u16   q13, q5
        vqadd.u16   q2,  q10
        vqadd.u16   q14, q6

        vqrshrn.u16 d0, q0,  #8
        vqrshrn.u16 d1, q12, #8
        vqrshrn.u16 d2, q1,  #8
        vqrshrn.u16 d3, q13, #8
        vqrshrn.u16 d4, q2,  #8
        vqrshrn.u16 d5, q14, #8

        vmov        q3, q11
.endm

#define params_MULTIPLY zipped=0
.macro blend_kernel_MULTIPLY
        vmull.u8    q12, d1, d17
        vmull.u8    q0,  d0, d16
        vmull.u8    q13, d3, d19
        vmull.u8    q1,  d2, d18
        vmull.u8    q14, d5, d21
        vmull.u8    q2,  d4, d20
        vmull.u8    q15, d7, d23
        vmull.u8    q3,  d6, d22

        vrshrn.u16  d8,  q0,  #8
        vrshrn.u16  d9,  q12, #8
        vrshrn.u16  d10, q1,  #8
        vrshrn.u16  d11, q13, #8
        vrshrn.u16  d12, q2,  #8
        vrshrn.u16  d13, q14, #8
        vrshrn.u16  d14, q3,  #8
        vrshrn.u16  d15, q15, #8

        vaddw.u8    q0,  d8
        vaddw.u8    q12, d9
        vaddw.u8    q1,  d10
        vaddw.u8    q13, d11
        vaddw.u8    q2,  d12
        vaddw.u8    q14, d13
        vaddw.u8    q3,  d14
        vaddw.u8    q15, d15

        vrshrn.u16  d0, q0,  #8
        vrshrn.u16  d1, q12, #8
        vrshrn.u16  d2, q1,  #8
        vrshrn.u16  d3, q13, #8
        vrshrn.u16  d4, q2,  #8
        vrshrn.u16  d5, q14, #8
        vrshrn.u16  d6, q3,  #8
        vrshrn.u16  d7, q15, #8
.endm

#define params_ADD zipped=0
.macro blend_kernel_ADD
        vqadd.u8 q0, q0, q8
        vqadd.u8 q1, q1, q9
        vqadd.u8 q2, q2, q10
        vqadd.u8 q3, q3, q11
.endm

#define params_SUBTRACT zipped=0
.macro blend_kernel_SUBTRACT
        vqsub.u8 q0, q0, q8
        vqsub.u8 q1, q1, q9
        vqsub.u8 q2, q2, q10
        vqsub.u8 q3, q3, q11
.endm

#define params_DIFFERENCE zipped=0
.macro blend_kernel_DIFFERENCE
        vabd.u8 q0, q0, q8
        vabd.u8 q1, q1, q9
        vabd.u8 q2, q2, q10
        vabd.u8 q3, q3, q11
.endm

#define params_XOR zipped=0
.macro blend_kernel_XOR
        veor    q0, q0, q8
        veor    q1, q1, q9
        veor    q2, q2, q10
        veor    q3, q3, q11
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
        vpush   {d8-d15}
        subs    r2, #64
        b       2f
        .align 4
1:
  .if \lddst
    .if \zipped
        vld4.8  {d0,d2,d4,d6}, [r0]!
        vld4.8  {d1,d3,d5,d7}, [r0]!
    .else
        vld1.8  {d0-d3}, [r0]!
        vld1.8  {d4-d7}, [r0]!
    .endif
        sub     r0, #64
  .endif
  .if \ldsrc
    .if \zipped
        vld4.8  {d16,d18,d20,d22}, [r1]!
        vld4.8  {d17,d19,d21,d23}, [r1]!
    .else
        vld1.8  {d16-d19}, [r1]!
        vld1.8  {d20-d23}, [r1]!
    .endif
  .endif
  .if \pld
    .if \lddst ; pld [r0, #192] ; .endif
    .if \ldsrc ; pld [r1, #192] ; .endif
  .endif

        \kernel

        subs    r2, #64
  .if \zipped
        vst4.8  {d0,d2,d4,d6}, [r0]!
        vst4.8  {d1,d3,d5,d7}, [r0]!
  .else
        vst1.8  {d0-d3}, [r0]!
        vst1.8  {d4-d7}, [r0]!
  .endif

2:      bge     1b
        adds    r2, #64
        beq     2f

        /* To handle the tail portion of the data (something less than 64
         * bytes) load small power-of-two chunks into working registers.  It
         * doesn't matter where they end up in the register; the same process
         * will store them back out using the same positions and the operations
         * don't require data to interact with its neighbours.
         */
        vmov.i8 q0, #0
        vmov.i8 q1, #0
        vmov.i8 q2, #0
        vmov.i8 q3, #0

        vmov.i8 q8, #0
        vmov.i8 q9, #0
        vmov.i8 q10, #0
        vmov.i8 q11, #0

        tst     r2, #32
        beq     1f
  .if \lddst ; vld1.64 {d4-d7}, [r0]!   ; .endif
  .if \ldsrc ; vld1.64 {d20-d23}, [r1]! ; .endif
1:      tst     r2, #16
        beq     1f
  .if \lddst ; vld1.64 {d2-d3}, [r0]!   ; .endif
  .if \ldsrc ; vld1.64 {d18-d19}, [r1]! ; .endif
1:      tst     r2, #8
        beq     1f
  .if \lddst ; vld1.64 {d1}, [r0]!      ; .endif
  .if \ldsrc ; vld1.64 {d17}, [r1]!     ; .endif
1:      tst     r2, #4
        beq     1f
  .if \lddst ; vld1.32 {d0[1]}, [r0]!   ; .endif
  .if \ldsrc ; vld1.32 {d16[1]}, [r1]!  ; .endif
1:      tst     r2, #2
        beq     1f
  .if \lddst ; vld1.16 {d0[1]}, [r0]!   ; .endif
  .if \ldsrc ; vld1.16 {d16[1]}, [r1]!  ; .endif
1:      tst     r2, #1
        beq     1f
  .if \lddst ; vld1.8  {d0[1]}, [r0]!   ; .endif
  .if \ldsrc ; vld1.8  {d16[1]}, [r1]!  ; .endif
1:
  .if \lddst ; sub     r0, r2           ; .endif

  .if \zipped
        /* One small impediment in the process above is that some of the load
         * operations can't perform byte-wise structure deinterleaving at the
         * same time as loading only part of a register.  So the data is loaded
         * linearly and unpacked manually at this point.
         */
        vuzp.8  q0, q1
        vuzp.8  q2, q3
        vuzp.8  q0, q2
        vuzp.8  q1, q3

        vuzp.8  q8, q9
        vuzp.8  q10, q11
        vuzp.8  q8, q10
        vuzp.8  q9, q11

        \kernel

        vzip.8  q0, q2
        vzip.8  q1, q3
        vzip.8  q0, q1
        vzip.8  q2, q3
  .else
        \kernel
  .endif

        tst     r2, #32
        beq     1f
        vst1.64 {d4-d7}, [r0]!
1:      tst     r2, #16
        beq     1f
        vst1.64 {d2-d3}, [r0]!
1:      tst     r2, #8
        beq     1f
        vst1.64 {d1}, [r0]!
1:      tst     r2, #4
        beq     1f
        vst1.32 {d0[1]}, [r0]!
1:      tst     r2, #2
        beq     1f
        vst1.16 {d0[1]}, [r0]!
1:      tst     r2, #1
        beq     2f
        vst1.8  {d0[1]}, [r0]!
2:      vpop    {d8-d15}
.endif
        mov     r0, #0
        bx      lr
.endm


/* produce list of blend_line_XX() functions; each function uses the wrap_line
 * macro, passing it the name of the operation macro it wants along with
 * optional parameters to remove unnecessary operations.
 */
#define BLEND_X(d, n) ENTRY(blend_line_##n) ; wrap_line blend_kernel_##n, params_##n ; END(blend_line_##n) ;
    BLEND_LIST(BLEND_X)
#undef BLEND_X


/*  int rsdIntrinsicBlend_K(
 *          uchar4 *out,        // r0
 *          uchar4 const *in,   // r1
 *          int slot,           // r2
 *          size_t xstart,      // r3
 *          size_t xend);       // [sp]
 */
ENTRY(rsdIntrinsicBlend_K)
    adr     ip, blend_functions
    cmp     r2, #(blend_functions_end - blend_functions) >> 2
    ldrlo   ip, [ip, r2, LSL #2]
    movhs   ip, #0
    ldr     r2, [sp]
    add     r0, r3, LSL #2
    add     r1, r3, LSL #2
    sub     r2, r3
    mov     r2, r2, LSL #2
    cmp     ip, #0
    addne   ip, ip, pc
    bxne    ip
1:  mov     r0, #-1
    bx      lr

blend_functions:
.set off,0
#define BLEND_X(d, n) .rept d-off ; .word 0 ; .endr ; .word blend_line_##n-1b ; .set off, d+1 ;
        BLEND_LIST(BLEND_X)
#undef BLEND_X
blend_functions_end:

END(rsdIntrinsicBlend_K)
