/*
 * Copyright (C) 2012 The Android Open Source Project
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

/*
        r0 = dst
        r1 = y0 base pointer
        r2 = y1 base pointer
        r3 = y2 base pointer
        sp = coeffs
        sp = length / 2
*/

#define ENTRY(f) .text; .align 0; .globl f; .type f,#function; f: .fnstart
#define END(f) .fnend; .size f, .-f;

ENTRY(rsdIntrinsicConvolve3x3_K)
        push            {r4-r8, r10, r11, lr}
        vpush           {q4-q7}

        /* Get the coeffs pointer from the stack and load the
           coefficients in the q0, q1 NEON registers */
        ldr r4, [sp, #32+64]
        vld1.16 {q0, q1}, [r4]

        /* Get count from the stack */
        ldr r4, [sp, #36+64]

        /* Load the frequently used immediate in a register */
        mov r5, #8

1:
        /* Load and post-increase the address by r5=#8 */
        vld1.8 {q13}, [r1], r5
        vld1.8 {q14}, [r2], r5
        vld1.8 {q15}, [r3], r5

        /* Signal memory for data that will be used in the loop after the next */
        pld         [r1, r5]
        pld         [r2, r5]
        pld         [r3, r5]

        vmovl.u8 q2, d26
        vmovl.u8 q3, d27
        vmovl.u8 q4, d28
        vmovl.u8 q5, d29
        vmovl.u8 q6, d30
        vmovl.u8 q7, d31

/*
        The two pixel source array is
        d4,  d5,  d6,  d7
        d8,  d9,  d10, d11
        d12, d13, d14, d15
*/

        vmull.s16 q8, d4, d0[0]
        vmlal.s16 q8, d5, d0[1]
        vmlal.s16 q8, d6, d0[2]
        vmlal.s16 q8, d8, d0[3]
        vmlal.s16 q8, d9, d1[0]
        vmlal.s16 q8, d10, d1[1]
        vmlal.s16 q8, d12, d1[2]
        vmlal.s16 q8, d13, d1[3]
        vmlal.s16 q8, d14, d2[0]

        vmull.s16 q9, d5, d0[0]
        vmlal.s16 q9, d6, d0[1]
        vmlal.s16 q9, d7, d0[2]
        vmlal.s16 q9, d9, d0[3]
        vmlal.s16 q9, d10, d1[0]
        vmlal.s16 q9, d11, d1[1]
        vmlal.s16 q9, d13, d1[2]
        vmlal.s16 q9, d14, d1[3]
        vmlal.s16 q9, d15, d2[0]

        vshrn.i32 d16, q8, #8
        vshrn.i32 d17, q9, #8

        vqmovun.s16 d16, q8
        vst1.8 d16, [r0]!

        /* Are we done yet? */
        subs r4, r4, #1
        bne 1b

        /* We're done, bye! */
        vpop            {q4-q7}
        pop             {r4-r8, r10, r11, lr}
        bx              lr
END(rsdIntrinsicConvolve3x3_K)


/* Convolve 5x5 */

/*
        r0 = dst
        r1 = y0 base pointer
        r2 = y1 base pointer
        r3 = y2 base pointer
        r4 = y3 base pointer
        r5 = y4 base pointer
        r6 = coeffs
        r7 = length
*/
ENTRY(rsdIntrinsicConvolve5x5_K)
        push        {r4-r7, lr}
        vpush       {q4-q7}

        /* load y3 in r4 */
        ldr     r4, [sp, #20 + 64]

        /* load y4 in r5 */
        ldr     r5, [sp, #24 + 64]

        /* Load the coefficients pointer */
        ldr     r6, [sp, #28 + 64]

        /* Create the coefficients vector */
        vld1.16     {d0, d1, d2, d3}, [r6]!
        vld1.16     {d4, d5, d6}, [r6]

        vmov.u32  q15, #0x7f

        /* load the count */
        ldr     r6, [sp, #32 + 64]

        /* Load the frequently used immediate in a register */
        mov     r7, #8

1:
        /* Load the y base pointers in Qregs and post-increase the address by r7=#8 */
        vld1.8  {d24, d25, d26}, [r1], r7      @  y0 ( y - 2 )
        vld1.8  {d27, d28, d29}, [r2], r7      @  y0 ( y - 1 )

        /* Signal memory for data that will be used in the loop after the next */
        pld         [r1, r7]
        pld         [r2, r7]

        /* Promoting the 8bit channels to 16bit */
        vmovl.u8 q9,  d24
        vmovl.u8 q10, d25
        vmovl.u8 q11, d26
        vmovl.u8 q12, d27
        vmovl.u8 q13, d28
        vmovl.u8 q14, d29

/*
        d18,  d19,  d20, d21, d22, d23,
        d24,  d25
*/
        vmull.s16 q4, d18, d0[0]
        vmlal.s16 q4, d19, d0[1]
        vmlal.s16 q4, d20, d0[2]
        vmlal.s16 q4, d21, d0[3]
        vmlal.s16 q4, d22, d1[0]

        vmlal.s16 q4, d24, d1[1]
        vmlal.s16 q4, d25, d1[2]
        vmlal.s16 q4, d26, d1[3]
        vmlal.s16 q4, d27, d2[0]
        vmlal.s16 q4, d28, d2[1]

        vmull.s16 q5, d19, d0[0]
        vmlal.s16 q5, d20, d0[1]
        vmlal.s16 q5, d21, d0[2]
        vmlal.s16 q5, d22, d0[3]
        vmlal.s16 q5, d23, d1[0]

        vmlal.s16 q5, d25, d1[1]
        vmlal.s16 q5, d26, d1[2]
        vmlal.s16 q5, d27, d1[3]
        vmlal.s16 q5, d28, d2[0]
        vmlal.s16 q5, d29, d2[1]


        /* Next 2 rows */
        /* Load the y base pointers in Qregs and post-increase the address by r7=#8 */
        vld1.8  {d24, d25, d26}, [r3], r7      @  y0 ( y )
        vld1.8  {d27, d28, d29}, [r4], r7      @  y0 ( y + 1 )

        /* Signal memory for data that will be used in the loop after the next */
        pld         [r3, r7]
        pld         [r4, r7]

        /* Promoting the 8bit channels to 16bit */
        vmovl.u8 q9,  d24
        vmovl.u8 q10, d25
        vmovl.u8 q11, d26
        vmovl.u8 q12, d27
        vmovl.u8 q13, d28
        vmovl.u8 q14, d29

/*
        d18,  d19,  d20, d21, d22, d23,
        d24,  d25
*/
        vmlal.s16 q4, d18, d2[2]
        vmlal.s16 q4, d19, d2[3]
        vmlal.s16 q4, d20, d3[0]
        vmlal.s16 q4, d21, d3[1]
        vmlal.s16 q4, d22, d3[2]

        vmlal.s16 q4, d24, d3[3]
        vmlal.s16 q4, d25, d4[0]
        vmlal.s16 q4, d26, d4[1]
        vmlal.s16 q4, d27, d4[2]
        vmlal.s16 q4, d28, d4[3]

        vmlal.s16 q5, d19, d2[2]
        vmlal.s16 q5, d20, d2[3]
        vmlal.s16 q5, d21, d3[0]
        vmlal.s16 q5, d22, d3[1]
        vmlal.s16 q5, d23, d3[2]

        vmlal.s16 q5, d25, d3[3]
        vmlal.s16 q5, d26, d4[0]
        vmlal.s16 q5, d27, d4[1]
        vmlal.s16 q5, d28, d4[2]
        vmlal.s16 q5, d29, d4[3]

        /* Last row */
        /* Load the y base pointers in Qregs and post-increase the address by r7=#8 */
        vld1.8  {d24, d25, d26}, [r5], r7      @  y0 ( y + 2 )

        /* Signal memory for data that will be used in the loop after the next */
        pld         [r5, r7]

        /* Promoting the 8bit channels to 16bit */
        vmovl.u8 q9,  d24
        vmovl.u8 q10, d25
        vmovl.u8 q11, d26

/*
        d18,  d19,  d20, d21, d22, d23,
        d24,  d25
*/

        vmlal.s16 q4, d18, d5[0]
        vmlal.s16 q4, d19, d5[1]
        vmlal.s16 q4, d20, d5[2]
        vmlal.s16 q4, d21, d5[3]
        vmlal.s16 q4, d22, d6[0]

        vmlal.s16 q5, d19, d5[0]
        vmlal.s16 q5, d20, d5[1]
        vmlal.s16 q5, d21, d5[2]
        vmlal.s16 q5, d22, d5[3]
        vmlal.s16 q5, d23, d6[0]



        vadd.i32 q4, q4, q15
        vadd.i32 q5, q5, q15

/*      Narrow it to a d-reg 32 -> 16 bit */
        vrshrn.i32 d8, q4, #8
        vrshrn.i32 d9, q5, #8


/*      Pack 16 -> 8 bit, saturate, put two pixels into D reg */
        vqmovun.s16 d8, q4

        vst1.8 d8, [r0]!           @ return the output and increase the address of r0

        /* Are we done? */
        subs r6, r6, #1
        bne 1b

        /* Yup, bye */
        vpop        {q4-q7}
        pop         {r4-r7, lr}
        bx          lr

END(rsdIntrinsicConvolve5x5_K)
