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
package com.github.skydoves.landscapistdemo.harness

import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A raw socket server in the test process, so a response can be as malformed as a real one. */
class LocalImageServer : AutoCloseable {

  private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
  private val workers = Executors.newCachedThreadPool()
  private val routes = ConcurrentHashMap<String, Route>()
  private val hits = ConcurrentHashMap<String, AtomicInteger>()

  val requests: MutableList<Recorded> = CopyOnWriteArrayList()

  val port: Int get() = socket.localPort

  fun url(path: String): String = "http://127.0.0.1:$port$path"

  fun hitCount(path: String): Int = hits[path]?.get() ?: 0

  fun resetCounts() {
    hits.clear()
    requests.clear()
  }

  /** [gate], when set, holds the response open so a test decides when the load finishes. */
  fun serve(
    path: String,
    body: ByteArray,
    contentType: String = "image/jpeg",
    status: Int = 200,
    delayMs: Long = 0,
    headers: List<String> = emptyList(),
    gate: CountDownLatch? = null,
    declaredLength: Int? = null,
  ) {
    routes[path] = Route(body, contentType, status, delayMs, headers, gate, declaredLength)
  }

  fun redirect(path: String, target: String, headers: List<String> = emptyList()) {
    routes[path] = Route(
      body = ByteArray(0),
      contentType = "text/plain",
      status = 302,
      delayMs = 0,
      headers = listOf("Location: $target") + headers,
      gate = null,
      declaredLength = null,
    )
  }

  fun fail(path: String, status: Int = 404) {
    routes[path] = Route(ByteArray(0), "text/plain", status, 0, emptyList(), null, null)
  }

  init {
    workers.execute {
      while (!socket.isClosed) {
        val client = try {
          socket.accept()
        } catch (_: Throwable) {
          return@execute
        }
        workers.execute { answer(client) }
      }
    }
  }

  private fun answer(client: Socket) {
    // A client that timed out or was cancelled is gone before the response is written, and the
    // broken pipe that follows is the test working, not the server failing.
    runCatching { respond(client) }
  }

  private fun respond(client: Socket) {
    client.use {
      val input = BufferedInputStream(client.getInputStream())
      val requestLine = readLine(input) ?: return
      val headerLines = buildList {
        while (true) {
          val line = readLine(input) ?: break
          if (line.isEmpty()) break
          add(line)
        }
      }
      val path = requestLine.split(' ').getOrNull(1) ?: "/"
      requests += Recorded(path, headerLines)
      hits.getOrPut(path) { AtomicInteger() }.incrementAndGet()

      val route = routes[path]
      val output = client.getOutputStream()
      if (route == null) {
        write(output, 404, "text/plain", ByteArray(0), emptyList(), 0)
        return
      }
      if (route.delayMs > 0) Thread.sleep(route.delayMs)
      route.gate?.await(30, TimeUnit.SECONDS)
      write(
        output = output,
        status = route.status,
        contentType = route.contentType,
        body = route.body,
        headers = route.headers,
        declaredLength = route.declaredLength ?: route.body.size,
      )
    }
  }

  private fun write(
    output: OutputStream,
    status: Int,
    contentType: String,
    body: ByteArray,
    headers: List<String>,
    declaredLength: Int,
  ) {
    val head = buildString {
      append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
      append("Content-Type: ").append(contentType).append("\r\n")
      // Declared rather than actual, so a route can promise more than it sends, which is what a
      // dropped connection looks like to a client.
      append("Content-Length: ").append(declaredLength).append("\r\n")
      append("Connection: close\r\n")
      // Verbatim, so a header a parser would refuse still reaches the client.
      for (header in headers) append(header).append("\r\n")
      append("\r\n")
    }
    output.write(head.toByteArray(Charsets.ISO_8859_1))
    if (body.isNotEmpty()) output.write(body)
    output.flush()
  }

  private fun reason(status: Int): String = when (status) {
    200 -> "OK"
    302 -> "Found"
    304 -> "Not Modified"
    404 -> "Not Found"
    500 -> "Internal Server Error"
    else -> "Status"
  }

  /** Null at the end of the stream. */
  private fun readLine(input: BufferedInputStream): String? {
    val line = StringBuilder()
    while (true) {
      val byte = input.read()
      if (byte == -1) return if (line.isEmpty()) null else line.toString()
      if (byte == '\n'.code) return line.removeSuffix("\r").toString()
      line.append(byte.toChar())
    }
  }

  override fun close() {
    routes.clear()
    runCatching { socket.close() }
    workers.shutdownNow()
  }

  private class Route(
    val body: ByteArray,
    val contentType: String,
    val status: Int,
    val delayMs: Long,
    val headers: List<String>,
    val gate: CountDownLatch?,
    val declaredLength: Int?,
  )

  data class Recorded(val path: String, val headers: List<String>) {
    fun header(name: String): String? = headers
      .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
      ?.substringAfter(':')
      ?.trim()
  }
}

private fun StringBuilder.removeSuffix(suffix: String): StringBuilder {
  if (length >= suffix.length && endsWith(suffix)) setLength(length - suffix.length)
  return this
}
