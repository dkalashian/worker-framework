package com.hpe.caf.worker.testing;

import com.hpe.caf.api.CodecException;
import com.hpe.caf.api.worker.TaskMessage;
import com.hpe.caf.util.rabbitmq.DefaultRabbitConsumer;
import com.hpe.caf.util.rabbitmq.Event;
import com.hpe.caf.util.rabbitmq.QueueConsumer;
import com.hpe.caf.util.rabbitmq.RabbitUtil;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

/**
 * Created by ploch on 08/11/2015.
 */
public class QueueManager implements Closeable {

    private final QueueServices queueServices;
    private final WorkerServices workerServices;
    private String consumerTag;

    private Channel pubChan;
    private Channel debugPubChan;
    private Channel conChan;
    private Channel debugConChan;

    private DefaultRabbitConsumer rabbitConsumer;
    private Connection connection;

    private String debugInputQueueName;
    private String debugOutputQueueName;
    private boolean debugEnabled;

    public QueueManager(QueueServices queueServices, WorkerServices workerServices, boolean debugEnabled) {

        this.queueServices = queueServices;
        this.workerServices = workerServices;
        this.debugInputQueueName = this.queueServices.getWorkerInputQueue() + "-debug";
        this.debugOutputQueueName = this.queueServices.getWorkerResultsQueue() + "-debug";
        this.debugEnabled = debugEnabled;
    }

    public Thread start(ResultHandler resultHandler) throws IOException {
        initialise();
        return startConsumer(resultHandler);
    }

    public void initialise() throws IOException{
        connection = queueServices.getConnection();
        pubChan = connection.createChannel();
        conChan = connection.createChannel();
        RabbitUtil.declareWorkerQueue(pubChan, queueServices.getWorkerInputQueue());
        RabbitUtil.declareWorkerQueue(conChan, queueServices.getWorkerResultsQueue());
        pubChan.queuePurge(queueServices.getWorkerInputQueue());
        conChan.queuePurge(queueServices.getWorkerResultsQueue());
    }
    public Thread startConsumer(ResultHandler resultHandler) throws IOException{
        BlockingQueue<Event<QueueConsumer>> conEvents = new LinkedBlockingQueue<>();
        SimpleQueueConsumerImpl queueConsumer = new SimpleQueueConsumerImpl(conEvents, conChan, resultHandler, workerServices.getCodec());
        rabbitConsumer = new DefaultRabbitConsumer(conEvents, queueConsumer);
        consumerTag = conChan.basicConsume(queueServices.getWorkerResultsQueue(), true, rabbitConsumer);
        Thread consumerThread = new Thread(rabbitConsumer);
        consumerThread.start();
        return consumerThread;
    }

    public void publish(TaskMessage message) throws CodecException, IOException {
        byte[] data = workerServices.getCodec().serialise(message);
        pubChan.basicPublish("", queueServices.getWorkerInputQueue(), MessageProperties.TEXT_PLAIN, data);
        if (debugEnabled) {
            debugPubChan.basicPublish("", debugInputQueueName, MessageProperties.TEXT_PLAIN, data);
        }
    }

    public void publishDebugOutput(TaskMessage message) throws CodecException, IOException {
        byte[] data = workerServices.getCodec().serialise(message);
        debugConChan.basicPublish("", debugOutputQueueName, MessageProperties.TEXT_PLAIN, data);
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public String getWorkerInputQueueName() {
        return queueServices.getWorkerInputQueue();
    }

    @Override
    public void close() throws IOException {
        if (consumerTag != null) {
            try {
                conChan.basicCancel(consumerTag);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (rabbitConsumer != null) {
            rabbitConsumer.shutdown();
        }
        if (conChan != null) {
            try {
                System.out.println("Closing queue connection");
                conChan.close();
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }
        if (pubChan != null) {
            try {
                pubChan.close();
            } catch (IOException | TimeoutException e) {
                e.printStackTrace();
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
    Need to modify close() method above so that it does not include the two if statements below.
    Leaving in these if statements for the moment because the method is still in use by tests using
    JUnit over TestNG
     */
    public void closeConsumer() throws IOException{
        System.out.println("Closing Consumer");
        if (consumerTag != null) {
            try {
                conChan.basicCancel(consumerTag);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (rabbitConsumer != null) {
            rabbitConsumer.shutdown();
        }
    }

}
