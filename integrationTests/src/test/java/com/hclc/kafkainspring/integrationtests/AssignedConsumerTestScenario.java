package com.hclc.kafkainspring.integrationtests;

import com.hclc.kafkainspring.integrationtests.consumers.ConsumedRecord;
import com.hclc.kafkainspring.integrationtests.consumers.AssignedConsumer;
import com.hclc.kafkainspring.integrationtests.producer.ProducedRecord;
import com.hclc.kafkainspring.integrationtests.producer.Producer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hclc.kafkainspring.integrationtests.TypeOfFailure.NONE;
import static org.assertj.core.api.Assertions.assertThat;

public class AssignedConsumerTestScenario {

    private Producer producer;
    private AssignedConsumer consumer;

    @BeforeEach
    void before() {
        producer = new Producer();
        consumer = new AssignedConsumer();
        consumer.drain();
    }

    @Test
    void producerProducesAssignedConsumerConsumes() {
        ProducedRecord produced = producer.produce("assignedConsumerTopic", NONE);
        ConsumedRecord consumed = consumer.readConsumed();

        assertThat(consumed.getFailableMessage()).isEqualTo(produced.getFailableMessage());
        assertThat(consumed.getConsumerRecord()).isEqualToComparingOnlyGivenFields(produced.getSendResult().getRecordMetadata(),
                "offset", "partition", "serializedKeySize", "serializedValueSize", "timestamp", "topic");
    }
}