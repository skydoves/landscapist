/*
 * Copyright (C) 2012,2014 The Android Open Source Project
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
        x0 = dst
        x1 = y0 base pointer
        x2 = y1 base pointer
        x3 = y2 base pointer
        x4 = coeffs
        x5 = length / 2
*/

#define ENTRY(f) .text; .align 2; .globl f; .type f,#function; f:
#define END(f) .size f, .-f;

ENTRY(rsdIntrinsicConvolve3x3_K)
        sub             x6, sp, #64
        sub             sp, sp, #64
        st1             {v8.1d-v11.1d}, [x6], #32
        st1             {v12.1d-v15.1d}, [x6]

        /* Load the coefficients in the v0, v1 registers */
        ld1     {v0.8h, v1.8h}, [x4]

        /* Load the frequently used immediate in a register */
        mov x4, #8

1:
        /* Load and post-increase the address by x4=#8 */
        ld1     {v13.16b}, [x1], x4
        ld1     {v14.16b}, [x2], x4
        ld1     {v15.16b}, [x3], x4

        /* Signal memory for data that will be used in the loop after the next */
//        prfm        PLDL1KEEP,[x1, x4] // TODO: test this
//        prfm        PLDL1KEEP,[x2, x4] // TODO: test this
//        prfm        PLDL1KEEP,[x3, x4] // TODO: test this

        uxtl      v2.8h, v13.8b
        uxtl2     v3.8h, v13.16b
        uxtl      v4.8h, v14.8b
        uxtl2     v5.8h, v14.16b
        uxtl      v6.8h, v15.8b
        uxtl2     v7.8h, v15.16b

/*
        The two pixel source array is
        v2,  v2hi,  v3lo,  v3hi
        v4,  v4hi,  v5lo, v5hi
        v6, v6hi, v7lo, v7hi
*/

        smull     v8.4s, v2.4h, v0.h[0]
        smull2    v9.4s, v2.8h, v0.h[0]
        smlal2    v8.4s, v2.8h, v0.h[1]
        smlal     v9.4s, v3.4h, v0.h[1]
        smlal     v8.4s, v3.4h, v0.h[2]
        smlal2    v9.4s, v3.8h, v0.h[2]
        smlal     v8.4s, v4.4h, v0.h[3]
        smlal2    v9.4s, v4.8h, v0.h[3]
        smlal2    v8.4s, v4.8h, v0.h[4]
        smlal     v9.4s, v5.4h, v0.h[4]
        smlal     v8.4s, v5.4h, v0.h[5]
        smlal2    v9.4s, v5.8h, v0.h[5]
        smlal     v8.4s, v6.4h, v0.h[6]
        smlal2    v9.4s, v6.8h, v0.h[6]
        smlal2    v8.4s, v6.8h, v0.h[7]
        smlal     v9.4s, v7.4h, v0.h[7]
        smlal     v8.4s, v7.4h, v1.h[0]
        smlal2    v9.4s, v7.8h, v1.h[0]

        shrn      v8.4h, v8.4s, #8
        shrn2     v8.8h, v9.4s, #8

        sqxtun      v8.8b, v8.8h
        st1         {v8.8b}, [x0], #8

        /* Are we done yet? */
        subs x5, x5, #1
        bne 1b

        /* We're done, bye! */
        ld1             {v8.1d-v11.1d}, [sp], #32
        ld1             {v12.1d-v15.1d}, [sp], #32
        ret
END(rsdIntrinsicConvolve3x3_K)


/* Convolve 5x5 */

/*
        x0 = dst
        x1 = y0 base pointer
        x2 = y1 base pointer
        x3 = y2 base pointer
        x4 = y3 base pointer
        x5 = y4 base pointer
        x6 = coeffs
        x7 = length
*/
ENTRY(rsdIntrinsicConvolve5x5_K)
        sub         x8, sp, #64
        sub         sp, sp, #64
        st1         {v8.1d-v11.1d}, [x8], #32
        st1         {v12.1d-v15.1d}, [x8]

        /* Create the coefficients vector  */
        ld1         {v0.8h-v2.8h}, [x6], #48
        ld1         {v3.4h}, [x6], #8

        movi      v15.4s, #0x7f

        /* Load the frequently used immediate in a register */
        mov     x6, #8

1:
        /* Load the y base pointers in Qregs and post-increase the address by x6=#8 */
        ld1     {v9.8b-v11.8b}, [x1], x6      //  y0 ( y - 2 )
        ld1     {v12.8b-v14.8b}, [x2], x6      //  y0 ( y - 1 )

        /* Signal memory for data that will be used in the loop after the next */
//        prfm        PLDL1KEEP,[x1, x6] // TODO: test this
//        prfm        PLDL1KEEP,[x2, x6] // TODO: test this

        /* Promoting the 8bit channels to 16bit */
        uxtl      v9.8h,  v9.8b
        uxtl      v10.8h, v10.8b
        uxtl      v11.8h, v11.8b
        uxtl      v12.8h, v12.8b
        uxtl      v13.8h, v13.8b
        uxtl      v14.8h, v14.8b

