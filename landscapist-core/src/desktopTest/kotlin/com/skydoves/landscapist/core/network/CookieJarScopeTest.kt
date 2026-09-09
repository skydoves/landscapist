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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How far a cookie a redirect handed over is allowed to travel. */
class CookieJarScopeTest {

  @Test
  fun `a cookie follows the host that set it`() {
    val jar = CookieJar()
    jar.record("images.example.com", listOf("sid=abc"))
    assertEquals("sid=abc", jar.headerFor("images.example.com"))
    assertNull(jar.headerFor("images.other.com"), "the cookie left the site that set it")
  }

  @Test
  fun `a cookie scoped to a parent domain reaches its subdomains`() {
    val jar = CookieJar()
    jar.record("images.example.com", listOf("sid=abc; Domain=example.com"))
    assertEquals("sid=abc", jar.headerFor("cdn.example.com"))
    assertNull(jar.headerFor("example.org"), "the cookie reached an unrelated site")
    // A suffix match without the separating dot: notexample.com ends with example.com and is a
    // different site.
    assertNull(
      jar.headerFor("notexample.com"),
      "the cookie reached a site that merely ends with the domain it was scoped to",
    )
  }

  @Test
  fun `a cookie cannot claim a whole top level domain`() {
    val jar = CookieJar()
    jar.record("images.example.com", listOf("sid=abc; Domain=com"))
    assertNull(
      jar.headerFor("victim.com"),
      "a Domain of com was honoured, so the cookie is sent to every .com host this client asks",
    )
    // It is still the setting host's own cookie, so it goes back there.
    assertEquals("sid=abc", jar.headerFor("images.example.com"))
  }

  @Test
  fun `a cookie cannot claim a domain the host is not under`() {
    val jar = CookieJar()
    jar.record("images.example.com", listOf("sid=abc; Domain=victim.com"))
    assertNull(jar.headerFor("victim.com"), "a host set a cookie for a site it does not own")
  }
}
