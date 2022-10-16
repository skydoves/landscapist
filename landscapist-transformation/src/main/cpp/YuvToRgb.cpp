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

#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.YuvToRgb"

namespace renderscript {

inline size_t roundUpTo16(size_t val) {
    return (val + 15u) & ~15u;
}

class YuvToRgbTask : public Task {
    uchar4* mOut;
    size_t mCstep;
    size_t mStrideY;
    size_t mStrideU;
    size_t mStrideV;
    const uchar* mInY;
    const uchar* mInU;
    const uchar* mInV;

    void kernel(uchar4* out, uint32_t xstart, uint32_t xend, uint32_t currentY);
    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    YuvToRgbTask(const uint8_t* input, uint8_t* output, size_t sizeX, size_t sizeY,
                 RenderScriptToolkit::YuvFormat format)
        : Task{sizeX, sizeY, 4, false, nullptr}, mOut{reinterpret_cast<uchar4*>(output)} {
        switch (format) {
            case RenderScriptToolkit::YuvFormat::NV21:
                mCstep = 2;
                mStrideY = sizeX;
                mStrideU = mStrideY;
                mStrideV = mStrideY;
                mInY = reinterpret_cast<const uchar*>(input);
                mInV = reinterpret_cast<const uchar*>(input + mStrideY * sizeY);
                mInU = mInV + 1;
                break;
            case RenderScriptToolkit::YuvFormat::YV12:
                mCstep = 1;
                mStrideY = roundUpTo16(sizeX);
                mStrideU = roundUpTo16(mStrideY >> 1u);
                mStrideV = mStrideU;
                mInY = reinterpret_cast<const uchar*>(input);
                mInU = reinterpret_cast<const uchar*>(input + mStrideY * sizeY);
                mInV = mInU + mStrideV * sizeY / 2;
                break;
        }
    }
};

void YuvToRgbTask::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                               size_t endY) {
    for (size_t y = startY; y < endY; y++) {
        size_t offset = mSizeX * y + startX;
        uchar4* out = mOut + offset;
        kernel(out, startX, endX, y);
    }
}

static uchar4 rsYuvToRGBA_uchar4(uchar y, uchar u, uchar v) {
    int16_t Y = ((int16_t)y) - 16;
    int16_t U = ((int16_t)u) - 128;
    int16_t V = ((int16_t)v) - 128;

    short4 p;
    p.x = (Y * 298 + V * 409 + 128) >> 8;
    p.y = (Y * 298 - U * 100 - V * 208 + 128) >> 8;
    p.z = (Y * 298 + U * 516 + 128) >> 8;
    p.w = 255;
    if(p.x < 0) {
        p.x = 0;
    }
    if(p.x > 255) {
        p.x = 255;
    }
    if(p.y < 0) {
        p.y = 0;
    }
    if(p.y > 255) {
        p.y = 255;
    }
    if(p.z < 0) {
        p.z = 0;
    }
    if(p.z > 255) {
        p.z = 255;
    }

    return (uchar4){static_cast<uchar>(p.x), static_cast<uchar>(p.y),
                    static_cast<uchar>(p.z), static_cast<uchar>(p.w)};
}

extern "C" void rsdIntrinsicYuv_K(void *dst, const uchar *Y, const uchar *uv, uint32_t xstart,
                                  size_t xend);
extern "C" void rsdIntrinsicYuvR_K(void *dst, const uchar *Y, const uchar *uv, uint32_t xstart,
                                   size_t xend);
extern "C" void rsdIntrinsicYuv2_K(void *dst, const uchar *Y, const uchar *u, const uchar *v,
                                   size_t xstart, size_t xend);

