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

#include <math.h>

#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

#if defined(ARCH_X86_HAVE_AVX2)
#include <stdint.h>
#include <x86intrin.h>
#include <xmmintrin.h>
#endif

#define LOG_TAG "renderscript.toolkit.Resize"

namespace renderscript {

class ResizeTask : public Task {
    const uchar* mIn;
    uchar* mOut;
    float mScaleX;
    float mScaleY;
    size_t mInputSizeX;
    size_t mInputSizeY;

    void kernelU1(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
    void kernelU2(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
    void kernelU4(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
    void kernelF1(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
    void kernelF2(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
    void kernelF4(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    ResizeTask(const uchar* input, uchar* output, size_t inputSizeX, size_t inputSizeY,
               size_t vectorSize, size_t outputSizeX, size_t outputSizeY,
               const Restriction* restriction)
        : Task{outputSizeX, outputSizeY, vectorSize, false, restriction},
          mIn{input},
          mOut{output},
          mInputSizeX{inputSizeX},
          mInputSizeY{inputSizeY} {
        mScaleX = static_cast<float>(inputSizeX) / outputSizeX;
        mScaleY = static_cast<float>(inputSizeY) / outputSizeY;
    }
};

void ResizeTask::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                             size_t endY) {
    typedef void (ResizeTask::*KernelFunction)(uchar*, uint32_t, uint32_t, uint32_t);

    KernelFunction kernel;
    switch (mVectorSize) {
        case 4:
            kernel = &ResizeTask::kernelU4;
            break;
        case 3:
            kernel = &ResizeTask::kernelU4;
            break;
        case 2:
            kernel = &ResizeTask::kernelU2;
            break;
        case 1:
            kernel = &ResizeTask::kernelU1;
            break;
        default:
            ALOGE("Bad vector size %zd", mVectorSize);
    }

    for (size_t y = startY; y < endY; y++) {
        size_t offset = (mSizeX * y + startX) * paddedSize(mVectorSize);
        uchar* out = mOut + offset;
        std::invoke(kernel, this, out, startX, endX, y);
    }
}

static float4 cubicInterpolate(float4 p0, float4 p1, float4 p2, float4 p3, float x) {
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}

static float2 cubicInterpolate(float2 p0,float2 p1,float2 p2,float2 p3, float x) {
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}


#if defined(ARCH_X86_HAVE_AVX2)
static float cubicInterpolate(float p0,float p1,float p2,float p3 , float x) {
   return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 +
           _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(4.f), _mm_set1_ps(p2),_mm_set1_ps(p3)))
           + x * (_mm_cvtss_f32(_mm_fmadd_ss (_mm_set1_ps(3.f),_mm_set1_ps(p1 - p2),
                                              _mm_set1_ps(p3 - p0))))));

}
#else
static float cubicInterpolate(float p0,float p1,float p2,float p3 , float x) {
    //ALOGI("CP, %f, %f, %f, %f, %f", p0, p1, p2, p3, x);
    return p1 + 0.5f * x * (p2 - p0 + x * (2.f * p0 - 5.f * p1 + 4.f * p2 - p3
            + x * (3.f * (p1 - p2) + p3 - p0)));
}
#endif

static uchar4 OneBiCubic(const uchar4 *yp0, const uchar4 *yp1, const uchar4 *yp2, const uchar4 *yp3,
                         float xf, float yf, int width) {
    int startx = (int) floor(xf - 1);
    xf = xf - floor(xf);
    int maxx = width - 1;
    int xs0 = std::max(0, startx + 0);
    int xs1 = std::max(0, startx + 1);
    int xs2 = std::min(maxx, startx + 2);
    int xs3 = std::min(maxx, startx + 3);

    float4 p0  = cubicInterpolate(convert<float4>(yp0[xs0]),
                                  convert<float4>(yp0[xs1]),
                                  convert<float4>(yp0[xs2]),
                                  convert<float4>(yp0[xs3]), xf);

    float4 p1  = cubicInterpolate(convert<float4>(yp1[xs0]),
                                  convert<float4>(yp1[xs1]),
                                  convert<float4>(yp1[xs2]),
                                  convert<float4>(yp1[xs3]), xf);

    float4 p2  = cubicInterpolate(convert<float4>(yp2[xs0]),
                                  convert<float4>(yp2[xs1]),
                                  convert<float4>(yp2[xs2]),
                                  convert<float4>(yp2[xs3]), xf);

    float4 p3  = cubicInterpolate(convert<float4>(yp3[xs0]),
                                  convert<float4>(yp3[xs1]),
                                  convert<float4>(yp3[xs2]),
                                  convert<float4>(yp3[xs3]), xf);

    float4 p  = cubicInterpolate(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    return convert<uchar4>(p);
}

static uchar2 OneBiCubic(const uchar2 *yp0, const uchar2 *yp1, const uchar2 *yp2, const uchar2 *yp3,
                         float xf, float yf, int width) {
    int startx = (int) floor(xf - 1);
    xf = xf - floor(xf);
    int maxx = width - 1;
    int xs0 = std::max(0, startx + 0);
    int xs1 = std::max(0, startx + 1);
    int xs2 = std::min(maxx, startx + 2);
    int xs3 = std::min(maxx, startx + 3);

    float2 p0  = cubicInterpolate(convert<float2>(yp0[xs0]),
                                  convert<float2>(yp0[xs1]),
                                  convert<float2>(yp0[xs2]),
                                  convert<float2>(yp0[xs3]), xf);

    float2 p1  = cubicInterpolate(convert<float2>(yp1[xs0]),
                                  convert<float2>(yp1[xs1]),
                                  convert<float2>(yp1[xs2]),
                                  convert<float2>(yp1[xs3]), xf);

    float2 p2  = cubicInterpolate(convert<float2>(yp2[xs0]),
                                  convert<float2>(yp2[xs1]),
                                  convert<float2>(yp2[xs2]),
                                  convert<float2>(yp2[xs3]), xf);

    float2 p3  = cubicInterpolate(convert<float2>(yp3[xs0]),
                                  convert<float2>(yp3[xs1]),
                                  convert<float2>(yp3[xs2]),
                                  convert<float2>(yp3[xs3]), xf);

    float2 p  = cubicInterpolate(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    return convert<uchar2>(p);
}

static uchar OneBiCubic(const uchar *yp0, const uchar *yp1, const uchar *yp2, const uchar *yp3,
                        float xf, float yf, int width) {
    int startx = (int) floor(xf - 1);
    xf = xf - floor(xf);
    int maxx = width - 1;
    int xs0 = std::max(0, startx + 0);
    int xs1 = std::max(0, startx + 1);
    int xs2 = std::min(maxx, startx + 2);
    int xs3 = std::min(maxx, startx + 3);

    float p0  = cubicInterpolate((float)yp0[xs0], (float)yp0[xs1],
                                 (float)yp0[xs2], (float)yp0[xs3], xf);
    float p1  = cubicInterpolate((float)yp1[xs0], (float)yp1[xs1],
                                 (float)yp1[xs2], (float)yp1[xs3], xf);
    float p2  = cubicInterpolate((float)yp2[xs0], (float)yp2[xs1],
                                 (float)yp2[xs2], (float)yp2[xs3], xf);
    float p3  = cubicInterpolate((float)yp3[xs0], (float)yp3[xs1],
                                 (float)yp3[xs2], (float)yp3[xs3], xf);

    float p  = cubicInterpolate(p0, p1, p2, p3, yf);
    p = clamp(p + 0.5f, 0.f, 255.f);
    //ALOGI("CUC,%f,%u", p, (uchar)p);
    return (uchar)p;
}

extern "C" uint64_t rsdIntrinsicResize_oscctl_K(uint32_t xinc);

extern "C" void rsdIntrinsicResizeB4_K(
            uchar4 *dst,
            size_t count,
            uint32_t xf,
            uint32_t xinc,
            uchar4 const *srcn,
            uchar4 const *src0,
            uchar4 const *src1,
            uchar4 const *src2,
            size_t xclip,
            size_t avail,
            uint64_t osc_ctl,
            int32_t const *yr);

extern "C" void rsdIntrinsicResizeB2_K(
            uchar2 *dst,
            size_t count,
            uint32_t xf,
            uint32_t xinc,
            uchar2 const *srcn,
            uchar2 const *src0,
            uchar2 const *src1,
            uchar2 const *src2,
            size_t xclip,
            size_t avail,
            uint64_t osc_ctl,
            int32_t const *yr);

extern "C" void rsdIntrinsicResizeB1_K(
            uchar *dst,
            size_t count,
            uint32_t xf,
            uint32_t xinc,
            uchar const *srcn,
            uchar const *src0,
            uchar const *src1,
            uchar const *src2,
            size_t xclip,
            size_t avail,
            uint64_t osc_ctl,
            int32_t const *yr);

#if defined(ARCH_ARM_USE_INTRINSICS)
static void mkYCoeff(int32_t *yr, float yf) {
    int32_t yf1 = rint(yf * 0x10000);
    int32_t yf2 = rint(yf * yf * 0x10000);
    int32_t yf3 = rint(yf * yf * yf * 0x10000);

    yr[0] = -(2 * yf2 - yf3 - yf1) >> 1;
    yr[1] = (3 * yf3 - 5 * yf2 + 0x20000) >> 1;
    yr[2] = (-3 * yf3 + 4 * yf2 + yf1) >> 1;
    yr[3] = -(yf3 - yf2) >> 1;
}
#endif

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
static float4 OneBiCubic(const float4 *yp0, const float4 *yp1, const float4 *yp2, const float4 *yp3,
                         float xf, float yf, int width) {
    int startx = (int) floor(xf - 1);
    xf = xf - floor(xf);
    int maxx = width - 1;
    int xs0 = std::max(0, startx + 0);
    int xs1 = std::max(0, startx + 1);
    int xs2 = std::min(maxx, startx + 2);
    int xs3 = std::min(maxx, startx + 3);

    float4 p0  = cubicInterpolate(yp0[xs0], yp0[xs1],
                                  yp0[xs2], yp0[xs3], xf);
    float4 p1  = cubicInterpolate(yp1[xs0], yp1[xs1],
                                  yp1[xs2], yp1[xs3], xf);
    float4 p2  = cubicInterpolate(yp2[xs0], yp2[xs1],
                                  yp2[xs2], yp2[xs3], xf);
    float4 p3  = cubicInterpolate(yp3[xs0], yp3[xs1],
                                  yp3[xs2], yp3[xs3], xf);

    float4 p  = cubicInterpolate(p0, p1, p2, p3, yf);
    return p;
}

static float2 OneBiCubic(const float2 *yp0, const float2 *yp1, const float2 *yp2, const float2 *yp3,
                         float xf, float yf, int width) {
    int startx = (int) floor(xf - 1);
    xf = xf - floor(xf);
    int maxx = width - 1;
    int xs0 = std::max(0, startx + 0);
    int xs1 = std::max(0, startx + 1);
    int xs2 = std::min(maxx, startx + 2);
    int xs3 = std::min(maxx, startx + 3);

    float2 p0  = cubicInterpolate(yp0[xs0], yp0[xs1],
                                  yp0[xs2], yp0[xs3], xf);
    float2 p1  = cubicInterpolate(yp1[xs0], yp1[xs1],
                                  yp1[xs2], yp1[xs3], xf);
    float2 p2  = cubicInterpolate(yp2[xs0], yp2[xs1],
                                  yp2[xs2], yp2[xs3], xf);
    float2 p3  = cubicInterpolate(yp3[xs0], yp3[xs1],
                                  yp3[xs2], yp3[xs3], xf);

    float2 p  = cubicInterpolate(p0, p1, p2, p3, yf);
    return p;
}

static float OneBiCubic(const float *yp0, const float *yp1, const float *yp2, const float *yp3,
                        float xf, float yf, int width) {
    int startx = (int) floor(xf - 1);
    xf = xf - floor(xf);
    int maxx = width - 1;
    int xs0 = std::max(0, startx + 0);
    int xs1 = std::max(0, startx + 1);
    int xs2 = std::min(maxx, startx + 2);
    int xs3 = std::min(maxx, startx + 3);

    float p0  = cubicInterpolate(yp0[xs0], yp0[xs1],
                                 yp0[xs2], yp0[xs3], xf);
    float p1  = cubicInterpolate(yp1[xs0], yp1[xs1],
                                 yp1[xs2], yp1[xs3], xf);
    float p2  = cubicInterpolate(yp2[xs0], yp2[xs1],
                                 yp2[xs2], yp2[xs3], xf);
    float p3  = cubicInterpolate(yp3[xs0], yp3[xs1],
                                 yp3[xs2], yp3[xs3], xf);

    float p  = cubicInterpolate(p0, p1, p2, p3, yf);
    return p;
}
#endif

void ResizeTask::kernelU4(uchar *outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar *pin = mIn;
    const int srcHeight = mInputSizeY;
    const int srcWidth = mInputSizeX;
    const size_t stride = mInputSizeX * paddedSize(mVectorSize);


#if defined(ARCH_X86_HAVE_AVX2)
    float yf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(currentY + 0.5f),
                                          _mm_set1_ps(scaleY), _mm_set1_ps(0.5f)));
#else
    float yf = (currentY + 0.5f) * mScaleY - 0.5f;
#endif


    int starty = (int) floor(yf - 1);
    yf = yf - floor(yf);
    int maxy = srcHeight - 1;
    int ys0 = std::max(0, starty + 0);
    int ys1 = std::max(0, starty + 1);
    int ys2 = std::min(maxy, starty + 2);
    int ys3 = std::min(maxy, starty + 3);

    const uchar4 *yp0 = (const uchar4 *)(pin + stride * ys0);
    const uchar4 *yp1 = (const uchar4 *)(pin + stride * ys1);
    const uchar4 *yp2 = (const uchar4 *)(pin + stride * ys2);
    const uchar4 *yp3 = (const uchar4 *)(pin + stride * ys3);

    uchar4 *out = ((uchar4 *)outPtr);
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd && x2 > x1 && mScaleX < 4.0f) {
        float xf = (x1 + 0.5f) * mScaleX - 0.5f;
        long xf16 = rint(xf * 0x10000);
        uint32_t xinc16 = rint(mScaleX * 0x10000);

        int xoff = (xf16 >> 16) - 1;
        int xclip = std::max(0, xoff) - xoff;
        int len = x2 - x1;

        int32_t yr[4];
        uint64_t osc_ctl = rsdIntrinsicResize_oscctl_K(xinc16);
        mkYCoeff(yr, yf);

        xoff += xclip;

        rsdIntrinsicResizeB4_K(
                out, len,
                xf16 & 0xffff, xinc16,
                yp0 + xoff, yp1 + xoff, yp2 + xoff, yp3 + xoff,
                xclip, srcWidth - xoff + xclip,
                osc_ctl, yr);
        out += len;
        x1 += len;
    }
#endif

