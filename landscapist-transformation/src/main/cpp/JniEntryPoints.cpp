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

#include <android/bitmap.h>
#include <cassert>
#include <jni.h>

#include "RenderScriptToolkit.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.JniEntryPoints"

using namespace renderscript;

/**
 * I compared using env->GetPrimitiveArrayCritical vs. env->GetByteArrayElements to get access
 * to the underlying data. On Pixel 4, it's actually faster to not use critical. The code is left
 * here if you want to experiment. Note that USE_CRITICAL could block the garbage collector.
 */
// #define USE_CRITICAL

class ByteArrayGuard {
   private:
    JNIEnv* env;
    jbyteArray array;
    jbyte* data;

   public:
    ByteArrayGuard(JNIEnv* env, jbyteArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jbyte*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetByteArrayElements(array, nullptr);
#endif
    }
    ~ByteArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseByteArrayElements(array, data, 0);
#endif
    }
    uint8_t* get() { return reinterpret_cast<uint8_t*>(data); }
};

class IntArrayGuard {
   private:
    JNIEnv* env;
    jintArray array;
    jint* data;

   public:
    IntArrayGuard(JNIEnv* env, jintArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jint*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetIntArrayElements(array, nullptr);
#endif
    }
    ~IntArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseIntArrayElements(array, data, 0);
#endif
    }
    int* get() { return reinterpret_cast<int*>(data); }
};

class FloatArrayGuard {
   private:
    JNIEnv* env;
    jfloatArray array;
    jfloat* data;

   public:
    FloatArrayGuard(JNIEnv* env, jfloatArray array) : env{env}, array{array} {
#ifdef USE_CRITICAL
        data = reinterpret_cast<jfloat*>(env->GetPrimitiveArrayCritical(array, nullptr));
#else
        data = env->GetFloatArrayElements(array, nullptr);
#endif
    }
    ~FloatArrayGuard() {
#ifdef USE_CRITICAL
        env->ReleasePrimitiveArrayCritical(array, data, 0);
#else
        env->ReleaseFloatArrayElements(array, data, 0);
#endif
    }
    float* get() { return reinterpret_cast<float*>(data); }
};

class BitmapGuard {
   private:
    JNIEnv* env;
    jobject bitmap;
    AndroidBitmapInfo info;
    int bytesPerPixel;
    void* bytes;
    bool valid;

   public:
    BitmapGuard(JNIEnv* env, jobject jBitmap) : env{env}, bitmap{jBitmap}, bytes{nullptr} {
        valid = false;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
            ALOGE("AndroidBitmap_getInfo failed");
            return;
        }
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 &&
            info.format != ANDROID_BITMAP_FORMAT_A_8) {
            ALOGE("AndroidBitmap in the wrong format");
            return;
        }
        bytesPerPixel = info.stride / info.width;
        if (bytesPerPixel != 1 && bytesPerPixel != 4) {
            ALOGE("Expected a vector size of 1 or 4. Got %d. Extra padding per line not currently "
                  "supported",
                  bytesPerPixel);
            return;
        }
        if (AndroidBitmap_lockPixels(env, bitmap, &bytes) != ANDROID_BITMAP_RESULT_SUCCESS) {
            ALOGE("AndroidBitmap_lockPixels failed");
            return;
        }
        valid = true;
    }
    ~BitmapGuard() {
        if (valid) {
            AndroidBitmap_unlockPixels(env, bitmap);
        }
    }
    uint8_t* get() const {
        assert(valid);
        return reinterpret_cast<uint8_t*>(bytes);
    }
    int width() const { return info.width; }
    int height() const { return info.height; }
    int vectorSize() const { return bytesPerPixel; }
};

/**
 * Copies the content of Kotlin Range2d object into the equivalent C++ struct.
 */
class RestrictionParameter {
   private:
    bool isNull;
    Restriction restriction;

