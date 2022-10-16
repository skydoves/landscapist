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

#define LOG_TAG "renderscript.toolkit.Lut3d"

/**
 * Converts a RGBA buffer using a 3D cube.
 */
class Lut3dTask : public Task {
    // The input array we're transforming.
    const uchar4* mIn;
    // Where we'll store the transformed result.
    uchar4* mOut;
    // The size of each of the three cube dimensions. We don't make use of the last value.
    int4 mCubeDimension;
    // The translation cube, in row major format.
    const uchar* mCubeTable;

    /**
     * Converts a subset of a line of the 2D buffer.
     *
     * @param in The start of the data to transform.
     * @param out Where to store the result.
     * @param length The number of 4-byte vectors to transform.
     */
    void kernel(const uchar4* in, uchar4* out, uint32_t length);

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    Lut3dTask(const uint8_t* input, uint8_t* output, size_t sizeX, size_t sizeY,
              const uint8_t* cube, int cubeSizeX, int cubeSizeY, int cubeSizeZ,
              const Restriction* restriction)
        : Task{sizeX, sizeY, 4, true, restriction},
          mIn{reinterpret_cast<const uchar4*>(input)},
          mOut{reinterpret_cast<uchar4*>(output)},
          mCubeDimension{cubeSizeX, cubeSizeY, cubeSizeZ, 0},
          mCubeTable{cube} {}
};

extern "C" void rsdIntrinsic3DLUT_K(void* dst, void const* in, size_t count, void const* lut,
                                    int32_t pitchy, int32_t pitchz, int dimx, int dimy, int dimz);

void Lut3dTask::kernel(const uchar4* in, uchar4* out, uint32_t length) {
    uint32_t x1 = 0;
    uint32_t x2 = length;

    const uchar* bp = mCubeTable;

    int4 dims = mCubeDimension - 1;

    const float4 m = (float4)(1.f / 255.f) * convert<float4>(dims);
    const int4 coordMul = convert<int4>(m * (float4)0x8000);
    const size_t stride_y = mCubeDimension.x * 4;
    const size_t stride_z = stride_y * mCubeDimension.y;

    // ALOGE("strides %zu %zu", stride_y, stride_z);

#if defined(ARCH_ARM_USE_INTRINSICS)
    if (mUsesSimd) {
        int32_t len = x2 - x1;
        if (len > 0) {
            rsdIntrinsic3DLUT_K(out, in, len, bp, stride_y, stride_z, dims.x, dims.y, dims.z);
            x1 += len;
            out += len;
            in += len;
        }
    }
#endif

    while (x1 < x2) {
        int4 baseCoord = convert<int4>(*in) * coordMul;
        int4 coord1 = baseCoord >> (int4)15;
        // int4 coord2 = min(coord1 + 1, gDims - 1);

        int4 weight2 = baseCoord & 0x7fff;
        int4 weight1 = (int4)0x8000 - weight2;

        // ALOGE("coord1      %08x %08x %08x %08x", coord1.x, coord1.y, coord1.z, coord1.w);
        const uchar* bp2 = bp + (coord1.x * 4) + (coord1.y * stride_y) + (coord1.z * stride_z);
        const uchar4* pt_00 = (const uchar4*)&bp2[0];
        const uchar4* pt_10 = (const uchar4*)&bp2[stride_y];
        const uchar4* pt_01 = (const uchar4*)&bp2[stride_z];
        const uchar4* pt_11 = (const uchar4*)&bp2[stride_y + stride_z];

        uint4 v000 = convert<uint4>(pt_00[0]);
        uint4 v100 = convert<uint4>(pt_00[1]);
        uint4 v010 = convert<uint4>(pt_10[0]);
        uint4 v110 = convert<uint4>(pt_10[1]);
        uint4 v001 = convert<uint4>(pt_01[0]);
        uint4 v101 = convert<uint4>(pt_01[1]);
        uint4 v011 = convert<uint4>(pt_11[0]);
        uint4 v111 = convert<uint4>(pt_11[1]);

        uint4 yz00 = ((v000 * weight1.x) + (v100 * weight2.x)) >> (int4)7;
        uint4 yz10 = ((v010 * weight1.x) + (v110 * weight2.x)) >> (int4)7;
        uint4 yz01 = ((v001 * weight1.x) + (v101 * weight2.x)) >> (int4)7;
        uint4 yz11 = ((v011 * weight1.x) + (v111 * weight2.x)) >> (int4)7;

        uint4 z0 = ((yz00 * weight1.y) + (yz10 * weight2.y)) >> (int4)15;
        uint4 z1 = ((yz01 * weight1.y) + (yz11 * weight2.y)) >> (int4)15;

        uint4 v = ((z0 * weight1.z) + (z1 * weight2.z)) >> (int4)15;
        uint4 v2 = (v + 0x7f) >> (int4)8;

        uchar4 ret = convert<uchar4>(v2);
        ret.w = in->w;

#if 0
        if (!x1) {
            ALOGE("in          %08x %08x %08x %08x", in->r, in->g, in->b, in->a);
            ALOGE("baseCoord   %08x %08x %08x %08x", baseCoord.x, baseCoord.y, baseCoord.z,
                  baseCoord.w);
            ALOGE("coord1      %08x %08x %08x %08x", coord1.x, coord1.y, coord1.z, coord1.w);
            ALOGE("weight1     %08x %08x %08x %08x", weight1.x, weight1.y, weight1.z, weight1.w);
            ALOGE("weight2     %08x %08x %08x %08x", weight2.x, weight2.y, weight2.z, weight2.w);

            ALOGE("v000        %08x %08x %08x %08x", v000.x, v000.y, v000.z, v000.w);
            ALOGE("v100        %08x %08x %08x %08x", v100.x, v100.y, v100.z, v100.w);
            ALOGE("yz00        %08x %08x %08x %08x", yz00.x, yz00.y, yz00.z, yz00.w);
            ALOGE("z0          %08x %08x %08x %08x", z0.x, z0.y, z0.z, z0.w);

            ALOGE("v           %08x %08x %08x %08x", v.x, v.y, v.z, v.w);
            ALOGE("v2          %08x %08x %08x %08x", v2.x, v2.y, v2.z, v2.w);
        }
#endif
        *out = ret;

        in++;
        out++;
        x1++;
    }
}

void Lut3dTask::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                            size_t endY) {
    for (size_t y = startY; y < endY; y++) {
        size_t offset = mSizeX * y + startX;
        kernel(mIn + offset, mOut + offset, endX - startX);
    }
}

void RenderScriptToolkit::lut3d(const uint8_t* input, uint8_t* output, size_t sizeX, size_t sizeY,
                                const uint8_t* cube, size_t cubeSizeX, size_t cubeSizeY,
                                size_t cubeSizeZ, const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
#endif

    Lut3dTask task(input, output, sizeX, sizeY, cube, cubeSizeX, cubeSizeY, cubeSizeZ, restriction);
    processor->doTask(&task);
}

}  // namespace renderscript
