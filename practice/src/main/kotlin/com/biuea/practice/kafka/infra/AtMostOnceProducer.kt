package com.biuea.practice.kafka.infra

import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Properties

@Component
class AtMostOnceProducer(
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

            // At Most Once 설정
            put(ProducerConfig.ACKS_CONFIG, "0")  // 응답 기다리지 않음
            put(ProducerConfig.RETRIES_CONFIG, 0)  // 재시도 없음
            put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy")  // 압축으로 성능 향상
        }

        producer = KafkaProducer(props)
    }

    fun send(topic: String, key: String, message: String) {
        try {
            val record = ProducerRecord(topic, key, message)

            // Fire-and-Forget: 결과를 기다리지 않음
            producer.send(record)

            logger.debug("Message sent (at-most-once): topic=$topic, key=$key")
        } catch (e: Exception) {
            logger.error("Failed to send message (at-most-once)", e)
            // 예외가 발생해도 재시도하지 않음 (메시지 손실 가능)
        }
    }

    @PreDestroy
    fun close() {
        producer.close()
    }
}