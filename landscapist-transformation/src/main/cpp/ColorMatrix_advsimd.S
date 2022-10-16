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


.macro vmxx_f32 i, mask, opd, opa, opb
  .if (\i) & \mask
    .if (\i) & (\mask - 1)
        fmla            \opd, \opa, \opb
    .else
        fmul            \opd, \opa, \opb
    .endif
  .endif
.endm

.macro vadd_f32 i, mask, opd, opa, opb, querkysyntax1, querkysyntax2
  .if (\i) & \mask
    .if (\i) & (\mask - 1)
        fadd            \opd, \opa, \opb
    .else
        mov             \querkysyntax1, \querkysyntax2
    .endif
  .endif
.endm

.macro vmxx_s16 i, mask, opd, opa, opb
  .if (\i) & \mask
    .if (\i) & (\mask - 1 + 16)
        smlal           \opd, \opa, \opb
    .else
        smull           \opd, \opa, \opb
    .endif
  .endif
.endm

.macro vmxx2_s16 i, mask, opd, opa, opb
  .if (\i) & \mask
    .if (\i) & (\mask - 1 + 16)
        smlal2          \opd, \opa, \opb
    .else
        smull2          \opd, \opa, \opb
    .endif
  .endif
.endm

/* x0 = dst
 * x1 = src
 * x2 = count
 * x3 = params
 * x4 = column0_fn
 * x5 = column1_fn
 * x6 = column2_fn
 * x7 = column3_fn
 * x8 = store_fn
 * x9 = load_fn
 */
.irp i, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15

.align 6
colormatrix_int_col0_\i:
      .if \i & 16
            dup         v6.4s, v4.s[0]
            dup         v7.4s, v4.s[0]
      .endif
            vmxx_s16    \i, 1, v6.4s, v12.4h, v0.h[0]
            vmxx_s16    \i, 2, v6.4s, v13.4h, v0.h[4]
            vmxx_s16    \i, 4, v6.4s, v14.4h, v1.h[0]
            vmxx_s16    \i, 8, v6.4s, v15.4h, v1.h[4]
            vmxx2_s16   \i, 1, v7.4s, v12.8h, v0.h[0]
            vmxx2_s16   \i, 2, v7.4s, v13.8h, v0.h[4]
            vmxx2_s16   \i, 4, v7.4s, v14.8h, v1.h[0]
            vmxx2_s16   \i, 8, v7.4s, v15.8h, v1.h[4]
            sqshrun     v8.4h, v6.4s, #8
            sqshrun2    v8.8h, v7.4s, #8
            br          x5

colormatrix_int_col0_n\i:
      .if (\i^31) & 16
            dup         v6.4s, v4.s[0]
            dup         v7.4s, v4.s[0]
      .endif
            vmxx_s16    \i^31, 1, v6.4s, v12.4h, v0.h[0]
            vmxx_s16    \i^31, 2, v6.4s, v13.4h, v0.h[4]
            vmxx_s16    \i^31, 4, v6.4s, v14.4h, v1.h[0]
            vmxx_s16    \i^31, 8, v6.4s, v15.4h, v1.h[4]
            vmxx2_s16   \i^31, 1, v7.4s, v12.8h, v0.h[0]
            vmxx2_s16   \i^31, 2, v7.4s, v13.8h, v0.h[4]
            vmxx2_s16   \i^31, 4, v7.4s, v14.8h, v1.h[0]
            vmxx2_s16   \i^31, 8, v7.4s, v15.8h, v1.h[4]
            sqshrun     v8.4h, v6.4s, #8
            sqshrun2    v8.8h, v7.4s, #8
            br          x5

.align 6
colormatrix_int_col1_\i:
      .if \i & 16
            dup         v6.4s, v4.s[1]
            dup         v7.4s, v4.s[1]
      .endif
            vmxx_s16    \i, 1, v6.4s, v12.4h, v0.h[1]
            vmxx_s16    \i, 2, v6.4s, v13.4h, v0.h[5]
            vmxx_s16    \i, 4, v6.4s, v14.4h, v1.h[1]
            vmxx_s16    \i, 8, v6.4s, v15.4h, v1.h[5]
            vmxx2_s16   \i, 1, v7.4s, v12.8h, v0.h[1]
            vmxx2_s16   \i, 2, v7.4s, v13.8h, v0.h[5]
            vmxx2_s16   \i, 4, v7.4s, v14.8h, v1.h[1]
            vmxx2_s16   \i, 8, v7.4s, v15.8h, v1.h[5]
            sqshrun     v9.4h, v6.4s, #8
            sqshrun2    v9.8h, v7.4s, #8
            br          x6

colormatrix_int_col1_n\i:
      .if (\i^31) & 16
            dup         v6.4s, v4.s[1]
            dup         v7.4s, v4.s[1]
      .endif
            vmxx_s16    \i^31, 1, v6.4s, v12.4h, v0.h[1]
            vmxx_s16    \i^31, 2, v6.4s, v13.4h, v0.h[5]
            vmxx_s16    \i^31, 4, v6.4s, v14.4h, v1.h[1]
            vmxx_s16    \i^31, 8, v6.4s, v15.4h, v1.h[5]
            vmxx2_s16   \i^31, 1, v7.4s, v12.8h, v0.h[1]
            vmxx2_s16   \i^31, 2, v7.4s, v13.8h, v0.h[5]
            vmxx2_s16   \i^31, 4, v7.4s, v14.8h, v1.h[1]
            vmxx2_s16   \i^31, 8, v7.4s, v15.8h, v1.h[5]
            sqshrun     v9.4h, v6.4s, #8
            sqshrun2    v9.8h, v7.4s, #8
            br          x6

.align 6
colormatrix_int_col2_\i:
      .if \i & 16
            dup         v6.4s, v4.s[2]
            dup         v7.4s, v4.s[2]
      .endif
            vmxx_s16    \i, 1, v6.4s, v12.4h, v0.h[2]
            vmxx_s16    \i, 2, v6.4s, v13.4h, v0.h[6]
            vmxx_s16    \i, 4, v6.4s, v14.4h, v1.h[2]
            vmxx_s16    \i, 8, v6.4s, v15.4h, v1.h[6]
            vmxx2_s16   \i, 1, v7.4s, v12.8h, v0.h[2]
            vmxx2_s16   \i, 2, v7.4s, v13.8h, v0.h[6]
            vmxx2_s16   \i, 4, v7.4s, v14.8h, v1.h[2]
            vmxx2_s16   \i, 8, v7.4s, v15.8h, v1.h[6]
            sqshrun     v10.4h, v6.4s, #8
            sqshrun2    v10.8h, v7.4s, #8
            br          x7

