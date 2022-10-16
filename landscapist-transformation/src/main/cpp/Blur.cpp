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

#include <cmath>
#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

namespace renderscript {

#define LOG_TAG "renderscript.toolkit.Blur"

/**
 * Blurs an image or a section of an image.
 *
 * Our algorithm does two passes: a vertical blur followed by an horizontal blur.
 */
class BlurTask : public Task {
    // The image we're blurring.
    const uchar* mIn;
    // Where we store the blurred image.
    uchar* outArray;
    // The size of the kernel radius is limited to 25 in ScriptIntrinsicBlur.java.
    // So, the max kernel size is 51 (= 2 * 25 + 1).
    // Considering SSSE3 case, which requires the size is multiple of 4,
    // at least 52 words are necessary. Values outside of the kernel should be 0.
    float mFp[104];
    uint16_t mIp[104];

    // Working area to store the result of the vertical blur, to be used by the horizontal pass.
    // There's one area per thread. Since the needed working area may be too large to put on the
    // stack, we are allocating it from the heap. To avoid paying the allocation cost for each
    // tile, we cache the scratch area here.
    std::vector<void*> mScratch;       // Pointers to the scratch areas, one per thread.
    std::vector<size_t> mScratchSize;  // The size in bytes of the scratch areas, one per thread.

    // The radius of the blur, in floating point and integer format.
    float mRadius;
    int mIradius;

    void kernelU4(void* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY,
                  uint32_t threadIndex);
    void kernelU1(void* outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY);
    void ComputeGaussianWeights();

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    BlurTask(const uint8_t* in, uint8_t* out, size_t sizeX, size_t sizeY, size_t vectorSize,
             uint32_t threadCount, float radius, const Restriction* restriction)
        : Task{sizeX, sizeY, vectorSize, false, restriction},
          mIn{in},
          outArray{out},
          mScratch{threadCount},
          mScratchSize{threadCount},
          mRadius{std::min(25.0f, radius)} {
        ComputeGaussianWeights();
    }

