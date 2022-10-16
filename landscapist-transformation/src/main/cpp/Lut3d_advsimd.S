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


.macro lanepair dst, src0, src1, xr0, xr1, yr0, yr1, zr0, zr1

            smov        x6, \src0
            smov        x7, \src1

            add         x6, x6, x3
            add         x7, x7, x3

            ld1         {v16.2s}, [x6], x4
            ld1         {v17.2s}, [x7], x4

            ld1         {v18.2s}, [x6], x5
            ld1         {v19.2s}, [x7], x5

            dup         v8.8b, \yr0
            dup         v9.8b, \yr1
            /* Y interpolate, front, lanes 0 and 1 -> v12 and v13 */
            zip1        v12.16b, v5.16b, v16.16b
            zip1        v13.16b, v5.16b, v17.16b
            umlsl       v12.8h, v16.8b, v8.8b
            umlsl       v13.8h, v17.8b, v9.8b
            umlal       v12.8h, v18.8b, v8.8b
            umlal       v13.8h, v19.8b, v9.8b

            ld1         {v18.2s}, [x6]
            ld1         {v19.2s}, [x7]

            sub         x6, x6, x4
            sub         x7, x7, x4

            ld1         {v16.2s}, [x6]
            ld1         {v17.2s}, [x7]

            /* Y interpolate, rear, lanes 0 and 1 -> v14 and v15 */
            zip1        v14.16b, v5.16b, v16.16b
            zip1        v15.16b, v5.16b, v17.16b
            umlsl       v14.8h, v16.8b, v8.8b
            umlsl       v15.8h, v17.8b, v9.8b
            umlal       v14.8h, v18.8b, v8.8b
            umlal       v15.8h, v19.8b, v9.8b

            /* Z interpolate, lane 0 v12/v14 -> v10 */
            ushll       v8.4s, v12.4h, #8
            ushll2      v9.4s, v12.8h, #8
            umlsl       v8.4s, v12.4h, \zr0
            umlsl2      v9.4s, v12.8h, \zr0
            umlal       v8.4s, v14.4h, \zr0
            umlal2      v9.4s, v14.8h, \zr0
            rshrn       v10.4h, v8.4s, #8
            rshrn2      v10.8h, v9.4s, #8

            /* Z interpolate, lane 1 v13/v15 -> v11 */
            ushll       v8.4s, v13.4h, #8
            ushll2      v9.4s, v13.8h, #8
            umlsl       v8.4s, v13.4h, \zr1
            umlsl2      v9.4s, v13.8h, \zr1
            umlal       v8.4s, v15.4h, \zr1
            umlal2      v9.4s, v15.8h, \zr1
            rshrn       v11.4h, v8.4s, #8
            rshrn2      v11.8h, v9.4s, #8

            /* X interpolate, lanes 0 and 1 v10,v11 -> v14 */
            ushll       v8.4s, v10.4h, #8
            ushll       v9.4s, v11.4h, #8
            umlsl       v8.4s, v10.4h, \xr0
            umlsl       v9.4s, v11.4h, \xr1
            umlal2      v8.4s, v10.8h, \xr0
            umlal2      v9.4s, v11.8h, \xr1
            shrn        v14.4h, v8.4s, #8
            shrn2       v14.8h, v9.4s, #8

            /* pack lanes 0-1 -> v6 */
.ifc \dst, v20.16b
            uqrshrn2    \dst, v14.8h, #8
.else ; .ifc \dst, v21.16b
            uqrshrn2    \dst, v14.8h, #8
.else
            uqrshrn     \dst, v14.8h, #8
.endif ; .endif
.endm

/* void rsdIntrinsic3DLUT_K(
 *          void *dst,          // x0
 *          void const *in,     // x1
 *          size_t count,       // x2
 *          void const *lut,    // x3
 *          int32_t pitchy,     // w4
 *          int32_t pitchz,     // w5
 *          int dimx,           // w6
 *          int dimy,           // w7
 *          int dimz);          // [sp]
 */
