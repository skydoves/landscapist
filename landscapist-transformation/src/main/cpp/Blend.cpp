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

#include <cassert>
#include <cstdint>

#include "RenderScriptToolkit.h"
#include "TaskProcessor.h"
#include "Utils.h"

namespace renderscript {

#define LOG_TAG "renderscript.toolkit.Blend"

/**
 * Blends a source into a destination, based on the mode.
 */
class BlendTask : public Task {
    // The type of blending to do.
    RenderScriptToolkit::BlendingMode mMode;
    // The input we're blending.
    const uchar4* mIn;
    // The destination, used both for input and output.
    uchar4* mOut;

    void blend(RenderScriptToolkit::BlendingMode mode, const uchar4* in, uchar4* out,
               uint32_t length);
    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    BlendTask(RenderScriptToolkit::BlendingMode mode, const uint8_t* in, uint8_t* out, size_t sizeX,
              size_t sizeY, const Restriction* restriction)
        : Task{sizeX, sizeY, 4, true, restriction},
          mMode{mode},
          mIn{reinterpret_cast<const uchar4*>(in)},
          mOut{reinterpret_cast<uchar4*>(out)} {}
};

#if defined(ARCH_ARM_USE_INTRINSICS)
extern "C" int rsdIntrinsicBlend_K(uchar4 *out, uchar4 const *in, int slot,
                    uint32_t xstart, uint32_t xend);
#endif

#if defined(ARCH_X86_HAVE_SSSE3)
extern void rsdIntrinsicBlendSrcOver_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendDstOver_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendSrcIn_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendDstIn_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendSrcOut_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendDstOut_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendSrcAtop_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendDstAtop_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendXor_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendMultiply_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendAdd_K(void *dst, const void *src, uint32_t count8);
extern void rsdIntrinsicBlendSub_K(void *dst, const void *src, uint32_t count8);
#endif

// Convert vector to uchar4, clipping each value to 255.
template <typename TI>
static inline uchar4 convertClipped(TI amount) {
    return uchar4 { static_cast<uchar>(amount.x > 255 ? 255 : amount.x),
                    static_cast<uchar>(amount.y > 255 ? 255 : amount.y),
                    static_cast<uchar>(amount.z > 255 ? 255 : amount.z),
                    static_cast<uchar>(amount.w > 255 ? 255 : amount.w)};
}

void BlendTask::blend(RenderScriptToolkit::BlendingMode mode, const uchar4* in, uchar4* out,
                      uint32_t length) {
    uint32_t x1 = 0;
    uint32_t x2 = length;

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd) {
        if (rsdIntrinsicBlend_K(out, in, (int) mode, x1, x2) >= 0) {
            return;
        } else {
            ALOGW("Intrinsic Blend failed to use SIMD for %d", mode);
        }
    }
#endif
    switch (mode) {
    case RenderScriptToolkit::BlendingMode::CLEAR:
        for (;x1 < x2; x1++, out++) {
            *out = 0;
        }
        break;
    case RenderScriptToolkit::BlendingMode::SRC:
        for (;x1 < x2; x1++, out++, in++) {
          *out = *in;
        }
        break;
    //RenderScriptToolkit::BlendingMode::DST is a NOP
    case RenderScriptToolkit::BlendingMode::DST:
        break;
    case RenderScriptToolkit::BlendingMode::SRC_OVER:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendSrcOver_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            ushort4 in_s = convert<ushort4>(*in);
            ushort4 out_s = convert<ushort4>(*out);
            in_s = in_s + ((out_s * (ushort4)(255 - in_s.w)) >> (ushort4)8);
            *out = convertClipped(in_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::DST_OVER:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendDstOver_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
     #endif
        for (;x1 < x2; x1++, out++, in++) {
            ushort4 in_s = convert<ushort4>(*in);
            ushort4 out_s = convert<ushort4>(*out);
            in_s = out_s + ((in_s * (ushort4)(255 - out_s.w)) >> (ushort4)8);
            *out = convertClipped(in_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::SRC_IN:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendSrcIn_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
#endif
        for (;x1 < x2; x1++, out++, in++) {
            ushort4 in_s = convert<ushort4>(*in);
            in_s = (in_s * out->w) >> (ushort4)8;
            *out = convert<uchar4>(in_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::DST_IN:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendDstIn_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
     #endif
        for (;x1 < x2; x1++, out++, in++) {
            ushort4 out_s = convert<ushort4>(*out);
            out_s = (out_s * in->w) >> (ushort4)8;
            *out = convert<uchar4>(out_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::SRC_OUT:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendSrcOut_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            ushort4 in_s = convert<ushort4>(*in);
            in_s = (in_s * (ushort4)(255 - out->w)) >> (ushort4)8;
            *out = convert<uchar4>(in_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::DST_OUT:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendDstOut_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            ushort4 out_s = convert<ushort4>(*out);
            out_s = (out_s * (ushort4)(255 - in->w)) >> (ushort4)8;
            *out = convert<uchar4>(out_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::SRC_ATOP:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendSrcAtop_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            // The max value the operation could produce before the shift
            // is 255 * 255 + 255 * (255 - 0) = 130050, or 0x1FC02.
            // That value does not fit in a ushort, so we use uint.
            uint4 in_s = convert<uint4>(*in);
            uint4 out_s = convert<uint4>(*out);
            out_s.xyz = ((in_s.xyz * out_s.w) +
              (out_s.xyz * ((uint3)255 - (uint3)in_s.w))) >> (uint3)8;
            *out = convertClipped(out_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::DST_ATOP:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendDstAtop_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
     #endif
        for (;x1 < x2; x1++, out++, in++) {
            uint4 in_s = convert<uint4>(*in);
            uint4 out_s = convert<uint4>(*out);
            out_s.xyz = ((out_s.xyz * in_s.w) +
              (in_s.xyz * ((uint3)255 - (uint3)out_s.w))) >> (uint3)8;
            out_s.w = in_s.w;
            *out = convertClipped(out_s);
        }
        break;
    case RenderScriptToolkit::BlendingMode::XOR:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendXor_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            *out = *in ^ *out;
        }
        break;
    case RenderScriptToolkit::BlendingMode::MULTIPLY:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if ((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendMultiply_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
          *out = convert<uchar4>((convert<ushort4>(*in) * convert<ushort4>(*out))
                                >> (ushort4)8);
        }
        break;
    case RenderScriptToolkit::BlendingMode::ADD:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendAdd_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            uint32_t iR = in->x, iG = in->y, iB = in->z, iA = in->w,
                oR = out->x, oG = out->y, oB = out->z, oA = out->w;
            out->x = (oR + iR) > 255 ? 255 : oR + iR;
            out->y = (oG + iG) > 255 ? 255 : oG + iG;
            out->z = (oB + iB) > 255 ? 255 : oB + iB;
            out->w = (oA + iA) > 255 ? 255 : oA + iA;
        }
        break;
    case RenderScriptToolkit::BlendingMode::SUBTRACT:
    #if defined(ARCH_X86_HAVE_SSSE3)
        if (mUsesSimd) {
            if((x1 + 8) < x2) {
                uint32_t len = (x2 - x1) >> 3;
                rsdIntrinsicBlendSub_K(out, in, len);
                x1 += len << 3;
                out += len << 3;
                in += len << 3;
            }
        }
    #endif
        for (;x1 < x2; x1++, out++, in++) {
            int32_t iR = in->x, iG = in->y, iB = in->z, iA = in->w,
                oR = out->x, oG = out->y, oB = out->z, oA = out->w;
            out->x = (oR - iR) < 0 ? 0 : oR - iR;
            out->y = (oG - iG) < 0 ? 0 : oG - iG;
            out->z = (oB - iB) < 0 ? 0 : oB - iB;
            out->w = (oA - iA) < 0 ? 0 : oA - iA;
        }
        break;

    default:
        ALOGE("Called unimplemented value %d", mode);
        assert(false);
    }
}

void BlendTask::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                            size_t endY) {
    for (size_t y = startY; y < endY; y++) {
        size_t offset = y * mSizeX + startX;
        blend(mMode, mIn + offset, mOut + offset, endX - startX);
    }
}

void RenderScriptToolkit::blend(BlendingMode mode, const uint8_t* in, uint8_t* out, size_t sizeX,
                                size_t sizeY, const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
#endif

    BlendTask task(mode, in, out, sizeX, sizeY, restriction);
    processor->doTask(&task);
}

}  // namespace google::android::renderscript
