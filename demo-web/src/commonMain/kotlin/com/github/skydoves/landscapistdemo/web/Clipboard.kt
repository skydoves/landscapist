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
package com.github.skydoves.landscapistdemo.web

/**
 * Puts [text] on the clipboard, and says whether it got there.
 *
 * Compose's own `ClipboardManager` writes through `navigator.clipboard.writeText` and does not
 * handle the rejection, so a browser that refuses leaves an uncaught error in the console and the
 * caller still believes it worked. The copy button on this page reports what happened instead.
 */
internal expect suspend fun writeToClipboard(text: String): Boolean