colormatrix_int_col2_n\i:
      .if (\i^31) & 16
            dup         v6.4s, v4.s[2]
            dup         v7.4s, v4.s[2]
      .endif
            vmxx_s16    \i^31, 1, v6.4s, v12.4h, v0.h[2]
            vmxx_s16    \i^31, 2, v6.4s, v13.4h, v0.h[6]
            vmxx_s16    \i^31, 4, v6.4s, v14.4h, v1.h[2]
            vmxx_s16    \i^31, 8, v6.4s, v15.4h, v1.h[6]
            vmxx2_s16   \i^31, 1, v7.4s, v12.8h, v0.h[2]
            vmxx2_s16   \i^31, 2, v7.4s, v13.8h, v0.h[6]
            vmxx2_s16   \i^31, 4, v7.4s, v14.8h, v1.h[2]
            vmxx2_s16   \i^31, 8, v7.4s, v15.8h, v1.h[6]
            sqshrun     v10.4h, v6.4s, #8
            sqshrun2    v10.8h, v7.4s, #8
            br          x7

.align 6
colormatrix_int_col3_\i:
      .if \i & 16
            dup         v6.4s, v4.s[3]
            dup         v7.4s, v4.s[3]
      .endif
            vmxx_s16    \i, 1, v6.4s, v12.4h, v0.h[3]
            vmxx_s16    \i, 2, v6.4s, v13.4h, v0.h[7]
            vmxx_s16    \i, 4, v6.4s, v14.4h, v1.h[3]
            vmxx_s16    \i, 8, v6.4s, v15.4h, v1.h[7]
            vmxx2_s16   \i, 1, v7.4s, v12.8h, v0.h[3]
            vmxx2_s16   \i, 2, v7.4s, v13.8h, v0.h[7]
            vmxx2_s16   \i, 4, v7.4s, v14.8h, v1.h[3]
            vmxx2_s16   \i, 8, v7.4s, v15.8h, v1.h[7]
            sqshrun     v11.4h, v6.4s, #8
            sqshrun2    v11.8h, v7.4s, #8
            br          x8

colormatrix_int_col3_n\i:
      .if (\i^31) & 16
            dup         v6.4s, v4.s[3]
            dup         v7.4s, v4.s[3]
      .endif
            vmxx_s16    \i^31, 1, v6.4s, v12.4h, v0.h[3]
            vmxx_s16    \i^31, 2, v6.4s, v13.4h, v0.h[7]
            vmxx_s16    \i^31, 4, v6.4s, v14.4h, v1.h[3]
            vmxx_s16    \i^31, 8, v6.4s, v15.4h, v1.h[7]
            vmxx2_s16   \i^31, 1, v7.4s, v12.8h, v0.h[3]
            vmxx2_s16   \i^31, 2, v7.4s, v13.8h, v0.h[7]
            vmxx2_s16   \i^31, 4, v7.4s, v14.8h, v1.h[3]
            vmxx2_s16   \i^31, 8, v7.4s, v15.8h, v1.h[7]
            sqshrun     v11.4h, v6.4s, #8
            sqshrun2    v11.8h, v7.4s, #8
            br          x8

.align 5
colormatrix_float_col0_\i:
            vmxx_f32    \i, 1,  v8.4s, v12.4s, v0.s[0]
            vmxx_f32    \i, 2,  v8.4s, v13.4s, v1.s[0]
            vmxx_f32    \i, 4,  v8.4s, v14.4s, v2.s[0]
            vmxx_f32    \i, 8,  v8.4s, v15.4s, v3.s[0]
            vadd_f32    \i, 16, v8.4s, v8.4s, v4.4s,        v8.16b, v4.16b
            vmxx_f32    \i, 1,  v16.4s, v20.4s, v0.s[0]
            vmxx_f32    \i, 2,  v16.4s, v21.4s, v1.s[0]
            vmxx_f32    \i, 4,  v16.4s, v22.4s, v2.s[0]
            vmxx_f32    \i, 8,  v16.4s, v23.4s, v3.s[0]
            vadd_f32    \i, 16, v16.4s, v16.4s, v4.4s,      v16.16b, v4.16b
            br          x5

.align 4
colormatrix_float_col0_n\i:
            vmxx_f32    \i^31, 1,  v8.4s, v12.4s, v0.s[0]
            vmxx_f32    \i^31, 2,  v8.4s, v13.4s, v1.s[0]
            vmxx_f32    \i^31, 4,  v8.4s, v14.4s, v2.s[0]
            vmxx_f32    \i^31, 8,  v8.4s, v15.4s, v3.s[0]
            vadd_f32    \i^31, 16, v8.4s, v8.4s, v4.4s,     v8.16b, v4.16b
            vmxx_f32    \i^31, 1,  v16.4s, v20.4s, v0.s[0]
            vmxx_f32    \i^31, 2,  v16.4s, v21.4s, v1.s[0]
            vmxx_f32    \i^31, 4,  v16.4s, v22.4s, v2.s[0]
            vmxx_f32    \i^31, 8,  v16.4s, v23.4s, v3.s[0]
            vadd_f32    \i^31, 16, v16.4s, v16.4s, v4.4s,   v16.16b, v4.16b
            br          x5

.align 5
colormatrix_float_col1_\i:
            vmxx_f32    \i, 1,  v9.4s, v12.4s, v0.s[1]
            vmxx_f32    \i, 2,  v9.4s, v13.4s, v1.s[1]
            vmxx_f32    \i, 4,  v9.4s, v14.4s, v2.s[1]
            vmxx_f32    \i, 8,  v9.4s, v15.4s, v3.s[1]
            vadd_f32    \i, 16, v9.4s, v9.4s, v5.4s,        v9.16b, v5.16b
            vmxx_f32    \i, 1,  v17.4s, v20.4s, v0.s[1]
            vmxx_f32    \i, 2,  v17.4s, v21.4s, v1.s[1]
            vmxx_f32    \i, 4,  v17.4s, v22.4s, v2.s[1]
            vmxx_f32    \i, 8,  v17.4s, v23.4s, v3.s[1]
            vadd_f32    \i, 16, v17.4s, v17.4s, v5.4s,      v17.16b, v5.16b
            br          x6

.align 4
colormatrix_float_col1_n\i:
            vmxx_f32    \i^31, 1,  v9.4s, v12.4s, v0.s[1]
            vmxx_f32    \i^31, 2,  v9.4s, v13.4s, v1.s[1]
            vmxx_f32    \i^31, 4,  v9.4s, v14.4s, v2.s[1]
            vmxx_f32    \i^31, 8,  v9.4s, v15.4s, v3.s[1]
            vadd_f32    \i^31, 16, v9.4s, v9.4s, v5.4s,     v9.16b, v5.16b
            vmxx_f32    \i^31, 1,  v17.4s, v20.4s, v0.s[1]
            vmxx_f32    \i^31, 2,  v17.4s, v21.4s, v1.s[1]
            vmxx_f32    \i^31, 4,  v17.4s, v22.4s, v2.s[1]
            vmxx_f32    \i^31, 8,  v17.4s, v23.4s, v3.s[1]
            vadd_f32    \i^31, 16, v17.4s, v17.4s, v5.4s,   v17.16b, v5.16b
            br          x6