    while(x1 < x2) {
#if defined(ARCH_X86_HAVE_AVX2)
        float xf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(x1 + 0.5f) , _mm_set1_ps(scaleX) ,
                                              _mm_set1_ps(0.5f)));
#else
        float xf = (x1 + 0.5f) * mScaleX - 0.5f;
#endif
        *out = OneBiCubic(yp0, yp1, yp2, yp3, xf, yf, srcWidth);
        out++;
        x1++;
    }
}

void ResizeTask::kernelU2(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar *pin = mIn;
    const int srcHeight = mInputSizeY;
    const int srcWidth = mInputSizeX;
    const size_t stride = mInputSizeX * mVectorSize;


#if defined(ARCH_X86_HAVE_AVX2)
    float yf = _mm_cvtss_f32(
            _mm_fmsub_ss(_mm_set1_ps(currentY + 0.5f), _mm_set1_ps(scaleY), _mm_set1_ps(0.5f)));
#else
    float yf = (currentY + 0.5f) * mScaleY - 0.5f;
#endif

    int starty = (int) floor(yf - 1);
    yf = yf - floor(yf);
    int maxy = srcHeight - 1;
    int ys0 = std::max(0, starty + 0);
    int ys1 = std::max(0, starty + 1);
    int ys2 = std::min(maxy, starty + 2);
    int ys3 = std::min(maxy, starty + 3);

    const uchar2 *yp0 = (const uchar2 *)(pin + stride * ys0);
    const uchar2 *yp1 = (const uchar2 *)(pin + stride * ys1);
    const uchar2 *yp2 = (const uchar2 *)(pin + stride * ys2);
    const uchar2 *yp3 = (const uchar2 *)(pin + stride * ys3);

    uchar2 *out = ((uchar2 *)outPtr);
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd && x2 > x1 && mScaleX < 4.0f) {
        float xf = (x1 + 0.5f) * mScaleX - 0.5f;
        long xf16 = rint(xf * 0x10000);
        uint32_t xinc16 = rint(mScaleX * 0x10000);

        int xoff = (xf16 >> 16) - 1;
        int xclip = std::max(0, xoff) - xoff;
        int len = x2 - x1;

        int32_t yr[4];
        uint64_t osc_ctl = rsdIntrinsicResize_oscctl_K(xinc16);
        mkYCoeff(yr, yf);

        xoff += xclip;

        rsdIntrinsicResizeB2_K(
                out, len,
                xf16 & 0xffff, xinc16,
                yp0 + xoff, yp1 + xoff, yp2 + xoff, yp3 + xoff,
                xclip, srcWidth - xoff + xclip,
                osc_ctl, yr);
        out += len;
        x1 += len;
    }
