#!/bin/bash

# Comprehensive Performance Test Runner
# Runs 5 rounds of performance tests for all image libraries

echo "========================================"
echo "Comprehensive Performance Test"
echo "Running 5 rounds for each library"
echo "========================================"
echo ""

# Run the test
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.github.skydoves.landscapistdemo.ComprehensivePerformanceTest \
  --console=plain 2>&1 | tee comprehensive-perf-results.log

echo ""
echo "========================================"
echo "Extracting Results..."
echo "========================================"
echo ""

# Extract and display the results
grep -A 200 "FINAL RESULTS" comprehensive-perf-results.log || echo "Test may still be running or failed. Check comprehensive-perf-results.log"

echo ""
echo "Full results saved to: comprehensive-perf-results.log"
