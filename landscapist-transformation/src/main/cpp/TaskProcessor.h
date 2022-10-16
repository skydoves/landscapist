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

#ifndef ANDROID_RENDERSCRIPT_TOOLKIT_TASKPROCESSOR_H
#define ANDROID_RENDERSCRIPT_TOOLKIT_TASKPROCESSOR_H

// #include <android-base/thread_annotations.h>

#include <atomic>
#include <condition_variable>
#include <cstddef>
#include <mutex>
#include <thread>
#include <vector>

namespace renderscript {

/**
 * Description of the data to be processed for one Toolkit method call, e.g. one blur or one
 * blend operation.
 *
 * The data to be processed is a 2D array of cells. Each cell is a vector of 1 to 4 unsigned bytes.
 * The most typical configuration is a 2D array of uchar4 used to represent RGBA images.
 *
 * This is a base class. There will be a subclass for each Toolkit op.
 *
 * Typical usage of a derived class would look like:
 *    BlurTask task(in, out, sizeX, sizeY, vectorSize, etc);
 *    processor->doTask(&task);
 *
 * The TaskProcessor should call setTiling() and setUsesSimd() once, before calling processTile().
 * Other classes should not call setTiling(), setUsesSimd(), and processTile().
 */
class Task {
   protected:
    /**
     * Number of cells in the X direction.
     */
    const size_t mSizeX;
    /**
     * Number of cells in the Y direction.
     */
    const size_t mSizeY;
    /**
     * Number of elements in a vector (cell). From 1-4.
     */
    const size_t mVectorSize;
    /**
     * Whether the task prefers the processData call to represent the work to be done as
     * one line rather than a rectangle. This would be the case for work that don't involve
     * vertical neighbors, e.g. blend or histogram. A task would prefer this to minimize the
     * number of SIMD calls to make, i.e. have one call that covers all the rows.
     *
     * This setting will be used only when a tile covers the entire width of the data to be
     * processed.
     */
    const bool mPrefersDataAsOneRow;
    /**
     * Whether the processor we're working on supports SIMD operations.
     */
    bool mUsesSimd = false;

   private:
    /**
     * If not null, we'll process a subset of the whole 2D array. This specifies the restriction.
     */
    const struct Restriction* mRestriction;

    /**
     * We'll divide the work into rectangular tiles. See setTiling().
     */

    /**
     * Size of a tile in the X direction, as a number of cells.
     */
    size_t mCellsPerTileX = 0;
    /**
     * Size of a tile in the Y direction, as a number of cells.
     */
    size_t mCellsPerTileY = 0;
    /**
     * Number of tiles per row of the restricted area we're working on.
     */
    size_t mTilesPerRow = 0;
    /**
     * Number of tiles per column of the restricted area we're working on.
     */
    size_t mTilesPerColumn = 0;

   public:
    /**
     * Construct a task.
     *
     * sizeX and sizeY should be greater than 0. vectorSize should be between 1 and 4.
     * The restriction should outlive this instance. The Toolkit validates the
     * arguments so we won't do that again here.
     */
    Task(size_t sizeX, size_t sizeY, size_t vectorSize, bool prefersDataAsOneRow,
         const Restriction* restriction)
        : mSizeX{sizeX},
          mSizeY{sizeY},
          mVectorSize{vectorSize},
          mPrefersDataAsOneRow{prefersDataAsOneRow},
          mRestriction{restriction} {}
    virtual ~Task() {}

    void setUsesSimd(bool uses) { mUsesSimd = uses; }

    /**
     * Divide the work into a number of tiles that can be distributed to the various threads.
     * A tile will be a rectangular region. To be robust, we'll want to handle regular cases
     * like 400x300 but also unusual ones like 1x120000, 120000x1, 1x1.
     *
     * We have a target size for the tiles, which corresponds roughly to how much data a thread
     * will want to process before checking for more work. If the target is set too low, we'll spend
     * more time in synchronization. If it's too large, some cores may not be used as efficiently.
     *
     * This method returns the number of tiles.
     *
     * @param targetTileSizeInBytes Target size. Values less than 1000 will be treated as 1000.
     */
    int setTiling(unsigned int targetTileSizeInBytes);

