package com.hclc.kafkainspring.consumers.assign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hclc.kafkainspring.monitoring.consumed.ConsumedRecord;
import com.hclc.kafkainspring.monitoring.errorhandled.ErrorHandledRecord;
import com.hclc.kafkainspring.monitoring.errorhandled.ErrorsCountTracker;
import com.hclc.kafkainspring.monitoring.failablemessages.FailableMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.hclc.kafkainspring.monitoring.MonotonicTimeProvider.monotonicNowInNano;
import static com.hclc.kafkainspring.monitoring.failablemessages.TypeOfFailure.EXCEPTION_AFTER_CONSUMED;
import static java.lang.Thread.currentThread;

@Component
public class AssignedConsumerStatelessRetry {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ErrorsCountTracker errorsCountTracker;

    public void consume(ConsumerRecord<String, String> record) {
        long consumedAtMonotonicNano = monotonicNowInNano();
        try {
            FailableMessage failableMessage = new ObjectMapper().readValue(record.value(), FailableMessage.class);
            eventPublisher.publishEvent(new ConsumedRecord<>(consumedAtMonotonicNano, record, failableMessage, currentThread().getName()));
            failableMessage
                    .getTypeOfFailureIfMatching(EXCEPTION_AFTER_CONSUMED)
                    .ifPresent(f -> f.performFailureAction(errorsCountTracker, record, failableMessage.getFailuresCount()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleError(Exception exception, ConsumerRecord<?, ?> record) {
        long errorHandledAtMonotonicNano = monotonicNowInNano();
        errorsCountTracker.remove(record);
        eventPublisher.publishEvent(new ErrorHandledRecord<>(errorHandledAtMonotonicNano, record, exception, currentThread().getName()));
    }
}
