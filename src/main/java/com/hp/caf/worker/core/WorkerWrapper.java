package com.hp.caf.worker.core;


import com.codahale.metrics.Timer;
import com.hp.caf.api.ServicePath;
import com.hp.caf.api.worker.TaskMessage;
import com.hp.caf.api.worker.Worker;
import com.hp.caf.api.worker.WorkerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;


/**
 * A wrapper for a worker used internally by the worker core. It is a Runnable that
 * executes a worker but ensures that a result is always returned, even if the task
 * throws some unhandled exception. The wrapper will use a CompleteTaskCallback once
 * the worker has terminated.
 */
class WorkerWrapper implements Runnable
{
    private final Worker worker;
    private final CompleteTaskCallback callback;
    private final TaskMessage message;
    private final String queueMsgId;
    private final ServicePath servicePath;
    private static final Timer TIMER = new Timer();
    private static final Logger LOG = LoggerFactory.getLogger(WorkerWrapper.class);


    public WorkerWrapper(final TaskMessage message, final String queueMsgId, final Worker worker, final CompleteTaskCallback callback, final ServicePath path)
    {
        this.message = Objects.requireNonNull(message);
        this.queueMsgId = Objects.requireNonNull(queueMsgId);
        this.worker = Objects.requireNonNull(worker);
        this.callback = Objects.requireNonNull(callback);
        this.servicePath = Objects.requireNonNull(path);
    }


    /**
     * Trigger the worker to perform its necessary computations and handle the result. The wrapper will
     * ensure some manner of result returns, whether the worker succeeds, fails explicitly, or otherwise.
     */
    @Override
    public void run()
    {
        try {
            Timer.Context t = TIMER.time();
            WorkerResponse response = worker.doWork();
            t.stop();
            doCallback(response);
        } catch (Exception e) {
            LOG.warn("Worker threw unhandled exception", e);
            doCallback(worker.getGeneralFailureResult());
        }
    }


    /**
     * @return the timer used for keeping statistics on worker run times
     */
    public static Timer getTimer()
    {
        return TIMER;
    }


    /**
     * Generate a TaskMessage from a WorkerResponse and callback to the core notifying of completion.
     * @param response the response from the Worker
     */
    private void doCallback(final WorkerResponse response)
    {
        Map<String, byte[]> contextMap = message.getContext();
        if ( response.getContext() != null ) {
            contextMap.put(servicePath.getPath(), response.getContext());
        }
        TaskMessage tm = new TaskMessage(message.getTaskId(), response.getMessageType(), response.getApiVersion(),
                                         response.getData(), response.getTaskStatus(), contextMap);
        callback.complete(queueMsgId, response.getQueueReference(), tm);
    }

}
