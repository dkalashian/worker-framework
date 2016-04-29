package com.hpe.caf.worker.core;

import com.hpe.caf.api.worker.*;
import com.hpe.caf.naming.ServicePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Utility class for preparing a new Worker for a task and executing it.
 */
final class WorkerExecutor
{
    private final ServicePath servicePath;
    private final WorkerCallback callback;
    private final WorkerFactory factory;
    private final WorkerThreadPool threadPool;
    private static final Logger LOG = LoggerFactory.getLogger(WorkerExecutor.class);


    /**
     * Create a WorkerWrapperFactory. The constructor parameters are the fixed properties of every WorkerWrapper on
     * this micro-service worker.
     * @param path the service path of this worker service
     * @param callback the callback the wrappers use when a task completes
     * @param workerFactory the origin of the Worker objects themselves
     */
    public WorkerExecutor
    (
        final ServicePath path,
        final WorkerCallback callback,
        final WorkerFactory workerFactory,
        final WorkerThreadPool pool
    )
    {
        this.servicePath = Objects.requireNonNull(path);
        this.callback = Objects.requireNonNull(callback);
        this.factory = Objects.requireNonNull(workerFactory);
        this.threadPool = Objects.requireNonNull(pool);
    }


    /**
     * Get a new Worker for a TaskMessage and hand the Worker off to a thread pool to execute, wrapped in a WorkerWrapper.
     * If the WorkerFactory indicates the task-specific data is invalid, a response is immediately returned indicating this.
     * @param tm the task message
     * @param queueMessageId the reference to the message this task arrived on
     * @throws TaskRejectedException if the WorkerFactory indicates the task cannot be handled at this time
     */
    public void executeTask(final TaskMessage tm, final String queueMessageId)
        throws TaskRejectedException
    {
        final WorkerTaskImpl workerTask = createWorkerTask(queueMessageId, tm);

        try {
            WorkerWrapper wrapper = new WorkerWrapper(workerTask);
            threadPool.submit(wrapper, queueMessageId);
        } catch (InvalidTaskException e) {
            workerTask.setResponse(e);
        }
    }


    /**
     * Decide whether the message is to be forwarded or discarded.
     * @param tm the task message
     * @param queueMessageId the reference to the message this task arrived on
     * @param headers the map of key/value paired headers to be stamped on the message
     */
    public void forwardTask(final TaskMessage tm, final String queueMessageId, Map<String, Object> headers) throws TaskRejectedException {
        //Check whether this worker application can evaluate messages for forwarding.
        if (factory instanceof TaskMessageForwardingEvaluator) {
            ((TaskMessageForwardingEvaluator) factory).determineForwardingAction(tm, queueMessageId, headers, callback);
        } else {
            //Messages are forwarded by default.
            callback.forward(queueMessageId, tm.getTo(), tm, headers);
        }
    }


    /**
     * Discard the supplied task message.
     * @param tm the task message to be discarded
     * @param queueMessageId the reference to the message this task arrived on
     */
    public void discardTask(final TaskMessage tm, final String queueMessageId) throws TaskRejectedException {
        LOG.warn("Discarding task {} (message id: {})", tm.getTaskId(), queueMessageId);
        callback.discard(queueMessageId);
    }


    /**
     * Creates a WorkerTask for the specified message
     */
    private WorkerTaskImpl createWorkerTask(final String messageId, final TaskMessage taskMessage)
    {
        return new WorkerTaskImpl(servicePath, callback, factory, messageId, taskMessage);
    }
}
