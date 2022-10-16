/*
 * Copyright (C) 2013 The Android Open Source Project
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

#include <array>
#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.Histogram"

namespace renderscript {

class HistogramTask : public Task {
    const uchar* mIn;
    std::vector<int> mSums;
    uint32_t mThreadCount;

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

    void kernelP1U4(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);
    void kernelP1U3(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);
    void kernelP1U2(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);
    void kernelP1U1(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);

   public:
    HistogramTask(const uint8_t* in, size_t sizeX, size_t sizeY, size_t vectorSize,
                  uint32_t threadCount, const Restriction* restriction);
    void collateSums(int* out);
};

class HistogramDotTask : public Task {
    const uchar* mIn;
    float mDot[4];
    int mDotI[4];
    std::vector<int> mSums;
    uint32_t mThreadCount;

    void kernelP1L4(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);
    void kernelP1L3(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);
    void kernelP1L2(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);
    void kernelP1L1(const uchar* in, int* sums, uint32_t xstart, uint32_t xend);

   public:
    HistogramDotTask(const uint8_t* in, size_t sizeX, size_t sizeY, size_t vectorSize,
                     uint32_t threadCount, const float* coefficients,
                     const Restriction* restriction);
    void collateSums(int* out);

    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;
};

HistogramTask::HistogramTask(const uchar* in, size_t sizeX, size_t sizeY, size_t vectorSize,
                             uint32_t threadCount, const Restriction* restriction)
    : Task{sizeX, sizeY, vectorSize, true, restriction},
      mIn{in},
      mSums(256 * paddedSize(vectorSize) * threadCount) {
    mThreadCount = threadCount;
}

void HistogramTask::processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                                size_t endY) {
    typedef void (HistogramTask::*KernelFunction)(const uchar*, int*, uint32_t, uint32_t);

    KernelFunction kernel;
    switch (mVectorSize) {
        case 4:
            kernel = &HistogramTask::kernelP1U4;
            break;
        case 3:
            kernel = &HistogramTask::kernelP1U3;
            break;
        case 2:
            kernel = &HistogramTask::kernelP1U2;
            break;
        case 1:
            kernel = &HistogramTask::kernelP1U1;
            break;
        default:
            ALOGE("Bad vector size %zd", mVectorSize);
            return;
    }

    int* sums = &mSums[256 * paddedSize(mVectorSize) * threadIndex];

    for (size_t y = startY; y < endY; y++) {
        const uchar* inPtr = mIn + (mSizeX * y + startX) * paddedSize(mVectorSize);
        std::invoke(kernel, this, inPtr, sums, startX, endX);
    }
}

void HistogramTask::kernelP1U4(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        sums[(in[0] << 2)]++;
        sums[(in[1] << 2) + 1]++;
        sums[(in[2] << 2) + 2]++;
        sums[(in[3] << 2) + 3]++;
        in += 4;
    }
}

void HistogramTask::kernelP1U3(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        sums[(in[0] << 2)]++;
        sums[(in[1] << 2) + 1]++;
        sums[(in[2] << 2) + 2]++;
        in += 4;
    }
}

void HistogramTask::kernelP1U2(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        sums[(in[0] << 1)]++;
        sums[(in[1] << 1) + 1]++;
        in += 2;
    }
}

void HistogramTask::kernelP1U1(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        sums[in[0]]++;
        in++;
    }
}

void HistogramTask::collateSums(int* out) {
    for (uint32_t ct = 0; ct < (256 * paddedSize(mVectorSize)); ct++) {
        out[ct] = mSums[ct];
        for (uint32_t t = 1; t < mThreadCount; t++) {
            out[ct] += mSums[ct + (256 * paddedSize(mVectorSize) * t)];
        }
    }
}

HistogramDotTask::HistogramDotTask(const uchar* in, size_t sizeX, size_t sizeY, size_t vectorSize,
                                   uint32_t threadCount, const float* coefficients,
                                   const Restriction* restriction)
    : Task{sizeX, sizeY, vectorSize, true, restriction}, mIn{in}, mSums(256 * threadCount, 0) {
    mThreadCount = threadCount;

    if (coefficients == nullptr) {
        mDot[0] = 0.299f;
        mDot[1] = 0.587f;
        mDot[2] = 0.114f;
        mDot[3] = 0;
    } else {
        memcpy(mDot, coefficients, 16);
    }
    mDotI[0] = (int)((mDot[0] * 256.f) + 0.5f);
    mDotI[1] = (int)((mDot[1] * 256.f) + 0.5f);
    mDotI[2] = (int)((mDot[2] * 256.f) + 0.5f);
    mDotI[3] = (int)((mDot[3] * 256.f) + 0.5f);
}

void HistogramDotTask::processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                                   size_t endY) {
    typedef void (HistogramDotTask::*KernelFunction)(const uchar*, int*, uint32_t, uint32_t);

    KernelFunction kernel;
    switch (mVectorSize) {
        case 4:
            kernel = &HistogramDotTask::kernelP1L4;
            break;
        case 3:
            kernel = &HistogramDotTask::kernelP1L3;
            break;
        case 2:
            kernel = &HistogramDotTask::kernelP1L2;
            break;
        case 1:
            kernel = &HistogramDotTask::kernelP1L1;
            break;
        default:
            ALOGI("Bad vector size %zd", mVectorSize);
            return;
    }

    int* sums = &mSums[256 * threadIndex];

    for (size_t y = startY; y < endY; y++) {
        const uchar* inPtr = mIn + (mSizeX * y + startX) * paddedSize(mVectorSize);
        std::invoke(kernel, this, inPtr, sums, startX, endX);
    }
}

void HistogramDotTask::kernelP1L4(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        int t = (mDotI[0] * in[0]) + (mDotI[1] * in[1]) + (mDotI[2] * in[2]) + (mDotI[3] * in[3]);
        sums[(t + 0x7f) >> 8]++;
        in += 4;
    }
}

void HistogramDotTask::kernelP1L3(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        int t = (mDotI[0] * in[0]) + (mDotI[1] * in[1]) + (mDotI[2] * in[2]);
        sums[(t + 0x7f) >> 8]++;
        in += 4;
    }
}

void HistogramDotTask::kernelP1L2(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        int t = (mDotI[0] * in[0]) + (mDotI[1] * in[1]);
        sums[(t + 0x7f) >> 8]++;
        in += 2;
    }
}

void HistogramDotTask::kernelP1L1(const uchar* in, int* sums, uint32_t xstart, uint32_t xend) {
    for (uint32_t x = xstart; x < xend; x++) {
        int t = (mDotI[0] * in[0]);
        sums[(t + 0x7f) >> 8]++;
        in++;
    }
}

void HistogramDotTask::collateSums(int* out) {
    for (uint32_t ct = 0; ct < 256; ct++) {
        out[ct] = mSums[ct];
        for (uint32_t t = 1; t < mThreadCount; t++) {
            out[ct] += mSums[ct + (256 * t)];
        }
    }
}

////////////////////////////////////////////////////////////////////////////

void RenderScriptToolkit::histogram(const uint8_t* in, int32_t* out, size_t sizeX, size_t sizeY,
                                    size_t vectorSize, const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
    if (vectorSize < 1 || vectorSize > 4) {
        ALOGE("The vectorSize should be between 1 and 4. %zu provided.", vectorSize);
        return;
    }
#endif

    HistogramTask task(in, sizeX, sizeY, vectorSize, processor->getNumberOfThreads(), restriction);
    processor->doTask(&task);
    task.collateSums(out);
}

void RenderScriptToolkit::histogramDot(const uint8_t* in, int32_t* out, size_t sizeX, size_t sizeY,
                                       size_t vectorSize, const float* coefficients,
                                       const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
    if (vectorSize < 1 || vectorSize > 4) {
        ALOGE("The vectorSize should be between 1 and 4. %zu provided.", vectorSize);
        return;
    }
    if (coefficients != nullptr) {
        float sum = 0.0f;
        for (size_t i = 0; i < vectorSize; i++) {
            if (coefficients[i] < 0.0f) {
                ALOGE("histogramDot coefficients should not be negative. Coefficient %zu was %f.",
                      i, coefficients[i]);
                return;
            }
            sum += coefficients[i];
        }
        if (sum > 1.0f) {
            ALOGE("histogramDot coefficients should add to 1 or less. Their sum is %f.", sum);
            return;
        }
    }
#endif

    HistogramDotTask task(in, sizeX, sizeY, vectorSize, processor->getNumberOfThreads(),
                          coefficients, restriction);
    processor->doTask(&task);
    task.collateSums(out);
}

}  // namespace renderscript
