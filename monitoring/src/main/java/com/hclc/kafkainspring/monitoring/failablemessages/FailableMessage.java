package com.hclc.kafkainspring.monitoring.failablemessages;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class FailableMessage {

    private String uniqueId;
    private TypeOfFailure typeOfFailure;
    private int failuresCount;

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public TypeOfFailure getTypeOfFailure() {
        return typeOfFailure;
    }

    public Optional<TypeOfFailure> getTypeOfFailureIfMatching(TypeOfFailure toMatch) {
        return ofNullable(typeOfFailure == toMatch ? typeOfFailure : null);
    }

    public void setTypeOfFailure(TypeOfFailure typeOfFailure) {
        this.typeOfFailure = typeOfFailure;
    }

    public int getFailuresCount() {
        return failuresCount;
    }

    public void setFailuresCount(int failuresCount) {
        this.failuresCount = failuresCount;
    }

    @Override
    public String toString() {
        return "FailableMessage{" +
                "uniqueId='" + uniqueId + '\'' +
                ", typeOfFailure=" + typeOfFailure +
                ", failuresCount=" + failuresCount +
                '}';
    }
}
