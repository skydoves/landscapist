/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <stdint.h>
#include <x86intrin.h>

namespace renderscript {

/* Unsigned extend packed 8-bit integer (in LBS) into packed 32-bit integer */
static inline __m128i cvtepu8_epi32(__m128i x) {
#if defined(__SSE4_1__)
    return _mm_cvtepu8_epi32(x);
#elif defined(__SSSE3__)
    const __m128i M8to32 = _mm_set_epi32(0xffffff03, 0xffffff02, 0xffffff01, 0xffffff00);
    x = _mm_shuffle_epi8(x, M8to32);
    return x;
#else
#   error "Require at least SSSE3"
#endif
}

static inline __m128i packus_epi32(__m128i lo, __m128i hi) {
#if defined(__SSE4_1__)
    return _mm_packus_epi32(lo, hi);
#elif defined(__SSSE3__)
    const __m128i C0 = _mm_set_epi32(0x0000, 0x0000, 0x0000, 0x0000);
    const __m128i C1 = _mm_set_epi32(0xffff, 0xffff, 0xffff, 0xffff);
    const __m128i M32to16L = _mm_set_epi32(0xffffffff, 0xffffffff, 0x0d0c0908, 0x05040100);
    const __m128i M32to16H = _mm_set_epi32(0x0d0c0908, 0x05040100, 0xffffffff, 0xffffffff);
    lo = _mm_and_si128(lo, _mm_cmpgt_epi32(lo, C0));
    lo = _mm_or_si128(lo, _mm_cmpgt_epi32(lo, C1));
    hi = _mm_and_si128(hi, _mm_cmpgt_epi32(hi, C0));
    hi = _mm_or_si128(hi, _mm_cmpgt_epi32(hi, C1));
    return _mm_or_si128(_mm_shuffle_epi8(lo, M32to16L),
                        _mm_shuffle_epi8(hi, M32to16H));
#else
#   error "Require at least SSSE3"
#endif
}

static inline __m128i mullo_epi32(__m128i x, __m128i y) {
#if defined(__SSE4_1__)
    return _mm_mullo_epi32(x, y);
#elif defined(__SSSE3__)
    const __m128i Meven = _mm_set_epi32(0x00000000, 0xffffffff, 0x00000000, 0xffffffff);
    __m128i even = _mm_mul_epu32(x, y);
    __m128i odd = _mm_mul_epu32(_mm_srli_si128(x, 4),
                                _mm_srli_si128(y, 4));
    even = _mm_and_si128(even, Meven);
    odd = _mm_and_si128(odd, Meven);
    return _mm_or_si128(even, _mm_slli_si128(odd, 4));
#else
#   error "Require at least SSSE3"
#endif
}

/* 'mask' must packed 8-bit of 0x00 or 0xff */
static inline __m128i blendv_epi8(__m128i x, __m128i y, __m128i mask) {
#if defined(__SSE4_1__)
    return _mm_blendv_epi8(x, y, mask);
#elif defined(__SSSE3__)
    return _mm_or_si128(_mm_andnot_si128(mask, x), _mm_and_si128(y, mask));
#else
#   error "Require at least SSSE3"
#endif
}

extern "C" void rsdIntrinsicConvolve3x3_K(void *dst, const void *y0,
                                          const void *y1, const void *y2,
                                          const short *coef, uint32_t count) {
    __m128i x;
    __m128i c0, c2, c4, c6, c8;
    __m128i r0, r1, r2;
    __m128i p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11;
    __m128i o0, o1;
    uint32_t i;

    x = _mm_loadl_epi64((const __m128i *)(coef+0));
    c0 = _mm_shuffle_epi32(x, 0x00);
    c2 = _mm_shuffle_epi32(x, 0x55);
    x = _mm_loadl_epi64((const __m128i *)(coef+4));
    c4 = _mm_shuffle_epi32(x, 0x00);
    c6 = _mm_shuffle_epi32(x, 0x55);
    x = _mm_loadl_epi64((const __m128i *)(coef+8));
    c8 = _mm_shuffle_epi32(x, 0x00);

    for (i = 0; i < count; ++i) {

        p0 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0)), _mm_setzero_si128());
        p1 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+1)), _mm_setzero_si128());
        p2 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+2)), _mm_setzero_si128());
        p3 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+3)), _mm_setzero_si128());
        p4 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1)), _mm_setzero_si128());
        p5 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+1)), _mm_setzero_si128());
        p6 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+2)), _mm_setzero_si128());
        p7 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+3)), _mm_setzero_si128());
        p8 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2)), _mm_setzero_si128());
        p9 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+1)), _mm_setzero_si128());
        p10 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+2)), _mm_setzero_si128());
        p11 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+3)), _mm_setzero_si128());

        o0 = _mm_madd_epi16(_mm_unpacklo_epi16(p0, p1), c0);
        o1 = _mm_madd_epi16(_mm_unpacklo_epi16(p1, p2), c0);

        o0 = _mm_add_epi32(o0, _mm_madd_epi16(_mm_unpacklo_epi16(p2, p4), c2));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16(_mm_unpacklo_epi16(p3, p5), c2));

        o0 = _mm_add_epi32(o0, _mm_madd_epi16(_mm_unpacklo_epi16(p5, p6), c4));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16(_mm_unpacklo_epi16(p6, p7), c4));

        o0 = _mm_add_epi32(o0, _mm_madd_epi16(_mm_unpacklo_epi16(p8, p9), c6));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16(_mm_unpacklo_epi16(p9, p10), c6));

        o0 = _mm_add_epi32(o0, _mm_madd_epi16(_mm_unpacklo_epi16(p10, _mm_setzero_si128()), c8));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16(_mm_unpacklo_epi16(p11, _mm_setzero_si128()), c8));

        o0 = _mm_srai_epi32(o0, 8);
        o1 = _mm_srai_epi32(o1, 8);

        o0 = packus_epi32(o0, o1);
        o0 = _mm_packus_epi16(o0, o0);
        _mm_storel_epi64((__m128i *)dst, o0);

        y0 = (const char *)y0 + 8;
        y1 = (const char *)y1 + 8;
        y2 = (const char *)y2 + 8;
        dst = (char *)dst + 8;
    }
}