#endif

    while(x1 < x2) {

#if defined(ARCH_X86_HAVE_AVX2)
        float xf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(x1 + 0.5f) , _mm_set1_ps(scaleX) ,
                                              _mm_set1_ps(0.5f)));
#else
        float xf = (x1 + 0.5f) * mScaleX - 0.5f;
#endif
        *out = OneBiCubic(yp0, yp1, yp2, yp3, xf, yf, srcWidth);
        out++;
        x1++;
    }
}

void ResizeTask::kernelU1(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    //ALOGI("TK kernelU1 xstart %u, xend %u, outstep %u", xstart, xend);
    const uchar *pin = mIn;
    const int srcHeight = mInputSizeY;
    const int srcWidth = mInputSizeX;
    const size_t stride = mInputSizeX * mVectorSize;

    // ALOGI("Toolkit   ResizeU1 (%ux%u) by (%f,%f), xstart:%u to %u, stride %zu, out %p", srcWidth,
    // srcHeight, scaleX, scaleY, xstart, xend, stride, outPtr);

#if defined(ARCH_X86_HAVE_AVX2)
    float yf = _mm_cvtss_f32(
            _mm_fmsub_ss(_mm_set1_ps(currentY + 0.5f), _mm_set1_ps(scaleY), _mm_set1_ps(0.5f)));
#else
    float yf = (currentY + 0.5f) * mScaleY - 0.5f;
#endif

    int starty = (int) floor(yf - 1);
    yf = yf - floor(yf);
    int maxy = srcHeight - 1;
    int ys0 = std::max(0, starty + 0);
    int ys1 = std::min(maxy, std::max(0, starty + 1));
    int ys2 = std::min(maxy, starty + 2);
    int ys3 = std::min(maxy, starty + 3);

    const uchar *yp0 = pin + stride * ys0;
    const uchar *yp1 = pin + stride * ys1;
    const uchar *yp2 = pin + stride * ys2;
    const uchar *yp3 = pin + stride * ys3;

    uchar *out = ((uchar *)outPtr);
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd && x2 > x1 && mScaleX < 4.0f) {
        float xf = (x1 + 0.5f) * mScaleX - 0.5f;
        long xf16 = rint(xf * 0x10000);
        uint32_t xinc16 = rint(mScaleX * 0x10000);

        int xoff = (xf16 >> 16) - 1;
        int xclip = std::max(0, xoff) - xoff;
        int len = x2 - x1;

        int32_t yr[4];
        uint64_t osc_ctl = rsdIntrinsicResize_oscctl_K(xinc16);
        mkYCoeff(yr, yf);

        // ALOGI("ys0 %d, ys1 %d, ys2 %d, ys3 %d, x1 %u, x2 %u, xf %f, xf16 %ld, xinc16 %u, xoff %d,
        // xclip %d, len %d, osc_ctl %lu)",
        //       ys0, ys1, ys2, ys3, x1, x2, xf, xf16, xinc16, xoff, xclip, len, (unsigned long)
        //       osc_ctl);
        // ALOGI("TK scaleX %f, xf %f, xf16 %ld, xinc16 %d, xoff %d, xclip %d, len %d", scaleX, xf,
        // xf16, xinc16, xoff, xclip, len); ALOGI("TK xf16 & 0xffff %ld, ys0 %u, ys1 %u, ys2 %u, ys3
        // %u, srcWidth - xoff + xclip %d", xf16 & 0xffff, ys0, ys1, ys2, ys3, srcWidth - xoff);

        xoff += xclip;

        rsdIntrinsicResizeB1_K(
                out, len,
                xf16 & 0xffff, xinc16,
                yp0 + xoff, yp1 + xoff, yp2 + xoff, yp3 + xoff,
                xclip, srcWidth - xoff + xclip,
                osc_ctl, yr);
        out += len;
        x1 += len;
    }
