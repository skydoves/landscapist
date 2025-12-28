#!/bin/bash

# Performance comparison test runner
# This script runs the network loading performance comparison test
# and displays the results in a readable format.

echo "=================================="
echo "Landscapist Performance Test"
echo "=================================="
echo ""
echo "This will compare network loading performance for:"
echo "  • GlideImage"
echo "  • CoilImage"
echo "  • LandscapistImage"
echo "  • FrescoImage"
echo ""
echo "Requirements:"
echo "  • Connected Android device or emulator"
echo "  • Stable internet connection"
echo "  • ~2-3 minutes to complete"
echo ""
read -p "Press Enter to start the test..."

echo ""
echo "Building and installing test APK..."
echo ""

# Build and run the test
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.NetworkLoadingPerformanceComparison \
  --info 2>&1 | tee performance-test-output.txt

# Extract results from logcat
echo ""
echo "Extracting results..."
adb logcat -d | grep -A 50 "NETWORK LOADING PERFORMANCE COMPARISON TEST" | tail -50

echo ""
echo "=================================="
echo "Test Complete!"
echo "=================================="
echo ""
echo "Full output saved to: performance-test-output.txt"
echo "You can also view results in Android Studio's Run window"
echo ""