void rsdIntrinsicColorMatrix4x4_K(void *dst, const void *src,
                                  const short *coef, uint32_t count) {
    const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                      14, 10, 6, 2,
                                      13,  9, 5, 1,
                                      12,  8, 4, 0);

    const __m128i Mxy = _mm_set_epi32(0xff0dff0c, 0xff09ff08, 0xff05ff04, 0xff01ff00);
    const __m128i Mzw = _mm_set_epi32(0xff0fff0e, 0xff0bff0a, 0xff07ff06, 0xff03ff02);
    __m128i c0, c1, c2, c3;
    __m128i i4, o4;
    __m128i xy, zw;
    __m128i x2, y2, z2, w2;
    uint32_t i;

    c0 = _mm_loadl_epi64((const __m128i *)(coef+0));
    c1 = _mm_loadl_epi64((const __m128i *)(coef+4));
    c0 = _mm_unpacklo_epi16(c0, c1);

    c2 = _mm_loadl_epi64((const __m128i *)(coef+8));
    c3 = _mm_loadl_epi64((const __m128i *)(coef+12));
    c2 = _mm_unpacklo_epi16(c2, c3);

    for (i = 0; i < count; ++i) {
        i4 = _mm_load_si128((const __m128i *)src);
        xy = _mm_shuffle_epi8(i4, Mxy);
        zw = _mm_shuffle_epi8(i4, Mzw);

        x2 = _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0x00));
        y2 = _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0x55));
        z2 = _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0xaa));
        w2 = _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0xff));

        x2 = _mm_add_epi32(x2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0x00)));
        y2 = _mm_add_epi32(y2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0x55)));
        z2 = _mm_add_epi32(z2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0xaa)));
        w2 = _mm_add_epi32(w2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0xff)));

        x2 = _mm_srai_epi32(x2, 8);
        y2 = _mm_srai_epi32(y2, 8);
        z2 = _mm_srai_epi32(z2, 8);
        w2 = _mm_srai_epi32(w2, 8);

        x2 = packus_epi32(x2, y2);
        z2 = packus_epi32(z2, w2);
        o4 = _mm_packus_epi16(x2, z2);

        o4 = _mm_shuffle_epi8(o4, T4x4);
        _mm_storeu_si128((__m128i *)dst, o4);

        src = (const char *)src + 16;
        dst = (char *)dst + 16;
    }
}

void rsdIntrinsicColorMatrix3x3_K(void *dst, const void *src,
                                  const short *coef, uint32_t count) {
    const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                      14, 10, 6, 2,
                                      13,  9, 5, 1,
                                      12,  8, 4, 0);

    const __m128i Mxy = _mm_set_epi32(0xff0dff0c, 0xff09ff08, 0xff05ff04, 0xff01ff00);
    const __m128i Mzw = _mm_set_epi32(0xff0fff0e, 0xff0bff0a, 0xff07ff06, 0xff03ff02);

    __m128i c0, c1, c2, c3;
    __m128i i4, o4;
    __m128i xy, zw;
    __m128i x2, y2, z2, w2;
    uint32_t i;

    c0 = _mm_loadl_epi64((const __m128i *)(coef+0));
    c1 = _mm_loadl_epi64((const __m128i *)(coef+4));
    c0 = _mm_unpacklo_epi16(c0, c1);

    c2 = _mm_loadl_epi64((const __m128i *)(coef+8));
    c3 = _mm_loadl_epi64((const __m128i *)(coef+12));
    c2 = _mm_unpacklo_epi16(c2, c3);

    for (i = 0; i < count; ++i) {
        i4 = _mm_loadu_si128((const __m128i *)src);
        xy = _mm_shuffle_epi8(i4, Mxy);
        zw = _mm_shuffle_epi8(i4, Mzw);

        x2 =  _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0x00));
        y2 =  _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0x55));
        z2 =  _mm_madd_epi16(xy, _mm_shuffle_epi32(c0, 0xaa));

        x2 = _mm_add_epi32(x2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0x00)));
        y2 = _mm_add_epi32(y2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0x55)));
        z2 = _mm_add_epi32(z2, _mm_madd_epi16(zw, _mm_shuffle_epi32(c2, 0xaa)));

        x2 = _mm_srai_epi32(x2, 8);
        y2 = _mm_srai_epi32(y2, 8);
        z2 = _mm_srai_epi32(z2, 8);
        w2 = _mm_srli_epi32(zw, 16);

        x2 = packus_epi32(x2, y2);
        z2 = packus_epi32(z2, w2);
        o4 = _mm_packus_epi16(x2, z2);

        o4 = _mm_shuffle_epi8(o4, T4x4);
        _mm_storeu_si128((__m128i *)dst, o4);

        src = (const char *)src + 16;
        dst = (char *)dst + 16;
    }
}

