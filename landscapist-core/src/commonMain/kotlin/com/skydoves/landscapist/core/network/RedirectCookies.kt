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
package com.skydoves.landscapist.core.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.HttpHeaders
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Carries cookies from one hop of a redirect chain to the next.
 *
 * Some CDNs set a cookie on the first response and redirect to a target that requires it. Without
 * one, the target keeps redirecting until the send limit is hit. See
 * https://github.com/skydoves/landscapist/issues/859.
 *
 * Ktor's own `HttpCookies` does this, and cannot be used for it: it parses every attribute of every
 * `Set-Cookie` header it sees, and `max-age` is parsed with `toLong()` rather than the `runCatching`
 * that guards `expires` beside it. A server that sends `max-age=7.0`, which unsplash.com does,
 * throws `NumberFormatException` out of the response pipeline and fails the request. The image has
 * nothing to do with the cookie, and the download dies anyway.
 *
 * So only the part that is needed is read here: the name, the value, and the domain to send it back
 * to. Every other attribute is ignored rather than parsed, which is what makes this unable to fail
 * on one. Cookies live in memory for the life of the client and are not persisted.
 */
internal val RedirectCookies = createClientPlugin("RedirectCookies") {
  val jar = CookieJar()
  // The send and receive pipelines rather than onRequest and onResponse, because a redirect hop is
  // dispatched through these two and never through the request pipeline. Hooking the wrong pair
  // reads only the final response, which is the one hop whose cookie nothing still needs.
  client.sendPipeline.intercept(HttpSendPipeline.State) {
    val held = jar.headerFor(context.url.host) ?: return@intercept
    val existing = context.headers[HttpHeaders.Cookie]
    // Set rather than append, and the caller's own header first: a caller who sent a cookie keeps
    // it, and a second hop does not send the header twice.
    context.headers[HttpHeaders.Cookie] =
      if (existing.isNullOrEmpty()) held else "$existing; $held"
  }
  client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
    val set = response.headers.getAll(HttpHeaders.SetCookie)
    if (!set.isNullOrEmpty()) jar.record(response.call.request.url.host, set)
  }
}

/**
 * The cookies one client has been handed, by the domain they are to be sent back to.
 *
 * Deliberately small: no paths, no expiry, no secure flag. This exists to get a redirect hop the
 * cookie the hop before it was given, which lasts milliseconds, not to be a browser's cookie store.
 */
internal class CookieJar {

  private val lock = SynchronizedObject()
  private val byDomain = linkedMapOf<String, MutableMap<String, String>>()

  /** Reads the `name=value` and the domain out of each header, ignoring every other attribute. */
  fun record(host: String, headers: List<String>): Unit = synchronized(lock) {
    for (header in headers) {
      val parts = header.split(';')
      val pair = parts.firstOrNull()?.trim() ?: continue
      val separator = pair.indexOf('=')
      if (separator <= 0) continue
      val name = pair.substring(0, separator).trim()
      val value = pair.substring(separator + 1).trim()
      if (name.isEmpty()) continue
      val domain = parts.asSequence()
        .drop(1)
        .map { it.trim() }
        .firstOrNull { it.startsWith(DOMAIN_ATTRIBUTE, ignoreCase = true) }
        ?.substring(DOMAIN_ATTRIBUTE.length)
        ?.trim()
        ?.removePrefix(".")
        ?.lowercase()
        ?.takeIf { it.coversMoreThanOneSite() && host.matchesDomain(it) }
        ?: host.lowercase()
      val cookies = byDomain.getOrPut(domain) { linkedMapOf() }
      // An empty value is how a server clears a cookie, and the attribute that usually carries the
      // clearing is one of the ones not read here.
      if (value.isEmpty()) cookies.remove(name) else cookies[name] = value
      if (cookies.isEmpty()) byDomain.remove(domain)
      evictIfNeeded()
    }
  }

  /** The `Cookie` header value for [host], or null when there is nothing to send it. */
  fun headerFor(host: String): String? = synchronized(lock) {
    val lowered = host.lowercase()
    var joined: StringBuilder? = null
    for ((domain, cookies) in byDomain) {
      if (!lowered.matchesDomain(domain)) continue
      for ((name, value) in cookies) {
        val target = joined ?: StringBuilder().also { joined = it }
        if (target.isNotEmpty()) target.append("; ")
        target.append(name).append('=').append(value)
      }
    }
    joined?.toString()
  }

  /**
   * Whether a cookie held for [domain] belongs to this host.
   *
   * The host itself, or a subdomain of it. A cookie is never sent anywhere else, so a redirect that
   * leaves the site leaves the cookie behind.
   */
  private fun String.matchesDomain(domain: String): Boolean {
    val lowered = lowercase()
    return lowered == domain || lowered.endsWith(".$domain")
  }

  /**
   * Whether a `Domain` attribute names something narrower than a whole top level domain.
   *
   * `Domain=com` passes [matchesDomain] for every host that ends in it, which would put one site's
   * cookie on every request this client makes to any of them. Two labels is the cheap half of the
   * check. The other half, that the two are not themselves a public suffix such as `co.uk`, needs a
   * list of them that this library does not carry, so a cookie scoped that widely is still honoured.
   */
  private fun String.coversMoreThanOneSite(): Boolean = contains('.') && !startsWith('.')

  /** Keeps a long lived client from accumulating a domain per host it has ever been sent to. */
  private fun evictIfNeeded() {
    while (byDomain.size > MAX_DOMAINS) {
      val oldest = byDomain.keys.firstOrNull() ?: return
      byDomain.remove(oldest)
    }
  }

  private companion object {
    const val DOMAIN_ATTRIBUTE = "domain="
    const val MAX_DOMAINS = 64
  }
}
