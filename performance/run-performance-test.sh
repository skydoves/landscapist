#!/bin/bash

# Landscapist image library benchmark runner.
#
# Runs the symmetric load-time benchmark (ImageLibraryBenchmark), which measures every library
# from the model change until its own onImageStateChanged terminal callback. Requires a connected
# Android device or emulator and a network connection.

set -e

echo "=================================="
echo "Landscapist Image Library Benchmark"
echo "=================================="
echo "Measures cold-network load time for CoilImage (Coil3), GlideImage, FrescoImage, and"
echo "LandscapistImage, each timed to its own real completion callback."
echo ""

adb devices | grep -qw device || {
  echo "No connected device/emulator found. Start one and re-run." >&2
  exit 1
}

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.ImageLibraryBenchmark \
  --console=plain 2>&1 | tee benchmark-output.txt

echo ""
echo "Results table (also printed by the test under 'IMAGE LIBRARY BENCHMARK'):"
adb logcat -d | grep -A 30 "IMAGE LIBRARY BENCHMARK" | tail -30 || true

echo ""
echo "Full output saved to: performance/benchmark-output.txt"
echo "For scrolling jank and memory, run the macrobenchmark in :benchmark-landscapist."