void rsdIntrinsicColorMatrixDot_K(void *dst, const void *src,
                                  const short *coef, uint32_t count) {
    const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                      14, 10, 6, 2,
                                      13,  9, 5, 1,
                                      12,  8, 4, 0);
    const __m128i Mxy = _mm_set_epi32(0xff0dff0c, 0xff09ff08, 0xff05ff04, 0xff01ff00);
    const __m128i Mzw = _mm_set_epi32(0xff0fff0e, 0xff0bff0a, 0xff07ff06, 0xff03ff02);
    __m128i c0, c1, c2, c3;
    __m128i i4, o4;
    __m128i xy, zw;
    __m128i x2, y2, z2, w2;
    uint32_t i;

    c0 = _mm_loadl_epi64((const __m128i *)(coef+0));
    c0 = _mm_shufflelo_epi16(c0, 0);
    c1 = _mm_loadl_epi64((const __m128i *)(coef+4));
    c1 = _mm_shufflelo_epi16(c1, 0);
    c0 = _mm_unpacklo_epi16(c0, c1);

    c2 = _mm_loadl_epi64((const __m128i *)(coef+8));
    c2 = _mm_shufflelo_epi16(c2, 0);
    c3 = _mm_loadl_epi64((const __m128i *)(coef+12));
    c3 = _mm_shufflelo_epi16(c3, 0);
    c2 = _mm_unpacklo_epi16(c2, c3);

    for (i = 0; i < count; ++i) {
        i4 = _mm_loadu_si128((const __m128i *)src);

        xy = _mm_shuffle_epi8(i4, Mxy);
        zw = _mm_shuffle_epi8(i4, Mzw);

        x2 =  _mm_madd_epi16(xy, c0);
        x2 = _mm_add_epi32(x2, _mm_madd_epi16(zw, c2));

        x2 = _mm_srai_epi32(x2, 8);
        y2 = x2;
        z2 = x2;
        w2 = _mm_srli_epi32(zw, 16);

        x2 = packus_epi32(x2, y2);
        z2 = packus_epi32(z2, w2);
        o4 = _mm_packus_epi16(x2, z2);

        o4 = _mm_shuffle_epi8(o4, T4x4);
        _mm_storeu_si128((__m128i *)dst, o4);

        src = (const char *)src + 16;
        dst = (char *)dst + 16;
    }
}

void rsdIntrinsicBlurVFU4_K(void *dst,
                          const void *pin, int stride, const void *gptr,
                          int rct, int x1, int x2) {
    const char *pi;
    __m128i pi0, pi1;
    __m128 pf0, pf1;
    __m128 bp0, bp1;
    __m128 x;
    int r;

    for (; x1 < x2; x1 += 2) {
        pi = (const char *)pin + (x1 << 2);
        bp0 = _mm_setzero_ps();
        bp1 = _mm_setzero_ps();

        for (r = 0; r < rct; ++r) {
            x = _mm_load_ss((const float *)gptr + r);
            x = _mm_shuffle_ps(x, x, _MM_SHUFFLE(0, 0, 0, 0));

            pi0 = _mm_cvtsi32_si128(*(const int *)pi);
            pi1 = _mm_cvtsi32_si128(*((const int *)pi + 1));

            pf0 = _mm_cvtepi32_ps(cvtepu8_epi32(pi0));
            pf1 = _mm_cvtepi32_ps(cvtepu8_epi32(pi1));

            bp0 = _mm_add_ps(bp0, _mm_mul_ps(pf0, x));
            bp1 = _mm_add_ps(bp1, _mm_mul_ps(pf1, x));

            pi += stride;
        }

        _mm_storeu_ps((float *)dst, bp0);
        _mm_storeu_ps((float *)dst + 4, bp1);
        dst = (char *)dst + 32;
    }
}

void rsdIntrinsicBlurHFU4_K(void *dst,
                          const void *pin, const void *gptr,
                          int rct, int x1, int x2) {
    const __m128i Mu8 = _mm_set_epi32(0xffffffff, 0xffffffff, 0xffffffff, 0x0c080400);
    const float *pi;
    __m128 pf, x, y;
    __m128i o;
    int r;

    for (; x1 < x2; ++x1) {
        /* rct is define as 2*r+1 by the caller */
        x = _mm_load_ss((const float *)gptr);
        x = _mm_shuffle_ps(x, x, _MM_SHUFFLE(0, 0, 0, 0));

        pi = (const float *)pin + (x1 << 2);
        pf = _mm_mul_ps(x, _mm_load_ps(pi));

        for (r = 1; r < rct; r += 2) {
            x = _mm_load_ss((const float *)gptr + r);
            y = _mm_load_ss((const float *)gptr + r + 1);
            x = _mm_shuffle_ps(x, x, _MM_SHUFFLE(0, 0, 0, 0));
            y = _mm_shuffle_ps(y, y, _MM_SHUFFLE(0, 0, 0, 0));

            pf = _mm_add_ps(pf, _mm_mul_ps(x, _mm_load_ps(pi + (r << 2))));
            pf = _mm_add_ps(pf, _mm_mul_ps(y, _mm_load_ps(pi + (r << 2) + 4)));
        }

        o = _mm_cvtps_epi32(pf);
        *(int *)dst = _mm_cvtsi128_si32(_mm_shuffle_epi8(o, Mu8));
        dst = (char *)dst + 4;
    }
}

