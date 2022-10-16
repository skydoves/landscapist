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
     * Determines how a source buffer is blended into a destination buffer.
     *
     * See {@link RenderScriptToolkit::blend}.
     *
     * blend only works on 4 byte RGBA data. In the descriptions below, ".a" represents
     * the alpha channel.
     */
    enum class BlendingMode {
        /**
         * dest = 0
         *
         * The destination is cleared, i.e. each pixel is set to (0, 0, 0, 0)
         */
        CLEAR = 0,
        /**
         * dest = src
         *
         * Sets each pixel of the destination to the corresponding one in the source.
         */
        SRC = 1,
        /**
         * dest = dest
         *
         * Leaves the destination untouched. This is a no-op.
         */
        DST = 2,
        /**
         * dest = src + dest * (1.0 - src.a)
         */
        SRC_OVER = 3,
        /**
         * dest = dest + src * (1.0 - dest.a)
         */
        DST_OVER = 4,
        /**
         * dest = src * dest.a
         */
        SRC_IN = 5,
        /**
         * dest = dest * src.a
         */
        DST_IN = 6,
        /**
         * dest = src * (1.0 - dest.a)
         */
        SRC_OUT = 7,
        /**
         * dest = dest * (1.0 - src.a)
         */
        DST_OUT = 8,
        /**
         * dest.rgb = src.rgb * dest.a + (1.0 - src.a) * dest.rgb, dest.a = dest.a
         */
        SRC_ATOP = 9,
        /**
         * dest = dest.rgb * src.a + (1.0 - dest.a) * src.rgb, dest.a = src.a
         */
        DST_ATOP = 10,
        /**
         * dest = {src.r ^ dest.r, src.g ^ dest.g, src.b ^ dest.b, src.a ^ dest.a}
         *
         * Note: this is NOT the Porter/Duff XOR mode; this is a bitwise xor.
         */
        XOR = 11,
        /**
         * dest = src * dest
         */
        MULTIPLY = 12,
        /**
         * dest = min(src + dest, 1.0)
         */
        ADD = 13,
        /**
         * dest = max(dest - src, 0.0)
         */
        SUBTRACT = 14
    };

    /**
     * Blend a source buffer with the destination buffer.
     *
     * Blends a source buffer and a destination buffer, placing the result in the destination
     * buffer. The blending is done pairwise between two corresponding RGBA values found in
     * each buffer. The mode parameter specifies one of fifteen blending operations.
     * See {@link BlendingMode}.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of each buffer. If provided, the range must be wholly contained with the dimensions
     * described by sizeX and sizeY.
     *
     * The source and destination buffers must have the same dimensions. Both buffers should be
     * large enough for sizeX * sizeY * 4 bytes. The buffers have a row-major layout.
     *
     * @param mode The specific blending operation to do.
     * @param source The RGBA input buffer.
     * @param dest The destination buffer. Used for input and output.
     * @param sizeX The width of both buffers, as a number of RGBA values.
     * @param sizeY The height of both buffers, as a number of RGBA values.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void blend(BlendingMode mode, const uint8_t* _Nonnull source, uint8_t* _Nonnull dst,
               size_t sizeX, size_t sizeY, const Restriction* _Nullable restriction = nullptr);

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
     * Identity matrix that can be passed to the {@link RenderScriptToolkit::colorMatrix} method.
     *
     * Using this matrix will result in no change to the pixel through multiplication although
     * the pixel value can still be modified by the add vector, or transformed to a different
     * format.
     */
    static constexpr float kIdentityMatrix[] =  {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };

    /**
     * Matrix to turn color pixels to a grey scale.
     *
     * Use this matrix with the {@link RenderScriptToolkit::colorMatrix} method to convert an
     * image from color to greyscale.
     */
    static constexpr float kGreyScaleColorMatrix[] = {
            0.299f, 0.299f, 0.299f, 0.0f,
            0.587f, 0.587f, 0.587f, 0.0f,
            0.114f, 0.114f, 0.114f, 0.0f,
            0.0f,   0.0f,   0.0f,   1.0f
    };

    /**
     * Matrix to convert RGB to YUV.
     *
     * Use this matrix with the {@link RenderScriptToolkit::colorMatrix} method to convert the
     * first three bytes of each pixel from RGB to YUV. This leaves the last byte (the alpha
     * channel) untouched.
     *
     * This is a simplistic conversion. Most YUV buffers have more complicated format, not supported
     * by this method.
     */
    static constexpr float kRgbToYuvMatrix[] = {
            0.299f, -0.14713f,  0.615f,   0.0f,
            0.587f, -0.28886f, -0.51499f, 0.0f,
            0.114f,  0.436f,   -0.10001f, 0.0f,
            0.0f,    0.0f,      0.0f,     1.0f
    };

    /**
     * Matrix to convert YUV to RGB.
     *
     * Use this matrix with the {@link RenderScriptToolkit::colorMatrix} method to convert the
     * first three bytes of each pixel from YUV to RGB. This leaves the last byte (the alpha
     * channel) untouched.
     *
     * This is a simplistic conversion. Most YUV buffers have more complicated format, not supported
     * by this method. Use {@link RenderScriptToolkit::yuvToRgb} to convert these buffers.
     */
    static constexpr float kYuvToRgbMatrix[] = {
            1.0f,      1.0f,     1.0f,     0.0f,
            0.0f,     -0.39465f, 2.03211f, 0.0f,
            1.13983f, -0.5806f,  0.0f,     0.0f,
            0.0f,      0.0f,     0.0f,     1.0f
    };

    /**
     * Transform an image using a color matrix.
     *
     * Converts a 2D array of vectors of unsigned bytes, multiplying each vectors by a 4x4 matrix
     * and adding an optional vector.
     *
     * Each input vector is composed of 1-4 unsigned bytes. If less than 4 bytes, it's extended to
     * 4, padding with zeroes. The unsigned bytes are converted from 0-255 to 0.0-1.0 floats
     * before the multiplication is done.
     *
     * The resulting value is normalized from 0.0-1.0 to a 0-255 value and stored in the output.
     * If the output vector size is less than four, the unused channels are discarded.
     *
     * If addVector is null, a vector of zeroes is added, i.e. a noop.
     *
     * Check kIdentityMatrix, kGreyScaleColorMatrix, kRgbToYuvMatrix, and kYuvToRgbMatrix for sample
     * matrices. The YUV conversion may not work for all color spaces.
     *
     * @param in The buffer of the image to be converted.
     * @param out The buffer that receives the converted image.
     * @param inputVectorSize The number of bytes in each input cell, a value from 1 to 4.
     * @param outputVectorSize The number of bytes in each output cell, a value from 1 to 4.
     * @param sizeX The width of both buffers, as a number of 1 to 4 byte cells.
     * @param sizeY The height of both buffers, as a number of 1 to 4 byte cells.
     * @param matrix The 4x4 matrix to multiply, in row major format.
     * @param addVector A vector of four floats that's added to the result of the multiplication.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void colorMatrix(const void* _Nonnull in, void* _Nonnull out, size_t inputVectorSize,
                     size_t outputVectorSize, size_t sizeX, size_t sizeY,
                     const float* _Nonnull matrix, const float* _Nullable addVector = nullptr,
                     const Restriction* _Nullable restriction = nullptr);

    /**
     * Convolve a ByteArray.
     *
     * Applies a 3x3 or 5x5 convolution to the input array using the provided coefficients.
     *
     * For 3x3 convolutions, 9 coefficients must be provided. For 5x5, 25 coefficients are needed.
     * The coefficients should be provided in row-major format.
     *
     * When the square extends past the edge, the edge values will be used as replacement for the
     * values that's are off boundary.
     *
     * Each input cell can either be represented by one to four bytes. Each byte is multiplied
     * and accumulated independently of the other bytes of the cell.
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
     * @param vectorSize The number of bytes in each cell, a value from 1 to 4.
     * @param sizeX The width of both buffers, as a number of 1 or 4 byte cells.
     * @param sizeY The height of both buffers, as a number of 1 or 4 byte cells.
     * @param coefficients 9 or 25 multipliers.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void convolve3x3(const void* _Nonnull in, void* _Nonnull out, size_t vectorSize, size_t sizeX,
                     size_t sizeY, const float* _Nonnull coefficients,
                     const Restriction* _Nullable restriction = nullptr);

    void convolve5x5(const void* _Nonnull in, void* _Nonnull out, size_t vectorSize, size_t sizeX,
                     size_t sizeY, const float* _Nonnull coefficients,
                     const Restriction* _Nullable restriction = nullptr);

    /**
     * Compute the histogram of an image.
     *
     * Tallies how many times each of the 256 possible values of a byte is found in the input.
     *
     * An input cell can be represented by one to four bytes. The tally is done independently
     * for each of the bytes of the cell. Correspondingly, the out array will have
     * 256 * vectorSize entries. The counts for value 0 are consecutive, followed by those for
     * value 1, etc.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of each buffer. If provided, the range must be wholly contained with the dimensions
     * described by sizeX and sizeY.
     *
     * The source buffers should be large enough for sizeX * sizeY * vectorSize bytes. The buffers
     * have a row-major layout. The out buffer should be large enough for 256 * vectorSize ints.
     *
     * @param in The buffer of the image to be analyzed.
     * @param out The resulting vector of counts.
     * @param sizeX The width of the input buffers, as a number of 1 or 4 byte cells.
     * @param sizeY The height of the input buffers, as a number of 1 or 4 byte cells.
     * @param vectorSize The number of bytes in each cell, a value from 1 to 4.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void histogram(const uint8_t* _Nonnull in, int32_t* _Nonnull out, size_t sizeX, size_t sizeY,
                   size_t vectorSize, const Restriction* _Nullable restriction = nullptr);

    /**
     * Compute the histogram of the dot product of an image.
     *
     * This method supports cells of 1 to 4 bytes in length. For each cell of the array,
     * the dot product of its bytes with the provided coefficients is computed. The resulting
     * floating point value is converted to an unsigned byte and tallied in the histogram.
     *
     * If coefficients is null, the coefficients used for RGBA luminosity calculation will be used,
     * i.e. the values [0.299f, 0.587f, 0.114f, 0.f].
     *
     * Each coefficients must be >= 0 and their sum must be 1.0 or less. There must be the same
     * number of coefficients as vectorSize.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of each buffer. If provided, the range must be wholly contained with the dimensions
     * described by sizeX and sizeY.
     *
     * The source buffers should be large enough for sizeX * sizeY * vectorSize bytes. The buffers
     * have a row-major layout. The out array should be large enough for 256 ints.
     *
     * @param in The buffer of the image to be analyzed.
     * @param out The resulting vector of counts.
     * @param sizeX The width of the input buffers, as a number of 1 or 4 byte cells.
     * @param sizeY The height of the input buffers, as a number of 1 or 4 byte cells.
     * @param vectorSize The number of bytes in each cell, a value from 1 to 4.
     * @param coefficients The values used for the dot product. Can be nullptr.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void histogramDot(const uint8_t* _Nonnull in, int32_t* _Nonnull out, size_t sizeX, size_t sizeY,
                      size_t vectorSize, const float* _Nullable coefficients,
                      const Restriction* _Nullable restriction = nullptr);

    /**
     * Transform an image using a look up table
     *
     * Transforms an image by using a per-channel lookup table. Each channel of the input has an
     * independent lookup table. The tables are 256 entries in size and can cover the full value
     * range of a byte.
     *
     * The input array should be in RGBA format, where four consecutive bytes form an cell.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of each buffer. If provided, the range must be wholly contained with the dimensions
     * described by sizeX and sizeY.
     *
     * The input and output buffers must have the same dimensions. Both buffers should be
     * large enough for sizeX * sizeY * vectorSize bytes. The buffers have a row-major layout.
     *
     * @param in The buffer of the image to be transformed.
     * @param out The buffer that receives the transformed image.
     * @param sizeX The width of both buffers, as a number of 4 byte cells.
     * @param sizeY The height of both buffers, as a number of 4 byte cells.
     * @param red An array of 256 values that's used to convert the R channel.
     * @param green An array of 256 values that's used to convert the G channel.
     * @param blue An array of 256 values that's used to convert the B channel.
     * @param alpha An array of 256 values that's used to convert the A channel.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void lut(const uint8_t* _Nonnull in, uint8_t* _Nonnull out, size_t sizeX, size_t sizeY,
             const uint8_t* _Nonnull red, const uint8_t* _Nonnull green,
             const uint8_t* _Nonnull blue, const uint8_t* _Nonnull alpha,
             const Restriction* _Nullable restriction = nullptr);

    /**
     * Transform an image using a 3D look up table
     *
     * Transforms an image, converting RGB to RGBA by using a 3D lookup table. The incoming R, G,
     * and B values are normalized to the dimensions of the provided 3D buffer. The eight nearest
     * values in that 3D buffer are sampled and linearly interpolated. The resulting RGBA entry
     * is stored in the output.
     *
     * The input array should be in RGBA format, where four consecutive bytes form an cell.
     * The fourth byte of each input cell is ignored.
     *
     * An optional range parameter can be set to restrict the operation to a rectangular subset
     * of each buffer. If provided, the range must be wholly contained with the dimensions
     * described by sizeX and sizeY.
     *
     * The input and output buffers must have the same dimensions. Both buffers should be
     * large enough for sizeX * sizeY * vectorSize bytes. The buffers have a row-major layout.
     *
     * @param in The buffer of the image to be transformed.
     * @param out The buffer that receives the transformed image.
     * @param sizeX The width of both buffers, as a number of 4 byte cells.
     * @param sizeY The height of both buffers, as a number of 4 byte cells.
     * @param cube The translation cube, in row major-format.
     * @param cubeSizeX The number of RGBA entries in the cube in the X direction.
     * @param cubeSizeY The number of RGBA entries in the cube in the Y direction.
     * @param cubeSizeZ The number of RGBA entries in the cube in the Z direction.
     * @param restriction When not null, restricts the operation to a 2D range of pixels.
     */
    void lut3d(const uint8_t* _Nonnull in, uint8_t* _Nonnull out, size_t sizeX, size_t sizeY,
               const uint8_t* _Nonnull cube, size_t cubeSizeX, size_t cubeSizeY, size_t cubeSizeZ,
               const Restriction* _Nullable restriction = nullptr);

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

    /**
     * The YUV formats supported by yuvToRgb.
     */
    enum class YuvFormat {
        NV21 = 0x11,
        YV12 = 0x32315659,
    };

    /**
     * Convert an image from YUV to RGB.
     *
     * Converts an Android YUV buffer to RGB. The input allocation should be
     * supplied in a supported YUV format as a YUV cell Allocation.
     * The output is RGBA; the alpha channel will be set to 255.
     *
     * Note that for YV12 and a sizeX that's not a multiple of 32, the
     * RenderScript Intrinsic may not have converted the image correctly.
     * This Toolkit method should.
     *
     * @param in The buffer of the image to be converted.
     * @param out The buffer that receives the converted image.
     * @param sizeX The width in pixels of the image. Must be even.
     * @param sizeY The height in pixels of the image.
     * @param format Either YV12 or NV21.
     */
    void yuvToRgb(const uint8_t* _Nonnull in, uint8_t* _Nonnull out, size_t sizeX, size_t sizeY,
                  YuvFormat format);
};

}  // namespace renderscript

#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_TOOLKIT_H
