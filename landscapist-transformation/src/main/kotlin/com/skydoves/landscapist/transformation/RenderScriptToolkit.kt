/*
 * Designed and developed by 2020-2022 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package com.skydoves.landscapist.transformation

import android.graphics.Bitmap
import com.skydoves.landscapist.InternalLandscapistApi

// This string is used for error messages.
private const val externalName = "RenderScript Toolkit"

/**
 * A collection of high-performance graphic utility functions like blur and blend.
 *
 * This toolkit provides ten image manipulation functions: blend, blur, color matrix, convolve,
 * histogram, histogramDot, lut, lut3d, resize, and YUV to RGB. These functions execute
 * multithreaded on the CPU.
 *
 * Most of the functions have two variants: one that manipulates Bitmaps, the other ByteArrays.
 * For ByteArrays, you need to specify the width and height of the data to be processed, as
 * well as the number of bytes per pixel. For most use cases, this will be 4.
 *
 * The Toolkit creates a thread pool that's used for processing the functions. The threads live
 * for the duration of the application. They can be destroyed by calling the method shutdown().
 *
 * This library is thread safe. You can call methods from different poolThreads. The functions will
 * execute sequentially.
 *
 * A native C++ version of this Toolkit is available. Check the RenderScriptToolkit.h file in the
 * cpp directory.
 *
 * This toolkit can be used as a replacement for most RenderScript Intrinsic functions. Compared
 * to RenderScript, it's simpler to use and more than twice as fast on the CPU. However RenderScript
 * Intrinsics allow more flexibility for the type of allocation supported. In particular, this
 * toolkit does not support allocations of floats.
 */
@InternalLandscapistApi
internal object RenderScriptToolkit {
  /**
   * Blends a source buffer with the destination buffer.
   *
   * Blends a source buffer and a destination buffer, placing the result in the destination
   * buffer. The blending is done pairwise between two corresponding RGBA values found in
   * each buffer. The mode parameter specifies one of fifteen supported blending operations.
   * See {@link BlendingMode}.
   *
   * A variant of this method is also available to blend Bitmaps.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY.
   *
   * The source and destination buffer must have the same dimensions. Both arrays should have
   * a size greater or equal to sizeX * sizeY * 4. The buffers have a row-major layout.
   *
   * @param mode The specific blending operation to do.
   * @param sourceArray The RGBA input buffer.
   * @param destArray The destination buffer. Used for input and output.
   * @param sizeX The width of both buffers, as a number of RGBA values.
   * @param sizeY The height of both buffers, as a number of RGBA values.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   */
  @JvmOverloads
  internal fun blend(
    mode: BlendingMode,
    sourceArray: ByteArray,
    destArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d? = null
  ) {
    require(sourceArray.size >= sizeX * sizeY * 4) {
      "$externalName blend. sourceArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*4 < ${sourceArray.size}."
    }
    require(destArray.size >= sizeX * sizeY * 4) {
      "$externalName blend. sourceArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*4 < ${sourceArray.size}."
    }
    validateRestriction("blend", sizeX, sizeY, restriction)

    nativeBlend(nativeHandle, mode.value, sourceArray, destArray, sizeX, sizeY, restriction)
  }

  /**
   * Blends a source bitmap with the destination bitmap.
   *
   * Blends a source bitmap and a destination bitmap, placing the result in the destination
   * bitmap. The blending is done pairwise between two corresponding RGBA values found in
   * each bitmap. The mode parameter specify one of fifteen supported blending operations.
   * See {@link BlendingMode}.
   *
   * A variant of this method is available to blend ByteArrays.
   *
   * The bitmaps should have identical width and height, and have a config of ARGB_8888.
   * Bitmaps with a stride different than width * vectorSize are not currently supported.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each bitmap. If provided, the range must be wholly contained with the dimensions
   * of the bitmap.
   *
   * @param mode The specific blending operation to do.
   * @param sourceBitmap The RGBA input buffer.
   * @param destBitmap The destination buffer. Used for input and output.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   */
  @JvmOverloads
  internal fun blend(
    mode: BlendingMode,
    sourceBitmap: Bitmap,
    destBitmap: Bitmap,
    restriction: Range2d? = null
  ) {
    validateBitmap("blend", sourceBitmap)
    validateBitmap("blend", destBitmap)
    require(
      sourceBitmap.width == destBitmap.width &&
        sourceBitmap.height == destBitmap.height
    ) {
      "$externalName blend. Source and destination bitmaps should be the same size. " +
        "${sourceBitmap.width}x${sourceBitmap.height} and " +
        "${destBitmap.width}x${destBitmap.height} provided."
    }
    require(sourceBitmap.config == destBitmap.config) {
      "RenderScript Toolkit blend. Source and destination bitmaps should have the same " +
        "config. ${sourceBitmap.config} and ${destBitmap.config} provided."
    }
    validateRestriction("blend", sourceBitmap.width, sourceBitmap.height, restriction)

    nativeBlendBitmap(nativeHandle, mode.value, sourceBitmap, destBitmap, restriction)
  }

  /**
   * Blurs an image.
   *
   * Performs a Gaussian blur of an image and returns result in a ByteArray buffer. A variant of
   * this method is available to blur Bitmaps.
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
   * described by sizeX and sizeY. NOTE: The output buffer will still be full size, with the
   * section that's not blurred all set to 0. This is to stay compatible with RenderScript.
   *
   * The source buffer should be large enough for sizeX * sizeY * mVectorSize bytes. It has a
   * row-major layout.
   *
   * @param inputArray The buffer of the image to be blurred.
   * @param vectorSize Either 1 or 4, the number of bytes in each cell, i.e. A vs. RGBA.
   * @param sizeX The width of both buffers, as a number of 1 or 4 byte cells.
   * @param sizeY The height of both buffers, as a number of 1 or 4 byte cells.
   * @param radius The radius of the pixels used to blur, a value from 1 to 25.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The blurred pixels, a ByteArray of size.
   */
  @JvmOverloads
  internal fun blur(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    radius: Int = 5,
    restriction: Range2d? = null
  ): ByteArray {
    require(vectorSize == 1 || vectorSize == 4) {
      "$externalName blur. The vectorSize should be 1 or 4. $vectorSize provided."
    }
    require(inputArray.size >= sizeX * sizeY * vectorSize) {
      "$externalName blur. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*$vectorSize < ${inputArray.size}."
    }
    require(radius in 1..25) {
      "$externalName blur. The radius should be between 1 and 25. $radius provided."
    }
    validateRestriction("blur", sizeX, sizeY, restriction)

    val outputArray = ByteArray(inputArray.size)
    nativeBlur(
      nativeHandle,
      inputArray,
      vectorSize,
      sizeX,
      sizeY,
      radius,
      outputArray,
      restriction
    )
    return outputArray
  }

