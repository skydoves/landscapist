/*
 * Designed and developed by 2020-2023 skydoves (Jaewoong Eum)
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
package com.skydoves.landscapist.core.decoder

/** The MIME type SVG markup is carried under through the loading pipeline. */
public const val SVG_MIME_TYPE: String = "image/svg+xml"

/** How much of the payload is read while looking for the root element. */
private const val SVG_PROLOGUE_LIMIT = 1024

private const val OPEN_ANGLE = '<'.code.toByte()
private val SVG_TAG = "<svg".encodeToByteArray()
private val COMMENT_OPEN = "<!--".encodeToByteArray()
private val COMMENT_CLOSE = "-->".encodeToByteArray()
private val BYTE_ORDER_MARK = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * Reports whether [bytes] is SVG markup.
 *
 * The [mimeType] is trusted when it says so, and otherwise the payload is sniffed, because plenty
 * of servers hand SVG back as `text/plain`, `application/octet-stream`, or with no type at all.
 * Sniffing looks for `<svg` as the document's root element, past a byte order mark, an XML
 * declaration, a doctype and any comments, so an HTML page that merely embeds an `<svg>` somewhere
 * is not mistaken for an image.
 */
public fun isSvg(bytes: ByteArray, mimeType: String?): Boolean {
  if (mimeType != null && mimeType.substringBefore(';').trim().equals(SVG_MIME_TYPE, true)) {
    return true
  }
  return bytes.indexOfSvgRootTag() != null
}

/**
 * Reads the intrinsic size out of the opening `<svg>` tag of [bytes], or returns null when it
 * declares none.
 *
 * The `width` and `height` attributes win when both are absolute; a relative one (`50%`) falls back
 * to the `viewBox`, which is what actually carries the aspect ratio in that case. Units are ignored
 * rather than converted: width and height are written in the same unit in practice, so the ratio is
 * right, and the numbers are only used for the aspect ratio and for cache accounting.
 */
public fun readSvgDimensions(bytes: ByteArray): ImageSize? {
  val tag = bytes.readSvgRootTag() ?: return null

  val width = SVG_WIDTH.attributeOf(tag)?.toSvgLengthOrNull()
  val height = SVG_HEIGHT.attributeOf(tag)?.toSvgLengthOrNull()
  svgSizeOrNull(width, height)?.let { return it }

  val viewBox = SVG_VIEW_BOX.attributeOf(tag)
    ?.split(' ', ',', '\t', '\n', '\r')
    ?.filter { it.isNotBlank() }
    ?: return null
  if (viewBox.size < 4) return null

  return svgSizeOrNull(viewBox[2].toSvgLengthOrNull(), viewBox[3].toSvgLengthOrNull())
}

// An attribute name has to be preceded by the start of the tag or by whitespace, so that
// `stroke-width` is not read as `width`.
private val SVG_WIDTH = svgAttributeRegex("width")
private val SVG_HEIGHT = svgAttributeRegex("height")
private val SVG_VIEW_BOX = svgAttributeRegex("viewBox")

private fun svgAttributeRegex(name: String) =
  Regex("""(?:^|\s)$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

private fun Regex.attributeOf(tag: String): String? = find(tag)?.groupValues?.get(1)?.trim()

/**
 * The index of the document's root `<svg` tag, or null when the document does not open with one.
 *
 * Only the first [SVG_PROLOGUE_LIMIT] bytes are examined: a real SVG declares its root element
 * within a few lines, and the bound keeps a large non-image payload from being scanned in full.
 */
private fun ByteArray.indexOfSvgRootTag(): Int? {
  var index = if (startsWith(BYTE_ORDER_MARK, 0)) BYTE_ORDER_MARK.size else 0
  val limit = minOf(size, SVG_PROLOGUE_LIMIT)
  while (index < limit) {
    when {
      this[index].isAsciiWhitespace() -> index++
      this[index] != OPEN_ANGLE -> return null
      // An XML declaration, a doctype or a comment can precede the root element.
      isPrologueAt(index) -> index = endOfPrologueAt(index, limit) ?: return null
      else -> return if (matchesSvgTagAt(index)) index else null
    }
  }
  return null
}

private fun ByteArray.isPrologueAt(index: Int): Boolean {
  val next = getOrNull(index + 1) ?: return false
  return next == '?'.code.toByte() || next == '!'.code.toByte()
}

/**
 * The index just past the prologue item starting at [index].
 *
 * A doctype holding an internal subset with a nested `>` ends the scan early, which fails detection
 * rather than misreading the document; the raster decoders then report the real problem.
 */
private fun ByteArray.endOfPrologueAt(index: Int, limit: Int): Int? =
  if (startsWith(COMMENT_OPEN, index)) {
    indexOf(COMMENT_CLOSE, index + COMMENT_OPEN.size, limit)?.plus(COMMENT_CLOSE.size)
  } else {
    indexOfByte('>'.code.toByte(), index + 1, limit)?.plus(1)
  }

private fun ByteArray.matchesSvgTagAt(index: Int): Boolean {
  if (index + SVG_TAG.size > size) return false
  for (offset in SVG_TAG.indices) {
    if (this[index + offset].lowercaseAscii() != SVG_TAG[offset]) return false
  }
  // Reject `<svgfoo`: the tag name has to end here.
  val next = getOrNull(index + SVG_TAG.size) ?: return false
  return next.isAsciiWhitespace() || next == '>'.code.toByte() || next == '/'.code.toByte()
}

/** The root `<svg ...>` tag as text, or null when it is absent or unterminated. */
private fun ByteArray.readSvgRootTag(): String? {
  val start = indexOfSvgRootTag() ?: return null
  var quote: Byte = 0
  for (index in start until size) {
    val byte = this[index]
    when {
      quote != 0.toByte() -> if (byte == quote) quote = 0
      byte == '"'.code.toByte() || byte == '\''.code.toByte() -> quote = byte
      byte == '>'.code.toByte() -> return decodeToString(start, index + 1, false)
    }
  }
  return null
}

/** Parses the leading number of an SVG length, ignoring its unit. Relative lengths return null. */
private fun String.toSvgLengthOrNull(): Float? {
  if (endsWith("%")) return null
  val number = takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }
  return number.toFloatOrNull()
}

private fun svgSizeOrNull(width: Float?, height: Float?): ImageSize? {
  if (width == null || height == null || width <= 0f || height <= 0f) return null
  return ImageSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1))
}

private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean {
  if (at + prefix.size > size) return false
  return prefix.indices.all { this[at + it] == prefix[it] }
}

private fun ByteArray.indexOf(target: ByteArray, from: Int, until: Int): Int? {
  for (index in from..minOf(until, size) - target.size) {
    if (startsWith(target, index)) return index
  }
  return null
}

private fun ByteArray.indexOfByte(target: Byte, from: Int, until: Int): Int? {
  for (index in from until minOf(until, size)) {
    if (this[index] == target) return index
  }
  return null
}

private fun Byte.isAsciiWhitespace(): Boolean =
  this == ' '.code.toByte() || this == '\t'.code.toByte() ||
    this == '\n'.code.toByte() || this == '\r'.code.toByte()

private fun Byte.lowercaseAscii(): Byte =
  if (this >= 'A'.code.toByte() && this <= 'Z'.code.toByte()) (this + 0x20).toByte() else this