.align 5
colormatrix_float_col2_\i:
            vmxx_f32    \i, 1,  v10.4s, v12.4s, v0.s[2]
            vmxx_f32    \i, 2,  v10.4s, v13.4s, v1.s[2]
            vmxx_f32    \i, 4,  v10.4s, v14.4s, v2.s[2]
            vmxx_f32    \i, 8,  v10.4s, v15.4s, v3.s[2]
            vadd_f32    \i, 16, v10.4s, v10.4s, v6.4s,      v10.16b, v6.16b
            vmxx_f32    \i, 1,  v18.4s, v20.4s, v0.s[2]
            vmxx_f32    \i, 2,  v18.4s, v21.4s, v1.s[2]
            vmxx_f32    \i, 4,  v18.4s, v22.4s, v2.s[2]
            vmxx_f32    \i, 8,  v18.4s, v23.4s, v3.s[2]
            vadd_f32    \i, 16, v18.4s, v18.4s, v6.4s,      v18.16b, v6.16b
            br          x7

.align 4
colormatrix_float_col2_n\i:
            vmxx_f32    \i^31, 1,  v10.4s, v12.4s, v0.s[2]
            vmxx_f32    \i^31, 2,  v10.4s, v13.4s, v1.s[2]
            vmxx_f32    \i^31, 4,  v10.4s, v14.4s, v2.s[2]
            vmxx_f32    \i^31, 8,  v10.4s, v15.4s, v3.s[2]
            vadd_f32    \i^31, 16, v10.4s, v10.4s, v6.4s,   v10.16b, v6.16b
            vmxx_f32    \i^31, 1,  v18.4s, v20.4s, v0.s[2]
            vmxx_f32    \i^31, 2,  v18.4s, v21.4s, v1.s[2]
            vmxx_f32    \i^31, 4,  v18.4s, v22.4s, v2.s[2]
            vmxx_f32    \i^31, 8,  v18.4s, v23.4s, v3.s[2]
            vadd_f32    \i^31, 16, v18.4s, v18.4s, v6.4s,   v18.16b, v6.16b
            br          x7

.align 5
colormatrix_float_col3_\i:
            vmxx_f32    \i, 1,  v11.4s, v12.4s, v0.s[3]
            vmxx_f32    \i, 2,  v11.4s, v13.4s, v1.s[3]
            vmxx_f32    \i, 4,  v11.4s, v14.4s, v2.s[3]
            vmxx_f32    \i, 8,  v11.4s, v15.4s, v3.s[3]
            vadd_f32    \i, 16, v11.4s, v11.4s, v7.4s,      v11.16b, v7.16b
            vmxx_f32    \i, 1,  v19.4s, v20.4s, v0.s[3]
            vmxx_f32    \i, 2,  v19.4s, v21.4s, v1.s[3]
            vmxx_f32    \i, 4,  v19.4s, v22.4s, v2.s[3]
            vmxx_f32    \i, 8,  v19.4s, v23.4s, v3.s[3]
            vadd_f32    \i, 16, v19.4s, v19.4s, v7.4s,      v19.16b, v7.16b
            br          x8

.align 4
colormatrix_float_col3_n\i:
            vmxx_f32    \i^31, 1,  v11.4s, v12.4s, v0.s[3]
            vmxx_f32    \i^31, 2,  v11.4s, v13.4s, v1.s[3]
            vmxx_f32    \i^31, 4,  v11.4s, v14.4s, v2.s[3]
            vmxx_f32    \i^31, 8,  v11.4s, v15.4s, v3.s[3]
            vadd_f32    \i^31, 16, v11.4s, v11.4s, v7.4s,  v11.16b, v7.16b
            vmxx_f32    \i^31, 1,  v19.4s, v20.4s, v0.s[3]
            vmxx_f32    \i^31, 2,  v19.4s, v21.4s, v1.s[3]
            vmxx_f32    \i^31, 4,  v19.4s, v22.4s, v2.s[3]
            vmxx_f32    \i^31, 8,  v19.4s, v23.4s, v3.s[3]
            vadd_f32    \i^31, 16, v19.4s, v19.4s, v7.4s,  v19.16b, v7.16b
            br          x8

.endr

.align 6
colormatrix_float_ldu4:
            ld4         {v20.8b,v21.8b,v22.8b,v23.8b}, [x1], #32
            uxtl        v20.8h, v20.8b
            uxtl        v21.8h, v21.8b
            uxtl        v22.8h, v22.8b
            uxtl        v23.8h, v23.8b
            uxtl        v12.4s, v20.4h
            uxtl        v13.4s, v21.4h
            uxtl        v14.4s, v22.4h
            uxtl        v15.4s, v23.4h
            uxtl2       v20.4s, v20.8h
            uxtl2       v21.4s, v21.8h
            uxtl2       v22.4s, v22.8h
            uxtl2       v23.4s, v23.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v13.4s, v13.4s
            ucvtf       v14.4s, v14.4s
            ucvtf       v15.4s, v15.4s
            ucvtf       v20.4s, v20.4s
            ucvtf       v21.4s, v21.4s
            ucvtf       v22.4s, v22.4s
            ucvtf       v23.4s, v23.4s
            br          x4

.align 5
colormatrix_int_ldu4:
            ld4         {v12.8b,v13.8b,v14.8b,v15.8b}, [x1], #32
            uxtl        v12.8h, v12.8b
            uxtl        v13.8h, v13.8b
            uxtl        v14.8h, v14.8b
            uxtl        v15.8h, v15.8b
            br          x4

.align 6
colormatrix_float_ldu3:
            ld4         {v20.8b,v21.8b,v22.8b,v23.8b}, [x1], #32
            uxtl        v20.8h, v20.8b
            uxtl        v21.8h, v21.8b
            uxtl        v22.8h, v22.8b
            uxtl        v12.4s, v20.4h
            uxtl        v13.4s, v21.4h
            uxtl        v14.4s, v22.4h
            uxtl2       v20.4s, v20.8h
            uxtl2       v21.4s, v21.8h
            uxtl2       v22.4s, v22.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v13.4s, v13.4s
            ucvtf       v14.4s, v14.4s
            ucvtf       v20.4s, v20.4s
            ucvtf       v21.4s, v21.4s
            ucvtf       v22.4s, v22.4s
            br          x4

colormatrix_int_ldu3:
            ld4         {v12.8b,v13.8b,v14.8b,v15.8b}, [x1], #32
            uxtl        v12.8h, v12.8b
            uxtl        v13.8h, v13.8b
            uxtl        v14.8h, v14.8b
            br          x4

.align 5
colormatrix_float_ldu1:
            ld1         {v20.8b}, [x1], #8
            uxtl        v20.8h, v20.8b
            uxtl        v12.4s, v20.4h
            uxtl2       v20.4s, v20.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v20.4s, v20.4s
            br          x4

