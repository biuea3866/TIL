package com.biuea.practice.kafka.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.kafka.test.context.EmbeddedKafka

@TestConfiguration
@EmbeddedKafka(
    partitions = 3,
    topics = ["orders", "inventory", "payments", "test-topic"],
    brokerProperties = [
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1"
    ]
)
class KafkaTestConfig {
}