#endif

    while(x1 < x2) {

#if defined(ARCH_X86_HAVE_AVX2)
        float xf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(x1 + 0.5f) , _mm_set1_ps(scaleX) ,
                                              _mm_set1_ps(0.5f)));
#else
        float xf = (x1 + 0.5f) * mScaleX - 0.5f;
#endif

        *out = OneBiCubic(yp0, yp1, yp2, yp3, xf, yf, srcWidth);
        out++;
        x1++;
    }
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
void ResizeTask::kernelF4(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar *pin = mIn;
    const int srcHeight = inputSizeY;
    const int srcWidth = inputSizeX;
    const size_t stride = sizeX * vectorSize;

#if defined(ARCH_X86_HAVE_AVX2)
    float yf = _mm_cvtss_f32(
            _mm_fmsub_ss(_mm_set1_ps(currentY + 0.5f), _mm_set1_ps(scaleY), _mm_set1_ps(0.5f)));
#else
    float yf = (currentY + 0.5f) * scaleY - 0.5f;
#endif

    int starty = (int) floor(yf - 1);
    yf = yf - floor(yf);
    int maxy = srcHeight - 1;
    int ys0 = std::max(0, starty + 0);
    int ys1 = std::max(0, starty + 1);
    int ys2 = std::min(maxy, starty + 2);
    int ys3 = std::min(maxy, starty + 3);

    const float4 *yp0 = (const float4 *)(pin + stride * ys0);
    const float4 *yp1 = (const float4 *)(pin + stride * ys1);
    const float4 *yp2 = (const float4 *)(pin + stride * ys2);
    const float4 *yp3 = (const float4 *)(pin + stride * ys3);

    float4 *out = ((float4 *)outPtr);
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

    while(x1 < x2) {

#if defined(ARCH_X86_HAVE_AVX2)
        float xf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(x1 + 0.5f) , _mm_set1_ps(scaleX) ,
                                              _mm_set1_ps(0.5f)));
