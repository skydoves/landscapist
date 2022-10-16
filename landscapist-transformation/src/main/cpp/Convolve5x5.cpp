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

#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

namespace renderscript {

#define LOG_TAG "renderscript.toolkit.Convolve5x5"

extern "C" void rsdIntrinsicConvolve5x5_K(void* dst, const void* y0, const void* y1, const void* y2,
                                          const void* y3, const void* y4, const int16_t* coef,
                                          uint32_t count);

class Convolve5x5Task : public Task {
    const void* mIn;
    void* mOut;
    // Even though we have exactly 25 coefficients, store them in an array of size 28 so that
    // the SIMD instructions can load them in three chunks of 8 and 1 of chunk of 4.
    float mFp[28];
    int16_t mIp[28];

    void kernelU4(uchar* out, uint32_t xstart, uint32_t xend, const uchar* py0, const uchar* py1,
                  const uchar* py2, const uchar* py3, const uchar* py4);
    void convolveU4(const uchar* pin, uchar* pout, size_t vectorSize, size_t sizeX, size_t sizeY,
                    size_t startX, size_t startY, size_t endX, size_t endY);

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    Convolve5x5Task(const void* in, void* out, size_t vectorSize, size_t sizeX, size_t sizeY,
                    const float* coefficients, const Restriction* restriction)
        : Task{sizeX, sizeY, vectorSize, false, restriction}, mIn{in}, mOut{out} {
        for (int ct = 0; ct < 25; ct++) {
            mFp[ct] = coefficients[ct];
            if (mFp[ct] >= 0) {
                mIp[ct] = (int16_t)(mFp[ct] * 256.f + 0.5f);
            } else {
                mIp[ct] = (int16_t)(mFp[ct] * 256.f - 0.5f);
            }
        }
    }
};

template <typename InputOutputType, typename ComputationType>
static void ConvolveOneU(uint32_t x, InputOutputType* out, const InputOutputType* py0,
                         const InputOutputType* py1, const InputOutputType* py2,
                         const InputOutputType* py3, const InputOutputType* py4, const float* coeff,
                         int32_t width) {
    uint32_t x0 = std::max((int32_t)x - 2, 0);
    uint32_t x1 = std::max((int32_t)x - 1, 0);
    uint32_t x2 = x;
    uint32_t x3 = std::min((int32_t)x + 1, width - 1);
    uint32_t x4 = std::min((int32_t)x + 2, width - 1);

    ComputationType px = convert<ComputationType>(py0[x0]) * coeff[0] +
                         convert<ComputationType>(py0[x1]) * coeff[1] +
                         convert<ComputationType>(py0[x2]) * coeff[2] +
                         convert<ComputationType>(py0[x3]) * coeff[3] +
                         convert<ComputationType>(py0[x4]) * coeff[4] +

                         convert<ComputationType>(py1[x0]) * coeff[5] +
                         convert<ComputationType>(py1[x1]) * coeff[6] +
                         convert<ComputationType>(py1[x2]) * coeff[7] +
                         convert<ComputationType>(py1[x3]) * coeff[8] +
                         convert<ComputationType>(py1[x4]) * coeff[9] +

                         convert<ComputationType>(py2[x0]) * coeff[10] +
                         convert<ComputationType>(py2[x1]) * coeff[11] +
                         convert<ComputationType>(py2[x2]) * coeff[12] +
                         convert<ComputationType>(py2[x3]) * coeff[13] +
                         convert<ComputationType>(py2[x4]) * coeff[14] +

                         convert<ComputationType>(py3[x0]) * coeff[15] +
                         convert<ComputationType>(py3[x1]) * coeff[16] +
                         convert<ComputationType>(py3[x2]) * coeff[17] +
                         convert<ComputationType>(py3[x3]) * coeff[18] +
                         convert<ComputationType>(py3[x4]) * coeff[19] +

                         convert<ComputationType>(py4[x0]) * coeff[20] +
                         convert<ComputationType>(py4[x1]) * coeff[21] +
                         convert<ComputationType>(py4[x2]) * coeff[22] +
                         convert<ComputationType>(py4[x3]) * coeff[23] +
                         convert<ComputationType>(py4[x4]) * coeff[24];
    px = clamp(px + 0.5f, 0.f, 255.f);
    *out = convert<InputOutputType>(px);
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
template <typename InputOutputType>
static void ConvolveOneF(uint32_t x, InputOutputType* out, const InputOutputType* py0,
                         const InputOutputType* py1, const InputOutputType* py2,
                         const InputOutputType* py3, const InputOutputType* py4, const float* coeff,
                         int32_t width) {
    uint32_t x0 = std::max((int32_t)x - 2, 0);
    uint32_t x1 = std::max((int32_t)x - 1, 0);
    uint32_t x2 = x;
    uint32_t x3 = std::min((int32_t)x + 1, width - 1);
    uint32_t x4 = std::min((int32_t)x + 2, width - 1);

    InputOutputType px = py0[x0] * coeff[0] + py0[x1] * coeff[1] + py0[x2] * coeff[2] +
                         py0[x3] * coeff[3] + py0[x4] * coeff[4] +

                         py1[x0] * coeff[5] + py1[x1] * coeff[6] + py1[x2] * coeff[7] +
                         py1[x3] * coeff[8] + py1[x4] * coeff[9] +

                         py2[x0] * coeff[10] + py2[x1] * coeff[11] + py2[x2] * coeff[12] +
                         py2[x3] * coeff[13] + py2[x4] * coeff[14] +

                         py3[x0] * coeff[15] + py3[x1] * coeff[16] + py3[x2] * coeff[17] +
                         py3[x3] * coeff[18] + py3[x4] * coeff[19] +

                         py4[x0] * coeff[20] + py4[x1] * coeff[21] + py4[x2] * coeff[22] +
                         py4[x3] * coeff[23] + py4[x4] * coeff[24];
    *out = px;
}
#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

/**
 * This function convolves one line.
 *
 * @param pout Where to place the next output.
 * @param xstart Index in the X direction of where to start.
 * @param xend End index
 * @param ppy0 Points to the start of the line two above.
 * @param ppy1 Points to the start of the line one above.
 * @param ppy2 Points to the start of the current line.
 * @param ppy3 Points to the start of the line one below.
 * @param ppy4 Points to the start of the line two below.
 */
void Convolve5x5Task::kernelU4(uchar* pout, uint32_t x1, uint32_t x2, const uchar* ppy0,
                               const uchar* ppy1, const uchar* ppy2, const uchar* ppy3,
                               const uchar* ppy4) {
    uchar4* out = (uchar4*)pout;
    const uchar4* py0 = (const uchar4*)ppy0;
    const uchar4* py1 = (const uchar4*)ppy1;
    const uchar4* py2 = (const uchar4*)ppy2;
    const uchar4* py3 = (const uchar4*)ppy3;
    const uchar4* py4 = (const uchar4*)ppy4;

    while ((x1 < x2) && (x1 < 2)) {
        ConvolveOneU<uchar4, float4>(x1, out, py0, py1, py2, py3, py4, mFp, mSizeX);
        out++;
        x1++;
    }
#if defined(ARCH_X86_HAVE_SSSE3)
    // for x86 SIMD, require minimum of 7 elements (4 for SIMD,
    // 3 for end boundary where x may hit the end boundary)
    if (mUsesSimd && ((x1 + 6) < x2)) {
        // subtract 3 for end boundary
        uint32_t len = (x2 - x1 - 3) >> 2;
        rsdIntrinsicConvolve5x5_K(out, py0 + x1 - 2, py1 + x1 - 2, py2 + x1 - 2, py3 + x1 - 2,
                                  py4 + x1 - 2, mIp, len);
        out += len << 2;
        x1 += len << 2;
    }
#endif

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd && ((x1 + 3) < x2)) {
        uint32_t len = (x2 - x1 - 3) >> 1;
        rsdIntrinsicConvolve5x5_K(out, py0 + x1 - 2, py1 + x1 - 2, py2 + x1 - 2, py3 + x1 - 2,
                                  py4 + x1 - 2, mIp, len);
        out += len << 1;
        x1 += len << 1;
    }
#endif

    while (x1 < x2) {
        ConvolveOneU<uchar4, float4>(x1, out, py0, py1, py2, py3, py4, mFp, mSizeX);
        out++;
        x1++;
    }
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
// This will need more cleanup before it can be used.
void Convolve5x5Task::kernelF4(const ConvolveInfo* info, float4* out,
                               uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar* pin = (const uchar*)info->in;
    const size_t stride = info->stride;

    uint32_t y0 = std::max((int32_t)currentY - 2, 0);
    uint32_t y1 = std::max((int32_t)currentY - 1, 0);
    uint32_t y2 = currentY;
    uint32_t y3 = std::min((int32_t)currentY + 1, sizeY);
    uint32_t y4 = std::min((int32_t)currentY + 2, sizeY);

    const float4* py0 = (const float4*)(pin + stride * y0);
    const float4* py1 = (const float4*)(pin + stride * y1);
    const float4* py2 = (const float4*)(pin + stride * y2);
    const float4* py3 = (const float4*)(pin + stride * y3);
    const float4* py4 = (const float4*)(pin + stride * y4);

    for (uint32_t x = xstart; x < xend; x++, out++) {
        ConvolveOneF<float4>(x, out, py0, py1, py2, py3, py4, mFp, sizeX);
    }
}

void RsdCpuScriptIntrinsicConvolve5x5_kernelF2(const ConvolveInfo* info, float2* out,
                                               uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar* pin = (const uchar*)info->in;
    const size_t stride = info->stride;

    uint32_t y0 = std::max((int32_t)currentY - 2, 0);
    uint32_t y1 = std::max((int32_t)currentY - 1, 0);
    uint32_t y2 = currentY;
    uint32_t y3 = std::min((int32_t)currentY + 1, sizeY);
    uint32_t y4 = std::min((int32_t)currentY + 2, sizeY);

    const float2* py0 = (const float2*)(pin + stride * y0);
    const float2* py1 = (const float2*)(pin + stride * y1);
    const float2* py2 = (const float2*)(pin + stride * y2);
    const float2* py3 = (const float2*)(pin + stride * y3);
    const float2* py4 = (const float2*)(pin + stride * y4);

    for (uint32_t x = xstart; x < xend; x++, out++) {
        ConvolveOneF<float2>(x, out, py0, py1, py2, py3, py4, mFp, sizeX);
    }
}

void RsdCpuScriptIntrinsicConvolve5x5_kernelF1(const ConvolveInfo* info, float* out,
                                               uint32_t xstart, uint32_t xend, uint32_t currentY) {
    const uchar* pin = (const uchar*)info->in;
    const size_t stride = info->stride;

    uint32_t y0 = std::max((int32_t)currentY - 2, 0);
    uint32_t y1 = std::max((int32_t)currentY - 1, 0);
    uint32_t y2 = currentY;
    uint32_t y3 = std::min((int32_t)currentY + 1, sizeY);
    uint32_t y4 = std::min((int32_t)currentY + 2, sizeY);

    const float* py0 = (const float*)(pin + stride * y0);
    const float* py1 = (const float*)(pin + stride * y1);
    const float* py2 = (const float*)(pin + stride * y2);
    const float* py3 = (const float*)(pin + stride * y3);
    const float* py4 = (const float*)(pin + stride * y4);

    for (uint32_t x = xstart; x < xend; x++, out++) {
        ConvolveOneF<float>(x, out, py0, py1, py2, py3, py4, mFp, sizeX);
    }
}
#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

template <typename InputOutputType, typename ComputationType>
static void convolveU(const uchar* pin, uchar* pout, size_t vectorSize, size_t sizeX, size_t sizeY,
                      size_t startX, size_t startY, size_t endX, size_t endY, float* mFp) {
    const size_t stride = vectorSize * sizeX;
    for (size_t y = startY; y < endY; y++) {
        uint32_t y0 = std::max((int32_t)y - 2, 0);
        uint32_t y1 = std::max((int32_t)y - 1, 0);
        uint32_t y2 = y;
        uint32_t y3 = std::min((int32_t)y + 1, (int32_t)(sizeY - 1));
        uint32_t y4 = std::min((int32_t)y + 2, (int32_t)(sizeY - 1));

        size_t offset = (y * sizeX + startX) * vectorSize;
        InputOutputType* px = (InputOutputType*)(pout + offset);
        InputOutputType* py0 = (InputOutputType*)(pin + stride * y0);
        InputOutputType* py1 = (InputOutputType*)(pin + stride * y1);
        InputOutputType* py2 = (InputOutputType*)(pin + stride * y2);
        InputOutputType* py3 = (InputOutputType*)(pin + stride * y3);
        InputOutputType* py4 = (InputOutputType*)(pin + stride * y4);
        for (uint32_t x = startX; x < endX; x++, px++) {
            ConvolveOneU<InputOutputType, ComputationType>(x, px, py0, py1, py2, py3, py4, mFp,
                                                           sizeX);
        }
    }
}

void Convolve5x5Task::convolveU4(const uchar* pin, uchar* pout, size_t vectorSize, size_t sizeX,
                                 size_t sizeY, size_t startX, size_t startY, size_t endX,
                                 size_t endY) {
    const size_t stride = paddedSize(vectorSize) * sizeX;
    for (size_t y = startY; y < endY; y++) {
        uint32_t y0 = std::max((int32_t)y - 2, 0);
        uint32_t y1 = std::max((int32_t)y - 1, 0);
        uint32_t y2 = y;
        uint32_t y3 = std::min((int32_t)y + 1, (int32_t)(sizeY - 1));
        uint32_t y4 = std::min((int32_t)y + 2, (int32_t)(sizeY - 1));

        size_t offset = (y * sizeX + startX) * paddedSize(vectorSize);
        uchar* px = pout + offset;
        const uchar* py0 = pin + stride * y0;
        const uchar* py1 = pin + stride * y1;
        const uchar* py2 = pin + stride * y2;
        const uchar* py3 = pin + stride * y3;
        const uchar* py4 = pin + stride * y4;
        kernelU4(px, startX, endX, py0, py1, py2, py3, py4);
    }
}

void Convolve5x5Task::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                                  size_t endY) {
    // ALOGI("Thread %d start tile from (%zd, %zd) to (%zd, %zd)", threadIndex, startX, startY,
    // endX, endY);
    switch (mVectorSize) {
        case 1:
            convolveU<uchar, float>((const uchar*)mIn, (uchar*)mOut, mVectorSize, mSizeX, mSizeY,
                                    startX, startY, endX, endY, mFp);
            break;
        case 2:
            convolveU<uchar2, float2>((const uchar*)mIn, (uchar*)mOut, mVectorSize, mSizeX, mSizeY,
                                      startX, startY, endX, endY, mFp);
            break;
        case 3:
        case 4:
            convolveU4((const uchar*)mIn, (uchar*)mOut, mVectorSize, mSizeX, mSizeY, startX, startY,
                       endX, endY);
            break;
    }
}

void RenderScriptToolkit::convolve5x5(const void* in, void* out, size_t vectorSize, size_t sizeX,
                                      size_t sizeY, const float* coefficients,
                                      const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
    if (vectorSize < 1 || vectorSize > 4) {
        ALOGE("The vectorSize should be between 1 and 4. %zu provided.", vectorSize);
        return;
    }
#endif

    Convolve5x5Task task(in, out, vectorSize, sizeX, sizeY, coefficients, restriction);
    processor->doTask(&task);
}

}  // namespace renderscript