    ~BlurTask() {
        for (size_t i = 0; i < mScratch.size(); i++) {
            if (mScratch[i]) {
                free(mScratch[i]);
            }
        }
    }
};

void BlurTask::ComputeGaussianWeights() {
    memset(mFp, 0, sizeof(mFp));
    memset(mIp, 0, sizeof(mIp));

    // Compute gaussian weights for the blur
    // e is the euler's number
    float e = 2.718281828459045f;
    float pi = 3.1415926535897932f;
    // g(x) = (1 / (sqrt(2 * pi) * sigma)) * e ^ (-x^2 / (2 * sigma^2))
    // x is of the form [-radius .. 0 .. radius]
    // and sigma varies with the radius.
    // Based on some experimental radius values and sigmas,
    // we approximately fit sigma = f(radius) as
    // sigma = radius * 0.4  + 0.6
    // The larger the radius gets, the more our gaussian blur
    // will resemble a box blur since with large sigma
    // the gaussian curve begins to lose its shape
    float sigma = 0.4f * mRadius + 0.6f;

    // Now compute the coefficients. We will store some redundant values to save
    // some math during the blur calculations precompute some values
    float coeff1 = 1.0f / (sqrtf(2.0f * pi) * sigma);
    float coeff2 = - 1.0f / (2.0f * sigma * sigma);

    float normalizeFactor = 0.0f;
    float floatR = 0.0f;
    int r;
    mIradius = (float)ceil(mRadius) + 0.5f;
    for (r = -mIradius; r <= mIradius; r ++) {
        floatR = (float)r;
        mFp[r + mIradius] = coeff1 * powf(e, floatR * floatR * coeff2);
        normalizeFactor += mFp[r + mIradius];
    }

    // Now we need to normalize the weights because all our coefficients need to add up to one
    normalizeFactor = 1.0f / normalizeFactor;
    for (r = -mIradius; r <= mIradius; r ++) {
        mFp[r + mIradius] *= normalizeFactor;
        mIp[r + mIradius] = (uint16_t)(mFp[r + mIradius] * 65536.0f + 0.5f);
    }
}

/**
 * Vertical blur of a uchar4 line.
 *
 * @param sizeY Number of cells of the input array in the vertical direction.
 * @param out Where to place the computed value.
 * @param x Coordinate of the point we're blurring.
 * @param y Coordinate of the point we're blurring.
 * @param ptrIn Start of the input array.
 * @param iStride The size in byte of a row of the input array.
 * @param gPtr The gaussian coefficients.
 * @param iradius The radius of the blur.
 */
static void OneVU4(uint32_t sizeY, float4* out, int32_t x, int32_t y, const uchar* ptrIn,
                   int iStride, const float* gPtr, int iradius) {
    const uchar *pi = ptrIn + x*4;

    float4 blurredPixel = 0;
    for (int r = -iradius; r <= iradius; r ++) {
        int validY = std::max((y + r), 0);
        validY = std::min(validY, (int)(sizeY - 1));
        const uchar4 *pvy = (const uchar4 *)&pi[validY * iStride];
        float4 pf = convert<float4>(pvy[0]);
        blurredPixel += pf * gPtr[0];
        gPtr++;
    }

    out[0] = blurredPixel;
}

/**
 * Vertical blur of a uchar1 line.
 *
 * @param sizeY Number of cells of the input array in the vertical direction.
 * @param out Where to place the computed value.
 * @param x Coordinate of the point we're blurring.
 * @param y Coordinate of the point we're blurring.
 * @param ptrIn Start of the input array.
 * @param iStride The size in byte of a row of the input array.
 * @param gPtr The gaussian coefficients.
 * @param iradius The radius of the blur.
 */
static void OneVU1(uint32_t sizeY, float *out, int32_t x, int32_t y,
                   const uchar *ptrIn, int iStride, const float* gPtr, int iradius) {

    const uchar *pi = ptrIn + x;

    float blurredPixel = 0;
    for (int r = -iradius; r <= iradius; r ++) {
        int validY = std::max((y + r), 0);
        validY = std::min(validY, (int)(sizeY - 1));
        float pf = (float)pi[validY * iStride];
        blurredPixel += pf * gPtr[0];
        gPtr++;
    }

    out[0] = blurredPixel;
}


extern "C" void rsdIntrinsicBlurU1_K(uchar *out, uchar const *in, size_t w, size_t h,
                 size_t p, size_t x, size_t y, size_t count, size_t r, uint16_t const *tab);
extern "C" void rsdIntrinsicBlurU4_K(uchar4 *out, uchar4 const *in, size_t w, size_t h,
                 size_t p, size_t x, size_t y, size_t count, size_t r, uint16_t const *tab);

#if defined(ARCH_X86_HAVE_SSSE3)
extern void rsdIntrinsicBlurVFU4_K(void *dst, const void *pin, int stride, const void *gptr,
                                   int rct, int x1, int ct);
extern void rsdIntrinsicBlurHFU4_K(void *dst, const void *pin, const void *gptr, int rct, int x1,
                                   int ct);
extern void rsdIntrinsicBlurHFU1_K(void *dst, const void *pin, const void *gptr, int rct, int x1,
                                   int ct);
#endif

/**
 * Vertical blur of a line of RGBA, knowing that there's enough rows above and below us to avoid
 * dealing with boundary conditions.
 *
 * @param out Where to store the results. This is the input to the horizontal blur.
 * @param ptrIn The input data for this line.
 * @param iStride The width of the input.
 * @param gPtr The gaussian coefficients.
 * @param ct The diameter of the blur.
 * @param len How many cells to blur.
 * @param usesSimd Whether this processor supports SIMD.
 */
static void OneVFU4(float4 *out, const uchar *ptrIn, int iStride, const float* gPtr, int ct,
                    int x2, bool usesSimd) {
    int x1 = 0;
#if defined(ARCH_X86_HAVE_SSSE3)
    if (usesSimd) {
        int t = (x2 - x1);
        t &= ~1;
        if (t) {
            rsdIntrinsicBlurVFU4_K(out, ptrIn, iStride, gPtr, ct, x1, x1 + t);
        }
        x1 += t;
        out += t;
        ptrIn += t << 2;
    }
#else
    (void) usesSimd; // Avoid unused parameter warning.
#endif
    while(x2 > x1) {
        const uchar *pi = ptrIn;
        float4 blurredPixel = 0;
        const float* gp = gPtr;

        for (int r = 0; r < ct; r++) {
            float4 pf = convert<float4>(((const uchar4 *)pi)[0]);
            blurredPixel += pf * gp[0];
            pi += iStride;
            gp++;
        }
        out->xyzw = blurredPixel;
        x1++;
        out++;
        ptrIn+=4;
    }
}

/**
 * Vertical blur of a line of U_8, knowing that there's enough rows above and below us to avoid
 * dealing with boundary conditions.
 *
 * @param out Where to store the results. This is the input to the horizontal blur.
 * @param ptrIn The input data for this line.
 * @param iStride The width of the input.
 * @param gPtr The gaussian coefficients.
 * @param ct The diameter of the blur.
 * @param len How many cells to blur.
 * @param usesSimd Whether this processor supports SIMD.
 */
static void OneVFU1(float* out, const uchar* ptrIn, int iStride, const float* gPtr, int ct, int len,
                    bool usesSimd) {
    int x1 = 0;

    while((len > x1) && (((uintptr_t)ptrIn) & 0x3)) {
        const uchar *pi = ptrIn;
        float blurredPixel = 0;
        const float* gp = gPtr;

        for (int r = 0; r < ct; r++) {
            float pf = (float)pi[0];
            blurredPixel += pf * gp[0];
            pi += iStride;
            gp++;
        }
        out[0] = blurredPixel;
        x1++;
        out++;
        ptrIn++;
        len--;
    }
#if defined(ARCH_X86_HAVE_SSSE3)
    if (usesSimd && (len > x1)) {
        int t = (len - x1) >> 2;
        t &= ~1;
        if (t) {
            rsdIntrinsicBlurVFU4_K(out, ptrIn, iStride, gPtr, ct, 0, t );
            len -= t << 2;
            ptrIn += t << 2;
            out += t << 2;
        }
    }
#else
    (void) usesSimd; // Avoid unused parameter warning.
#endif
    while(len > 0) {
        const uchar *pi = ptrIn;
        float blurredPixel = 0;
        const float* gp = gPtr;

        for (int r = 0; r < ct; r++) {
            float pf = (float)pi[0];
            blurredPixel += pf * gp[0];
            pi += iStride;
            gp++;
        }
        out[0] = blurredPixel;
        len--;
        out++;
        ptrIn++;
    }
}

/**
 * Horizontal blur of a uchar4 line.
 *
 * @param sizeX Number of cells of the input array in the horizontal direction.
 * @param out Where to place the computed value.
 * @param x Coordinate of the point we're blurring.
 * @param ptrIn The start of the input row from which we're indexing x.
 * @param gPtr The gaussian coefficients.
 * @param iradius The radius of the blur.
 */
static void OneHU4(uint32_t sizeX, uchar4* out, int32_t x, const float4* ptrIn, const float* gPtr,
                   int iradius) {
    float4 blurredPixel = 0;
    for (int r = -iradius; r <= iradius; r ++) {
        int validX = std::max((x + r), 0);
        validX = std::min(validX, (int)(sizeX - 1));
        float4 pf = ptrIn[validX];
        blurredPixel += pf * gPtr[0];
        gPtr++;
    }

    out->xyzw = convert<uchar4>(blurredPixel);
}

/**
 * Horizontal blur of a uchar line.
 *
 * @param sizeX Number of cells of the input array in the horizontal direction.
 * @param out Where to place the computed value.
 * @param x Coordinate of the point we're blurring.
 * @param ptrIn The start of the input row from which we're indexing x.
 * @param gPtr The gaussian coefficients.
 * @param iradius The radius of the blur.
 */
static void OneHU1(uint32_t sizeX, uchar* out, int32_t x, const float* ptrIn, const float* gPtr,
                   int iradius) {
    float blurredPixel = 0;
    for (int r = -iradius; r <= iradius; r ++) {
        int validX = std::max((x + r), 0);
        validX = std::min(validX, (int)(sizeX - 1));
        float pf = ptrIn[validX];
        blurredPixel += pf * gPtr[0];
        gPtr++;
    }

    out[0] = (uchar)blurredPixel;
}

/**
 * Full blur of a line of RGBA data.
 *
 * @param outPtr Where to store the results
 * @param xstart The index of the section we're starting to blur.
 * @param xend  The end index of the section.
 * @param currentY The index of the line we're blurring.
 * @param usesSimd Whether this processor supports SIMD.
 */
void BlurTask::kernelU4(void *outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY,
                        uint32_t threadIndex) {
    float4 stackbuf[2048];
    float4 *buf = &stackbuf[0];
    const uint32_t stride = mSizeX * mVectorSize;

    uchar4 *out = (uchar4 *)outPtr;
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd && mSizeX >= 4) {
      rsdIntrinsicBlurU4_K(out, (uchar4 const *)(mIn + stride * currentY),
                 mSizeX, mSizeY,
                 stride, x1, currentY, x2 - x1, mIradius, mIp + mIradius);
        return;
    }
#endif