   public:
    RestrictionParameter(JNIEnv* env, jobject jRestriction) : isNull{jRestriction == nullptr} {
        if (isNull) {
            return;
        }
        /* TODO Measure how long FindClass and related functions take. Consider passing the
         * four values instead. This would also require setting the default when Range2D is null.
         */
        jclass restrictionClass = env->FindClass("com/skydoves/landscapist/blur/Range2d");
        if (restrictionClass == nullptr) {
            ALOGE("RenderScriptToolit. Internal error. Could not find the Kotlin Range2d class.");
            isNull = true;
            return;
        }
        jfieldID startXId = env->GetFieldID(restrictionClass, "startX", "I");
        jfieldID startYId = env->GetFieldID(restrictionClass, "startY", "I");
        jfieldID endXId = env->GetFieldID(restrictionClass, "endX", "I");
        jfieldID endYId = env->GetFieldID(restrictionClass, "endY", "I");
        restriction.startX = env->GetIntField(jRestriction, startXId);
        restriction.startY = env->GetIntField(jRestriction, startYId);
        restriction.endX = env->GetIntField(jRestriction, endXId);
        restriction.endY = env->GetIntField(jRestriction, endYId);
    }
    Restriction* get() { return isNull ? nullptr : &restriction; }
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_createNative(JNIEnv* /*env*/, jobject /*thiz*/) {
    return reinterpret_cast<jlong>(new RenderScriptToolkit());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_destroyNative(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong native_handle) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    delete toolkit;
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeBlend(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jint jmode, jbyteArray source_array,
        jbyteArray dest_array, jint size_x, jint size_y, jobject restriction) {
    auto toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    auto mode = static_cast<RenderScriptToolkit::BlendingMode>(jmode);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard source{env, source_array};
    ByteArrayGuard dest{env, dest_array};

    toolkit->blend(mode, source.get(), dest.get(), size_x, size_y, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeBlendBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jint jmode, jobject source_bitmap,
        jobject dest_bitmap, jobject restriction) {
    auto toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    auto mode = static_cast<RenderScriptToolkit::BlendingMode>(jmode);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard source{env, source_bitmap};
    BitmapGuard dest{env, dest_bitmap};

    toolkit->blend(mode, source.get(), dest.get(), source.width(), source.height(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeBlur(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array, jint vectorSize,
        jint size_x, jint size_y, jint radius, jbyteArray output_array, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};

    toolkit->blur(input.get(), output.get(), size_x, size_y, vectorSize, radius, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeBlurBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jint radius, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};

    toolkit->blur(input.get(), output.get(), input.width(), input.height(), input.vectorSize(),
                  radius, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeColorMatrix(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jint input_vector_size, jint size_x, jint size_y, jbyteArray output_array,
        jint output_vector_size, jfloatArray jmatrix, jfloatArray add_vector, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};
    FloatArrayGuard matrix{env, jmatrix};
    FloatArrayGuard add{env, add_vector};

    toolkit->colorMatrix(input.get(), output.get(), input_vector_size, output_vector_size, size_x,
                         size_y, matrix.get(), add.get(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeColorMatrixBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jfloatArray jmatrix, jfloatArray add_vector, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};
    FloatArrayGuard matrix{env, jmatrix};
    FloatArrayGuard add{env, add_vector};

    toolkit->colorMatrix(input.get(), output.get(), input.vectorSize(), output.vectorSize(),
                         input.width(), input.height(), matrix.get(), add.get(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeConvolve(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array, jint vectorSize,
        jint size_x, jint size_y, jbyteArray output_array, jfloatArray coefficients,
        jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};
    FloatArrayGuard coeffs{env, coefficients};

    switch (env->GetArrayLength(coefficients)) {
        case 9:
            toolkit->convolve3x3(input.get(), output.get(), vectorSize, size_x, size_y,
                                 coeffs.get(), restrict.get());
            break;
        case 25:
            toolkit->convolve5x5(input.get(), output.get(), vectorSize, size_x, size_y,
                                 coeffs.get(), restrict.get());
            break;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeConvolveBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jfloatArray coefficients, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};
    FloatArrayGuard coeffs{env, coefficients};

    switch (env->GetArrayLength(coefficients)) {
        case 9:
            toolkit->convolve3x3(input.get(), output.get(), input.vectorSize(), input.width(),
                                 input.height(), coeffs.get(), restrict.get());
            break;
        case 25:
            toolkit->convolve5x5(input.get(), output.get(), input.vectorSize(), input.width(),
                                 input.height(), coeffs.get(), restrict.get());
            break;
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeHistogram(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jint vector_size, jint size_x, jint size_y, jintArray output_array, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    IntArrayGuard output{env, output_array};

    toolkit->histogram(input.get(), output.get(), size_x, size_y, vector_size, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeHistogramBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jintArray output_array, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    IntArrayGuard output{env, output_array};

    toolkit->histogram(input.get(), output.get(), input.width(), input.height(), input.vectorSize(),
                       restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeHistogramDot(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jint vector_size, jint size_x, jint size_y, jintArray output_array,
        jfloatArray coefficients, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    IntArrayGuard output{env, output_array};
    FloatArrayGuard coeffs{env, coefficients};

    toolkit->histogramDot(input.get(), output.get(), size_x, size_y, vector_size, coeffs.get(),
                          restrict.get());
}

extern "C" JNIEXPORT
void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeHistogramDotBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jintArray output_array, jfloatArray coefficients, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    IntArrayGuard output{env, output_array};
    FloatArrayGuard coeffs{env, coefficients};

    toolkit->histogramDot(input.get(), output.get(), input.width(), input.height(),
                          input.vectorSize(), coeffs.get(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeLut(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jbyteArray output_array, jint size_x, jint size_y, jbyteArray red_table,
        jbyteArray green_table, jbyteArray blue_table, jbyteArray alpha_table,
        jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};

    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};
    ByteArrayGuard red{env, red_table};
    ByteArrayGuard green{env, green_table};
    ByteArrayGuard blue{env, blue_table};
    ByteArrayGuard alpha{env, alpha_table};

    toolkit->lut(input.get(), output.get(), size_x, size_y, red.get(), green.get(), blue.get(),
                 alpha.get(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeLutBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jbyteArray red_table, jbyteArray green_table, jbyteArray blue_table,
        jbyteArray alpha_table, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};

    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};
    ByteArrayGuard red{env, red_table};
    ByteArrayGuard green{env, green_table};
    ByteArrayGuard blue{env, blue_table};
    ByteArrayGuard alpha{env, alpha_table};

    toolkit->lut(input.get(), output.get(), input.width(), input.height(), red.get(), green.get(),
                 blue.get(), alpha.get(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeLut3d(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jbyteArray output_array, jint size_x, jint size_y, jbyteArray cube_values, jint cubeSizeX,
        jint cubeSizeY, jint cubeSizeZ, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};
    ByteArrayGuard cube{env, cube_values};

    toolkit->lut3d(input.get(), output.get(), size_x, size_y, cube.get(), cubeSizeX, cubeSizeY,
                   cubeSizeZ, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeLut3dBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jbyteArray cube_values, jint cubeSizeX, jint cubeSizeY,
        jint cubeSizeZ, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};
    ByteArrayGuard cube{env, cube_values};

    toolkit->lut3d(input.get(), output.get(), input.width(), input.height(), cube.get(), cubeSizeX,
                   cubeSizeY, cubeSizeZ, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeResize(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jint vector_size, jint input_size_x, jint input_size_y, jbyteArray output_array,
        jint output_size_x, jint output_size_y, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};

    toolkit->resize(input.get(), output.get(), input_size_x, input_size_y, vector_size,
                    output_size_x, output_size_y, restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeResizeBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jobject input_bitmap,
        jobject output_bitmap, jobject restriction) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    RestrictionParameter restrict {env, restriction};
    BitmapGuard input{env, input_bitmap};
    BitmapGuard output{env, output_bitmap};

    toolkit->resize(input.get(), output.get(), input.width(), input.height(), input.vectorSize(),
                    output.width(), output.height(), restrict.get());
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeYuvToRgb(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array,
        jbyteArray output_array, jint size_x, jint size_y, jint format) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    ByteArrayGuard input{env, input_array};
    ByteArrayGuard output{env, output_array};

    toolkit->yuvToRgb(input.get(), output.get(), size_x, size_y,
                      static_cast<RenderScriptToolkit::YuvFormat>(format));
}

extern "C" JNIEXPORT void JNICALL Java_com_skydoves_landscapist_transformation_RenderScriptToolkit_nativeYuvToRgbBitmap(
        JNIEnv* env, jobject /*thiz*/, jlong native_handle, jbyteArray input_array, jint size_x,
        jint size_y, jobject output_bitmap, jint format) {
    RenderScriptToolkit* toolkit = reinterpret_cast<RenderScriptToolkit*>(native_handle);
    BitmapGuard output{env, output_bitmap};
    ByteArrayGuard input{env, input_array};

    toolkit->yuvToRgb(input.get(), output.get(), size_x, size_y,
                      static_cast<RenderScriptToolkit::YuvFormat>(format));
}
