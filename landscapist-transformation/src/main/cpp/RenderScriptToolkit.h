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

#ifndef ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H
#define ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H

#include <cstdint>
#include <memory>

namespace renderscript {

class TaskProcessor;

/**
 * Define a range of data to process.
 *
 * This class is used to restrict a Toolkit operation to a rectangular subset of the input
 * tensor.
 *
 * @property startX The index of the first value to be included on the X axis.
 * @property endX The index after the last value to be included on the X axis.
 * @property startY The index of the first value to be included on the Y axis.
 * @property endY The index after the last value to be included on the Y axis.
 */
struct Restriction {
    size_t startX;
    size_t endX;
    size_t startY;
    size_t endY;
};

/**
 * A collection of high-performance graphic utility functions like blur and blend.
 *
 * This toolkit provides ten image manipulation functions: blend, blur, color matrix, convolve,
 * histogram, histogramDot, lut, lut3d, resize, and YUV to RGB. These functions execute
 * multithreaded on the CPU.
 *
 * These functions work over raw byte arrays. You'll need to specify the width and height of
 * the data to be processed, as well as the number of bytes per pixel. For most use cases,
 * this will be 4.
 *
 * You should instantiate the Toolkit once and reuse it throughout your application.
 * On instantiation, the Toolkit creates a thread pool that's used for processing all the functions.
 * You can limit the number of pool threads used by the Toolkit via the constructor. The pool
 * threads are destroyed once the Toolkit is destroyed, after any pending work is done.
 *
 * This library is thread safe. You can call methods from different pool threads. The functions will
 * execute sequentially.
 *
 * A Java/Kotlin Toolkit is available. It calls this library through JNI.
 *
 * This toolkit can be used as a replacement for most RenderScript Intrinsic functions. Compared
 * to RenderScript, it's simpler to use and more than twice as fast on the CPU. However RenderScript
 * Intrinsics allow more flexibility for the type of allocation supported. In particular, this
 * toolkit does not support allocations of floats.
 */
class RenderScriptToolkit {
    /** Each Toolkit method call is converted to a Task. The processor owns the thread pool. It
     * tiles the tasks and schedule them over the pool threads.
     */
    std::unique_ptr<TaskProcessor> processor;

   public:
    /**
     * Creates the pool threads that are used for processing the method calls.
     */
    RenderScriptToolkit(int numberOfThreads = 0);
    /**
     * Destroys the thread pool. This stops any in-progress work; the Toolkit methods called from
     * other pool threads will return without having completed the work. Because of the undefined
     * state of the output buffers, an application should avoid destroying the Toolkit if other pool
     * threads are executing Toolkit methods.
     */
    ~RenderScriptToolkit();

    /**
     * Blur an image.
     *
     * Performs a Gaussian blur of the input image and stores the result in the out buffer.
     *
     * The radius determines which pixels are used to compute each blurred pixels. This Toolkit
     * accepts values between 1 and 25. Larger values create a more blurred effect but also
     * take longer to compute. When the radius extends past the edge, the edge pixel will
     * be used as replacement for the pixel that's out off boundary.
     *
     * Each input pixel can either be represented by four bytes (RGBA format) or one byte
     * for the less common blurring of alpha channel only image.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of each buffer. If provided, the range must be wholly contained with the dimensions
     * described by sizeX and sizeY.
     *
     * The input and output buffers must have the same dimensions. Both buffers should be
     * large enough for sizeX * sizeY * vectorSize bytes. The buffers have a row-major layout.
     *
     * @param in The buffer of the image to be blurred.
     * @param out The buffer that receives the blurred image.
     * @param sizeX The width of both buffers, as a number of 1 or 4 byte cells.
     * @param sizeY The height of both buffers, as a number of 1 or 4 byte cells.
     * @param vectorSize Either 1 or 4, the number of bytes in each cell, i.e. A vs. RGBA.
     * @param radius The radius of the pixels used to blur.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void blur(const uint8_t* _Nonnull in, uint8_t* _Nonnull out, size_t sizeX, size_t sizeY,
              size_t vectorSize, int radius, const Restriction* _Nullable restriction = nullptr);

    /**
     * Resize an image.
     *
     * Resizes an image using bicubic interpolation.
     *
     * This method supports cells of 1 to 4 bytes in length. Each byte of the cell is
     * interpolated independently from the others.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of the output buffer. The corresponding scaled range of the input will be used.  If provided,
     * the range must be wholly contained with the dimensions described by outputSizeX and
     * outputSizeY.
     *
     * The input and output buffers have a row-major layout. Both buffers should be
     * large enough for sizeX * sizeY * vectorSize bytes.
     *
     * @param in The buffer of the image to be resized.
     * @param out The buffer that receives the resized image.
     * @param inputSizeX The width of the input buffer, as a number of 1-4 byte cells.
     * @param inputSizeY The height of the input buffer, as a number of 1-4 byte cells.
     * @param vectorSize The number of bytes in each cell of both buffers. A value from 1 to 4.
     * @param outputSizeX The width of the output buffer, as a number of 1-4 byte cells.
     * @param outputSizeY The height of the output buffer, as a number of 1-4 byte cells.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void resize(const uint8_t* _Nonnull in, uint8_t* _Nonnull out, size_t inputSizeX,
                size_t inputSizeY, size_t vectorSize, size_t outputSizeX, size_t outputSizeY,
                const Restriction* _Nullable restriction = nullptr);
};

}  // namespace renderscript

#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H
