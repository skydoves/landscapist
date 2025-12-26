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
package com.skydoves.landscapist.core.memory

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration

/**
 * Android-specific handler for system memory pressure events.
 *
 * Integrates with Android's [ComponentCallbacks2] to receive onTrimMemory callbacks
 * and translate them to [MemoryPressureLevel] events.
 */
public class AndroidMemoryPressureHandler(
  private val manager: MemoryPressureManager,
) : ComponentCallbacks2 {

  private var registered = false
  private var application: Application? = null

  /**
   * Registers this handler with the application.
   *
   * @param app The application context.
   */
  public fun register(app: Application) {
    if (!registered) {
      application = app
      app.registerComponentCallbacks(this)
      registered = true
    }
  }

  /**
   * Unregisters this handler from the application.
   */
  public fun unregister() {
    if (registered) {
      application?.unregisterComponentCallbacks(this)
      application = null
      registered = false
    }
  }

  override fun onTrimMemory(level: Int) {
    val pressureLevel = when {
      level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> MemoryPressureLevel.CRITICAL
      level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> MemoryPressureLevel.HIGH
      level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryPressureLevel.MODERATE
      level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryPressureLevel.LOW
      level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryPressureLevel.CRITICAL
      level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> MemoryPressureLevel.HIGH
      level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> MemoryPressureLevel.MODERATE
      else -> MemoryPressureLevel.LOW
    }
    manager.notifyMemoryPressure(pressureLevel)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    // No-op
  }

  override fun onLowMemory() {
    manager.notifyMemoryPressure(MemoryPressureLevel.CRITICAL)
  }
}