void YuvToRgbTask::kernel(uchar4 *out, uint32_t xstart, uint32_t xend, uint32_t currentY) {
    //ALOGI("kernel out %p, xstart=%u, xend=%u, currentY=%u", out, xstart, xend, currentY);

    const uchar *y = mInY + (currentY * mStrideY);
    const uchar *v = mInV + ((currentY >> 1) * mStrideV);
    const uchar *u = mInU + ((currentY >> 1) * mStrideU);

    //ALOGI("pinY %p, pinV %p, pinU %p", pinY, pinV, pinU);

    uint32_t x1 = xstart;
    uint32_t x2 = xend;

    /*
    ALOGE("pinY, %p, Y, %p, currentY, %d, strideY, %zu", pinY, y, currentY, mStrideY);
    ALOGE("pinU, %p, U, %p, currentY, %d, strideU, %zu", pinU, u, currentY, mStrideU);
    ALOGE("pinV, %p, V, %p, currentY, %d, strideV, %zu", pinV, v, currentY, mStrideV);
    ALOGE("dimX, %d, dimY, %d", cp->alloc->mHal.drvState.lod[0].dimX,
          cp->alloc->mHal.drvState.lod[0].dimY);
    ALOGE("info->dim.x, %d, info->dim.y, %d", info->dim.x, info->dim.y);
    uchar* pinY = (uchar*)mInY;
    uchar* pinU = (uchar*)mInU;
    uchar* pinV = (uchar*)mInV;
    ALOGE("Y %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinY, pinY[0], pinY[1], pinY[2], pinY[3], pinY[4], pinY[5], pinY[6], pinY[7], pinY[8],
          pinY[9], pinY[10], pinY[11], pinY[12], pinY[13], pinY[14], pinY[15]);
    ALOGE("Y %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinY, pinY[16], pinY[17], pinY[18], pinY[19], pinY[20], pinY[21], pinY[22], pinY[23],
          pinY[24], pinY[25], pinY[26], pinY[27], pinY[28], pinY[29], pinY[30], pinY[31]);
    ALOGE("Y %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinY, pinY[32], pinY[33], pinY[34], pinY[35], pinY[36], pinY[37], pinY[38], pinY[39],
          pinY[40], pinY[41], pinY[42], pinY[43], pinY[44], pinY[45], pinY[46], pinY[47]);

    ALOGE("U %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinU, pinU[0], pinU[1], pinU[2], pinU[3], pinU[4], pinU[5], pinU[6], pinU[7], pinU[8],
          pinU[9], pinU[10], pinU[11], pinU[12], pinU[13], pinU[14], pinU[15]);
    ALOGE("U %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinU, pinU[16], pinU[17], pinU[18], pinU[19], pinU[20], pinU[21], pinU[22], pinU[23],
          pinU[24], pinU[25], pinU[26], pinU[27], pinU[28], pinU[29], pinU[30], pinU[31]);
    ALOGE("U %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinU, pinU[32], pinU[33], pinU[34], pinU[35], pinU[36], pinU[37], pinU[38], pinU[39],
          pinU[40], pinU[41], pinU[42], pinU[43], pinU[44], pinU[45], pinU[46], pinU[47]);

    ALOGE("V %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinV, pinV[0], pinV[1], pinV[2], pinV[3], pinV[4], pinV[5], pinV[6], pinV[7], pinV[8],
          pinV[9], pinV[10], pinV[11], pinV[12], pinV[13], pinV[14], pinV[15]);
    ALOGE("V %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinV, pinV[16], pinV[17], pinV[18], pinV[19], pinV[20], pinV[21], pinV[22], pinV[23],
          pinV[24], pinV[25], pinV[26], pinV[27], pinV[28], pinV[29], pinV[30], pinV[31]);
    ALOGE("V %p %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx "
          "%02hhx %02hhx %02hhx %02hhx %02hhx %02hhx %02hhx",
          pinV, pinV[32], pinV[33], pinV[34], pinV[35], pinV[36], pinV[37], pinV[38], pinV[39],
          pinV[40], pinV[41], pinV[42], pinV[43], pinV[44], pinV[45], pinV[46], pinV[47]);
    */

    /* If we start on an odd pixel then deal with it here and bump things along
     * so that subsequent code can carry on with even-odd pairing assumptions.
     */
    if((x1 & 1) && (x2 > x1)) {
        int cx = (x1 >> 1) * mCstep;
        *out = rsYuvToRGBA_uchar4(y[x1], u[cx], v[cx]);
        out++;
        x1++;
    }

#if defined(ARCH_ARM_USE_INTRINSICS)
    if((x2 > x1) && mUsesSimd) {
        int32_t len = x2 - x1;
        if (mCstep == 1) {
            rsdIntrinsicYuv2_K(out, y, u, v, x1, x2);
            x1 += len;
            out += len;
        } else if (mCstep == 2) {
            // Check for proper interleave
            intptr_t ipu = (intptr_t)u;
            intptr_t ipv = (intptr_t)v;

            if (ipu == (ipv + 1)) {
                rsdIntrinsicYuv_K(out, y, v, x1, x2);
                x1 += len;
                out += len;
            } else if (ipu == (ipv - 1)) {
                rsdIntrinsicYuvR_K(out, y, u, x1, x2);
                x1 += len;
                out += len;
            }
        }
    }
#endif

    if(x2 > x1) {
       // ALOGE("y %i  %i  %i", currentY, x1, x2);
        while(x1 < x2) {
            int cx = (x1 >> 1) * mCstep;
            *out = rsYuvToRGBA_uchar4(y[x1], u[cx], v[cx]);
            out++;
            x1++;
            *out = rsYuvToRGBA_uchar4(y[x1], u[cx], v[cx]);
            out++;
            x1++;
        }
    }
}

void RenderScriptToolkit::yuvToRgb(const uint8_t* input, uint8_t* output, size_t sizeX,
                                   size_t sizeY, YuvFormat format) {
    YuvToRgbTask task(input, output, sizeX, sizeY, format);
    processor->doTask(&task);
}

}  // namespace renderscript
