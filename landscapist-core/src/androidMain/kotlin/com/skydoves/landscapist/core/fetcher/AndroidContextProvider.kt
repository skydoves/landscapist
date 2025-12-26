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
package com.skydoves.landscapist.core.fetcher

import android.content.Context

/**
 * Provides Android [Context] access to fetchers that need it.
 *
 * This singleton must be initialized before using Android-specific fetchers
 * that require context (e.g., [UriFetcher], [DrawableResFetcher]).
 *
 * Initialization is done automatically when using [Landscapist.builder(context)].
 */
public object AndroidContextProvider {

  @Volatile
  private var context: Context? = null

  /**
   * Initializes the provider with the application context.
   *
   * @param context The context to use. The application context will be extracted.
   */
  public fun initialize(context: Context) {
    this.context = context.applicationContext
  }

  /**
   * Gets the context, or null if not initialized.
   *
   * @return The application context, or null.
   */
  public fun getOrNull(): Context? = context

  /**
   * Gets the context, throwing if not initialized.
   *
   * @return The application context.
   * @throws IllegalStateException if not initialized.
   */
  public fun get(): Context = context
    ?: throw IllegalStateException(
      "AndroidContextProvider not initialized. " +
        "Call AndroidContextProvider.initialize(context) or " +
        "use Landscapist.builder(context) on Android.",
    )

  /**
   * Clears the context. Useful for testing.
   */
  internal fun clear() {
    context = null
  }
}
