package com.hclc.kafkainspring.integrationtests.consumers;

public class ErrorHandledRecord {

    private long errorHandledAtMonotonicNano;
    private ConsumerRecord consumerRecord;
    private HandledException handledException;

    public long getErrorHandledAtMonotonicNano() {
        return errorHandledAtMonotonicNano;
    }

    public void setErrorHandledAtMonotonicNano(long errorHandledAtMonotonicNano) {
        this.errorHandledAtMonotonicNano = errorHandledAtMonotonicNano;
    }

    public ConsumerRecord getConsumerRecord() {
        return consumerRecord;
    }

    public void setConsumerRecord(ConsumerRecord consumerRecord) {
        this.consumerRecord = consumerRecord;
    }

    public HandledException getHandledException() {
        return handledException;
    }

    public void setHandledException(HandledException handledException) {
        this.handledException = handledException;
    }
}