  /**
   * Blurs an image.
   *
   * Performs a Gaussian blur of a Bitmap and returns result as a Bitmap. A variant of
   * this method is available to blur ByteArrays.
   *
   * The radius determines which pixels are used to compute each blurred pixels. This Toolkit
   * accepts values between 1 and 25. Larger values create a more blurred effect but also
   * take longer to compute. When the radius extends past the edge, the edge pixel will
   * be used as replacement for the pixel that's out off boundary.
   *
   * This method supports input Bitmap of config ARGB_8888 and ALPHA_8. Bitmaps with a stride
   * different than width * vectorSize are not currently supported. The returned Bitmap has the
   * same config.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output Bitmap will still be full size, with the
   * section that's not blurred all set to 0. This is to stay compatible with RenderScript.
   *
   * @param inputBitmap The buffer of the image to be blurred.
   * @param radius The radius of the pixels used to blur, a value from 1 to 25. Default is 5.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The blurred Bitmap.
   */
  @JvmOverloads
  internal fun blur(inputBitmap: Bitmap, radius: Int = 5, restriction: Range2d? = null): Bitmap {
    validateBitmap("blur", inputBitmap)
    require(radius in 1..25) {
      "$externalName blur. The radius should be between 1 and 25. $radius provided."
    }
    validateRestriction("blur", inputBitmap.width, inputBitmap.height, restriction)

    val outputBitmap = createCompatibleBitmap(inputBitmap)
    nativeBlurBitmap(nativeHandle, inputBitmap, outputBitmap, radius, restriction)
    return outputBitmap
  }

  /**
   * Identity matrix that can be passed to the {@link RenderScriptToolkit::colorMatrix} method.
   *
   * Using this matrix will result in no change to the pixel through multiplication although
   * the pixel value can still be modified by the add vector, or transformed to a different
   * format.
   */
  internal val identityMatrix: FloatArray
    get() = floatArrayOf(
      1f, 0f, 0f, 0f,
      0f, 1f, 0f, 0f,
      0f, 0f, 1f, 0f,
      0f, 0f, 0f, 1f
    )

  /**
   * Matrix to turn color pixels to a grey scale.
   *
   * Use this matrix with the {@link RenderScriptToolkit::colorMatrix} method to convert an
   * image from color to greyscale.
   */
  internal val greyScaleColorMatrix: FloatArray
    get() = floatArrayOf(
      0.299f, 0.299f, 0.299f, 0f,
      0.587f, 0.587f, 0.587f, 0f,
      0.114f, 0.114f, 0.114f, 0f,
      0f, 0f, 0f, 1f
    )

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
  internal val rgbToYuvMatrix: FloatArray
    get() = floatArrayOf(
      0.299f, -0.14713f, 0.615f, 0f,
      0.587f, -0.28886f, -0.51499f, 0f,
      0.114f, 0.436f, -0.10001f, 0f,
      0f, 0f, 0f, 1f
    )

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
  internal val yuvToRgbMatrix: FloatArray
    get() = floatArrayOf(
      1f, 1f, 1f, 0f,
      0f, -0.39465f, 2.03211f, 0f,
      1.13983f, -0.5806f, 0f, 0f,
      0f, 0f, 0f, 1f
    )

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
   * If addVector is not specified, a vector of zeroes is added, i.e. a noop.
   *
   * Like the RenderScript Intrinsics, vectorSize of size 3 are padded to occupy 4 bytes.
   *
   * Check identityMatrix, greyScaleColorMatrix, rgbToYuvMatrix, and yuvToRgbMatrix for sample
   * matrices. The YUV conversion may not work for all color spaces.
   *
   * @param inputArray The buffer of the image to be converted.
   * @param inputVectorSize The number of bytes in each input cell, a value from 1 to 4.
   * @param sizeX The width of both buffers, as a number of 1 to 4 byte cells.
   * @param sizeY The height of both buffers, as a number of 1 to 4 byte cells.
   * @param outputVectorSize The number of bytes in each output cell, a value from 1 to 4.
   * @param matrix The 4x4 matrix to multiply, in row major format.
   * @param addVector A vector of four floats that's added to the result of the multiplication.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The converted buffer.
   */
  @JvmOverloads
  internal fun colorMatrix(
    inputArray: ByteArray,
    inputVectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    outputVectorSize: Int,
    matrix: FloatArray,
    addVector: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
    restriction: Range2d? = null
  ): ByteArray {
    require(inputVectorSize in 1..4) {
      "$externalName colorMatrix. The inputVectorSize should be between 1 and 4. " +
        "$inputVectorSize provided."
    }
    require(outputVectorSize in 1..4) {
      "$externalName colorMatrix. The outputVectorSize should be between 1 and 4. " +
        "$outputVectorSize provided."
    }
    require(inputArray.size >= sizeX * sizeY * inputVectorSize) {
      "$externalName colorMatrix. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*$inputVectorSize < ${inputArray.size}."
    }
    require(matrix.size == 16) {
      "$externalName colorMatrix. matrix should have 16 entries. ${matrix.size} provided."
    }
    require(addVector.size == 4) {
      "$externalName colorMatrix. addVector should have 4 entries. " +
        "${addVector.size} provided."
    }
    validateRestriction("colorMatrix", sizeX, sizeY, restriction)

    val outputArray = ByteArray(sizeX * sizeY * paddedSize(outputVectorSize))
    nativeColorMatrix(
      nativeHandle, inputArray, inputVectorSize, sizeX, sizeY, outputArray, outputVectorSize,
      matrix, addVector, restriction
    )
    return outputArray
  }