ENTRY(rsdIntrinsic3DLUT_K)
            ldr         w8, [sp]
            stp         d8, d9, [sp, #-64]!
            stp         d10, d11, [sp, #16]
            stp         d12, d13, [sp, #32]
            stp         d14, d15, [sp, #48]
            movi        v4.8b, #1
            ins         v4.h[0], w6
            ins         v4.h[1], w7
            ins         v4.h[2], w8
            ins         v4.s[2], w4
            ins         v4.s[3], w5
            movi        v5.16b, #0

            subs        x2, x2, #8
            bge         2f
            cmn         x2, #8    // same as cmp x2, #-8
            ble         9f
            b           4f

            .align 6
1:          st4         {v20.8b,v21.8b,v22.8b,v23.8b}, [x0], #32
/* x0  = dst
 * x1  = src
 * x2  = count
 * x3  = lut
 * x4  = pitchy
 * x5  = pitchz
 * x6 = offset0
 * x7 = offset1
 */
2:          ld4         {v0.8b-v3.8b}, [x1], #32
/* v0,v1,v2,v3 source data
 * v4 dimensions and pitches
 */
3:          uxtl        v0.8h, v0.8b
            uxtl        v1.8h, v1.8b
            uxtl        v2.8h, v2.8b
            mul         v0.8h, v0.8h, v4.h[0]
            mul         v1.8h, v1.8h, v4.h[1]
            mul         v2.8h, v2.8h, v4.h[2]

/* ursra below would be more accurate, but this can result in a dim.0 case
 * where we try to read from the limit of the array and the limit +1 to
 * interpolate, even though the fractional component is zero.  Strictly this is
 * correct, except for the llegal access problem.
 */
            usra        v0.8h, v0.8h, #8
            usra        v1.8h, v1.8h, #8
            usra        v2.8h, v2.8h, #8

            ushr        v12.8h, v0.8h, #8
            ushr        v13.8h, v1.8h, #8
            ushr        v14.8h, v2.8h, #8
            bic         v0.8h, #0xff, LSL #8
            xtn         v1.8b, v1.8h
            bic         v2.8h, #0xff, LSL #8

/* v0.8h,v1.8b,v2.hb fractional offset
 * v12.8h,v13.8h,v14.8h integer offset
 */

            ushll       v6.4s, v12.4h, #2
            ushll2      v7.4s, v12.8h, #2
            uxtl        v8.4s, v13.4h
            uxtl2       v9.4s, v13.8h
            uxtl        v10.4s, v14.4h
            uxtl2       v11.4s, v14.8h
            mla         v6.4s, v8.4s,  v4.s[2]
            mla         v7.4s, v9.4s,  v4.s[2]
            mla         v6.4s, v10.4s, v4.s[3]
            mla         v7.4s, v11.4s, v4.s[3]

/* v6,v7 list of table offsets */

        /* lanes 0 and 1 */
            lanepair    dst=v20.8b,  src0=v6.s[0], src1=v6.s[1], xr0=v0.h[0], xr1=v0.h[1], yr0=v1.b[0], yr1=v1.b[1], zr0=v2.h[0], zr1=v2.h[1]

        /* lanes 2 and 3 */
            lanepair    dst=v20.16b, src0=v6.s[2], src1=v6.s[3], xr0=v0.h[2], xr1=v0.h[3], yr0=v1.b[2], yr1=v1.b[3], zr0=v2.h[2], zr1=v2.h[3]

        /* lanes 4 and 5 */
            lanepair    dst=v21.8b,  src0=v7.s[0], src1=v7.s[1], xr0=v0.h[4], xr1=v0.h[5], yr0=v1.b[4], yr1=v1.b[5], zr0=v2.h[4], zr1=v2.h[5]

        /* lanes 6 and 7 */
            lanepair    dst=v21.16b, src0=v7.s[2], src1=v7.s[3], xr0=v0.h[6], xr1=v0.h[7], yr0=v1.b[6], yr1=v1.b[7], zr0=v2.h[6], zr1=v2.h[7]

            uzp1        v6.16b, v20.16b, v21.16b
            uzp2        v7.16b, v20.16b, v21.16b
            uzp1        v20.16b, v6.16b, v7.16b
            uzp2        v22.16b, v6.16b, v7.16b
            mov         v21.d[0], v20.d[1]

            subs        x2, x2, #8
            mov         v23.8b, v3.8b

            bge         1b

            cmn         x2, #8    // same as cmp x2, #-8
            blt         1f

            st4         {v20.8b,v21.8b,v22.8b,v23.8b}, [x0], #32
            beq         9f

            /* fill the vector  with a safe value */
4:          ld4r        {v0.8b-v3.8b}, [x1]
            tbz         x2, #2, 2f
            ld4         {v0.b-v3.b}[0], [x1], #4
            ld4         {v0.b-v3.b}[1], [x1], #4
            ld4         {v0.b-v3.b}[2], [x1], #4
            ld4         {v0.b-v3.b}[3], [x1], #4
2:          tbz         x2, #1, 2f
            ld4         {v0.b-v3.b}[4], [x1], #4
            ld4         {v0.b-v3.b}[5], [x1], #4
2:          tbz         x2, #0, 2f
            ld4         {v0.b-v3.b}[6], [x1], #4
2:          b           3b

1:          tst         x2, #4
            beq         2f
            st4         {v20.b-v23.b}[0], [x0], #4
            st4         {v20.b-v23.b}[1], [x0], #4
            st4         {v20.b-v23.b}[2], [x0], #4
            st4         {v20.b-v23.b}[3], [x0], #4
2:          tst         x2, #2
            beq         2f
            st4         {v20.b-v23.b}[4], [x0], #4
            st4         {v20.b-v23.b}[5], [x0], #4
2:          tst         x2, #1
            beq         9f
            st4         {v20.b-v23.b}[6], [x0], #4

9:          ldp         d14, d15, [sp, #48]
            ldp         d12, d13, [sp, #32]
            ldp         d10, d11, [sp, #16]
            ldp         d8, d9, [sp], #64
            ret
END(rsdIntrinsic3DLUT_K)
