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

#define LOG_TAG "renderscript.toolkit.Lut"

namespace renderscript {

class LutTask : public Task {
    const uchar4* mIn;
    uchar4* mOut;
    const uchar* mRedTable;
    const uchar* mGreenTable;
    const uchar* mBlueTable;
    const uchar* mAlphaTable;

    // Process a 2D tile of the overall work. threadIndex identifies which thread does the work.
    void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                     size_t endY) override;

   public:
    LutTask(const uint8_t* input, uint8_t* output, size_t sizeX, size_t sizeY, const uint8_t* red,
            const uint8_t* green, const uint8_t* blue, const uint8_t* alpha,
            const Restriction* restriction)
        : Task{sizeX, sizeY, 4, true, restriction},
          mIn{reinterpret_cast<const uchar4*>(input)},
          mOut{reinterpret_cast<uchar4*>(output)},
          mRedTable{red},
          mGreenTable{green},
          mBlueTable{blue},
          mAlphaTable{alpha} {}
};

void LutTask::processData(int /* threadIndex */, size_t startX, size_t startY, size_t endX,
                          size_t endY) {
    for (size_t y = startY; y < endY; y++) {
        size_t offset = mSizeX * y + startX;
        const uchar4* in = mIn + offset;
        uchar4* out = mOut + offset;
        for (size_t x = startX; x < endX; x++) {
            auto v = *in;
            *out = uchar4{mRedTable[v.x], mGreenTable[v.y], mBlueTable[v.z], mAlphaTable[v.w]};
            in++;
            out++;
        }
    }
}

void RenderScriptToolkit::lut(const uint8_t* input, uint8_t* output, size_t sizeX, size_t sizeY,
                              const uint8_t* red, const uint8_t* green, const uint8_t* blue,
                              const uint8_t* alpha, const Restriction* restriction) {
#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
    if (!validRestriction(LOG_TAG, sizeX, sizeY, restriction)) {
        return;
    }
#endif

    LutTask task(input, output, sizeX, sizeY, red, green, blue, alpha, restriction);
    processor->doTask(&task);
}

}  // namespace renderscript