    if (mSizeX > 2048) {
        if ((mSizeX > mScratchSize[threadIndex]) || !mScratch[threadIndex]) {
            // Pad the side of the allocation by one unit to allow alignment later
            mScratch[threadIndex] = realloc(mScratch[threadIndex], (mSizeX + 1) * 16);
            mScratchSize[threadIndex] = mSizeX;
        }
        // realloc only aligns to 8 bytes so we manually align to 16.
        buf = (float4 *) ((((intptr_t)mScratch[threadIndex]) + 15) & ~0xf);
    }
    float4 *fout = (float4 *)buf;
    int y = currentY;
    if ((y > mIradius) && (y < ((int)mSizeY - mIradius))) {
        const uchar *pi = mIn + (y - mIradius) * stride;
        OneVFU4(fout, pi, stride, mFp, mIradius * 2 + 1, mSizeX, mUsesSimd);
    } else {
        x1 = 0;
        while(mSizeX > x1) {
            OneVU4(mSizeY, fout, x1, y, mIn, stride, mFp, mIradius);
            fout++;
            x1++;
        }
    }

    x1 = xstart;
    while ((x1 < (uint32_t)mIradius) && (x1 < x2)) {
        OneHU4(mSizeX, out, x1, buf, mFp, mIradius);
        out++;
        x1++;
    }
#if defined(ARCH_X86_HAVE_SSSE3)
    if (mUsesSimd) {
        if ((x1 + mIradius) < x2) {
            rsdIntrinsicBlurHFU4_K(out, buf - mIradius, mFp,
                                   mIradius * 2 + 1, x1, x2 - mIradius);
            out += (x2 - mIradius) - x1;
            x1 = x2 - mIradius;
        }
    }
#endif
    while(x2 > x1) {
        OneHU4(mSizeX, out, x1, buf, mFp, mIradius);
        out++;
        x1++;
    }
}