void rsdIntrinsicBlurHFU1_K(void *dst,
                          const void *pin, const void *gptr,
                          int rct, int x1, int x2) {
    const __m128i Mu8 = _mm_set_epi32(0xffffffff, 0xffffffff, 0xffffffff, 0x0c080400);
    const float *pi;
    __m128 pf, g0, g1, g2, g3, gx, p0, p1;
    __m128i o;
    int r;

    for (; x1 < x2; x1+=4) {
        g0 = _mm_load_ss((const float *)gptr);
        g0 = _mm_shuffle_ps(g0, g0, _MM_SHUFFLE(0, 0, 0, 0));

        pi = (const float *)pin + x1;
        pf = _mm_mul_ps(g0, _mm_loadu_ps(pi));

        for (r = 1; r < rct; r += 4) {
            gx = _mm_loadu_ps((const float *)gptr + r);
            p0 = _mm_loadu_ps(pi + r);
            p1 = _mm_loadu_ps(pi + r + 4);

            g0 = _mm_shuffle_ps(gx, gx, _MM_SHUFFLE(0, 0, 0, 0));
            pf = _mm_add_ps(pf, _mm_mul_ps(g0, p0));
            g1 = _mm_shuffle_ps(gx, gx, _MM_SHUFFLE(1, 1, 1, 1));
            pf = _mm_add_ps(pf, _mm_mul_ps(g1, _mm_alignr_epi8(p1, p0, 4)));
            g2 = _mm_shuffle_ps(gx, gx, _MM_SHUFFLE(2, 2, 2, 2));
            pf = _mm_add_ps(pf, _mm_mul_ps(g2, _mm_alignr_epi8(p1, p0, 8)));
            g3 = _mm_shuffle_ps(gx, gx, _MM_SHUFFLE(3, 3, 3, 3));
            pf = _mm_add_ps(pf, _mm_mul_ps(g3, _mm_alignr_epi8(p1, p0, 12)));
        }

        o = _mm_cvtps_epi32(pf);
        *(int *)dst = _mm_cvtsi128_si32(_mm_shuffle_epi8(o, Mu8));
        dst = (char *)dst + 4;
    }
}

void rsdIntrinsicYuv_K(void *dst,
                       const unsigned char *pY, const unsigned char *pUV,
                       uint32_t count, const short *param) {
    __m128i biasY, biasUV;
    __m128i c0, c1, c2, c3, c4;

    biasY = _mm_set1_epi32(param[8]);   /*  16 */
    biasUV = _mm_set1_epi32(param[16]); /* 128 */

    c0 = _mm_set1_epi32(param[0]);  /*  298 */
    c1 = _mm_set1_epi32(param[1]);  /*  409 */
    c2 = _mm_set1_epi32(param[2]);  /* -100 */
    c3 = _mm_set1_epi32(param[3]);  /*  516 */
    c4 = _mm_set1_epi32(param[4]);  /* -208 */

    __m128i Y, UV, U, V, R, G, B, A;

    A = _mm_set1_epi32(255);
    uint32_t i;

    for (i = 0; i < (count << 1); ++i) {
        Y = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pY));
        UV = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pUV));

        Y = _mm_sub_epi32(Y, biasY);
        UV = _mm_sub_epi32(UV, biasUV);

        U = _mm_shuffle_epi32(UV, 0xf5);
        V = _mm_shuffle_epi32(UV, 0xa0);

        Y = mullo_epi32(Y, c0);

        R = _mm_add_epi32(Y, mullo_epi32(V, c1));
        R = _mm_add_epi32(R, biasUV);
        R = _mm_srai_epi32(R, 8);

        G = _mm_add_epi32(Y, mullo_epi32(U, c2));
        G = _mm_add_epi32(G, mullo_epi32(V, c4));
        G = _mm_add_epi32(G, biasUV);
        G = _mm_srai_epi32(G, 8);

        B = _mm_add_epi32(Y, mullo_epi32(U, c3));
        B = _mm_add_epi32(B, biasUV);
        B = _mm_srai_epi32(B, 8);

        __m128i y1, y2, y3, y4;

        y1 = packus_epi32(R, G);
        y2 = packus_epi32(B, A);
        y3 = _mm_packus_epi16(y1, y2);
        const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                          14, 10, 6, 2,
                                          13,  9, 5, 1,
                                          12,  8, 4, 0);
        y4 = _mm_shuffle_epi8(y3, T4x4);
        _mm_storeu_si128((__m128i *)dst, y4);
        pY += 4;
        pUV += 4;
        dst = (__m128i *)dst + 1;
    }
}