    /**
     * This is called by the TaskProcessor to instruct the task to process a tile.
     *
     * @param threadIndex The index of the thread that's processing the tile.
     * @param tileIndex The index of the tile to process.
     */
    void processTile(unsigned int threadIndex, size_t tileIndex);

   private:
    /**
     * Call to the derived class to process the data bounded by the rectangle specified
     * by (startX, startY) and (endX, endY). The end values are EXCLUDED. This rectangle
     * will be contained with the restriction, if one is provided.
     */
    virtual void processData(int threadIndex, size_t startX, size_t startY, size_t endX,
                             size_t endY) = 0;
};

/**
 * There's one instance of the task processor for the Toolkit. This class owns the thread pool,
 * and dispatches the tiles of work to the threads.
 */
class TaskProcessor {
    /**
     * Does this processor support SIMD-like instructions?
     */
    const bool mUsesSimd;
    /**
     * The number of separate threads we'll spawn. It's one less than the number of threads that
     * do the work as the client thread that starts the work will also be used.
     */
    const unsigned int mNumberOfPoolThreads;
    /**
     * Ensures that only one task is done at a time.
     */
    std::mutex mTaskMutex;
    /**
     * Ensures consistent access to the shared queue state.
     */
    std::mutex mQueueMutex;
    /**
     * The thread pool workers.
     */
    std::vector<std::thread> mPoolThreads;
    /**
     * The task being processed, if any. We only do one task at a time. We could create a queue
     * of tasks but using a mTaskMutex is sufficient for now.
     */
    Task* mCurrentTask /*GUARDED_BY(mTaskMutex)*/ = nullptr;
    /**
     * Signals that the mPoolThreads should terminate.
     */
    bool mStopThreads /*GUARDED_BY(mQueueMutex)*/ = false;
    /**
     * Signaled when work is available or the mPoolThreads need to shut down. mStopThreads is used
     * to distinguish between the two.
     */
    std::condition_variable mWorkAvailableOrStop;
    /**
     * Signaled when the work for the task is finished.
     */
    std::condition_variable mWorkIsFinished;
    /**
     * A user task, e.g. a blend or a blur, is split into a number of tiles. When a thread starts
     * working on a new tile, it uses this count to identify which tile to work on. The tile
     * number is sufficient to determine the boundaries of the data to process.
     *
     * The number of tiles left to process.
     */
    int mTilesNotYetStarted /*GUARDED_BY(mQueueMutex)*/ = 0;
    /**
     * The number of tiles currently being processed. Must not be greater than
     * mNumberOfPoolThreads + 1.
     */
    int mTilesInProcess /*GUARDED_BY(mQueueMutex)*/ = 0;

    /**
     * Determines how we'll tile the work and signals the thread pool of available work.
     *
     * @param task The task to be performed.
     */
    void startWork(Task* task) /*REQUIRES(mTaskMutex)*/;

    /**
     * Tells the thread to start processing work off the queue.
     *
     * The flag is used for prevent the main thread from blocking forever if the work is
     * so trivial that the worker threads complete the work before the main thread calls this
     * method.
     *
     * @param threadIndex The index number (0..mNumberOfPoolThreads) this thread will referred by.
     * @param returnWhenNoWork If there's no work, return immediately.
     */
    void processTilesOfWork(int threadIndex, bool returnWhenNoWork);

    /**
     * Wait for the pool workers to complete the work on the current task.
     */
    void waitForPoolWorkersToComplete();

   public:
    /**
     * Create the processor.
     *
     * @param numThreads The total number of threads to use. If 0, we'll decided based on system
     * properties.
     */
    explicit TaskProcessor(unsigned int numThreads = 0);

    ~TaskProcessor();

    /**
     * Do the specified task. Returns only after the task has been completed.
     */
    void doTask(Task* task);

    /**
     * Some Tasks need to allocate temporary storage for each worker thread.
     * This provides the number of threads.
     */
    unsigned int getNumberOfThreads() const { return mNumberOfPoolThreads + 1; }
};

}  // namespace renderscript

#endif  // ANDROID_RENDERSCRIPT_TOOLKIT_TASKPROCESSOR_H