#else
        float xf = (x1 + 0.5f) * scaleX - 0.5f;
#endif

        *out = OneBiCubic(yp0, yp1, yp2, yp3, xf, yf, srcWidth);
        out++;
        x1++;
    }
}

void ResizeTask::kernelF2(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar *pin = mIn;
    const int srcHeight = inputSizeY;
    const int srcWidth = inputSizeX;
    const size_t stride = sizeX * vectorSize;


#if defined(ARCH_X86_HAVE_AVX2)
    float yf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(currentY + 0.5f),
                                          _mm_set1_ps(scaleY), _mm_set1_ps(0.5f)));
#else
    float yf = (currentY + 0.5f) * scaleY - 0.5f;
#endif

    int starty = (int) floor(yf - 1);
    yf = yf - floor(yf);
    int maxy = srcHeight - 1;
    int ys0 = std::max(0, starty + 0);
    int ys1 = std::max(0, starty + 1);
    int ys2 = std::min(maxy, starty + 2);
    int ys3 = std::min(maxy, starty + 3);

    const float2 *yp0 = (const float2 *)(pin + stride * ys0);
    const float2 *yp1 = (const float2 *)(pin + stride * ys1);
    const float2 *yp2 = (const float2 *)(pin + stride * ys2);
    const float2 *yp3 = (const float2 *)(pin + stride * ys3);

    float2 *out = ((float2 *)outPtr);
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

    while(x1 < x2) {

#if defined(ARCH_X86_HAVE_AVX2)
        float xf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(x1 + 0.5f) , _mm_set1_ps(scaleX) ,
                                              _mm_set1_ps(0.5f)));