void rsdIntrinsicYuvR_K(void *dst,
                       const unsigned char *pY, const unsigned char *pUV,
                       uint32_t count, const short *param) {
    __m128i biasY, biasUV;
    __m128i c0, c1, c2, c3, c4;

    biasY = _mm_set1_epi32(param[8]);   /*  16 */
    biasUV = _mm_set1_epi32(param[16]); /* 128 */

    c0 = _mm_set1_epi32(param[0]);  /*  298 */
    c1 = _mm_set1_epi32(param[1]);  /*  409 */
    c2 = _mm_set1_epi32(param[2]);  /* -100 */
    c3 = _mm_set1_epi32(param[3]);  /*  516 */
    c4 = _mm_set1_epi32(param[4]);  /* -208 */

    __m128i Y, UV, U, V, R, G, B, A;

    A = _mm_set1_epi32(255);
    uint32_t i;

    for (i = 0; i < (count << 1); ++i) {
        Y = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pY));
        UV = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pUV));

        Y = _mm_sub_epi32(Y, biasY);
        UV = _mm_sub_epi32(UV, biasUV);

        V = _mm_shuffle_epi32(UV, 0xf5);
        U = _mm_shuffle_epi32(UV, 0xa0);

        Y = mullo_epi32(Y, c0);

        R = _mm_add_epi32(Y, mullo_epi32(V, c1));
        R = _mm_add_epi32(R, biasUV);
        R = _mm_srai_epi32(R, 8);

        G = _mm_add_epi32(Y, mullo_epi32(U, c2));
        G = _mm_add_epi32(G, mullo_epi32(V, c4));
        G = _mm_add_epi32(G, biasUV);
        G = _mm_srai_epi32(G, 8);

        B = _mm_add_epi32(Y, mullo_epi32(U, c3));
        B = _mm_add_epi32(B, biasUV);
        B = _mm_srai_epi32(B, 8);

        __m128i y1, y2, y3, y4;

        y1 = packus_epi32(R, G);
        y2 = packus_epi32(B, A);
        y3 = _mm_packus_epi16(y1, y2);
        const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                          14, 10, 6, 2,
                                          13,  9, 5, 1,
                                          12,  8, 4, 0);
        y4 = _mm_shuffle_epi8(y3, T4x4);
        _mm_storeu_si128((__m128i *)dst, y4);
        pY += 4;
        pUV += 4;
        dst = (__m128i *)dst + 1;
    }
}

void rsdIntrinsicYuv2_K(void *dst,
                       const unsigned char *pY, const unsigned char *pU,
                       const unsigned char *pV, uint32_t count, const short *param) {
    __m128i biasY, biasUV;
    __m128i c0, c1, c2, c3, c4;

    biasY = _mm_set1_epi32(param[8]);   /*  16 */
    biasUV = _mm_set1_epi32(param[16]); /* 128 */

    c0 = _mm_set1_epi32(param[0]);  /*  298 */
    c1 = _mm_set1_epi32(param[1]);  /*  409 */
    c2 = _mm_set1_epi32(param[2]);  /* -100 */
    c3 = _mm_set1_epi32(param[3]);  /*  516 */
    c4 = _mm_set1_epi32(param[4]);  /* -208 */

    __m128i Y, U, V, R, G, B, A;

    A = _mm_set1_epi32(255);
    uint32_t i;

    for (i = 0; i < (count << 1); ++i) {
        Y = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pY));
        U = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pU));
		V = cvtepu8_epi32(_mm_set1_epi32(*(const int *)pV));

        Y = _mm_sub_epi32(Y, biasY);
        U = _mm_sub_epi32(U, biasUV);
		V = _mm_sub_epi32(V, biasUV);

        Y = mullo_epi32(Y, c0);

        R = _mm_add_epi32(Y, mullo_epi32(V, c1));
        R = _mm_add_epi32(R, biasUV);
        R = _mm_srai_epi32(R, 8);

        G = _mm_add_epi32(Y, mullo_epi32(U, c2));
        G = _mm_add_epi32(G, mullo_epi32(V, c4));
        G = _mm_add_epi32(G, biasUV);
        G = _mm_srai_epi32(G, 8);

        B = _mm_add_epi32(Y, mullo_epi32(U, c3));
        B = _mm_add_epi32(B, biasUV);
        B = _mm_srai_epi32(B, 8);

        __m128i y1, y2, y3, y4;

        y1 = packus_epi32(R, G);
        y2 = packus_epi32(B, A);
        y3 = _mm_packus_epi16(y1, y2);
        const __m128i T4x4 = _mm_set_epi8(15, 11, 7, 3,
                                          14, 10, 6, 2,
                                          13,  9, 5, 1,
                                          12,  8, 4, 0);
        y4 = _mm_shuffle_epi8(y3, T4x4);
        _mm_storeu_si128((__m128i *)dst, y4);
        pY += 4;
        pU += 4;
		pV += 4;
        dst = (__m128i *)dst + 1;
    }
}