  /**
   * Transform an image using a color matrix.
   *
   * Converts a bitmap, multiplying each RGBA value by a 4x4 matrix and adding an optional vector.
   * Each byte of the RGBA is converted from 0-255 to 0.0-1.0 floats before the multiplication
   * is done.
   *
   * Bitmaps with a stride different than width * vectorSize are not currently supported.
   *
   * The resulting value is normalized from 0.0-1.0 to a 0-255 value and stored in the output.
   *
   * If addVector is not specified, a vector of zeroes is added, i.e. a noop.
   *
   * Check identityMatrix, greyScaleColorMatrix, rgbToYuvMatrix, and yuvToRgbMatrix for sample
   * matrices. The YUV conversion may not work for all color spaces.
   *
   * @param inputBitmap The image to be converted.
   * @param matrix The 4x4 matrix to multiply, in row major format.
   * @param addVector A vector of four floats that's added to the result of the multiplication.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The converted buffer.
   */
  @JvmOverloads
  internal fun colorMatrix(
    inputBitmap: Bitmap,
    matrix: FloatArray,
    addVector: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
    restriction: Range2d? = null
  ): Bitmap {
    validateBitmap("colorMatrix", inputBitmap)
    require(matrix.size == 16) {
      "$externalName colorMatrix. matrix should have 16 entries. ${matrix.size} provided."
    }
    require(addVector.size == 4) {
      "$externalName colorMatrix. addVector should have 4 entries."
    }
    validateRestriction("colorMatrix", inputBitmap.width, inputBitmap.height, restriction)

    val outputBitmap = createCompatibleBitmap(inputBitmap)
    nativeColorMatrixBitmap(
      nativeHandle,
      inputBitmap,
      outputBitmap,
      matrix,
      addVector,
      restriction
    )
    return outputBitmap
  }

  /**
   * Convolve a ByteArray.
   *
   * Applies a 3x3 or 5x5 convolution to the input array using the provided coefficients.
   * A variant of this method is available to convolve Bitmaps.
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
   * An optional range parameter can be set to restrict the convolve operation to a rectangular
   * subset of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output buffer will still be full size, with the
   * section that's not convolved all set to 0. This is to stay compatible with RenderScript.
   *
   * The source array should be large enough for sizeX * sizeY * vectorSize bytes. It has a
   * row-major layout. The output array will have the same dimensions.
   *
   * Like the RenderScript Intrinsics, vectorSize of size 3 are padded to occupy 4 bytes.
   *
   * @param inputArray The buffer of the image to be blurred.
   * @param vectorSize The number of bytes in each cell, a value from 1 to 4.
   * @param sizeX The width of both buffers, as a number of 1 or 4 byte cells.
   * @param sizeY The height of both buffers, as a number of 1 or 4 byte cells.
   * @param coefficients A FloatArray of size 9 or 25, containing the multipliers.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The convolved array.
   */
  @JvmOverloads
  internal fun convolve(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    coefficients: FloatArray,
    restriction: Range2d? = null
  ): ByteArray {
    require(vectorSize in 1..4) {
      "$externalName convolve. The vectorSize should be between 1 and 4. " +
        "$vectorSize provided."
    }
    require(inputArray.size >= sizeX * sizeY * vectorSize) {
      "$externalName convolve. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*$vectorSize < ${inputArray.size}."
    }
    require(coefficients.size == 9 || coefficients.size == 25) {
      "$externalName convolve. Only 3x3 or 5x5 convolutions are supported. " +
        "${coefficients.size} coefficients provided."
    }
    validateRestriction("convolve", sizeX, sizeY, restriction)

    val outputArray = ByteArray(inputArray.size)
    nativeConvolve(
      nativeHandle,
      inputArray,
      vectorSize,
      sizeX,
      sizeY,
      outputArray,
      coefficients,
      restriction
    )
    return outputArray
  }

  /**
   * Convolve a Bitmap.
   *
   * Applies a 3x3 or 5x5 convolution to the input Bitmap using the provided coefficients.
   * A variant of this method is available to convolve ByteArrays. Bitmaps with a stride different
   * than width * vectorSize are not currently supported.
   *
   * For 3x3 convolutions, 9 coefficients must be provided. For 5x5, 25 coefficients are needed.
   * The coefficients should be provided in row-major format.
   *
   * Each input cell can either be represented by one to four bytes. Each byte is multiplied
   * and accumulated independently of the other bytes of the cell.
   *
   * An optional range parameter can be set to restrict the convolve operation to a rectangular
   * subset of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output Bitmap will still be full size, with the
   * section that's not convolved all set to 0. This is to stay compatible with RenderScript.
   *
   * @param inputBitmap The image to be blurred.
   * @param coefficients A FloatArray of size 9 or 25, containing the multipliers.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The convolved Bitmap.
   */
  @JvmOverloads
  internal fun convolve(
    inputBitmap: Bitmap,
    coefficients: FloatArray,
    restriction: Range2d? = null
  ): Bitmap {
    validateBitmap("convolve", inputBitmap)
    require(coefficients.size == 9 || coefficients.size == 25) {
      "$externalName convolve. Only 3x3 or 5x5 convolutions are supported. " +
        "${coefficients.size} coefficients provided."
    }
    validateRestriction("convolve", inputBitmap, restriction)

    val outputBitmap = createCompatibleBitmap(inputBitmap)
    nativeConvolveBitmap(nativeHandle, inputBitmap, outputBitmap, coefficients, restriction)
    return outputBitmap
  }

