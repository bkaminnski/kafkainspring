# Kafka in Spring

This project presents how to use Kafka in Spring. Each case is covered by a separate integration test (see [integrationTests](https://github.com/bkaminnski/kafkainspring/tree/master/integrationTests) project).

## Assigned Consumer

Uses `assign()` to subscribe to individual partition. Does not require group id for Kafka consumer. Such consumer also does not need to care about rebalancing (e.g. new partition added to a topic). Declares error handler that handles exceptions thrown during consumption and simple retry policy (up to 3 retries) with exponential backoff policy.

### Stateless retry vs stateful retry

Each case is covered by two variants: stateless retry and stateful retry; you can read more about stateless and stateful retries [here](https://docs.spring.io/spring-kafka/docs/2.1.10.RELEASE/reference/html/_reference.html#stateful-retry):

- successful processing of consumed message (no exception)
- exception thrown on first try having max 3 tries policy with exponential back off policy - successful processing
- exception thrown on each of 3 retries - eventually handled by error handler (after 3rd retry)
- exception thrown on each of 3 retries programmed for 4 consecutive errors (not making to the 4th one) - eventually handled by error handler (after 3rd retry) 

**Watch out!** Interval between retries for stateless retry is different from interval in stateful retry. 
- Stateless retry is handled completely on Spring side (record is consumed only once; retries do not cause additional polling of the partition). This is why exceeding `max.poll.interval.ms` is at risk. However in this case, **interval between retries relies solely on retry policy**. In our case, with exponential back-off policy starting at 100 and limited to 400 milliseconds, these values are: 100, 200, 400, 400, ...
-  Stateful retry on the other hand relies on restoring offset and polling a message another time. This is why exceeding `max.poll.interval.ms` is *not* at risk anymore. There is however another component that adds to the time between retries: `fetch.max.wait.ms`. Following [official Kafka documentation](http://kafka.apache.org/090/documentation.html), this is 
    > The maximum amount of time the server will block before answering the fetch request if there isn't sufficient data to immediately satisfy the requirement given by fetch.min.bytes.

    In our case this is set to 300 milliseconds (in [AssignedConsumerStatefulRetryConfig class](https://github.com/bkaminnski/kafkainspring/blob/master/consumers/assigned/src/main/java/com/hclc/kafkainspring/consumers/assign/AssignedConsumerStatefulRetryConfig.java)), and is represented by `additionalIntervalMillisForPolling` component in [AssignedConsumerTestScenario class](https://github.com/bkaminnski/kafkainspring/blob/master/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/AssignedConsumerTestScenario.java). Therefore for stateful retry, interval between retries follows a shifted pattern: 400, 500, 700, 700, ...

### Pause consumption on error

Let's say we have a case that each and every message has to be handled successfully, and when it is not, messages consumption needs to be paused and manual intervention is required (in such case, a pager alert might be generated - this is however out of the scope of this exercise). This scenario is covered by [pauseConsumptionAfterHandlingFailureAndConsumeAfterResumed integration test](https://github.com/bkaminnski/kafkainspring/blob/master/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/AssignedConsumerTestScenario.java#L106).

According to [this part of official Spring Kafka documentation](https://docs.spring.io/spring-kafka/docs/2.1.10.RELEASE/reference/html/_reference.html#pause-resume):

> Version 2.1.3 added pause() and resume() methods to listener containers. Previously, you could pause a consumer within a ConsumerAwareMessageListener and resume it by listening for ListenerContainerIdleEvent s, which provide access to the Consumer object. While you could pause a consumer in an idle container via an event listener, in some cases this was not thread-safe since there is no guarantee that the event listener is invoked on the consumer thread. **To safely pause/resume consumers, you should use the methods on the listener containers.**

## Subscribed Consumer

Uses `subscribe()` to subscribe to a topic. Requires group id for Kafka consumer.

### Simple @KafkaListener

Presents a simple consumer registered with `@KafkaListener` consuming messages from `subscribedConsumerSimpleTopic`, covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/3e86ea9bbff812fdfd1d74157782bd7e3a7ac1f2/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/SubscribedConsumerTestScenario.java#L30).

### Rebalancing

- A topic (`subscribedConsumerRebalancedTopic`) with 2 partitions and 2 consumers (consumer 0 for partition 0, consumer 1 for partition 1).
- Send a message (A) to partition 0, simulate long processing time.
- Expect rebalancing: partition 0 reassigned from consumer 0 to consumer 1.
- Problematic message (A) processed by consumer 1 (this time with no artificial delay).
- Send another message (B) to partition 0 (with no artificial delay).
- Expect to process message (B) by consumer 1.
- Expect to eventually process message (A) by consumer 0 - observe side effect and `CommitFailedException` handled by error handler.
- Wait until consumer 0 is unblocked.
- Expect rebalancing back to original state.
- The scenario is covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/3e86ea9bbff812fdfd1d74157782bd7e3a7ac1f2/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/SubscribedConsumerTestScenario.java#L64).  
 
**Watch out!** for potential side effects caused by prolonged processing of **message (A) in consumer 0**! The real reason for extended processing time might be of external nature, like long time connecting to an overloaded database, long GC pause, network issues. Eventually, when the real reason is gone, that forgotten message might have actually executed some code leaving unwanted side effects.

## Forwarding consumer/Prosumer

Consumes from one topic (`forwardingConsumerFirstLineTopic`), produces to another (`forwardingConsumerSecondLineTopic`) in the same transaction handled by  `transactionManager` bean ([here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerProducerConfig.java#L38)).

### Simple forwarding (no exception)

All operations are successful, covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingConsumerTestScenario.java#L22).

### Exception after forward

These two variants of *Exception after forward* demonstrate that `KafkaTransactionManager` indeed prevents from publishing a message in case of an exception (rollback). To make the point clear, `ForwardingConsumerFirstLine` first sends a message to the `forwardingConsumerSecondLineTopic` ([here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerFirstLine.java#L40)) and only then throws an exception (if requested by a producer, [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerFirstLine.java#L41)).

Following [official Spring Kafka documentation](https://docs.spring.io/spring-kafka/docs/2.1.10.RELEASE/reference/html/_reference.html#_kafkatransactionmanager):

> You can use the `KafkaTransactionManager` with normal Spring transaction support (`@Transactional`, `TransactionTemplate` etc). If a transaction is active, any `KafkaTemplate` operations performed within the scope of the transaction will use the transaction’s `Producer`. The manager will commit or rollback the transaction depending on success or failure. The `KafkaTemplate` must be configured to use the same `ProducerFactory` as the transaction manager.

- First variant is covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingConsumerTestScenario.java#L32):
  - exception is thrown only on first try,
  - not reaching 3 tries limit (configured [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerConsumerConfig.java#L50)), 
  - producer error handler ([ProducerListener](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerProducerListener.java#L20)) is invoked once,
  - consumer error handler is not invoked, 
  - a message is successfully forwarded to the `forwardingConsumerSecondLineTopic` the second time message it is consumed by `ForwardingConsumerFirstLine`.
- Second variant is covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingConsumerTestScenario.java#L44):
  - exception is thrown on each of three tries, eventually reaching three tries limit,
  - producer error handler ([ProducerListener](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerProducerListener.java#L20)) is invoked on each try,
  - consumer error handler is invoked, 
  - a message is not forwarded to the `forwardingConsumerSecondLineTopic`.
  
## Forwarding consumer/Prosumer with DB transaction

Consumes from one topic (`forwardingWithDbConsumerFirstLineTopic`), produces to another (`forwardingWithDbConsumerSecondLineTopic`) also writing to Postgres database in the same transaction.

Uses `ChainedTransactionManager` (in particular `ChainedKafkaTransactionManager`). Following [ChainedTransactionManager javadoc](https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/transaction/ChainedTransactionManager.html):

> The configured instances will start transactions in the order given and commit/rollback in reverse order, which means **the `PlatformTransactionManager` most likely to break the transaction should be the *last* in the list** configured. A `PlatformTransactionManager` throwing an exception during commit will automatically cause the remaining transaction managers to roll back instead of committing.

The value that `ChainedKafkaTransactionManager` brings is that it forces exactly one `KafkaTransactionManager` in the chain and takes care of sending offsets. Following [official Spring Kafka documentation](https://docs.spring.io/spring-kafka/docs/2.1.10.RELEASE/reference/html/_reference.html#chained-transaction-manager):

> The `ChainedKafkaTransactionManager` was introduced in version 2.1.3. This is a subclass of `ChainedTransactionManager` that can have exactly one `KafkaTransactionManager`. Since it is a `KafkaAwareTransactionManager`, the container can send the offsets to the transaction in the same way as when the container is configured with a simple `KafkaTransactionManager`. This provides another mechanism for synchronizing transactions without having to send the offsets to the transaction in the listener code. **Chain your transaction managers in the desired order and provide the `ChainedTransactionManager` in the `ContainerProperties`**.

This is one of the possible implementations of **Best Efforts 1PC pattern**, described in [Distributed transactions in Spring, with and without XA](https://www.javaworld.com/article/2077963/open-source-tools/distributed-transactions-in-spring--with-and-without-xa.html?page=2) by David Syer.

### Simple forwarding (no exception)

All operations are successful, covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/d4650d39853434629a5c27543f3d7c17abef36e2/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingWithDbConsumerTestScenario.java#L24).

### Exception after forward

These two variants of *Exception after forward* demonstrate that `KafkaTransactionManager` indeed prevents from publishing a message in case of an exception (rollback). To make the point clear, `ForwardingWithDbConsumerFirstLine` first writes to database ([here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwardingwithdb/ForwardingWithDbConsumerFirstLine.java#L48)) and sends a message to the `forwardingWithDbConsumerSecondLineTopic` ([here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwardingwithdb/ForwardingWithDbConsumerFirstLine.java#L49)) and only then throws an exception (if requested by a producer, [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwardingwithdb/ForwardingWithDbConsumerFirstLine.java#L50)).

- First variant is covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingWithDbConsumerTestScenario.java#L40):
  - exception is thrown only on first try,
  - not reaching 3 tries limit (configured [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwardingwithdb/ForwardingWithDbConsumerConsumerConfig.java#L50)), 
  - producer error handler ([ProducerListener](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwarding/ForwardingConsumerProducerListener.java#L20)) is invoked once,
  - database transaction is rolled back,
  - consumer error handler is not invoked, 
  - a message is successfully forwarded to the `forwardingWithDbConsumerSecondLineTopic` the second time message it is consumed by `ForwardingWithDbConsumerFirstLine`,
  - a message is saved to the database on second try (this can be proved by observing one database identifier generated from a sequence being skipped, [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingWithDbConsumerTestScenario.java#L53)).
- Second variant is covered by [this test](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingWithDbConsumerTestScenario.java#L65):
  - exception is thrown on each of three tries, eventually reaching three tries limit,
  - producer error handler ([ProducerListener](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/consumers/forwarding/src/main/java/com/hclc/kafkainspring/consumers/forwardingwithdb/ForwardingWithDbConsumerProducerListener.java#L20)) is invoked on each try,
  - consumer error handler is invoked, 
  - a message is not forwarded to the `forwardingWithDbConsumerSecondLineTopic`,
  - no message is written to the database (verified [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingWithDbConsumerTestScenario.java#L80))
  - database transaction was rolled back 3 times (verified [here](https://github.com/bkaminnski/kafkainspring/blob/08485798db98fa9c6a000600d238d120b81e79a5/integrationTests/src/test/java/com/hclc/kafkainspring/integrationtests/ForwardingWithDbConsumerTestScenario.java#L83)).