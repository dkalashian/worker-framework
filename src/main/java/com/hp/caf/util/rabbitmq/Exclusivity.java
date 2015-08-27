package com.hp.caf.util.rabbitmq;


/**
 * Possible states of exclusivity for a RabbitMQ queue.
 */
public enum Exclusivity
{
    /**
     * The queue is exclusive to the channel consumer.
     */
    EXCLUSIVE,
    /**
     * The queue can be used by any channel consumer.
     */
    NON_EXCLUSIVE
}