.align 6
colormatrix_float_ldu2:
            ld2         {v20.8b,v21.8b}, [x1], #16
            uxtl        v20.8h, v20.8b
            uxtl        v21.8h, v21.8b
            uxtl        v12.4s, v20.4h
            uxtl        v13.4s, v21.4h
            uxtl2       v20.4s, v20.8h
            uxtl2       v21.4s, v21.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v13.4s, v13.4s
            ucvtf       v20.4s, v20.4s
            ucvtf       v21.4s, v21.4s
            br          x4

.align 4
colormatrix_int_ldu2:
            ld2         {v12.8b,v13.8b}, [x1], #16
            uxtl        v12.8h, v12.8b
            uxtl        v13.8h, v13.8b
            br          x4

.align 6
colormatrix_float_stu4:
            fcvtzs      v24.4s, v8.4s, #1
            fcvtzs      v25.4s, v9.4s, #1
            fcvtzs      v26.4s, v10.4s, #1
            fcvtzs      v27.4s, v11.4s, #1
            fcvtzs      v28.4s, v16.4s, #1
            fcvtzs      v29.4s, v17.4s, #1
            fcvtzs      v30.4s, v18.4s, #1
            fcvtzs      v31.4s, v19.4s, #1
            sqrshrun    v24.4h, v24.4s, #1
            sqrshrun    v25.4h, v25.4s, #1
            sqrshrun    v26.4h, v26.4s, #1
            sqrshrun    v27.4h, v27.4s, #1
            sqrshrun2   v24.8h, v28.4s, #1
            sqrshrun2   v25.8h, v29.4s, #1
            sqrshrun2   v26.8h, v30.4s, #1
            sqrshrun2   v27.8h, v31.4s, #1
            uqxtn       v24.8b, v24.8h
            uqxtn       v25.8b, v25.8h
            uqxtn       v26.8b, v26.8h
            uqxtn       v27.8b, v27.8h
            subs        x2, x2, #8
            st4         {v24.8b,v25.8b,v26.8b,v27.8b}, [x0], #32
            blo         colormatrix_float_end
            br          x9

.align 5
colormatrix_int_stu4:
            uqxtn       v12.8b, v8.8h
            uqxtn       v13.8b, v9.8h
            uqxtn       v14.8b, v10.8h
            uqxtn       v15.8b, v11.8h
            subs        x2, x2, #8
            st4         {v12.8b,v13.8b,v14.8b,v15.8b}, [x0], #32
            blo         colormatrix_int_end
            br          x9

.align 6
colormatrix_float_stu3:
            fcvtzs      v24.4s, v8.4s, #1
            fcvtzs      v25.4s, v9.4s, #1
            fcvtzs      v26.4s, v10.4s, #1
            fcvtzs      v28.4s, v16.4s, #1
            fcvtzs      v29.4s, v17.4s, #1
            fcvtzs      v30.4s, v18.4s, #1
            sqrshrun    v24.4h, v24.4s, #1
            sqrshrun    v25.4h, v25.4s, #1
            sqrshrun    v26.4h, v26.4s, #1
            sqrshrun2   v24.8h, v28.4s, #1
            sqrshrun2   v25.8h, v29.4s, #1
            sqrshrun2   v26.8h, v30.4s, #1
            uqxtn       v24.8b, v24.8h
            uqxtn       v25.8b, v25.8h
            uqxtn       v26.8b, v26.8h
            movi        v27.8b, #0
            subs        x2, x2, #8
            st4         {v24.8b,v25.8b,v26.8b,v27.8b}, [x0], #32
            blo         colormatrix_float_end
            br          x9

.align 4
colormatrix_int_ldu1:
            ld1         {v12.8b}, [x1], #8
            uxtl        v12.8h, v12.8b
            br          x4

.align 5
colormatrix_int_stu3:
            uqxtn       v12.8b, v8.8h
            uqxtn       v13.8b, v9.8h
            uqxtn       v14.8b, v10.8h
            movi        v15.8b, #0
            subs        x2, x2, #8
            st4         {v12.8b,v13.8b,v14.8b,v15.8b}, [x0], #32
            blo         colormatrix_int_end
            br          x9

.align 6
colormatrix_float_stu2:
            fcvtzs      v24.4s, v8.4s, #1
            fcvtzs      v25.4s, v9.4s, #1
            fcvtzs      v28.4s, v16.4s, #1
            fcvtzs      v29.4s, v17.4s, #1
            sqrshrun    v24.4h, v24.4s, #1
            sqrshrun    v25.4h, v25.4s, #1
            sqrshrun2   v24.8h, v28.4s, #1
            sqrshrun2   v25.8h, v29.4s, #1
            uqxtn       v24.8b, v24.8h
            uqxtn       v25.8b, v25.8h
            subs        x2, x2, #8
            st2         {v24.8b,v25.8b}, [x0], #16
            blo         colormatrix_float_end
            br          x9

.align 5
colormatrix_int_stu2:
            uqxtn       v12.8b, v8.8h
            uqxtn       v13.8b, v9.8h
            subs        x2, x2, #8
            st2         {v12.8b,v13.8b}, [x0], #16
            blo         colormatrix_int_end
            br          x9

.align 5
colormatrix_int_stu1:
            uqxtn       v12.8b, v8.8h
            subs        x2, x2, #8
            st1         {v12.8b}, [x0], #8
            blo         colormatrix_int_end
            br          x9

colormatrix_float_ldf3:
            ld4         {v12.4s,v13.4s,v14.4s,v15.4s}, [x1], #64
            ld4         {v20.4s,v21.4s,v22.4s,v23.4s}, [x1], #64
            br          x4

.align 6
colormatrix_float_stu1:
            fcvtzs      v24.4s, v8.4s, #1
            fcvtzs      v28.4s, v16.4s, #1
            sqrshrun    v24.4h, v24.4s, #1
            sqrshrun2   v24.8h, v28.4s, #1
            uqxtn       v24.8b, v24.8h
            subs        x2, x2, #8
            st1         {v24.8b}, [x0], #8
            blo         colormatrix_float_end
            br          x9

colormatrix_float_stf3:
            movi        v11.16b, #0
            st4         {v8.4s,v9.4s,v10.4s,v11.4s}, [x0], #64
            movi        v19.16b, #0
            subs        x2, x2, #8
            st4         {v16.4s,v17.4s,v18.4s,v19.4s}, [x0], #64
            blo         colormatrix_float_end
            br          x9

.align 5
colormatrix_float_stf4:
            st4         {v8.4s,v9.4s,v10.4s,v11.4s}, [x0], #64
            subs        x2, x2, #8
            st4         {v16.4s,v17.4s,v18.4s,v19.4s}, [x0], #64
            blo         colormatrix_float_end
            br          x9

colormatrix_float_ldf4:
            ld4         {v12.4s,v13.4s,v14.4s,v15.4s}, [x1], #64
            ld4         {v20.4s,v21.4s,v22.4s,v23.4s}, [x1], #64
            br          x4