  /**
   * Compute the histogram of an image.
   *
   * Tallies how many times each of the 256 possible values of a byte is found in the input.
   * A variant of this method is available to do the histogram of a Bitmap.
   *
   * An input cell can be represented by one to four bytes. The tally is done independently
   * for each of the bytes of the cell. Correspondingly, the returned IntArray will have
   * 256 * vectorSize entries. The counts for value 0 are consecutive, followed by those for
   * value 1, etc.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY.
   *
   * The source buffer should be large enough for sizeX * sizeY * vectorSize bytes. It has a
   * row-major layout.
   *
   * Like the RenderScript Intrinsics, vectorSize of size 3 are padded to occupy 4 bytes.
   *
   * @param inputArray The buffer of the image to be analyzed.
   * @param vectorSize The number of bytes in each cell, a value from 1 to 4.
   * @param sizeX The width of the input buffers, as a number of 1 to 4 byte cells.
   * @param sizeY The height of the input buffers, as a number of 1 to 4 byte cells.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The resulting array of counts.
   */
  @JvmOverloads
  internal fun histogram(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d? = null
  ): IntArray {
    require(vectorSize in 1..4) {
      "$externalName histogram. The vectorSize should be between 1 and 4. " +
        "$vectorSize provided."
    }
    require(inputArray.size >= sizeX * sizeY * vectorSize) {
      "$externalName histogram. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*$vectorSize < ${inputArray.size}."
    }
    validateRestriction("histogram", sizeX, sizeY, restriction)

    val outputArray = IntArray(256 * paddedSize(vectorSize))
    nativeHistogram(
      nativeHandle,
      inputArray,
      vectorSize,
      sizeX,
      sizeY,
      outputArray,
      restriction
    )
    return outputArray
  }

  /**
   * Compute the histogram of an image.
   *
   * Tallies how many times each of the 256 possible values of a byte is found in the bitmap.
   * This method supports Bitmaps of config ARGB_8888 and ALPHA_8.
   *
   * For ARGB_8888, the tally is done independently of the four bytes. Correspondingly, the
   * returned IntArray will have 4 * 256 entries. The counts for value 0 are consecutive,
   * followed by those for value 1, etc.
   *
   * For ALPHA_8, an IntArray of size 256 is returned.
   *
   * Bitmaps with a stride different than width * vectorSize are not currently supported.
   *
   * A variant of this method is available to do the histogram of a ByteArray.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY.
   *
   * @param inputBitmap The bitmap to be analyzed.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The resulting array of counts.
   */
  @JvmOverloads
  internal fun histogram(
    inputBitmap: Bitmap,
    restriction: Range2d? = null
  ): IntArray {
    validateBitmap("histogram", inputBitmap)
    validateRestriction("histogram", inputBitmap, restriction)

    val outputArray = IntArray(256 * vectorSize(inputBitmap))
    nativeHistogramBitmap(nativeHandle, inputBitmap, outputArray, restriction)
    return outputArray
  }

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
   * A variant of this method is available to do the histogram of a Bitmap.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY.
   *
   * The source buffer should be large enough for sizeX * sizeY * vectorSize bytes. The returned
   * array will have 256 ints.
   *
   * Like the RenderScript Intrinsics, vectorSize of size 3 are padded to occupy 4 bytes.
   *
   * @param inputArray The buffer of the image to be analyzed.
   * @param vectorSize The number of bytes in each cell, a value from 1 to 4.
   * @param sizeX The width of the input buffers, as a number of 1 to 4 byte cells.
   * @param sizeY The height of the input buffers, as a number of 1 to 4 byte cells.
   * @param coefficients The dot product multipliers. Size should equal vectorSize. Can be null.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The resulting vector of counts.
   */
  @JvmOverloads
  internal fun histogramDot(
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    coefficients: FloatArray? = null,
    restriction: Range2d? = null
  ): IntArray {
    require(vectorSize in 1..4) {
      "$externalName histogramDot. The vectorSize should be between 1 and 4. " +
        "$vectorSize provided."
    }
    require(inputArray.size >= sizeX * sizeY * vectorSize) {
      "$externalName histogramDot. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*$vectorSize < ${inputArray.size}."
    }
    validateHistogramDotCoefficients(coefficients, vectorSize)
    validateRestriction("histogramDot", sizeX, sizeY, restriction)

    val outputArray = IntArray(256)
    val actualCoefficients = coefficients ?: floatArrayOf(0.299f, 0.587f, 0.114f, 0f)
    nativeHistogramDot(
      nativeHandle,
      inputArray,
      vectorSize,
      sizeX,
      sizeY,
      outputArray,
      actualCoefficients,
      restriction
    )
    return outputArray
  }

  /**
   * Compute the histogram of the dot product of an image.
   *
   * This method supports Bitmaps of config ARGB_8888 and ALPHA_8. For each pixel of the bitmap,
   * the dot product of its bytes with the provided coefficients is computed. The resulting
   * floating point value is converted to an unsigned byte and tallied in the histogram.
   *
   * If coefficients is null, the coefficients used for RGBA luminosity calculation will be used,
   * i.e. the values [0.299f, 0.587f, 0.114f, 0.f].
   *
   * Each coefficients must be >= 0 and their sum must be 1.0 or less. For ARGB_8888, four values
   * must be provided; for ALPHA_8, one.
   *
   * Bitmaps with a stride different than width * vectorSize are not currently supported.
   *
   * A variant of this method is available to do the histogram of a ByteArray.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY.
   *
   * The returned array will have 256 ints.
   *
   * @param inputBitmap The bitmap to be analyzed.
   * @param coefficients The one or four values used for the dot product. Can be null.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The resulting vector of counts.
   */
  @JvmOverloads
  internal fun histogramDot(
    inputBitmap: Bitmap,
    coefficients: FloatArray? = null,
    restriction: Range2d? = null
  ): IntArray {
    validateBitmap("histogramDot", inputBitmap)
    validateHistogramDotCoefficients(coefficients, vectorSize(inputBitmap))
    validateRestriction("histogramDot", inputBitmap, restriction)

    val outputArray = IntArray(256)
    val actualCoefficients = coefficients ?: floatArrayOf(0.299f, 0.587f, 0.114f, 0f)
    nativeHistogramDotBitmap(
      nativeHandle,
      inputBitmap,
      outputArray,
      actualCoefficients,
      restriction
    )
    return outputArray
  }

