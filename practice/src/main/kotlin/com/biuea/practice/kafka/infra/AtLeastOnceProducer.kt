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
import java.util.Properties
import java.util.concurrent.Future

@Component
class AtLeastOnceProducer(
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

            // At Least Once 설정
            put(ProducerConfig.ACKS_CONFIG, "1")  // 리더 브로커만 확인
            put(ProducerConfig.RETRIES_CONFIG, 3)  // 최대 3번 재시도
            put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5)
            put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000)
            put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000)
        }

        producer = KafkaProducer(props)
    }

    // 동기 방식
    fun sendSync(topic: String, key: String, message: String): RecordMetadata {
        val record = ProducerRecord(topic, key, message)

        return try {
            val metadata = producer.send(record).get()  // 동기 대기
            logger.info("Message sent successfully (at-least-once): " +
                    "topic=$topic, partition=${metadata.partition()}, offset=${metadata.offset()}")
            metadata
        } catch (e: Exception) {
            logger.error("Failed to send message (at-least-once)", e)
            throw e
        }
    }

    // 비동기 방식 (콜백)
    fun sendAsync(topic: String, key: String, message: String): Future<RecordMetadata> {
        val record = ProducerRecord(topic, key, message)

        return producer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error("Failed to send message asynchronously", exception)
            } else {
                logger.info("Message sent asynchronously: " +
                        "topic=$topic, partition=${metadata.partition()}, offset=${metadata.offset()}")
            }
        }
    }

    @PreDestroy
    fun close() {
        producer.close()
    }
}