.align 5
colormatrix_float_stf2:
            st2         {v8.4s, v9.4s}, [x0], #32
            subs        x2, x2, #8
            st2         {v16.4s, v17.4s}, [x0], #32
            blo         colormatrix_float_end
            br          x9

colormatrix_float_ldf2:
            ld2         {v12.4s,v13.4s}, [x1], #32
            ld2         {v20.4s,v21.4s}, [x1], #32
            br          x4

.align 5
colormatrix_float_stf1:
            st1         {v8.4s}, [x0], #16
            subs        x2, x2, #8
            st1         {v16.4s}, [x0], #16
            blo         colormatrix_float_end
            br          x9

colormatrix_float_ldf1:
            ld1         {v12.4s}, [x1], #16
            ld1         {v20.4s}, [x1], #16
            br          x4

colormatrix_int_stu1_end:
            uqxtn       v12.8b, v8.8h
            tbz         x2, #2, 1f
            st1         {v12.s}[1], [x0], #4
1:          tbz         x2, #1, 1f
            st1         {v12.h}[1], [x0], #2
1:          tbz         x2, #0, 1f
            st1         {v12.b}[1], [x0], #1
1:          b           colormatrix_int_realend

colormatrix_int_stu2_end:
            uqxtn       v12.8b, v8.8h
            uqxtn       v13.8b, v9.8h
            zip1        v12.16b, v12.16b, v13.16b
            tbz         x2, #2, 1f
            st1         {v12.d}[1], [x0], #8
1:          tbz         x2, #1, 1f
            st1         {v12.s}[1], [x0], #4
1:          tbz         x2, #0, 1f
            st1         {v12.h}[1], [x0], #2
1:          b           colormatrix_int_realend

colormatrix_int_stu3_end:
            uqxtn       v12.8b, v8.8h
            uqxtn       v13.8b, v9.8h
            uqxtn       v14.8b, v10.8h
            movi        v15.8b, #0
            tbz         x2, #2, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[4], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[5], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[6], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[7], [x0], #4
1:          tbz         x2, #1, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[2], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[3], [x0], #4
1:          tbz         x2, #0, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[1], [x0], #4
1:          b           colormatrix_int_realend

colormatrix_int_stu4_end:
            uqxtn       v12.8b, v8.8h
            uqxtn       v13.8b, v9.8h
            uqxtn       v14.8b, v10.8h
            uqxtn       v15.8b, v11.8h
            tbz         x2, #2, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[4], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[5], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[6], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[7], [x0], #4
1:          tbz         x2, #1, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[2], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[3], [x0], #4
1:          tbz         x2, #0, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[1], [x0], #4
1:          b           colormatrix_int_realend


colormatrix_int_ldu1_end:
            tbz         x2, #2, 1f
            ld1         {v15.s}[3], [x1], #4
1:          tbz         x2, #1, 1f
            ld1         {v15.h}[5], [x1], #2
1:          tbz         x2, #0, 1f
            ld1         {v15.b}[9], [x1], #1
1:          uxtl2       v12.8h, v15.16b
            br          x4

colormatrix_int_ldu2_end:
            tbz         x2, #2, 1f
            ld1         {v15.d}[1], [x1], #8
1:          tbz         x2, #1, 1f
            ld1         {v15.s}[1], [x1], #4
1:          tbz         x2, #0, 1f
            ld1         {v15.h}[1], [x1], #2
1:          uzp1        v14.16b, v15.16b, v15.16b
            uzp2        v15.16b, v15.16b, v15.16b
            uxtl        v12.8h, v14.8b
            uxtl        v13.8h, v15.8b
            br          x4

colormatrix_int_ldu3_end:
            tbz         x2, #2, 1f
            ld4         {v12.b,v13.b,v14.b,v15.b}[4], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[5], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[6], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[7], [x1], #4
1:          tbz         x2, #1, 1f
            ld4         {v12.b,v13.b,v14.b,v15.b}[2], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[3], [x1], #4
1:          tbz         x2, #0, 1f
            ld4         {v12.b,v13.b,v14.b,v15.b}[1], [x1], #4
1:          uxtl        v12.8h, v12.8b
            uxtl        v13.8h, v13.8b
            uxtl        v14.8h, v14.8b
            br          x4

colormatrix_int_ldu4_end:
            tbz         x2, #2, 1f
            ld4         {v12.b,v13.b,v14.b,v15.b}[4], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[5], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[6], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[7], [x1], #4
1:          tbz         x2, #1, 1f
            ld4         {v12.b,v13.b,v14.b,v15.b}[2], [x1], #4
            ld4         {v12.b,v13.b,v14.b,v15.b}[3], [x1], #4
1:          tbz         x2, #0, 1f
            ld4         {v12.b,v13.b,v14.b,v15.b}[1], [x1], #4
1:          uxtl        v12.8h, v12.8b
            uxtl        v13.8h, v13.8b
            uxtl        v14.8h, v14.8b
            uxtl        v15.8h, v15.8b
            br          x4

colormatrix_float_stu1_end:
            fcvtzs      v12.4s, v8.4s, #1
            fcvtzs      v13.4s, v16.4s, #1
            sqrshrun    v12.4h, v12.4s, #1
            sqrshrun2   v12.8h, v13.4s, #1
            uqxtn       v12.8b, v12.8h
            tbz         x2, #2, 1f
            st1         {v12.s}[1], [x0], #4
1:          tbz         x2, #1, 1f
            st1         {v12.h}[1], [x0], #2
1:          tbz         x2, #0, 1f
            st1         {v12.b}[1], [x0], #1
1:          b           colormatrix_float_realend

colormatrix_float_stu2_end:
            fcvtzs      v12.4s, v8.4s, #1
            fcvtzs      v13.4s, v9.4s, #1
            fcvtzs      v14.4s, v16.4s, #1
            fcvtzs      v15.4s, v17.4s, #1
            sqrshrun    v12.4h, v12.4s, #1
            sqrshrun    v13.4h, v13.4s, #1
            sqrshrun    v14.4h, v14.4s, #1
            sqrshrun    v15.4h, v15.4s, #1
            zip1        v12.8h, v12.8h, v13.8h
            zip1        v13.8h, v14.8h, v15.8h
            uqxtn       v12.8b, v12.8h
            uqxtn2      v12.16b, v13.8h
            tbz         x2, #2, 1f
            st1         {v12.d}[1], [x0], #8
1:          tbz         x2, #1, 1f
            st1         {v12.s}[1], [x0], #4
1:          tbz         x2, #0, 1f
            st1         {v12.h}[1], [x0], #2
1:          b           colormatrix_float_realend

