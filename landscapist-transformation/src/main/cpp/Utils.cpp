/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "Utils.h"

#include <cpu-features.h>

#include "RenderScriptToolkit.h"

namespace renderscript {

#define LOG_TAG "renderscript.toolkit.Utils"

bool cpuSupportsSimd() {
    AndroidCpuFamily family = android_getCpuFamily();
    uint64_t features = android_getCpuFeatures();

    if (family == ANDROID_CPU_FAMILY_ARM && (features & ANDROID_CPU_ARM_FEATURE_NEON)) {
        // ALOGI("Arm with Neon");
        return true;
    } else if (family == ANDROID_CPU_FAMILY_ARM64 && (features & ANDROID_CPU_ARM64_FEATURE_ASIMD)) {
        // ALOGI("Arm64 with ASIMD");
        return true;
    } else if ((family == ANDROID_CPU_FAMILY_X86 || family == ANDROID_CPU_FAMILY_X86_64) &&
               (features & ANDROID_CPU_X86_FEATURE_SSSE3)) {
        // ALOGI("x86* with SSE3");
        return true;
    }
    // ALOGI("Not simd");
    return false;
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
bool validRestriction(const char* tag, size_t sizeX, size_t sizeY, const Restriction* restriction) {
    if (restriction == nullptr) {
        return true;
    }
    if (restriction->startX >= sizeX || restriction->endX > sizeX) {
        ALOGE("%s. sizeX should be greater than restriction->startX and greater or equal to "
              "restriction->endX. %zu, %zu, and %zu were provided respectively.",
              tag, sizeX, restriction->startX, restriction->endY);
        return false;
    }
    if (restriction->startY >= sizeY && restriction->endY > sizeY) {
        ALOGE("%s. sizeY should be greater than restriction->startY and greater or equal to "
              "restriction->endY. %zu, %zu, and %zu were provided respectively.",
              tag, sizeY, restriction->startY, restriction->endY);
        return false;
    }
    if (restriction->startX >= restriction->endX) {
        ALOGE("%s. Restriction startX should be less than endX. "
              "%zu and %zu were provided respectively.",
              tag, restriction->startX, restriction->endX);
        return false;
    }
    if (restriction->startY >= restriction->endY) {
        ALOGE("%s. Restriction startY should be less than endY. "
              "%zu and %zu were provided respectively.",
              tag, restriction->startY, restriction->endY);
        return false;
    }
    return true;
}
#endif

}  // namespace renderscript
