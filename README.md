# Kafka in Spring

This project presents how to use Kafka in Spring. Each case is covered by a separate integration test (see [integrationTests](https://github.com/bkaminnski/kafkainspring/tree/master/integrationTests) project).

## Assigned Consumer

Uses `assign()` to subscribe to individual partition. Does not require group id for Kafka consumer. Such consumer also does not need to care about rebalancing (e.g. new partition added to a topic). Declares error handler that handles exceptions thrown during consumption.

Cases:

- successful processing of consumed message (no exception)
- exception thrown on first try having max 3 tries policy with exponential back off policy - successful processing
- exception thrown on each of 3 retries - eventually handled by error handler (after 3rd retry)
- exception thrown on each of 3 retries programmed for 4 consecutive errors (not making to the 4th one) - eventually handled by error handler (after 3rd retry) 

## Subscribed Consumer

Uses `subscribe()` to subscribe to a topic. Requires group id for Kafka consumer.

Cases:

- successful processing of consumed message (no exception)

## Prosumer

`[Coming soon...]`

Consumes from one topic, produces to another.

## Prosumer synced with DB transaction

`[Coming soon...]`

Consumes from one topic, produces to another synchronizing with database transaction.