  /**
   * Transform an image using a look up table
   *
   * Transforms an image by using a per-channel lookup table. Each channel of the input has an
   * independent lookup table. The tables are 256 entries in size and can cover the full value
   * range of a byte.
   *
   * The input array should be in RGBA format, where four consecutive bytes form an cell.
   * A variant of this method is available to transform a Bitmap.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output Bitmap will still be full size, with the
   * section that's not convolved all set to 0. This is to stay compatible with RenderScript.
   *
   * The source array should be large enough for sizeX * sizeY * vectorSize bytes. The returned
   * ray has the same dimensions as the input. The arrays have a row-major layout.
   *
   * @param inputArray The buffer of the image to be transformed.
   * @param sizeX The width of both buffers, as a number of 4 byte cells.
   * @param sizeY The height of both buffers, as a number of 4 byte cells.
   * @param table The four arrays of 256 values that's used to convert each channel.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The transformed image.
   */
  @JvmOverloads
  internal fun lut(
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    table: LookupTable,
    restriction: Range2d? = null
  ): ByteArray {
    require(inputArray.size >= sizeX * sizeY * 4) {
      "$externalName lut. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*4 < ${inputArray.size}."
    }
    validateRestriction("lut", sizeX, sizeY, restriction)

    val outputArray = ByteArray(inputArray.size)
    nativeLut(
      nativeHandle,
      inputArray,
      outputArray,
      sizeX,
      sizeY,
      table.red,
      table.green,
      table.blue,
      table.alpha,
      restriction
    )
    return outputArray
  }

  /**
   * Transform an image using a look up table
   *
   * Transforms an image by using a per-channel lookup table. Each channel of the input has an
   * independent lookup table. The tables are 256 entries in size and can cover the full value
   * range of a byte.
   *
   * The input Bitmap should be in config ARGB_8888. A variant of this method is available to
   * transform a ByteArray. Bitmaps with a stride different than width * vectorSize are not
   * currently supported.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output Bitmap will still be full size, with the
   * section that's not convolved all set to 0. This is to stay compatible with RenderScript.
   *
   * @param inputBitmap The buffer of the image to be transformed.
   * @param table The four arrays of 256 values that's used to convert each channel.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The transformed image.
   */
  @JvmOverloads
  internal fun lut(
    inputBitmap: Bitmap,
    table: LookupTable,
    restriction: Range2d? = null
  ): Bitmap {
    validateBitmap("lut", inputBitmap)
    validateRestriction("lut", inputBitmap, restriction)

    val outputBitmap = createCompatibleBitmap(inputBitmap)
    nativeLutBitmap(
      nativeHandle,
      inputBitmap,
      outputBitmap,
      table.red,
      table.green,
      table.blue,
      table.alpha,
      restriction
    )
    return outputBitmap
  }

  /**
   * Transform an image using a 3D look up table
   *
   * Transforms an image, converting RGB to RGBA by using a 3D lookup table. The incoming R, G,
   * and B values are normalized to the dimensions of the provided 3D buffer. The eight nearest
   * values in that 3D buffer are sampled and linearly interpolated. The resulting RGBA entry
   * is returned in the output array.
   *
   * The input array should be in RGBA format, where four consecutive bytes form an cell.
   * The fourth byte of each input cell is ignored. A variant of this method is also available
   * to transform Bitmaps.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output array will still be full size, with the
   * section that's not convolved all set to 0. This is to stay compatible with RenderScript.
   *
   * The source array should be large enough for sizeX * sizeY * vectorSize bytes. The returned
   * array will have the same dimensions. The arrays have a row-major layout.
   *
   * @param inputArray The buffer of the image to be transformed.
   * @param sizeX The width of both buffers, as a number of 4 byte cells.
   * @param sizeY The height of both buffers, as a number of 4 byte cells.
   * @param cube The translation cube.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The transformed image.
   */
  @JvmOverloads
  internal fun lut3d(
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    cube: Rgba3dArray,
    restriction: Range2d? = null
  ): ByteArray {
    require(inputArray.size >= sizeX * sizeY * 4) {
      "$externalName lut3d. inputArray is too small for the given dimensions. " +
        "$sizeX*$sizeY*4 < ${inputArray.size}."
    }
    require(
      cube.sizeX >= 2 && cube.sizeY >= 2 && cube.sizeZ >= 2 &&
        cube.sizeX <= 256 && cube.sizeY <= 256 && cube.sizeZ <= 256
    ) {
      "$externalName lut3d. The dimensions of the cube should be between 2 and 256. " +
        "(${cube.sizeX}, ${cube.sizeY}, ${cube.sizeZ}) provided."
    }
    validateRestriction("lut3d", sizeX, sizeY, restriction)

    val outputArray = ByteArray(inputArray.size)
    nativeLut3d(
      nativeHandle, inputArray, outputArray, sizeX, sizeY, cube.values, cube.sizeX,
      cube.sizeY, cube.sizeZ, restriction
    )
    return outputArray
  }

  /**
   * Transform an image using a 3D look up table
   *
   * Transforms an image, converting RGB to RGBA by using a 3D lookup table. The incoming R, G,
   * and B values are normalized to the dimensions of the provided 3D buffer. The eight nearest
   * values in that 3D buffer are sampled and linearly interpolated. The resulting RGBA entry
   * is returned in the output array.
   *
   * The input bitmap should be in RGBA_8888 format. The A channel is preserved. A variant of this
   * method is also available to transform ByteArray. Bitmaps with a stride different than
   * width * vectorSize are not currently supported.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of each buffer. If provided, the range must be wholly contained with the dimensions
   * described by sizeX and sizeY. NOTE: The output array will still be full size, with the
   * section that's not convolved all set to 0. This is to stay compatible with RenderScript.
   *
   * The source array should be large enough for sizeX * sizeY * vectorSize bytes. The returned
   * array will have the same dimensions. The arrays have a row-major layout.
   *
   * @param inputBitmap The image to be transformed.
   * @param cube The translation cube.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return The transformed image.
   */
  @JvmOverloads
  internal fun lut3d(
    inputBitmap: Bitmap,
    cube: Rgba3dArray,
    restriction: Range2d? = null
  ): Bitmap {
    validateBitmap("lut3d", inputBitmap)
    validateRestriction("lut3d", inputBitmap, restriction)

    val outputBitmap = createCompatibleBitmap(inputBitmap)
    nativeLut3dBitmap(
      nativeHandle,
      inputBitmap,
      outputBitmap,
      cube.values,
      cube.sizeX,
      cube.sizeY,
      cube.sizeZ,
      restriction
    )
    return outputBitmap
  }

