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

#ifndef ANDROID_RENDERSCRIPT_TOOLKIT_UTILS_H
#define ANDROID_RENDERSCRIPT_TOOLKIT_UTILS_H

#include <android/log.h>
#include <stddef.h>

namespace renderscript {

/* The Toolkit does not support floating point buffers but the original RenderScript Intrinsics
 * did for some operations. That code was preserved and protected by
 * ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT.
 */
// TODO: On final packaging, decide whether this should be define in the build file, and for which
// config.
// #define ANDROID_RENDERSCRIPT_TOOLKIT_SUPPORTS_FLOAT

/* If we release the Toolkit as a C++ API, we'll want to enable validation at the C++ level
 * by uncommenting this define.
 *
 * If we only have a Java/Kotlin API, the Kotlin layer does validation. We don't need to duplicate
 * this effort.
 */
#define ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE

#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using uchar = unsigned char;
using uint = unsigned int;
using ushort = unsigned short;

using uint8_t = uchar;
using uint16_t = ushort;
using uint32_t = uint;

typedef float float2 __attribute__((ext_vector_type(2)));
typedef float float3 __attribute__((ext_vector_type(3)));
typedef float float4 __attribute__((ext_vector_type(4)));
typedef uchar uchar2 __attribute__((ext_vector_type(2)));
typedef uchar uchar3 __attribute__((ext_vector_type(3)));
typedef uchar uchar4 __attribute__((ext_vector_type(4)));
typedef ushort ushort2 __attribute__((ext_vector_type(2)));
typedef ushort ushort3 __attribute__((ext_vector_type(3)));
typedef ushort ushort4 __attribute__((ext_vector_type(4)));
typedef uint uint2 __attribute__((ext_vector_type(2)));
typedef uint uint3 __attribute__((ext_vector_type(3)));
typedef uint uint4 __attribute__((ext_vector_type(4)));
typedef short short2 __attribute__((ext_vector_type(2)));
typedef short short3 __attribute__((ext_vector_type(3)));
typedef short short4 __attribute__((ext_vector_type(4)));
typedef int int2 __attribute__((ext_vector_type(2)));
typedef int int3 __attribute__((ext_vector_type(3)));
typedef int int4 __attribute__((ext_vector_type(4)));

template <typename TO, typename TI>
inline TO convert(TI i) {
    // assert(i.x >= 0 && i.y >= 0 && i.z >= 0 && i.w >= 0);
    // assert(i.x <= 255 && i.y <= 255 && i.z <= 255 && i.w <= 255);
    return __builtin_convertvector(i, TO);
}

template <>
inline uchar convert(float i) {
    // assert(i.x >= 0 && i.y >= 0 && i.z >= 0 && i.w >= 0);
    // assert(i.x <= 255 && i.y <= 255 && i.z <= 255 && i.w <= 255);
    return (uchar)i;
}

template <>
inline float convert(uchar i) {
    // assert(i.x >= 0 && i.y >= 0 && i.z >= 0 && i.w >= 0);
    // assert(i.x <= 255 && i.y <= 255 && i.z <= 255 && i.w <= 255);
    return (float)i;
}

inline int4 clamp(int4 amount, int low, int high) {
    int4 r;
    r.x = amount.x < low ? low : (amount.x > high ? high : amount.x);
    r.y = amount.y < low ? low : (amount.y > high ? high : amount.y);
    r.z = amount.z < low ? low : (amount.z > high ? high : amount.z);
    r.w = amount.w < low ? low : (amount.w > high ? high : amount.w);
    return r;
}

inline float4 clamp(float4 amount, float low, float high) {
    float4 r;
    r.x = amount.x < low ? low : (amount.x > high ? high : amount.x);
    r.y = amount.y < low ? low : (amount.y > high ? high : amount.y);
    r.z = amount.z < low ? low : (amount.z > high ? high : amount.z);
    r.w = amount.w < low ? low : (amount.w > high ? high : amount.w);
    return r;
}

inline int2 clamp(int2 amount, int low, int high) {
    int2 r;
    r.x = amount.x < low ? low : (amount.x > high ? high : amount.x);
    r.y = amount.y < low ? low : (amount.y > high ? high : amount.y);
    return r;
}

inline float2 clamp(float2 amount, float low, float high) {
    float2 r;
    r.x = amount.x < low ? low : (amount.x > high ? high : amount.x);
    r.y = amount.y < low ? low : (amount.y > high ? high : amount.y);
    return r;
}

inline int clamp(int amount, int low, int high) {
    return amount < low ? low : (amount > high ? high : amount);
}

inline float clamp(float amount, float low, float high) {
    return amount < low ? low : (amount > high ? high : amount);
}

#ifdef ANDROID_RENDERSCRIPT_TOOLKIT_VALIDATE
struct Restriction;

bool validRestriction(const char* tag, size_t sizeX, size_t sizeY, const Restriction* restriction);
#endif

/**
 * Returns true if the processor we're running on supports the SIMD instructions that are
 * used in our assembly code.
 */
bool cpuSupportsSimd();

inline size_t divideRoundingUp(size_t a, size_t b) {
    return a / b + (a % b == 0 ? 0 : 1);
}

inline size_t paddedSize(size_t size) {
    return size == 3 ? 4 : size;
}

}  // namespace renderscript

#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_UTILS_H
