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

#define LOG_TAG "renderscript.toolkit.Convolve3x3"

namespace renderscript {

extern "C" void rsdIntrinsicConvolve3x3_K(void* dst, const void* y0, const void* y1, const void* y2,
                                          const int16_t* coef, uint32_t count);

class Convolve3x3Task : public Task {
    const void* mIn;
    void* mOut;
    // Even though we have exactly 9 coefficients, store them in an array of size 16 so that
    // the SIMD instructions can load them in chunks multiple of 8.
    float mFp[16];
    int16_t mIp[16];

    void kernelU4(uchar* out, uint32_t xstart, uint32_t xend, const uchar* py0, const uchar* py1,
                  const uchar* py2);
    void convolveU4(const uchar* pin, uchar* pout, size_t vectorSize, size_t sizeX, size_t sizeY,
                    size_t startX, size_t startY, size_t endX, size_t endY);

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    Convolve3x3Task(const void* in, void* out, size_t vectorSize, size_t sizeX, size_t sizeY,
                    const float* coefficients, const Restriction* restriction)
        : Task{sizeX, sizeY, vectorSize, false, restriction}, mIn{in}, mOut{out} {
        for (int ct = 0; ct < 9; ct++) {
            mFp[ct] = coefficients[ct];
            if (mFp[ct] >= 0) {
                mIp[ct] = (int16_t)(mFp[ct] * 256.f + 0.5f);
            } else {
                mIp[ct] = (int16_t)(mFp[ct] * 256.f - 0.5f);
            }
        }
    }
};

/**
 * Computes one convolution and stores the result in the output. This is used for uchar, uchar2,
 * uchar3, and uchar4 vectors.
 *
 * @tparam InputOutputType Type of the input and output arrays. A vector type, e.g. uchar4.
 * @tparam ComputationType Type we use for the intermediate computations.
 * @param x The index in the row of the value we'll convolve.
 * @param out The location in the output array where we store the value.
 * @param py0 The start of the top row.
 * @param py1 The start of the middle row.
 * @param py2 The start of the bottom row.
 * @param coeff Pointer to the float coefficients, in row major format.
 * @param sizeX The number of cells of one row.
 */
template <typename InputOutputType, typename ComputationType>
static void convolveOneU(uint32_t x, InputOutputType* out, const InputOutputType* py0,
                         const InputOutputType* py1, const InputOutputType* py2, const float* coeff,
                         int32_t sizeX) {
    uint32_t x1 = std::max((int32_t)x - 1, 0);
    uint32_t x2 = std::min((int32_t)x + 1, sizeX - 1);

    ComputationType px = convert<ComputationType>(py0[x1]) * coeff[0] +
                         convert<ComputationType>(py0[x]) * coeff[1] +
                         convert<ComputationType>(py0[x2]) * coeff[2] +
                         convert<ComputationType>(py1[x1]) * coeff[3] +
                         convert<ComputationType>(py1[x]) * coeff[4] +
                         convert<ComputationType>(py1[x2]) * coeff[5] +
                         convert<ComputationType>(py2[x1]) * coeff[6] +
                         convert<ComputationType>(py2[x]) * coeff[7] +
                         convert<ComputationType>(py2[x2]) * coeff[8];

    px = clamp(px + 0.5f, 0.f, 255.f);
    *out = convert<InputOutputType>(px);
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
/**
 * Computes one convolution and stores the result in the output. This is used for float, float2,
 * float3, and float4 vectors.
 *
 * @tparam InputOutputType Type of the input and output arrays. A vector type, e.g. float4.
 * @param x The index in the row of the value we'll convolve.
 * @param out The location in the output array where we store the value.
 * @param py0 The start of the top row.
 * @param py1 The start of the middle row.
 * @param py2 The start of the bottom row.
 * @param coeff Pointer to the float coefficients, in row major format.
 * @param sizeX The number of cells of one row.
 */
template <typename InputOutputType>
static void ConvolveOneF(uint32_t x, InputOutputType* out, const InputOutputType* py0,
                         const InputOutputType* py1, const InputOutputType* py2, const float* coeff,
                         int32_t sizeX) {
    uint32_t x1 = std::max((int32_t)x - 1, 0);
    uint32_t x2 = std::min((int32_t)x + 1, sizeX - 1);
    *out = (py0[x1] * coeff[0]) + (py0[x] * coeff[1]) + (py0[x2] * coeff[2]) +
           (py1[x1] * coeff[3]) + (py1[x] * coeff[4]) + (py1[x2] * coeff[5]) +
           (py2[x1] * coeff[6]) + (py2[x] * coeff[7]) + (py2[x2] * coeff[8]);
}
#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

/**
 * This function convolves one line.
 *
 * @param pout Where to place the next output.
 * @param xstart Index in the X direction of where to start.
 * @param xend End index
 * @param ppy0 Points to the start of the previous line.
 * @param ppy1 Points to the start of the current line.
 * @param ppy2 Points to the start of the next line.
 */
void Convolve3x3Task::kernelU4(uchar* pout, uint32_t xstart, uint32_t xend, const uchar* ppy0,
                               const uchar* ppy1, const uchar* ppy2) {
    uchar4* out = (uchar4*)pout;
    const uchar4* py0 = (const uchar4*)ppy0;
    const uchar4* py1 = (const uchar4*)ppy1;
    const uchar4* py2 = (const uchar4*)ppy2;

    uint32_t x1 = xstart;
    uint32_t x2 = xend;
    if (x1 == 0) {
        convolveOneU<uchar4, float4>(0, out, py0, py1, py2, mFp, mSizeX);
        x1++;
        out++;
    }

    if (x2 > x1) {
#if defined(ARCH_ARM_USE_INTRINSICS) || defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            int32_t len = (x2 - x1 - 1) >> 1;
            if (len > 0) {
                rsdIntrinsicConvolve3x3_K(out, &py0[x1 - 1], &py1[x1 - 1], &py2[x1 - 1], mIp, len);
                x1 += len << 1;
                out += len << 1;
            }
        }
#endif

        while (x1 != x2) {
            convolveOneU<uchar4, float4>(x1, out, py0, py1, py2, mFp, mSizeX);
            out++;
            x1++;
        }
    }
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT
template <typename T>
void RsdCpuScriptIntrinsicConvolve3x3_kernelF(void* in, T* out, uint32_t xstart, uint32_t xend,
                                              uint32_t currentY, size_t sizeX, size_t sizeY,
                                              size_t vectorSize, float* fp) {
    const uchar* pin = (const uchar*)in;
    const size_t stride = sizeX * vectorSize * 4;  // float takes 4 bytes

    uint32_t y1 = std::min((int32_t)currentY + 1, (int32_t)(sizeY - 1));
    uint32_t y2 = std::max((int32_t)currentY - 1, 0);
    const T* py0 = (const T*)(pin + stride * y2);
    const T* py1 = (const T*)(pin + stride * currentY);
    const T* py2 = (const T*)(pin + stride * y1);

    for (uint32_t x = xstart; x < xend; x++, out++) {
        ConvolveOneF<T>(x, out, py0, py1, py2, fp, sizeX);
    }
}
#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

template <typename InputOutputType, typename ComputationType>
static void convolveU(const uchar* pin, uchar* pout, size_t vectorSize, size_t sizeX, size_t sizeY,
                      size_t startX, size_t startY, size_t endX, size_t endY, float* fp) {
    const size_t stride = vectorSize * sizeX;
    for (size_t y = startY; y < endY; y++) {
        uint32_t y1 = std::min((int32_t)y + 1, (int32_t)(sizeY - 1));
        uint32_t y2 = std::max((int32_t)y - 1, 0);

        size_t offset = (y * sizeX + startX) * vectorSize;
        InputOutputType* px = (InputOutputType*)(pout + offset);
        InputOutputType* py0 = (InputOutputType*)(pin + stride * y2);
        InputOutputType* py1 = (InputOutputType*)(pin + stride * y);
        InputOutputType* py2 = (InputOutputType*)(pin + stride * y1);
        for (uint32_t x = startX; x < endX; x++, px++) {
            convolveOneU<InputOutputType, ComputationType>(x, px, py0, py1, py2, fp, sizeX);
        }
    }
}

void Convolve3x3Task::convolveU4(const uchar* pin, uchar* pout, size_t vectorSize, size_t sizeX,
                                 size_t sizeY, size_t startX, size_t startY, size_t endX,
                                 size_t endY) {
    const size_t stride = paddedSize(vectorSize) * sizeX;
    for (size_t y = startY; y < endY; y++) {
        uint32_t y1 = std::min((int32_t)y + 1, (int32_t)(sizeY - 1));
        uint32_t y2 = std::max((int32_t)y - 1, 0);

        size_t offset = (y * sizeX + startX) * paddedSize(vectorSize);
        uchar* px = pout + offset;
        const uchar* py0 = pin + stride * y2;
        const uchar* py1 = pin + stride * y;
        const uchar* py2 = pin + stride * y1;
        kernelU4(px, startX, endX, py0, py1, py2);
    }
}

void Convolve3x3Task::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
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

void RenderScriptToolkit::convolve3x3(const void* in, void* out, size_t vectorSize, size_t sizeX,
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

    Convolve3x3Task task(in, out, vectorSize, sizeX, sizeY, coefficients, restriction);
    processor->doTask(&task);
}

}  // namespace renderscript