#else
        float xf = (x1 + 0.5f) * scaleX - 0.5f;
#endif

        *out = OneBiCubic(yp0, yp1, yp2, yp3, xf, yf, srcWidth);
        out++;
        x1++;
    }
}

void ResizeTask::kernelF1(uchar* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar *pin = mIn;
    const int srcHeight = inputSizeY;
    const int srcWidth = inputSizeX;
    const size_t stride = sizeX * vectorSize;


#if defined(ARCH_X86_HAVE_AVX2)
    float yf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(currentY + 0.5f),
                                          _mm_set1_ps(scaleY), _mm_set1_ps(0.5f)));
#else
    float yf = (currentY + 0.5f) * scaleY - 0.5f;
#endif

    int starty = (int) floor(yf - 1);
    yf = yf - floor(yf);
    int maxy = srcHeight - 1;
    int ys0 = std::max(0, starty + 0);
    int ys1 = std::max(0, starty + 1);
    int ys2 = std::min(maxy, starty + 2);
    int ys3 = std::min(maxy, starty + 3);

    const float *yp0 = (const float *)(pin + stride * ys0);
    const float *yp1 = (const float *)(pin + stride * ys1);
    const float *yp2 = (const float *)(pin + stride * ys2);
    const float *yp3 = (const float *)(pin + stride * ys3);

    float *out = ((float *)outPtr);
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

    while(x1 < x2) {

#if defined(ARCH_X86_HAVE_AVX2)
        float xf = _mm_cvtss_f32(_mm_fmsub_ss(_mm_set1_ps(x1 + 0.5f) , _mm_set1_ps(scaleX) ,
                                              _mm_set1_ps(0.5f)));
#else
        float xf = (x1 + 0.5f) * scaleX - 0.5f;
#endif

        *out = OneBiCubic(yp0, yp1, yp2, yp3, xf, yf, srcWidth);
        out++;
        x1++;
    }
}