extern "C" void rsdIntrinsicConvolve5x5_K(void *dst, const void *y0,
                                          const void *y1, const void *y2,
                                          const void *y3, const void *y4,
                                          const short *coef, uint32_t count) {
    __m128i x;
    __m128i c0, c2, c4, c6, c8, c10, c12;
    __m128i c14, c16, c18, c20, c22, c24;
    __m128i r0, r1, r2, r3, r4, r5, r6, r7, r8, r9;
    __m128i p0,  p1,  p2,  p3,  p4,  p5,  p6,  p7;
    __m128i p8,  p9, p10, p11, p12, p13, p14, p15;
    __m128i p16, p17, p18, p19, p20, p21, p22, p23;
    __m128i p24, p25, p26, p27, p28, p29, p30, p31;
    __m128i p32, p33, p34, p35, p36, p37, p38, p39;
    __m128i o0, o1, o2, o3;
    uint32_t i;

    x = _mm_loadl_epi64((const __m128i *)(coef+0));
    c0  = _mm_shuffle_epi32(x, 0x00);
    c2  = _mm_shuffle_epi32(x, 0x55);

    x = _mm_loadl_epi64((const __m128i *)(coef+4));
    c4  = _mm_shuffle_epi32(x, 0x00);
    c6  = _mm_shuffle_epi32(x, 0x55);

    x = _mm_loadl_epi64((const __m128i *)(coef+8));
    c8  = _mm_shuffle_epi32(x, 0x00);
    c10  = _mm_shuffle_epi32(x, 0x55);

    x = _mm_loadl_epi64((const __m128i *)(coef+12));
    c12  = _mm_shuffle_epi32(x, 0x00);
    c14  = _mm_shuffle_epi32(x, 0x55);

    x = _mm_loadl_epi64((const __m128i *)(coef+16));
    c16  = _mm_shuffle_epi32(x, 0x00);
    c18  = _mm_shuffle_epi32(x, 0x55);

    x = _mm_loadl_epi64((const __m128i *)(coef+20));
    c20  = _mm_shuffle_epi32(x, 0x00);
    c22  = _mm_shuffle_epi32(x, 0x55);

    x = _mm_loadl_epi64((const __m128i *)(coef+24));
    c24  = _mm_shuffle_epi32(x, 0x00);

    for (i = 0; i < count; ++i) {

        p0 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*(int32_t *)y0), _mm_setzero_si128());
        p1 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+1)), _mm_setzero_si128());
        p2 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+2)), _mm_setzero_si128());
        p3 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+3)), _mm_setzero_si128());
        p4 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+4)), _mm_setzero_si128());
        p5 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+5)), _mm_setzero_si128());
        p6 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+6)), _mm_setzero_si128());
        p7 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y0+7)), _mm_setzero_si128());

        p8 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1)), _mm_setzero_si128());
        p9 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+1)), _mm_setzero_si128());
        p10 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+2)), _mm_setzero_si128());
        p11 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+3)), _mm_setzero_si128());
        p12 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+4)), _mm_setzero_si128());
        p13 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+5)), _mm_setzero_si128());
        p14 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+6)), _mm_setzero_si128());
        p15 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y1+7)), _mm_setzero_si128());

        p16 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2)), _mm_setzero_si128());
        p17 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+1)), _mm_setzero_si128());
        p18 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+2)), _mm_setzero_si128());
        p19 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+3)), _mm_setzero_si128());
        p20 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+4)), _mm_setzero_si128());
        p21 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+5)), _mm_setzero_si128());
        p22 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+6)), _mm_setzero_si128());
        p23 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y2+7)), _mm_setzero_si128());

        p24 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3)), _mm_setzero_si128());
        p25 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+1)), _mm_setzero_si128());
        p26 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+2)), _mm_setzero_si128());
        p27 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+3)), _mm_setzero_si128());
        p28 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+4)), _mm_setzero_si128());
        p29 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+5)), _mm_setzero_si128());
        p30 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+6)), _mm_setzero_si128());
        p31 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y3+7)), _mm_setzero_si128());

        p32 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4)), _mm_setzero_si128());
        p33 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+1)), _mm_setzero_si128());
        p34 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+2)), _mm_setzero_si128());
        p35 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+3)), _mm_setzero_si128());
        p36 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+4)), _mm_setzero_si128());
        p37 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+5)), _mm_setzero_si128());
        p38 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+6)), _mm_setzero_si128());
        p39 = _mm_unpacklo_epi8(_mm_cvtsi32_si128(*((int32_t *)y4+7)), _mm_setzero_si128());

        o0 =                   _mm_madd_epi16( _mm_unpacklo_epi16(p0, p1),  c0);
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p2, p3),  c2));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p4, p8),  c4));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p9,p10),  c6));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p11, p12),  c8));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p16, p17), c10));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p18, p19), c12));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p20, p24), c14));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p25,p26), c16));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p27, p28), c18));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p32, p33), c20));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p34, p35), c22));
        o0 = _mm_add_epi32(o0, _mm_madd_epi16( _mm_unpacklo_epi16(p36, _mm_setzero_si128()), c24));
        o0 = _mm_srai_epi32(o0, 8);

        o1 =                   _mm_madd_epi16( _mm_unpacklo_epi16(p1, p2),  c0);
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p3,p4),  c2));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p5, p9),  c4));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p10,p11),  c6));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p12,p13),  c8));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p17,p18), c10));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p19,p20), c12));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p21,p25), c14));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p26, p27), c16));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p28, p29), c18));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p33, p34), c20));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p35, p36), c22));
        o1 = _mm_add_epi32(o1, _mm_madd_epi16( _mm_unpacklo_epi16(p37, _mm_setzero_si128()), c24));
        o1 = _mm_srai_epi32(o1, 8);

        o2 =                   _mm_madd_epi16( _mm_unpacklo_epi16(p2,p3),  c0);
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p4, p5),  c2));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p6, p10),  c4));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p11, p12),  c6));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p13, p14),  c8));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p18, p19), c10));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p20, p21), c12));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p22, p26), c14));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p27, p28), c16));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p29, p30), c18));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p34, p35), c20));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p36, p37), c22));
        o2 = _mm_add_epi32(o2, _mm_madd_epi16( _mm_unpacklo_epi16(p38, _mm_setzero_si128()), c24));
        o2 = _mm_srai_epi32(o2, 8);

        o3 =                   _mm_madd_epi16( _mm_unpacklo_epi16(p3,p4),  c0);
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p5, p6),  c2));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p7, p11),  c4));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p12, p13),  c6));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p14, p15),  c8));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p19, p20), c10));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p21, p22), c12));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p23, p27), c14));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p28, p29), c16));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p30, p31), c18));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p35, p36), c20));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p37,p38), c22));
        o3 = _mm_add_epi32(o3, _mm_madd_epi16( _mm_unpacklo_epi16(p39, _mm_setzero_si128()), c24));
        o3 = _mm_srai_epi32(o3, 8);

        o0 = packus_epi32(o0, o1);
        o2 = packus_epi32(o2, o3);
        o0 = _mm_packus_epi16(o0, o2);
        _mm_storeu_si128((__m128i *)dst, o0);

        y0 = (const char *)y0 + 16;
        y1 = (const char *)y1 + 16;
        y2 = (const char *)y2 + 16;
        y3 = (const char *)y3 + 16;
        y4 = (const char *)y4 + 16;
        dst = (char *)dst + 16;
    }
}