colormatrix_float_stu3_end:
            fcvtzs      v24.4s, v8.4s, #1
            fcvtzs      v25.4s, v9.4s, #1
            fcvtzs      v26.4s, v10.4s, #1
            fcvtzs      v28.4s, v16.4s, #1
            fcvtzs      v29.4s, v17.4s, #1
            fcvtzs      v30.4s, v18.4s, #1
            sqrshrun    v24.4h, v24.4s, #1
            sqrshrun    v25.4h, v25.4s, #1
            sqrshrun    v26.4h, v26.4s, #1
            sqrshrun2   v24.8h, v28.4s, #1
            sqrshrun2   v25.8h, v29.4s, #1
            sqrshrun2   v26.8h, v30.4s, #1
            uqxtn       v12.8b, v24.8h
            uqxtn       v13.8b, v25.8h
            uqxtn       v14.8b, v26.8h
            movi        v15.8b, #0
            tbz         x2, #2, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[4], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[5], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[6], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[7], [x0], #4
1:          tbz         x2, #1, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[2], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[3], [x0], #4
1:          tbz         x2, #0, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[1], [x0], #4
1:          b           colormatrix_float_realend

colormatrix_float_stu4_end:
            fcvtzs      v24.4s, v8.4s, #1
            fcvtzs      v25.4s, v9.4s, #1
            fcvtzs      v26.4s, v10.4s, #1
            fcvtzs      v27.4s, v11.4s, #1
            fcvtzs      v28.4s, v16.4s, #1
            fcvtzs      v29.4s, v17.4s, #1
            fcvtzs      v30.4s, v18.4s, #1
            fcvtzs      v31.4s, v19.4s, #1
            sqrshrun    v24.4h, v24.4s, #1
            sqrshrun    v25.4h, v25.4s, #1
            sqrshrun    v26.4h, v26.4s, #1
            sqrshrun    v27.4h, v27.4s, #1
            sqrshrun2   v24.8h, v28.4s, #1
            sqrshrun2   v25.8h, v29.4s, #1
            sqrshrun2   v26.8h, v30.4s, #1
            sqrshrun2   v27.8h, v31.4s, #1
            uqxtn       v12.8b, v24.8h
            uqxtn       v13.8b, v25.8h
            uqxtn       v14.8b, v26.8h
            uqxtn       v15.8b, v27.8h
            tbz         x2, #2, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[4], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[5], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[6], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[7], [x0], #4
1:          tbz         x2, #1, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[2], [x0], #4
            st4         {v12.b,v13.b,v14.b,v15.b}[3], [x0], #4
1:          tbz         x2, #0, 1f
            st4         {v12.b,v13.b,v14.b,v15.b}[1], [x0], #4
1:          b           colormatrix_float_realend

colormatrix_float_stf1_end:
            tbz         x2, #2, 1f
            st1         {v16.4s}, [x0], #16
1:          tbz         x2, #1, 1f
            st1         {v8.d}[1], [x0], #8
1:          tbz         x2, #0, 1f
            st1         {v8.s}[1], [x0], #4
1:          b           colormatrix_float_realend

colormatrix_float_stf2_end:
            tbz         x2, #2, 1f
            st2         {v16.4s, v17.4s}, [x0], #32
1:          tbz         x2, #1, 1f
            st2         {v8.s,v9.s}[2], [x0], #8
            st2         {v8.s,v9.s}[3], [x0], #8
1:          tbz         x2, #0, 1f
            st2         {v8.s,v9.s}[1], [x0], #8
1:          b           colormatrix_float_realend

colormatrix_float_stf3_end:
            movi        v11.16b, #0
            movi        v19.16b, #0
colormatrix_float_stf4_end:
            tbz         x2, #2, 1f
            st4         {v16.4s,v17.4s,v18.4s,v19.4s}, [x0], #64
1:          tbz         x2, #1, 1f
            st4         {v8.s,v9.s,v10.s,v11.s}[2], [x0], #16
            st4         {v8.s,v9.s,v10.s,v11.s}[3], [x0], #16
1:          tbz         x2, #0, 1f
            st4         {v8.s,v9.s,v10.s,v11.s}[1], [x0], #16
1:          b           colormatrix_float_realend

colormatrix_float_ldu1_end:
            tbz         x2, #2, 1f
            ld1         {v15.s}[1], [x1], #4
1:          tbz         x2, #1, 1f
            ld1         {v15.h}[1], [x1], #2
1:          tbz         x2, #0, 1f
            ld1         {v15.b}[1], [x1], #1
1:          uxtl        v15.8h, v15.8b
            uxtl        v12.4s, v15.4h
            uxtl2       v20.4s, v15.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v20.4s, v20.4s
            br          x4

colormatrix_float_ldu2_end:
            tbz         x2, #2, 1f
            ld1         {v15.d}[1], [x1], #8
1:          tbz         x2, #1, 1f
            ld1         {v15.s}[1], [x1], #4
1:          tbz         x2, #0, 1f
            ld1         {v15.h}[1], [x1], #2
1:          uxtl        v14.8h, v15.8b
            uxtl2       v15.8h, v15.16b
            uzp1        v12.8h, v14.8h, v14.8h
            uzp2        v13.8h, v14.8h, v14.8h
            uzp1        v20.8h, v15.8h, v15.8h
            uzp2        v21.8h, v15.8h, v15.8h
            uxtl        v12.4s, v12.4h
            uxtl        v13.4s, v13.4h
            uxtl        v20.4s, v20.4h
            uxtl        v21.4s, v21.4h
            ucvtf       v12.4s, v12.4s
            ucvtf       v13.4s, v13.4s
            ucvtf       v20.4s, v20.4s
            ucvtf       v21.4s, v21.4s
            br          x4

colormatrix_float_ldu3_end:
            tbz         x2, #2, 1f
            ld4         {v20.b,v21.b,v22.b,v23.b}[4], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[5], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[6], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[7], [x1], #4
1:          tbz         x2, #1, 1f
            ld4         {v20.b,v21.b,v22.b,v23.b}[2], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[3], [x1], #4
1:          tbz         x2, #0, 1f
            ld4         {v20.b,v21.b,v22.b,v23.b}[1], [x1], #4
1:          uxtl        v20.8h, v20.8b
            uxtl        v21.8h, v21.8b
            uxtl        v22.8h, v22.8b
            uxtl        v12.4s, v20.4h
            uxtl        v13.4s, v21.4h
            uxtl        v14.4s, v22.4h
            uxtl2       v20.4s, v20.8h
            uxtl2       v21.4s, v21.8h
            uxtl2       v22.4s, v22.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v13.4s, v13.4s
            ucvtf       v14.4s, v14.4s
            ucvtf       v20.4s, v20.4s
            ucvtf       v21.4s, v21.4s
            ucvtf       v22.4s, v22.4s
            br          x4

colormatrix_float_ldu4_end:
            tbz         x2, #2, 1f
            ld4         {v20.b,v21.b,v22.b,v23.b}[4], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[5], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[6], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[7], [x1], #4
1:          tbz         x2, #1, 1f
            ld4         {v20.b,v21.b,v22.b,v23.b}[2], [x1], #4
            ld4         {v20.b,v21.b,v22.b,v23.b}[3], [x1], #4
1:          tbz         x2, #0, 1f
            ld4         {v20.b,v21.b,v22.b,v23.b}[1], [x1], #4