void ResizeTask::preLaunch(uint32_t slot, const RsScriptCall *sc)
{

    //check the data type to determine F or U.
    if (mAlloc->getType()->getElement()->getType() == RS_TYPE_UNSIGNED_8) {
        switch(mAlloc->getType()->getElement()->getVectorSize()) {
        case 1:
            mRootPtr = &kernelU1;
            break;
        case 2:
            mRootPtr = &kernelU2;
            break;
        case 3:
        case 4:
            mRootPtr = &kernelU4;
            break;
        }
    } else {
        switch(mAlloc->getType()->getElement()->getVectorSize()) {
        case 1:
            mRootPtr = &kernelF1;
            break;
        case 2:
            mRootPtr = &kernelF2;
            break;
        case 3:
        case 4:
            mRootPtr = &kernelF4;
            break;
        }
    }
}
#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

void RenderScriptToolkit::resize(const uint8_t* input, uint8_t* output, size_t inputSizeX,
                                 size_t inputSizeY, size_t vectorSize, size_t outputSizeX,
                                 size_t outputSizeY, const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, outputSizeX, outputSizeY, restriction)) {
        return;
    }
    if (vectorSize < 1 || vectorSize > 4) {
        ALOGE("The vectorSize should be between 1 and 4. %zu provided.", vectorSize);
        return;
    }
#endif

    ResizeTask task((const uchar*)input, (uchar*)output, inputSizeX, inputSizeY, vectorSize,
                    outputSizeX, outputSizeY, restriction);
    processor->doTask(&task);
}

}  // namespace renderscript
