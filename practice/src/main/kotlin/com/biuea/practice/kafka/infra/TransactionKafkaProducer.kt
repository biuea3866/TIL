package com.biuea.practice.kafka.infra

import com.biuea.practice.kafka.domain.OrderEvent
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class TransactionKafkaProducer(
    @Value("\${spring.kafka.bootstrap-servers}")
    private val bootstrapServers: String,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private lateinit var producer: KafkaProducer<String, String>

    companion object {
        const val ORDERS_TOPIC = "orders"
        const val INVENTORY_TOPIC = "inventory"
        const val PAYMENTS_TOPIC = "payments"
    }

    @PostConstruct
    fun init() {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)

            // 트랜잭션 설정
            put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-producer-${UUID.randomUUID()}")
            // enable.idempotence=true, acks=all 자동 설정됨
            put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, 60000)
        }

        producer = KafkaProducer(props)
        producer.initTransactions()
        logger.info("Transactional producer initialized")
    }

    fun sendOrderTransaction(orderCreated: OrderEvent.OrderCreated) {
        try {
            producer.beginTransaction()
            logger.info("Transaction started for order: ${orderCreated.orderId}")

            // 1. 주문 생성 이벤트
            sendInTransaction(
                ORDERS_TOPIC,
                orderCreated.orderId,
                orderCreated
            )

            // 2. 재고 예약 이벤트
            val inventoryEvent = OrderEvent.InventoryReserved(
                orderId = orderCreated.orderId,
                productId = orderCreated.productId,
                quantity = orderCreated.quantity
            )
            sendInTransaction(
                INVENTORY_TOPIC,
                orderCreated.orderId,
                inventoryEvent
            )

            // 3. 결제 요청 이벤트
            val paymentEvent = OrderEvent.PaymentRequested(
                orderId = orderCreated.orderId,
                amount = orderCreated.amount,
                customerId = orderCreated.customerId
            )
            sendInTransaction(
                PAYMENTS_TOPIC,
                orderCreated.orderId,
                paymentEvent
            )

            producer.commitTransaction()
            logger.info("Transaction committed for order: ${orderCreated.orderId}")

        } catch (e: Exception) {
            producer.abortTransaction()
            logger.error("Transaction aborted for order: ${orderCreated.orderId}", e)
            throw e
        }
    }

    private fun sendInTransaction(topic: String, key: String, event: OrderEvent) {
        val json = objectMapper.writeValueAsString(event)
        val record = ProducerRecord(topic, key, json)

        producer.send(record) { metadata, exception ->
            if (exception != null) {
                logger.error("Failed to send message in transaction: topic=$topic, key=$key", exception)
            } else {
                logger.debug("Message sent in transaction: topic=$topic, partition=${metadata.partition()}, offset=${metadata.offset()}")
            }
        }
    }

    fun sendPaymentCompleted(orderId: String, transactionId: String) {
        try {
            producer.beginTransaction()

            val event = OrderEvent.PaymentCompleted(
                orderId = orderId,
                transactionId = transactionId
            )

            sendInTransaction(ORDERS_TOPIC, orderId, event)

            producer.commitTransaction()
            logger.info("Payment completed event sent for order: $orderId")

        } catch (e: Exception) {
            producer.abortTransaction()
            logger.error("Failed to send payment completed event", e)
            throw e
        }
    }

    @PreDestroy
    fun close() {
        producer.close()
        logger.info("Transactional producer closed")
    }
}