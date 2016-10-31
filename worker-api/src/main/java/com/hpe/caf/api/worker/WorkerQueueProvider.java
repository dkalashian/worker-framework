package com.hpe.caf.api.worker;


import com.hpe.caf.api.ConfigurationSource;


/**
 * Boilerplate for retrieving a WorkerQueue implementation.
 * @since 9.0
 */
public interface WorkerQueueProvider
{
    /**
     * Create a new WorkerQueue instance.
     * @param configurationSource used for configuring the WorkerQueue
     * @param maxTasks the maximum number of tasks the worker can perform at once
     * @return a new WorkerQueue instance
     * @throws QueueException if a WorkerQueue could not be created
     */
    ManagedWorkerQueue getWorkerQueue(ConfigurationSource configurationSource, int maxTasks)
        throws QueueException;
}