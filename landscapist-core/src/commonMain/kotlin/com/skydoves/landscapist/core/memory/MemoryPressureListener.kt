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

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Represents different levels of memory pressure.
 */
public enum class MemoryPressureLevel {
  /**
   * Low memory pressure - cache can operate normally.
   */
  LOW,

  /**
   * Moderate memory pressure - consider reducing cache size.
   */
  MODERATE,

  /**
   * High memory pressure - should aggressively reduce cache size.
   */
  HIGH,

  /**
   * Critical memory pressure - should clear caches immediately.
   */
  CRITICAL,
}

/**
 * Listener for memory pressure events.
 */
public fun interface MemoryPressureListener {
  /**
   * Called when memory pressure changes.
   *
   * @param level The current memory pressure level.
   */
  public fun onMemoryPressure(level: MemoryPressureLevel)
}

/**
 * Manager for handling memory pressure events and notifying listeners.
 */
public class MemoryPressureManager {

  private val lock = SynchronizedObject()
  private val listeners = mutableListOf<MemoryPressureListener>()

  /**
   * Registers a listener for memory pressure events.
   *
   * @param listener The listener to register.
   */
  public fun addListener(listener: MemoryPressureListener) {
    synchronized(lock) {
      listeners.add(listener)
    }
  }

  /**
   * Unregisters a listener.
   *
   * @param listener The listener to unregister.
   */
  public fun removeListener(listener: MemoryPressureListener) {
    synchronized(lock) {
      listeners.remove(listener)
    }
  }

  /**
   * Notifies all listeners of a memory pressure event.
   *
   * @param level The memory pressure level.
   */
  public fun notifyMemoryPressure(level: MemoryPressureLevel) {
    val listenersCopy = synchronized(lock) { listeners.toList() }
    listenersCopy.forEach { listener -> listener.onMemoryPressure(level) }
  }

  /**
   * Clears all listeners.
   */
  public fun clearListeners() {
    synchronized(lock) {
      listeners.clear()
    }
  }
}