/*
        v9,  v9hi,  v10lo, v10hi, v11lo, v11hi,
        v12,  v12hi
*/
        smull     v4.4s, v9.4h, v0.h[0]
        smull2    v5.4s, v9.8h, v0.h[0]
        smlal2    v4.4s, v9.8h, v0.h[1]
        smlal     v5.4s, v10.4h, v0.h[1]
        smlal     v4.4s, v10.4h, v0.h[2]
        smlal2    v5.4s, v10.8h, v0.h[2]
        smlal2    v4.4s, v10.8h, v0.h[3]
        smlal     v5.4s, v11.4h, v0.h[3]
        smlal     v4.4s, v11.4h, v0.h[4]
        smlal2    v5.4s, v11.8h, v0.h[4]

        smlal     v4.4s, v12.4h, v0.h[5]
        smlal2    v5.4s, v12.8h, v0.h[5]
        smlal2    v4.4s, v12.8h, v0.h[6]
        smlal     v5.4s, v13.4h, v0.h[6]
        smlal     v4.4s, v13.4h, v0.h[7]
        smlal2    v5.4s, v13.8h, v0.h[7]
        smlal2    v4.4s, v13.8h, v1.h[0]
        smlal     v5.4s, v14.4h, v1.h[0]
        smlal     v4.4s, v14.4h, v1.h[1]
        smlal2    v5.4s, v14.8h, v1.h[1]

        /* Next 2 rows */
        /* Load the y base pointers in Qregs and post-increase the address by x6=#8 */
        ld1     {v9.8b-v11.8b}, [x3], x6      //  y0 ( y )
        ld1     {v12.8b-v14.8b}, [x4], x6      //  y0 ( y + 1 )

        /* Signal memory for data that will be used in the loop after the next */
//        prfm        PLDL1KEEP,[x3, x6] // TODO: test this
//        prfm        PLDL1KEEP,[x4, x6] // TODO: test this

        /* Promoting the 8bit channels to 16bit */
        uxtl      v9.8h,  v9.8b
        uxtl      v10.8h, v10.8b
        uxtl      v11.8h, v11.8b
        uxtl      v12.8h, v12.8b
        uxtl      v13.8h, v13.8b
        uxtl      v14.8h, v14.8b

/*
        v9,  v9hi,  v10lo, v10hi, v11lo, v11hi,
        v12,  v12hi
*/
        smlal     v4.4s, v9.4h, v1.h[2]
        smlal2    v5.4s, v9.8h, v1.h[2]
        smlal2    v4.4s, v9.8h, v1.h[3]
        smlal     v5.4s, v10.4h, v1.h[3]
        smlal     v4.4s, v10.4h, v1.h[4]
        smlal2    v5.4s, v10.8h, v1.h[4]
        smlal2    v4.4s, v10.8h, v1.h[5]
        smlal     v5.4s, v11.4h, v1.h[5]
        smlal     v4.4s, v11.4h, v1.h[6]
        smlal2    v5.4s, v11.8h, v1.h[6]

        smlal     v4.4s, v12.4h, v1.h[7]
        smlal2    v5.4s, v12.8h, v1.h[7]
        smlal2    v4.4s, v12.8h, v2.h[0]
        smlal     v5.4s, v13.4h, v2.h[0]
        smlal     v4.4s, v13.4h, v2.h[1]
        smlal2    v5.4s, v13.8h, v2.h[1]
        smlal2    v4.4s, v13.8h, v2.h[2]
        smlal     v5.4s, v14.4h, v2.h[2]
        smlal     v4.4s, v14.4h, v2.h[3]
        smlal2    v5.4s, v14.8h, v2.h[3]

        /* Last row */
        /* Load the y base pointers in Qregs and post-increase the address by x6=#8 */
        ld1     {v9.8b- v11.8b}, [x5], x6      //  y0 ( y + 2 )

        /* Signal memory for data that will be used in the loop after the next */
//        prfm        PLDL1KEEP,[x5, x6] // TODO: test this

        /* Promoting the 8bit channels to 16bit */
        uxtl      v9.8h,  v9.8b
        uxtl      v10.8h, v10.8b
        uxtl      v11.8h, v11.8b

/*
        v9,  v9hi,  v10lo, v10hi, v11lo, v11hi,
        v12,  v12hi
*/

        smlal     v4.4s, v9.4h, v2.h[4]
        smlal2    v5.4s, v9.8h, v2.h[4]
        smlal2    v4.4s, v9.8h, v2.h[5]
        smlal     v5.4s, v10.4h, v2.h[5]
        smlal     v4.4s, v10.4h, v2.h[6]
        smlal2    v5.4s, v10.8h, v2.h[6]
        smlal2    v4.4s, v10.8h, v2.h[7]
        smlal     v5.4s, v11.4h, v2.h[7]
        smlal     v4.4s, v11.4h, v3.h[0]
        smlal2    v5.4s, v11.8h, v3.h[0]

        add      v4.4s, v4.4s, v15.4s
        add      v5.4s, v5.4s, v15.4s

/*      Narrow it to a d-reg 32 -> 16 bit */
        rshrn      v4.4h, v4.4s, #8
        rshrn2     v4.8h, v5.4s, #8


/*      Pack 16 -> 8 bit, saturate, put two pixels into D reg */
        sqxtun      v4.8b, v4.8h

        st1     {v4.8b}, [x0], #8        // return the output and increase the address of x0

        /* Are we done? */
        subs x7, x7, #1
        bne 1b

        /* Yup, bye */
        ld1         {v8.1d-v11.1d}, [sp], #32
        ld1         {v12.1d-v15.1d}, [sp], #32
        ret

END(rsdIntrinsicConvolve5x5_K)
