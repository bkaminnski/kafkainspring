package com.hclc.kafkainspring.consumers.forwarding;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ComponentScan({
        "com.hclc.kafkainspring.monitoring",
        "com.hclc.kafkainspring.kafkalisteners"
})
public class ForwardingConsumerConfig {

}