void rsdIntrinsicBlendSrcOver_K(void *dst, const void *src, uint32_t count8) {
    __m128i all1s, ina, ins;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    all1s = _mm_set1_epi16(255);

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        ins = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t0 = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, _mm_sub_epi16(all1s, ina));
        t0 = _mm_srli_epi16(t0, 8);
        t0 = _mm_add_epi16(t0, ins);

        ins = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t1 = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, _mm_sub_epi16(all1s, ina));
        t1 = _mm_srli_epi16(t1, 8);
        t1 = _mm_add_epi16(t1, ins);

        ins = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t2 = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, _mm_sub_epi16(all1s, ina));
        t2 = _mm_srli_epi16(t2, 8);
        t2 = _mm_add_epi16(t2, ins);

        ins = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t3 = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, _mm_sub_epi16(all1s, ina));
        t3 = _mm_srli_epi16(t3, 8);
        t3 = _mm_add_epi16(t3, ins);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendDstOver_K(void *dst, const void *src, uint32_t count8) {
    __m128i all1s, outa, outs;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    all1s = _mm_set1_epi16(255);

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);


        outs = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t0 = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, _mm_sub_epi16(all1s, outa));
        t0 = _mm_srli_epi16(t0, 8);
        t0 = _mm_add_epi16(t0, outs);

        outs = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t1 = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, _mm_sub_epi16(all1s, outa));
        t1 = _mm_srli_epi16(t1, 8);
        t1 = _mm_add_epi16(t1, outs);

        outs = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t2 = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, _mm_sub_epi16(all1s, outa));
        t2 = _mm_srli_epi16(t2, 8);
        t2 = _mm_add_epi16(t2, outs);

        outs = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t3 = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, _mm_sub_epi16(all1s, outa));
        t3 = _mm_srli_epi16(t3, 8);
        t3 = _mm_add_epi16(t3, outs);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendSrcIn_K(void *dst, const void *src, uint32_t count8) {
    __m128i outa;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        outa = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t0 = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, outa);
        t0 = _mm_srli_epi16(t0, 8);

        outa = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t1 = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, outa);
        t1 = _mm_srli_epi16(t1, 8);

        outa = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t2 = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, outa);
        t2 = _mm_srli_epi16(t2, 8);

        outa = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t3 = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, outa);
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendDstIn_K(void *dst, const void *src, uint32_t count8) {
    __m128i ina;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        ina = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t0 = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, ina);
        t0 = _mm_srli_epi16(t0, 8);

        ina = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t1 = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, ina);
        t1 = _mm_srli_epi16(t1, 8);

        ina = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t2 = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, ina);
        t2 = _mm_srli_epi16(t2, 8);

        ina = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t3 = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, ina);
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendSrcOut_K(void *dst, const void *src, uint32_t count8) {
    __m128i all1s, outa;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    all1s = _mm_set1_epi16(255);

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        outa = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t0 = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, _mm_sub_epi16(all1s, outa));
        t0 = _mm_srli_epi16(t0, 8);

        outa = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t1 = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, _mm_sub_epi16(all1s, outa));
        t1 = _mm_srli_epi16(t1, 8);

        outa = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t2 = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, _mm_sub_epi16(all1s, outa));
        t2 = _mm_srli_epi16(t2, 8);

        outa = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outa, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t3 = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, _mm_sub_epi16(all1s, outa));
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendDstOut_K(void *dst, const void *src, uint32_t count8) {
    __m128i all1s, ina;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    all1s = _mm_set1_epi16(255);

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        ina = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t0 = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, _mm_sub_epi16(all1s, ina));
        t0 = _mm_srli_epi16(t0, 8);

        ina = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t1 = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, _mm_sub_epi16(all1s, ina));
        t1 = _mm_srli_epi16(t1, 8);

        ina = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t2 = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, _mm_sub_epi16(all1s, ina));
        t2 = _mm_srli_epi16(t2, 8);

        ina = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ina, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        t3 = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, _mm_sub_epi16(all1s, ina));
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendSrcAtop_K(void *dst, const void *src, uint32_t count8) {
    const __m128i M0001 = _mm_set_epi32(0xff000000, 0xff000000, 0xff000000, 0xff000000);
    __m128i all1s, ina, outa, ins, outs;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    all1s = _mm_set1_epi16(255);

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        ins = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t0 = _mm_sub_epi16(all1s, ina);
        t0 = _mm_mullo_epi16(t0, outs);
        t0 = _mm_adds_epu16(t0, _mm_mullo_epi16(outa, ins));
        t0 = _mm_srli_epi16(t0, 8);

        ins = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t1 = _mm_sub_epi16(all1s, ina);
        t1 = _mm_mullo_epi16(t1, outs);
        t1 = _mm_adds_epu16(t1, _mm_mullo_epi16(outa, ins));
        t1 = _mm_srli_epi16(t1, 8);

        ins = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t2 = _mm_sub_epi16(all1s, ina);
        t2 = _mm_mullo_epi16(t2, outs);
        t2 = _mm_adds_epu16(t2, _mm_mullo_epi16(outa, ins));
        t2 = _mm_srli_epi16(t2, 8);

        ins = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t3 = _mm_sub_epi16(all1s, ina);
        t3 = _mm_mullo_epi16(t3, outs);
        t3 = _mm_adds_epu16(t3, _mm_mullo_epi16(outa, ins));
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t0 = blendv_epi8(t0, out0, M0001);
        t2 = _mm_packus_epi16(t2, t3);
        t2 = blendv_epi8(t2, out1, M0001);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendDstAtop_K(void *dst, const void *src, uint32_t count8) {
    const __m128i M0001 = _mm_set_epi32(0xff000000, 0xff000000, 0xff000000, 0xff000000);
    __m128i all1s, ina, ins, outa, outs;
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    all1s = _mm_set1_epi16(255);

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        ins = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpacklo_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t0 = _mm_sub_epi16(all1s, outa);
        t0 = _mm_mullo_epi16(t0, ins);
        t0 = _mm_adds_epu16(t0, _mm_mullo_epi16(ina, outs));
        t0 = _mm_srli_epi16(t0, 8);

        ins = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpackhi_epi8(out0, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t1 = _mm_sub_epi16(all1s, outa);
        t1 = _mm_mullo_epi16(t1, ins);
        t1 = _mm_adds_epu16(t1, _mm_mullo_epi16(ina, outs));
        t1 = _mm_srli_epi16(t1, 8);

        ins = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpacklo_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t2 = _mm_sub_epi16(all1s, outa);
        t2 = _mm_mullo_epi16(t2, ins);
        t2 = _mm_adds_epu16(t2, _mm_mullo_epi16(ina, outs));
        t2 = _mm_srli_epi16(t2, 8);

        ins = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        ina = _mm_shufflelo_epi16(ins, 0xFF);
        ina = _mm_shufflehi_epi16(ina, 0xFF);
        outs = _mm_unpackhi_epi8(out1, _mm_setzero_si128());
        outa = _mm_shufflelo_epi16(outs, 0xFF);
        outa = _mm_shufflehi_epi16(outa, 0xFF);
        t3 = _mm_sub_epi16(all1s, outa);
        t3 = _mm_mullo_epi16(t3, ins);
        t3 = _mm_adds_epu16(t3, _mm_mullo_epi16(ina, outs));
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t0 = blendv_epi8(t0, in0, M0001);
        t2 = _mm_packus_epi16(t2, t3);
        t2 = blendv_epi8(t2, in1, M0001);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendXor_K(void *dst, const void *src, uint32_t count8) {
    __m128i in0, in1, out0, out1;
    uint32_t i;

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        out0 = _mm_xor_si128(out0, in0);
        out1 = _mm_xor_si128(out1, in1);

        _mm_storeu_si128((__m128i *)dst, out0);
        _mm_storeu_si128((__m128i *)dst + 1, out1);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendMultiply_K(void *dst, const void *src, uint32_t count8) {
    __m128i in0, in1, out0, out1;
    __m128i t0, t1, t2, t3;
    uint32_t i;

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        t0 = _mm_unpacklo_epi8(in0, _mm_setzero_si128());
        t0 = _mm_mullo_epi16(t0, _mm_unpacklo_epi8(out0, _mm_setzero_si128()));
        t0 = _mm_srli_epi16(t0, 8);

        t1 = _mm_unpackhi_epi8(in0, _mm_setzero_si128());
        t1 = _mm_mullo_epi16(t1, _mm_unpackhi_epi8(out0, _mm_setzero_si128()));
        t1 = _mm_srli_epi16(t1, 8);

        t2 = _mm_unpacklo_epi8(in1, _mm_setzero_si128());
        t2 = _mm_mullo_epi16(t2, _mm_unpacklo_epi8(out1, _mm_setzero_si128()));
        t2 = _mm_srli_epi16(t2, 8);

        t3 = _mm_unpackhi_epi8(in1, _mm_setzero_si128());
        t3 = _mm_mullo_epi16(t3, _mm_unpackhi_epi8(out1, _mm_setzero_si128()));
        t3 = _mm_srli_epi16(t3, 8);

        t0 = _mm_packus_epi16(t0, t1);
        t2 = _mm_packus_epi16(t2, t3);
        _mm_storeu_si128((__m128i *)dst, t0);
        _mm_storeu_si128((__m128i *)dst + 1, t2);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendAdd_K(void *dst, const void *src, uint32_t count8) {
    __m128i in0, in1, out0, out1;
    uint32_t i;

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        out0 = _mm_adds_epu8(out0, in0);
        out1 = _mm_adds_epu8(out1, in1);

        _mm_storeu_si128((__m128i *)dst, out0);
        _mm_storeu_si128((__m128i *)dst + 1, out1);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

void rsdIntrinsicBlendSub_K(void *dst, const void *src, uint32_t count8) {
    __m128i in0, in1, out0, out1;
    uint32_t i;

    for (i = 0; i < count8; ++i) {
        in0 = _mm_loadu_si128((const __m128i *)src);
        in1 = _mm_loadu_si128((const __m128i *)src + 1);
        out0 = _mm_loadu_si128((const __m128i *)dst);
        out1 = _mm_loadu_si128((const __m128i *)dst + 1);

        out0 = _mm_subs_epu8(out0, in0);
        out1 = _mm_subs_epu8(out1, in1);

        _mm_storeu_si128((__m128i *)dst, out0);
        _mm_storeu_si128((__m128i *)dst + 1, out1);

        src = (const __m128i *)src + 2;
        dst = (__m128i *)dst + 2;
    }
}

}  // namespace renderscript