  /**
   * Resize an image.
   *
   * Resizes an image using bicubic interpolation.
   *
   * This method supports elements of 1 to 4 bytes in length. Each byte of the element is
   * interpolated independently from the others.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of the output buffer. The corresponding scaled range of the input will be used. If provided,
   * the range must be wholly contained with the dimensions described by outputSizeX and
   * outputSizeY.
   *
   * The input and output arrays have a row-major layout. The input array should be
   * large enough for sizeX * sizeY * vectorSize bytes.
   *
   * Like the RenderScript Intrinsics, vectorSize of size 3 are padded to occupy 4 bytes.
   *
   * @param inputArray The buffer of the image to be resized.
   * @param vectorSize The number of bytes in each element of both buffers. A value from 1 to 4.
   * @param inputSizeX The width of the input buffer, as a number of 1-4 byte elements.
   * @param inputSizeY The height of the input buffer, as a number of 1-4 byte elements.
   * @param outputSizeX The width of the output buffer, as a number of 1-4 byte elements.
   * @param outputSizeY The height of the output buffer, as a number of 1-4 byte elements.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return An array that contains the rescaled image.
   */
  @JvmOverloads
  internal fun resize(
    inputArray: ByteArray,
    vectorSize: Int,
    inputSizeX: Int,
    inputSizeY: Int,
    outputSizeX: Int,
    outputSizeY: Int,
    restriction: Range2d? = null
  ): ByteArray {
    require(vectorSize in 1..4) {
      "$externalName resize. The vectorSize should be between 1 and 4. $vectorSize provided."
    }
    require(inputArray.size >= inputSizeX * inputSizeY * vectorSize) {
      "$externalName resize. inputArray is too small for the given dimensions. " +
        "$inputSizeX*$inputSizeY*$vectorSize < ${inputArray.size}."
    }
    validateRestriction("resize", outputSizeX, outputSizeY, restriction)

    val outputArray = ByteArray(outputSizeX * outputSizeY * paddedSize(vectorSize))
    nativeResize(
      nativeHandle,
      inputArray,
      vectorSize,
      inputSizeX,
      inputSizeY,
      outputArray,
      outputSizeX,
      outputSizeY,
      restriction
    )
    return outputArray
  }

  /**
   * Resize an image.
   *
   * Resizes an image using bicubic interpolation.
   *
   * This method supports input Bitmap of config ARGB_8888 and ALPHA_8. The returned Bitmap
   * has the same config. Bitmaps with a stride different than width * vectorSize are not
   * currently supported.
   *
   * An optional range parameter can be set to restrict the operation to a rectangular subset
   * of the output buffer. The corresponding scaled range of the input will be used. If provided,
   * the range must be wholly contained with the dimensions described by outputSizeX and
   * outputSizeY.
   *
   * @param inputBitmap The Bitmap to be resized.
   * @param outputSizeX The width of the output buffer, as a number of 1-4 byte elements.
   * @param outputSizeY The height of the output buffer, as a number of 1-4 byte elements.
   * @param restriction When not null, restricts the operation to a 2D range of pixels.
   * @return A Bitmap that contains the rescaled image.
   */
  @JvmOverloads
  internal fun resize(
    inputBitmap: Bitmap,
    outputSizeX: Int,
    outputSizeY: Int,
    restriction: Range2d? = null
  ): Bitmap {
    validateBitmap("resize", inputBitmap)
    validateRestriction("resize", outputSizeX, outputSizeY, restriction)

    val outputBitmap = Bitmap.createBitmap(outputSizeX, outputSizeY, Bitmap.Config.ARGB_8888)
    nativeResizeBitmap(nativeHandle, inputBitmap, outputBitmap, restriction)
    return outputBitmap
  }

  /**
   * Convert an image from YUV to RGB.
   *
   * Converts a YUV buffer to RGB. The input array should be supplied in a supported YUV format.
   * The output is RGBA; the alpha channel will be set to 255.
   *
   * Note that for YV12 and a sizeX that's not a multiple of 32, the RenderScript Intrinsic may
   * not have converted the image correctly. This Toolkit method should.
   *
   * @param inputArray The buffer of the image to be converted.
   * @param sizeX The width in pixels of the image.
   * @param sizeY The height in pixels of the image.
   * @param format Either YV12 or NV21.
   * @return The converted image as a byte array.
   */
  internal fun yuvToRgb(
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    format: YuvFormat
  ): ByteArray {
    require(sizeX % 2 == 0 && sizeY % 2 == 0) {
      "$externalName yuvToRgb. Non-even dimensions are not supported. " +
        "$sizeX and $sizeY were provided."
    }

    val outputArray = ByteArray(sizeX * sizeY * 4)
    nativeYuvToRgb(nativeHandle, inputArray, outputArray, sizeX, sizeY, format.value)
    return outputArray
  }

  /**
   * Convert an image from YUV to an RGB Bitmap.
   *
   * Converts a YUV buffer to an RGB Bitmap. The input array should be supplied in a supported
   * YUV format. The output is RGBA; the alpha channel will be set to 255.
   *
   * Note that for YV12 and a sizeX that's not a multiple of 32, the RenderScript Intrinsic may
   * not have converted the image correctly. This Toolkit method should.
   *
   * @param inputArray The buffer of the image to be converted.
   * @param sizeX The width in pixels of the image.
   * @param sizeY The height in pixels of the image.
   * @param format Either YV12 or NV21.
   * @return The converted image.
   */
  internal fun yuvToRgbBitmap(
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    format: YuvFormat
  ): Bitmap {
    require(sizeX % 2 == 0 && sizeY % 2 == 0) {
      "$externalName yuvToRgbBitmap. Non-even dimensions are not supported. " +
        "$sizeX and $sizeY were provided."
    }

    val outputBitmap = Bitmap.createBitmap(sizeX, sizeY, Bitmap.Config.ARGB_8888)
    nativeYuvToRgbBitmap(nativeHandle, inputArray, sizeX, sizeY, outputBitmap, format.value)
    return outputBitmap
  }