1:          uxtl        v20.8h, v20.8b
            uxtl        v21.8h, v21.8b
            uxtl        v22.8h, v22.8b
            uxtl        v23.8h, v23.8b
            uxtl        v12.4s, v20.4h
            uxtl        v13.4s, v21.4h
            uxtl        v14.4s, v22.4h
            uxtl        v15.4s, v23.4h
            uxtl2       v20.4s, v20.8h
            uxtl2       v21.4s, v21.8h
            uxtl2       v22.4s, v22.8h
            uxtl2       v23.4s, v23.8h
            ucvtf       v12.4s, v12.4s
            ucvtf       v13.4s, v13.4s
            ucvtf       v14.4s, v14.4s
            ucvtf       v15.4s, v15.4s
            ucvtf       v20.4s, v20.4s
            ucvtf       v21.4s, v21.4s
            ucvtf       v22.4s, v22.4s
            ucvtf       v23.4s, v23.4s
            br          x4

colormatrix_float_ldf1_end:
            tbz         x2, #2, 1f
            ld1         {v20.4s}, [x1], #16
1:          tbz         x2, #1, 1f
            ld1         {v12.d}[1], [x1], #8
1:          tbz         x2, #0, 1f
            ld1         {v12.s}[1], [x1], #4
1:          br          x4

colormatrix_float_ldf2_end:
            tbz         x2, #2, 1f
            ld2         {v20.4s,v21.4s}, [x1], #32
1:          tbz         x2, #1, 1f
            ld2         {v12.s,v13.s}[2], [x1], #8
            ld2         {v12.s,v13.s}[3], [x1], #8
1:          tbz         x2, #0, 1f
            ld2         {v12.s,v13.s}[1], [x1], #8
1:          br          x4

colormatrix_float_ldf3_end:
colormatrix_float_ldf4_end:
            tbz         x2, #2, 1f
            ld4         {v20.4s,v21.4s,v22.4s,v23.4s}, [x1], #64
1:          tbz         x2, #1, 1f
            ld4         {v12.s,v13.s,v14.s,v15.s}[2], [x1], #16
            ld4         {v12.s,v13.s,v14.s,v15.s}[3], [x1], #16
1:          tbz         x2, #0, 1f
            ld4         {v12.s,v13.s,v14.s,v15.s}[1], [x1], #16
1:          br          x4

/* void rsdIntrinsicColorMatrix_int_K(
 *          void *out,              // x0
 *          void const *in,         // x1
 *          size_t count,           // x2
 *          fntab_t const *fns,     // x3
 *          int16_t const *mult,    // x4
 *          int32_t const *add);    // x5
 */
ENTRY(rsdIntrinsicColorMatrix_int_K)
            sub         x7, sp, #32
            sub         sp, sp, #64
            st1         {v8.1d-v11.1d}, [sp]
            st1         {v12.1d-v15.1d}, [x7]

            ld1         {v0.8h,v1.8h}, [x4], #32
            ld1         {v4.4s}, [x5], #16

            ldp         x4,x5, [x3],#16
            ldp         x6,x7, [x3],#16
            ldp         x8,x9, [x3],#16

            dup         v12.4s, v4.s[0]
            dup         v13.4s, v4.s[1]
            dup         v14.4s, v4.s[2]
            dup         v15.4s, v4.s[3]
            sqshrun     v8.4h, v12.4s, #8
            sqshrun2    v8.8h, v12.4s, #8
            sqshrun     v9.4h, v13.4s, #8
            sqshrun2    v9.8h, v13.4s, #8
            sqshrun     v10.4h, v14.4s, #8
            sqshrun2    v10.8h, v14.4s, #8
            sqshrun     v11.4h, v15.4s, #8
            sqshrun2    v11.8h, v15.4s, #8

            subs        x2, x2, #8
            blo         colormatrix_int_end
            br          x9

colormatrix_int_end:
            adds        x2, x2, #8
            bls         colormatrix_int_realend
            mov         x16, x8
            ldp         x8, x9, [x3], #16
            cmp         x4, x16
            csel        x4, x8, x4, eq
            cmp         x5, x16
            csel        x5, x8, x5, eq
            cmp         x6, x16
            csel        x6, x8, x6, eq
            cmp         x7, x16
            csel        x7, x8, x7, eq
            br          x9

colormatrix_int_realend:
            ld1         {v8.1d-v11.1d}, [sp], #32
            ld1         {v12.1d-v15.1d}, [sp], #32
            ret
END(rsdIntrinsicColorMatrix_int_K)

/* void rsdIntrinsicColorMatrixSetup_int_K(
 *          fntab_t const *fns, // x0
 *          uint32_t mask,      // x1
 *          int dt,             // x2
 *          int st);            // x3
 */