/**
 * Full blur of a line of U_8 data.
 *
 * @param outPtr Where to store the results
 * @param xstart The index of the section we're starting to blur.
 * @param xend  The end index of the section.
 * @param currentY The index of the line we're blurring.
 */
void BlurTask::kernelU1(void *outPtr, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    float buf[4 * 2048];
    const uint32_t stride = mSizeX * mVectorSize;

    uchar *out = (uchar *)outPtr;
    uint32_t x1 = xstart;
    uint32_t x2 = xend;

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd && mSizeX >= 16) {
        // The specialisation for r<=8 has an awkward prefill case, which is
        // fiddly to resolve, where starting close to the right edge can cause
        // a read beyond the end of input.  So avoid that case here.
        if (mIradius > 8 || (mSizeX - std::max(0, (int32_t)x1 - 8)) >= 16) {
            rsdIntrinsicBlurU1_K(out, mIn + stride * currentY, mSizeX, mSizeY,
                     stride, x1, currentY, x2 - x1, mIradius, mIp + mIradius);
            return;
        }
    }
#endif

    float *fout = (float *)buf;
    int y = currentY;
    if ((y > mIradius) && (y < ((int)mSizeY - mIradius -1))) {
        const uchar *pi = mIn + (y - mIradius) * stride;
        OneVFU1(fout, pi, stride, mFp, mIradius * 2 + 1, mSizeX, mUsesSimd);
    } else {
        x1 = 0;
        while(mSizeX > x1) {
            OneVU1(mSizeY, fout, x1, y, mIn, stride, mFp, mIradius);
            fout++;
            x1++;
        }
    }

    x1 = xstart;
    while ((x1 < x2) &&
           ((x1 < (uint32_t)mIradius) || (((uintptr_t)out) & 0x3))) {
        OneHU1(mSizeX, out, x1, buf, mFp, mIradius);
        out++;
        x1++;
    }
#if defined(ARCH_X86_HAVE_SSSE3)
    if (mUsesSimd) {
        if ((x1 + mIradius) < x2) {
            uint32_t len = x2 - (x1 + mIradius);
            len &= ~3;

            // rsdIntrinsicBlurHFU1_K() processes each four float values in |buf| at once, so it
            // nees to ensure four more values can be accessed in order to avoid accessing
            // uninitialized buffer.
            if (len > 4) {
                len -= 4;
                rsdIntrinsicBlurHFU1_K(out, ((float *)buf) - mIradius, mFp,
                                       mIradius * 2 + 1, x1, x1 + len);
                out += len;
                x1 += len;
            }
        }
    }
#endif
    while(x2 > x1) {
        OneHU1(mSizeX, out, x1, buf, mFp, mIradius);
        out++;
        x1++;
    }
}

void BlurTask::processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                           size_t endY) {
    for (size_t y = startY; y < endY; y++) {
        void* outPtr = outArray + (mSizeX * y + startX) * mVectorSize;
        if (mVectorSize == 4) {
            kernelU4(outPtr, startX, endX, y, threadIndex);
        } else {
            kernelU1(outPtr, startX, endX, y);
        }
    }
}

void RenderScriptToolkit::blur(const uint8_t* in, uint8_t* out, size_t sizeX, size_t sizeY,
                               size_t vectorSize, int radius, const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
    if (radius <= 0 || radius > 25) {
        ALOGE("The radius should be between 1 and 25. %d provided.", radius);
    }
    if (vectorSize != 1 && vectorSize != 4) {
        ALOGE("The vectorSize should be 1 or 4. %zu provided.", vectorSize);
    }
#endif

    BlurTask task(in, out, sizeX, sizeY, vectorSize, processor->getNumberOfThreads(), radius,
                  restriction);
    processor->doTask(&task);
}

}  // namespace renderscript
