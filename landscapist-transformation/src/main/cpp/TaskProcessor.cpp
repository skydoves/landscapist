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

#include "TaskProcessor.h"

#include <cassert>
#include <sys/prctl.h>

#include "RenderScriptToolkit.h"
#include "Utils.h"

#define LOG_TAG "renderscript.toolkit.TaskProcessor"

namespace renderscript {

int Task::setTiling(unsigned int targetTileSizeInBytes) {
    // Empirically, values smaller than 1000 are unlikely to give good performance.
    targetTileSizeInBytes = std::max(1000u, targetTileSizeInBytes);
    const size_t cellSizeInBytes =
            mVectorSize;  // If we add float support, vectorSize * 4 for that.
    const size_t targetCellsPerTile = targetTileSizeInBytes / cellSizeInBytes;
    assert(targetCellsPerTile > 0);

    size_t cellsToProcessY;
    size_t cellsToProcessX;
    if (mRestriction == nullptr) {
        cellsToProcessX = mSizeX;
        cellsToProcessY = mSizeY;
    } else {
        assert(mRestriction->endX > mRestriction->startX);
        assert(mRestriction->endY > mRestriction->startY);
        cellsToProcessX = mRestriction->endX - mRestriction->startX;
        cellsToProcessY = mRestriction->endY - mRestriction->startY;
    }

    // We want rows as large as possible, as the SIMD code we have is more efficient with
    // large rows.
    mTilesPerRow = divideRoundingUp(cellsToProcessX, targetCellsPerTile);
    // Once we know the number of tiles per row, we divide that row evenly. We round up to make
    // sure all cells are included in the last tile of the row.
    mCellsPerTileX = divideRoundingUp(cellsToProcessX, mTilesPerRow);

    // We do the same thing for the Y direction.
    size_t targetRowsPerTile = divideRoundingUp(targetCellsPerTile, mCellsPerTileX);
    mTilesPerColumn = divideRoundingUp(cellsToProcessY, targetRowsPerTile);
    mCellsPerTileY = divideRoundingUp(cellsToProcessY, mTilesPerColumn);

    return mTilesPerRow * mTilesPerColumn;
}

void Task::processTile(unsigned int threadIndex, size_t tileIndex) {
    // Figure out the overall boundaries.
    size_t startWorkX;
    size_t startWorkY;
    size_t endWorkX;
    size_t endWorkY;
    if (mRestriction == nullptr) {
        startWorkX = 0;
        startWorkY = 0;
        endWorkX = mSizeX;
        endWorkY = mSizeY;
    } else {
        startWorkX = mRestriction->startX;
        startWorkY = mRestriction->startY;
        endWorkX = mRestriction->endX;
        endWorkY = mRestriction->endY;
    }
    // Figure out the rectangle for this tileIndex. All our tiles form a 2D grid. Identify
    // first the X, Y coordinate of our tile in that grid.
    size_t tileIndexY = tileIndex / mTilesPerRow;
    size_t tileIndexX = tileIndex % mTilesPerRow;
    // Calculate the starting and ending point of that tile.
    size_t startCellX = startWorkX + tileIndexX * mCellsPerTileX;
    size_t startCellY = startWorkY + tileIndexY * mCellsPerTileY;
    size_t endCellX = std::min(startCellX + mCellsPerTileX, endWorkX);
    size_t endCellY = std::min(startCellY + mCellsPerTileY, endWorkY);

    // Call the derived class to do the specific work.
    if (mPrefersDataAsOneRow && startCellX == 0 && endCellX == mSizeX) {
        // When the tile covers entire rows, we can take advantage that some ops are not 2D.
        processData(threadIndex, 0, startCellY, mSizeX * (endCellY - startCellY), startCellY + 1);
    } else {
        processData(threadIndex, startCellX, startCellY, endCellX, endCellY);
    }
}

TaskProcessor::TaskProcessor(unsigned int numThreads)
    : mUsesSimd{cpuSupportsSimd()},
      /* If the requested number of threads is 0, we'll decide based on the number of cores.
       * Through empirical testing, we've found that using more than 6 threads does not help.
       * There may be more optimal choices to make depending on the SoC but we'll stick to
       * this simple heuristic for now.
       *
       * We'll re-use the thread that calls the processor doTask method, so we'll spawn one less
       * worker pool thread than the total number of threads.
       */
      mNumberOfPoolThreads{numThreads ? numThreads - 1
                                      : std::min(6u, std::thread::hardware_concurrency() - 1)} {
    for (size_t i = 0; i < mNumberOfPoolThreads; i++) {
        mPoolThreads.emplace_back(
                std::bind(&TaskProcessor::processTilesOfWork, this, i + 1, false));
    }
}

TaskProcessor::~TaskProcessor() {
    {
        std::lock_guard<std::mutex> lock(mQueueMutex);
        mStopThreads = true;
        mWorkAvailableOrStop.notify_all();
    }

    for (auto& thread : mPoolThreads) {
        thread.join();
    }
}

void TaskProcessor::processTilesOfWork(int threadIndex, bool returnWhenNoWork) {
    if (threadIndex != 0) {
        // Set the name of the thread, except for thread 0, which is not part of the pool.
        // PR_SET_NAME takes a maximum of 16 characters, including the terminating null.
        char name[16]{"RenderScToolkit"};
        prctl(PR_SET_NAME, name, 0, 0, 0);
        // ALOGI("Starting thread%d", threadIndex);
    }

    std::unique_lock<std::mutex> lock(mQueueMutex);
    while (true) {
        mWorkAvailableOrStop.wait(lock, [this, returnWhenNoWork]() /*REQUIRES(mQueueMutex)*/ {
            return mStopThreads || (mTilesNotYetStarted > 0) ||
                   (returnWhenNoWork && (mTilesNotYetStarted == 0));
        });
        // ALOGI("Woke thread%d", threadIndex);

        // This ScopedLockAssertion is to help the compiler when it checks thread annotations
        // to realize that we have the lock. It's however not completely true; we don't
        // hold the lock while processing the tile.
        // TODO Figure out how to fix that.
        // android::base::ScopedLockAssertion lockAssert(mQueueMutex);
        if (mStopThreads || (returnWhenNoWork && mTilesNotYetStarted == 0)) {
            break;
        }

        while (mTilesNotYetStarted > 0 && !mStopThreads) {
            // This picks the tiles in decreasing order but that does not matter.
            int myTile = --mTilesNotYetStarted;
            mTilesInProcess++;
            lock.unlock();
            {
                // We won't be executing this code unless the main thread is
                // holding the mTaskMutex lock, which guards mCurrentTask.
                // The compiler can't figure this out.
                // android::base::ScopedLockAssertion lockAssert(mTaskMutex);
                mCurrentTask->processTile(threadIndex, myTile);
            }
            lock.lock();
            mTilesInProcess--;
            if (mTilesInProcess == 0 && mTilesNotYetStarted == 0) {
                mWorkIsFinished.notify_one();
            }
        }
    }
    // if (threadIndex != 0) {
    //     ALOGI("Ending thread%d", threadIndex);
    // }
}

void TaskProcessor::doTask(Task* task) {
    std::lock_guard<std::mutex> lockGuard(mTaskMutex);
    task->setUsesSimd(mUsesSimd);
    mCurrentTask = task;
    // Notify the thread pool of available work.
    startWork(task);
    // Start processing some of the tiles on the calling thread.
    processTilesOfWork(0, true);
    // Wait for all the pool workers to complete.
    waitForPoolWorkersToComplete();
    mCurrentTask = nullptr;
}

void TaskProcessor::startWork(Task* task) {
    /**
     * The size in bytes that we're hoping each tile will be. If this value is too small,
     * we'll spend too much time in synchronization. If it's too large, some cores may be
     * idle while others still have a lot of work to do. Ideally, it would depend on the
     * device we're running. 16k is the same value used by RenderScript and seems reasonable
     * from ad-hoc tests.
     */
    const size_t targetTileSize = 16 * 1024;

    std::lock_guard<std::mutex> lock(mQueueMutex);
    assert(mTilesInProcess == 0);
    mTilesNotYetStarted = task->setTiling(targetTileSize);
    mWorkAvailableOrStop.notify_all();
}

void TaskProcessor::waitForPoolWorkersToComplete() {
    std::unique_lock<std::mutex> lock(mQueueMutex);
    // The predicate, i.e. the lambda, will make sure that
    // we terminate even if the main thread calls this after
    // mWorkIsFinished is signaled.
    mWorkIsFinished.wait(lock, [this]() /*REQUIRES(mQueueMutex)*/ {
        return mTilesNotYetStarted == 0 && mTilesInProcess == 0;
    });
}

}  // namespace renderscript