  private var nativeHandle: Long = 0

  init {
    System.loadLibrary("renderscript-toolkit")
    nativeHandle = createNative()
  }

  /**
   * Shutdown the thread pool.
   *
   * Waits for the threads to complete their work and destroys them.
   *
   * An application should call this method only if it is sure that it won't call the
   * toolkit again, as it is irreversible.
   */
  internal fun shutdown() {
    destroyNative(nativeHandle)
    nativeHandle = 0
  }

  private external fun createNative(): Long

  private external fun destroyNative(nativeHandle: Long)

  private external fun nativeBlend(
    nativeHandle: Long,
    mode: Int,
    sourceArray: ByteArray,
    destArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    restriction: Range2d?
  )

  private external fun nativeBlendBitmap(
    nativeHandle: Long,
    mode: Int,
    sourceBitmap: Bitmap,
    destBitmap: Bitmap,
    restriction: Range2d?
  )

  private external fun nativeBlur(
    nativeHandle: Long,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    radius: Int,
    outputArray: ByteArray,
    restriction: Range2d?
  )

  private external fun nativeBlurBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    radius: Int,
    restriction: Range2d?
  )

  private external fun nativeColorMatrix(
    nativeHandle: Long,
    inputArray: ByteArray,
    inputVectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    outputArray: ByteArray,
    outputVectorSize: Int,
    matrix: FloatArray,
    addVector: FloatArray,
    restriction: Range2d?
  )

  private external fun nativeColorMatrixBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    matrix: FloatArray,
    addVector: FloatArray,
    restriction: Range2d?
  )

  private external fun nativeConvolve(
    nativeHandle: Long,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    outputArray: ByteArray,
    coefficients: FloatArray,
    restriction: Range2d?
  )

  private external fun nativeConvolveBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    coefficients: FloatArray,
    restriction: Range2d?
  )

  private external fun nativeHistogram(
    nativeHandle: Long,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    outputArray: IntArray,
    restriction: Range2d?
  )

  private external fun nativeHistogramBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputArray: IntArray,
    restriction: Range2d?
  )

  private external fun nativeHistogramDot(
    nativeHandle: Long,
    inputArray: ByteArray,
    vectorSize: Int,
    sizeX: Int,
    sizeY: Int,
    outputArray: IntArray,
    coefficients: FloatArray,
    restriction: Range2d?
  )

  private external fun nativeHistogramDotBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputArray: IntArray,
    coefficients: FloatArray,
    restriction: Range2d?
  )

  private external fun nativeLut(
    nativeHandle: Long,
    inputArray: ByteArray,
    outputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    red: ByteArray,
    green: ByteArray,
    blue: ByteArray,
    alpha: ByteArray,
    restriction: Range2d?
  )

  private external fun nativeLutBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    red: ByteArray,
    green: ByteArray,
    blue: ByteArray,
    alpha: ByteArray,
    restriction: Range2d?
  )

  private external fun nativeLut3d(
    nativeHandle: Long,
    inputArray: ByteArray,
    outputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    cube: ByteArray,
    cubeSizeX: Int,
    cubeSizeY: Int,
    cubeSizeZ: Int,
    restriction: Range2d?
  )

  private external fun nativeLut3dBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    cube: ByteArray,
    cubeSizeX: Int,
    cubeSizeY: Int,
    cubeSizeZ: Int,
    restriction: Range2d?
  )

  private external fun nativeResize(
    nativeHandle: Long,
    inputArray: ByteArray,
    vectorSize: Int,
    inputSizeX: Int,
    inputSizeY: Int,
    outputArray: ByteArray,
    outputSizeX: Int,
    outputSizeY: Int,
    restriction: Range2d?
  )

  private external fun nativeResizeBitmap(
    nativeHandle: Long,
    inputBitmap: Bitmap,
    outputBitmap: Bitmap,
    restriction: Range2d?
  )

  private external fun nativeYuvToRgb(
    nativeHandle: Long,
    inputArray: ByteArray,
    outputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    format: Int
  )

  private external fun nativeYuvToRgbBitmap(
    nativeHandle: Long,
    inputArray: ByteArray,
    sizeX: Int,
    sizeY: Int,
    outputBitmap: Bitmap,
    value: Int
  )
}

/**
 * Determines how a source buffer is blended into a destination buffer.
 * See {@link RenderScriptToolkit::blend}.
 *
 * blend only works on 4 byte RGBA data. In the descriptions below, ".a" represents
 * the alpha channel.
 */
internal enum class BlendingMode(val value: Int) {
  /**
   * dest = 0
   *
   * The destination is cleared, i.e. each pixel is set to (0, 0, 0, 0)
   */
  CLEAR(0),

  /**
   * dest = src
   *
   * Sets each pixel of the destination to the corresponding one in the source.
   */
  SRC(1),

  /**
   * dest = dest
   *
   * Leaves the destination untouched. This is a no-op.
   */
  DST(2),

  /**
   * dest = src + dest * (1.0 - src.a)
   */
  SRC_OVER(3),

  /**
   * dest = dest + src * (1.0 - dest.a)
   */
  DST_OVER(4),

  /**
   * dest = src * dest.a
   */
  SRC_IN(5),

  /**
   * dest = dest * src.a
   */
  DST_IN(6),

  /**
   * dest = src * (1.0 - dest.a)
   */
  SRC_OUT(7),

  /**
   * dest = dest * (1.0 - src.a)
   */
  DST_OUT(8),

  /**
   * dest.rgb = src.rgb * dest.a + (1.0 - src.a) * dest.rgb, dest.a = dest.a
   */
  SRC_ATOP(9),

  /**
   * dest = dest.rgb * src.a + (1.0 - dest.a) * src.rgb, dest.a = src.a
   */
  DST_ATOP(10),

  /**
   * dest = {src.r ^ dest.r, src.g ^ dest.g, src.b ^ dest.b, src.a ^ dest.a}
   *
   * Note: this is NOT the Porter/Duff XOR mode; this is a bitwise xor.
   */
  XOR(11),

