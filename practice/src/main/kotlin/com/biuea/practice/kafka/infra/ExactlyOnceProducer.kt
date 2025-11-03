package com.biuea.practice.kafka.infra

import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class ExactlyOnceProducer(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val producer: KafkaProducer<String, String>

    init {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)

            // Exactly Once 설정 (멱등성)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            // 아래 설정들은 자동으로 적용됨:
            // acks=all
            // retries=Integer.MAX_VALUE
            // max.in.flight.requests.per.connection=5
        }

        producer = KafkaProducer(props)
    }

    fun send(topic: String, key: String, message: String): RecordMetadata {
        val record = ProducerRecord(topic, key, message)

        return try {
            val metadata = producer.send(record).get()
            logger.info("Message sent with idempotence: " +
                    "topic=$topic, partition=${metadata.partition()}, offset=${metadata.offset()}")
            metadata
        } catch (e: Exception) {
            logger.error("Failed to send idempotent message", e)
            throw e
        }
    }

    @PreDestroy
    fun close() {
        producer.close()
    }
}