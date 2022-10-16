/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "RenderScriptToolkit.h"

#include "TaskProcessor.h"

#define LOG_TAG "renderscript.toolkit.RenderScriptToolkit"

namespace renderscript {

// You will find the implementation of the various transformations in the correspondingly
// named source file. E.g. RenderScriptToolkit::blur() is found in Blur.cpp.

RenderScriptToolkit::RenderScriptToolkit(int numberOfThreads)
    : processor{new TaskProcessor(numberOfThreads)} {}

RenderScriptToolkit::~RenderScriptToolkit() {
    // By defining the destructor here, we don't need to include TaskProcessor.h
    // in RenderScriptToolkit.h.
}

}  // namespace renderscript