  /**
   * dest = src * dest
   */
  MULTIPLY(12),

  /**
   * dest = min(src + dest, 1.0)
   */
  ADD(13),

  /**
   * dest = max(dest - src, 0.0)
   */
  SUBTRACT(14)
}

/**
 * A translation table used by the lut method. For each potential red, green, blue, and alpha
 * value, specifies it's replacement value.
 *
 * The fields are initialized to be a no-op operation, i.e. replace 1 by 1, 2 by 2, etc.
 * You can modify just the values you're interested in having a translation.
 */
internal class LookupTable {
  var red = ByteArray(256) { it.toByte() }
  var green = ByteArray(256) { it.toByte() }
  var blue = ByteArray(256) { it.toByte() }
  var alpha = ByteArray(256) { it.toByte() }
}

/**
 * The YUV formats supported by yuvToRgb.
 */
internal enum class YuvFormat(val value: Int) {
  NV21(0x11),
  YV12(0x32315659),
}

/**
 * Define a range of data to process.
 *
 * This class is used to restrict a [RenderScriptToolkit] operation to a rectangular subset of the input
 * tensor.
 *
 * @property startX The index of the first value to be included on the X axis.
 * @property endX The index after the last value to be included on the X axis.
 * @property startY The index of the first value to be included on the Y axis.
 * @property endY The index after the last value to be included on the Y axis.
 */
internal data class Range2d(
  val startX: Int,
  val endX: Int,
  val startY: Int,
  val endY: Int
) {
  internal constructor() : this(0, 0, 0, 0)
}

internal class Rgba3dArray(val values: ByteArray, val sizeX: Int, val sizeY: Int, val sizeZ: Int) {
  init {
    require(values.size >= sizeX * sizeY * sizeZ * 4)
  }

  operator fun get(x: Int, y: Int, z: Int): ByteArray {
    val index = indexOfVector(x, y, z)
    return ByteArray(4) { values[index + it] }
  }

  operator fun set(x: Int, y: Int, z: Int, value: ByteArray) {
    require(value.size == 4)
    val index = indexOfVector(x, y, z)
    for (i in 0..3) {
      values[index + i] = value[i]
    }
  }

  private fun indexOfVector(x: Int, y: Int, z: Int): Int {
    require(x in 0 until sizeX)
    require(y in 0 until sizeY)
    require(z in 0 until sizeZ)
    return ((z * sizeY + y) * sizeX + x) * 4
  }
}

internal fun validateBitmap(
  function: String,
  inputBitmap: Bitmap,
  alphaAllowed: Boolean = true
) {
  if (alphaAllowed) {
    require(
      inputBitmap.config == Bitmap.Config.ARGB_8888 ||
        inputBitmap.config == Bitmap.Config.ALPHA_8
    ) {
      "$externalName. $function supports only ARGB_8888 and ALPHA_8 bitmaps. " +
        "${inputBitmap.config} provided."
    }
  } else {
    require(inputBitmap.config == Bitmap.Config.ARGB_8888) {
      "$externalName. $function supports only ARGB_8888. " +
        "${inputBitmap.config} provided."
    }
  }
  require(inputBitmap.width * vectorSize(inputBitmap) == inputBitmap.rowBytes) {
    "$externalName $function. Only bitmaps with rowSize equal to the width * vectorSize are " +
      "currently supported. Provided were rowBytes=${inputBitmap.rowBytes}, " +
      "width={${inputBitmap.width}, and vectorSize=${vectorSize(inputBitmap)}."
  }
}

internal fun createCompatibleBitmap(inputBitmap: Bitmap) =
  Bitmap.createBitmap(inputBitmap.width, inputBitmap.height, inputBitmap.config)

internal fun validateHistogramDotCoefficients(
  coefficients: FloatArray?,
  vectorSize: Int
) {
  require(coefficients == null || coefficients.size == vectorSize) {
    "$externalName histogramDot. The coefficients should be null or have $vectorSize values."
  }
  if (coefficients !== null) {
    var sum = 0f
    for (i in 0 until vectorSize) {
      require(coefficients[i] >= 0.0f) {
        "$externalName histogramDot. Coefficients should not be negative. " +
          "Coefficient $i was ${coefficients[i]}."
      }
      sum += coefficients[i]
    }
    require(sum <= 1.0f) {
      "$externalName histogramDot. Coefficients should add to 1 or less. Their sum is $sum."
    }
  }
}

internal fun validateRestriction(tag: String, bitmap: Bitmap, restriction: Range2d? = null) {
  validateRestriction(tag, bitmap.width, bitmap.height, restriction)
}

internal fun validateRestriction(
  tag: String,
  sizeX: Int,
  sizeY: Int,
  restriction: Range2d? = null
) {
  if (restriction == null) return
  require(restriction.startX < sizeX && restriction.endX <= sizeX) {
    "$externalName $tag. sizeX should be greater than restriction.startX and greater " +
      "or equal to restriction.endX. $sizeX, ${restriction.startX}, " +
      "and ${restriction.endX} were provided respectively."
  }
  require(restriction.startY < sizeY && restriction.endY <= sizeY) {
    "$externalName $tag. sizeY should be greater than restriction.startY and greater " +
      "or equal to restriction.endY. $sizeY, ${restriction.startY}, " +
      "and ${restriction.endY} were provided respectively."
  }
  require(restriction.startX < restriction.endX) {
    "$externalName $tag. Restriction startX should be less than endX. " +
      "${restriction.startX} and ${restriction.endX} were provided respectively."
  }
  require(restriction.startY < restriction.endY) {
    "$externalName $tag. Restriction startY should be less than endY. " +
      "${restriction.startY} and ${restriction.endY} were provided respectively."
  }
}

internal fun vectorSize(bitmap: Bitmap): Int {
  return when (bitmap.config) {
    Bitmap.Config.ARGB_8888 -> 4
    Bitmap.Config.ALPHA_8 -> 1
    else -> throw IllegalArgumentException(
      "$externalName. Only ARGB_8888 and ALPHA_8 Bitmap are supported."
    )
  }
}

internal fun paddedSize(vectorSize: Int) = if (vectorSize == 3) 4 else vectorSize