ENTRY(rsdIntrinsicColorMatrixSetup_int_K)
            adrp        x7, 2f
            add         x7, x7, :lo12:2f
            add         x4, x7, x2, LSL #3
            ldrsw       x2, [x4], #4
            ldrsw       x4, [x4]
            add         x2, x2, x7
            add         x4, x4, x7
            adrp        x7, 3f
            add         x7, x7, :lo12:3f
            add         x5, x7, x3, LSL #3
            ldrsw       x3, [x5], #4
            ldrsw       x5, [x5]
            add         x3, x3, x7
            add         x5, x5, x7
            stp         x2, x3, [x0, #32]
            stp         x4, x5, [x0, #48]

/* For each column function, if the matrix is all zeroes then write NULL,
 * otherwise look up the appropriate function and store that. */

            mov         x3, #4
            adrp        x7, 4f
            add         x7, x7, :lo12:4f
1:          ands        x2, x1, #15
            beq         9f
            and         x2, x1, #31
            lsl         x2, x2, #4
            ldrsw       x2, [x7, x2]
            add         x2, x2, x7
9:          str         x2, [x0], #8
            lsr         x1, x1, #5
            add         x7, x7, #4
            subs        x3, x3, #1
            bne         1b

/* For every NULL entry, copy the non-NULL entry that follows it, or the store
 * function. */

            ldr         x2, [x0]
            mov         x3, #4
1:          ldr         x1, [x0, #-8]!
            cmp         x1, #0
            csel        x2, x1, x2, ne
            str         x2, [x0]
            subs        x3, x3, #1
            bne         1b
            ret

END(rsdIntrinsicColorMatrixSetup_int_K)
.rodata
            .align 4
2:          .word      colormatrix_int_stu1-2b
            .word      colormatrix_int_stu1_end-2b
            .word      colormatrix_int_stu2-2b
            .word      colormatrix_int_stu2_end-2b
            .word      colormatrix_int_stu3-2b
            .word      colormatrix_int_stu3_end-2b
            .word      colormatrix_int_stu4-2b
            .word      colormatrix_int_stu4_end-2b
3:          .word      colormatrix_int_ldu1-3b
            .word      colormatrix_int_ldu1_end-3b
            .word      colormatrix_int_ldu2-3b
            .word      colormatrix_int_ldu2_end-3b
            .word      colormatrix_int_ldu3-3b
            .word      colormatrix_int_ldu3_end-3b
            .word      colormatrix_int_ldu4-3b
            .word      colormatrix_int_ldu4_end-3b
4:
.irp i, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
            .word      colormatrix_int_col0_\i-4b
            .word      colormatrix_int_col1_\i-4b-4
            .word      colormatrix_int_col2_\i-4b-8
            .word      colormatrix_int_col3_\i-4b-12
.endr
.irp i, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
            .word      colormatrix_int_col0_n\i-4b
            .word      colormatrix_int_col1_n\i-4b-4
            .word      colormatrix_int_col2_n\i-4b-8
            .word      colormatrix_int_col3_n\i-4b-12
.endr


/* void rsdIntrinsicColorMatrix_float_K(
 *          void *out,              // x0
 *          void const *in,         // x1
 *          size_t count,           // x2
 *          fntab_t const *fns,     // x3
 *          float const *mult,      // x4
 *          float const *add);      // x5
 */
ENTRY(rsdIntrinsicColorMatrix_float_K)
            sub         x7, sp, #32
            sub         sp, sp, #64
            st1         {v8.1d-v11.1d}, [sp]
            st1         {v12.1d-v15.1d}, [x7]

            ld1         {v0.4s,v1.4s,v2.4s,v3.4s}, [x4], #64
            ld1r        {v4.4s}, [x5], #4
            ld1r        {v5.4s}, [x5], #4
            ld1r        {v6.4s}, [x5], #4
            ld1r        {v7.4s}, [x5], #4

            ldp         x4,x5, [x3], #16
            ldp         x6,x7, [x3], #16
            ldp         x8,x9, [x3], #16

            mov         v8.16b, v4.16b
            mov         v9.16b, v5.16b
            mov         v10.16b, v6.16b
            mov         v11.16b, v7.16b

            mov         v16.16b, v4.16b
            mov         v17.16b, v5.16b
            mov         v18.16b, v6.16b
            mov         v19.16b, v7.16b

            subs        x2, x2, #8
            blo         colormatrix_float_end
            br          x9

colormatrix_float_end:
            adds        x2, x2, #8
            bls         colormatrix_int_realend
            mov         x16, x8
            ldp         x8,x9, [x3], #16
            cmp         x4, x16
            csel        x4, x8, x4, eq
            cmp         x5, x16
            csel        x5, x8, x5, eq
            cmp         x6, x16
            csel        x6, x8, x6, eq
            cmp         x7, x16
            csel        x7, x8, x7, eq
            br          x9

colormatrix_float_realend:
            ld1         {v8.1d-v11.1d}, [sp], #32
            ld1         {v12.1d-v15.1d}, [sp], #32
            ret
END(rsdIntrinsicColorMatrix_float_K)

/* void rsdIntrinsicColorMatrixSetup_float_K(
 *          fntab_t const *fns, // x0
 *          uint32_t mask,      // x1
 *          int dt,             // x2
 *          int st);            // x3
 */
ENTRY(rsdIntrinsicColorMatrixSetup_float_K)
            adrp        x7, 2f
            add         x7, x7, :lo12:2f
            add         x4, x7, x2, LSL #3
            ldrsw       x2, [x4], #4
            ldrsw       x4, [x4]
            add         x2, x2, x7
            add         x4, x4, x7
            adrp        x7, 3f
            add         x7, x7, :lo12:3f
            add         x5, x7, x3, LSL #3
            ldrsw       x3, [x5], #4
            ldrsw       x5, [x5]
            add         x3, x3, x7
            add         x5, x5, x7
            stp         x2, x3, [x0, #32]
            stp         x4, x5, [x0, #48]

/* For each column function, if the matrix is all zeroes then write NULL,
 * otherwise look up the appropriate function and store that. */

            mov         x3, #4
            adrp        x7, 4f
            add         x7, x7, :lo12:4f
1:          ands        x2, x1, #15
            beq         9f
            and         x2, x1, #31
            lsl         x2, x2, #4
            ldrsw       x2, [x7, x2]
            add         x2, x2, x7
9:          str         x2, [x0], #8
            lsr         x1, x1, #5
            add         x7, x7, #4
            subs        x3, x3, #1
            bne         1b

/* For every NULL entry, copy the non-NULL entry that follows it, or the store
 * function. */

            ldr         x2, [x0]
            mov         x3, #4
1:          ldr         x1, [x0, #-8]!
            cmp         x1, #0
            csel        x2, x1, x2, ne
            str         x2, [x0]
            subs        x3, x3, #1
            bne         1b
            ret

END(rsdIntrinsicColorMatrixSetup_float_K)
.rodata
            .align 4
2:          .word      colormatrix_float_stu1-2b
            .word      colormatrix_float_stu1_end-2b
            .word      colormatrix_float_stu2-2b
            .word      colormatrix_float_stu2_end-2b
            .word      colormatrix_float_stu3-2b
            .word      colormatrix_float_stu3_end-2b
            .word      colormatrix_float_stu4-2b
            .word      colormatrix_float_stu4_end-2b
            .word      colormatrix_float_stf1-2b
            .word      colormatrix_float_stf1_end-2b
            .word      colormatrix_float_stf2-2b
            .word      colormatrix_float_stf2_end-2b
            .word      colormatrix_float_stf3-2b
            .word      colormatrix_float_stf3_end-2b
            .word      colormatrix_float_stf4-2b
            .word      colormatrix_float_stf4_end-2b
3:          .word      colormatrix_float_ldu1-3b
            .word      colormatrix_float_ldu1_end-3b
            .word      colormatrix_float_ldu2-3b
            .word      colormatrix_float_ldu2_end-3b
            .word      colormatrix_float_ldu3-3b
            .word      colormatrix_float_ldu3_end-3b
            .word      colormatrix_float_ldu4-3b
            .word      colormatrix_float_ldu4_end-3b
            .word      colormatrix_float_ldf1-3b
            .word      colormatrix_float_ldf1_end-3b
            .word      colormatrix_float_ldf2-3b
            .word      colormatrix_float_ldf2_end-3b
            .word      colormatrix_float_ldf3-3b
            .word      colormatrix_float_ldf3_end-3b
            .word      colormatrix_float_ldf4-3b
            .word      colormatrix_float_ldf4_end-3b
4:
.irp i, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
            .word      colormatrix_float_col0_\i-4b
            .word      colormatrix_float_col1_\i-4b-4
            .word      colormatrix_float_col2_\i-4b-8
            .word      colormatrix_float_col3_\i-4b-12
.endr
.irp i, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
            .word      colormatrix_float_col0_n\i-4b
            .word      colormatrix_float_col1_n\i-4b-4
            .word      colormatrix_float_col2_n\i-4b-8
            .word      colormatrix_float_col3_n\i-4b-12
.endr
