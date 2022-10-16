# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("RenderScript Toolkit")

set(can_use_assembler TRUE)
enable_language(ASM)
add_definitions(-v -DANDROID -DOC_ARM_ASM)

set(CMAKE_CXX_FLAGS "-Wall -Wextra ${CMAKE_CXX_FLAGS}")

#message( STATUS "Architecture: ${CMAKE_SYSTEM_PROCESSOR}" )
#message( STATUS "CMAKE_CXX_FLAGS: ${CMAKE_CXX_FLAGS}")
#message( STATUS "CMAKE_CXX_FLAGS_DEBUG: ${CMAKE_CXX_FLAGS_DEBUG}")
#message( STATUS "CMAKE_CXX_FLAGS_RELEASE: ${CMAKE_CXX_FLAGS_RELEASE}")
#set(CMAKE_VERBOSE_MAKEFILE on)
#set(CMAKE_CXX_FLAGS_DEBUG "-O0 -fno-limit-debug-info -g")
#set(CMAKE_CXX_FLAGS_RELEASE "-O2 -Os -DNDEBUG")

#TODO check that the definitions are all needed. Do they have impact outside of our code?
if(CMAKE_SYSTEM_PROCESSOR STREQUAL armv7-a)
    add_definitions(-DARCH_ARM_USE_INTRINSICS -DARCH_ARM_HAVE_VFP)
    set(ASM_SOURCES
        Blend_neon.S
        Blur_neon.S
        ColorMatrix_neon.S
        Convolve_neon.S
        Lut3d_neon.S
        Resize_neon.S
        YuvToRgb_neon.S)
endif()

if(CMAKE_SYSTEM_PROCESSOR STREQUAL aarch64)
    add_definitions(-DARCH_ARM_USE_INTRINSICS -DARCH_ARM64_USE_INTRINSICS -DARCH_ARM64_HAVE_NEON)
    set(ASM_SOURCES
        Blend_advsimd.S
        Blur_advsimd.S
        ColorMatrix_advsimd.S
        Convolve_advsimd.S
        Lut3d_advsimd.S
        Resize_advsimd.S
        YuvToRgb_advsimd.S)
endif()
# TODO add also for x86

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(# Sets the name of the library.
            renderscript-toolkit
            # Sets the library as a shared library.
            SHARED
            # Provides a relative path to your source file(s).
            Blend.cpp
            Blur.cpp
            ColorMatrix.cpp
            Convolve3x3.cpp
            Convolve5x5.cpp
            Histogram.cpp
            JniEntryPoints.cpp
            Lut.cpp
            Lut3d.cpp
            RenderScriptToolkit.cpp
            Resize.cpp
            TaskProcessor.cpp
            Utils.cpp
            YuvToRgb.cpp
            ${ASM_SOURCES})

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library(# Sets the name of the path variable.
             log-lib
             # Specifies the name of the NDK library that
             # you want CMake to locate.
             log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries(# Specifies the target library.
                      renderscript-toolkit

                      cpufeatures
                      jnigraphics
                      # Links the target library to the log library
                      # included in the NDK.
                      ${log-lib} )

include(AndroidNdkModules)
android_ndk_import_module_cpufeatures()
