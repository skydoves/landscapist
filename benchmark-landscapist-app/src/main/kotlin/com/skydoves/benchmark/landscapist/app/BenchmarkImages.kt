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
package com.skydoves.benchmark.landscapist.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * The images every benchmark list loads, served over loopback from this process.
 *
 * These used to come from picsum.photos, which put a live internet round trip inside every measured
 * block and made the numbers depend on a CDN. Each of the [COUNT] paths serves the same encoded
 * JPEG under its own url, so no cache on any side can share an entry and every list fetches and
 * decodes [COUNT] times. The port belongs to the process, so a restarted process is always a cold
 * cache for every library at once.
 */
internal object BenchmarkImages {

  /** Number of images per library list. Large enough that the list scrolls past several screens. */
  private const val COUNT = 30

  /** Height of each list item, in dp. */
  const val ITEM_HEIGHT_DP = 200

  /** Side of the served JPEG, in pixels. */
  private const val SIDE = 400

  /** Side of one noise block, in pixels. Small enough that JPEG cannot collapse the image. */
  private const val BLOCK = 8

  private const val QUALITY = 90

  private val workers = Executors.newCachedThreadPool()

  @Volatile
  private var socket: ServerSocket? = null

  /** Encoded once, off the main thread, so no measured frame ever waits for a JPEG encoder. */
  private val jpeg: ByteArray by lazy { encodeJpeg() }

  /** Starts the loopback server. Called from [App], before any list can compose. */
  fun start() {
    if (socket != null) return
    val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    socket = server
    // Encoded ahead of any request, so the first measured frame never waits for a JPEG encoder.
    workers.execute { check(jpeg.isNotEmpty()) { "the benchmark image failed to encode" } }
    workers.execute {
      while (!server.isClosed) {
        val client = try {
          server.accept()
        } catch (_: Throwable) {
          return@execute
        }
        // A client that was cancelled is gone before the response is written, and the broken pipe
        // that follows is a library cancelling a load rather than the server failing.
        workers.execute { runCatching { respond(client) } }
      }
    }
  }

  fun urls(): List<String> {
    val server = checkNotNull(socket) { "the benchmark image server was never started" }
    return List(COUNT) { "http://127.0.0.1:${server.localPort}/image-$it.jpg" }
  }

  private fun respond(client: Socket) {
    client.use {
      val input = BufferedInputStream(it.getInputStream())
      // Every path serves the same image, so the request head only has to be drained.
      while (true) {
        val line = readLine(input) ?: return
        if (line.isEmpty()) break
      }
      val body = jpeg
      val head = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\n" +
        "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
      val output = it.getOutputStream()
      output.write(head.toByteArray(Charsets.ISO_8859_1))
      output.write(body)
      output.flush()
    }
  }

  /** Null at the end of the stream. */
  private fun readLine(input: BufferedInputStream): String? {
    val line = StringBuilder()
    while (true) {
      val byte = input.read()
      if (byte == -1) return if (line.isEmpty()) null else line.toString()
      if (byte == '\n'.code) return line.toString().removeSuffix("\r")
      line.append(byte.toChar())
    }
  }

  /** A JPEG with enough detail that the encoder cannot collapse it to nothing. */
  private fun encodeJpeg(): ByteArray {
    val bitmap = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    var seed = 0x9E3779B9.toInt()
    for (y in 0 until SIDE step BLOCK) {
      for (x in 0 until SIDE step BLOCK) {
        seed = seed * 1_664_525 + 1_013_904_223
        paint.color = Color.BLACK or (seed and 0xFFFFFF)
        val right = (x + BLOCK).toFloat()
        val bottom = (y + BLOCK).toFloat()
        canvas.drawRect(x.toFloat(), y.toFloat(), right, bottom, paint)
      }
    }
    return ByteArrayOutputStream().use { out ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
      bitmap.recycle()
      out.toByteArray()
    }
  }
}
