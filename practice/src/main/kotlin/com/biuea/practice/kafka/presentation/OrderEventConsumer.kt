package com.biuea.practice.kafka.presentation

import com.biuea.practice.kafka.domain.OrderEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class OrderEventConsumer(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // 멱등성을 위한 처리된 메시지 추적 (실제로는 Redis나 DB 사용)
    private val processedMessages = ConcurrentHashMap.newKeySet<String>()

    @KafkaListener(
        topics = ["orders"],
        groupId = "order-consumer-group",
        properties = [
            "isolation.level=read_committed",  // COMMITTED 메시지만 읽기
            "enable.auto.commit=false"  // 수동 커밋
        ]
    )
    fun consumeOrderEvents(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        val messageId = "$topic-$partition-$offset"

        // 중복 처리 방지 (멱등성)
        if (processedMessages.contains(messageId)) {
            logger.warn("Duplicate message detected: $messageId")
            acknowledgment.acknowledge()
            return
        }

        try {
            when (val event = parseOrderEvent(message)) {
                is OrderEvent.OrderCreated -> handleOrderCreated(event)
                is OrderEvent.PaymentCompleted -> handlePaymentCompleted(event)
                is OrderEvent.OrderCompleted -> handleOrderCompleted(event)
                is OrderEvent.OrderFailed -> handleOrderFailed(event)
                else -> logger.warn("Unhandled event type: ${event::class.simpleName}")
            }

            // 처리 완료 후 커밋
            processedMessages.add(messageId)
            acknowledgment.acknowledge()

            logger.info("Message processed successfully: $messageId")

        } catch (e: Exception) {
            logger.error("Error processing message: $messageId", e)
            // 재처리 또는 DLQ로 전송
        }
    }

    @KafkaListener(
        topics = ["inventory"],
        groupId = "inventory-consumer-group",
        properties = [
            "isolation.level=read_committed",
            "enable.auto.commit=false"
        ]
    )
    fun consumeInventoryEvents(
        @Payload message: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            val event = objectMapper.readValue(message, OrderEvent.InventoryReserved::class.java)
            handleInventoryReserved(event)
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            logger.error("Error processing inventory event", e)
        }
    }

    @KafkaListener(
        topics = ["payments"],
        groupId = "payment-consumer-group",
        properties = [
            "isolation.level=read_committed",
            "enable.auto.commit=false"
        ]
    )
    fun consumePaymentEvents(
        @Payload message: String,
        acknowledgment: Acknowledgment
    ) {
        try {
            val event = objectMapper.readValue(message, OrderEvent.PaymentRequested::class.java)
            handlePaymentRequested(event)
            acknowledgment.acknowledge()

        } catch (e: Exception) {
            logger.error("Error processing payment event", e)
        }
    }

    private fun parseOrderEvent(json: String): OrderEvent {
        val node = objectMapper.readTree(json)

        return when {
            json.contains("OrderCreated") || node.has("customerId") ->
                objectMapper.readValue(json, OrderEvent.OrderCreated::class.java)
            json.contains("PaymentCompleted") || node.has("transactionId") ->
                objectMapper.readValue(json, OrderEvent.PaymentCompleted::class.java)
            json.contains("OrderCompleted") ->
                objectMapper.readValue(json, OrderEvent.OrderCompleted::class.java)
            json.contains("OrderFailed") || node.has("reason") ->
                objectMapper.readValue(json, OrderEvent.OrderFailed::class.java)
            else -> throw IllegalArgumentException("Unknown event type")
        }
    }

    private fun handleOrderCreated(event: OrderEvent.OrderCreated) {
        logger.info("Processing order created: ${event.orderId}")
        // 비즈니스 로직: 주문 저장, 알림 전송 등
    }

    private fun handleInventoryReserved(event: OrderEvent.InventoryReserved) {
        logger.info("Processing inventory reserved: ${event.orderId}")
        // 비즈니스 로직: 재고 차감
    }

    private fun handlePaymentRequested(event: OrderEvent.PaymentRequested) {
        logger.info("Processing payment requested: ${event.orderId}")
        // 비즈니스 로직: 결제 처리
    }

    private fun handlePaymentCompleted(event: OrderEvent.PaymentCompleted) {
        logger.info("Processing payment completed: ${event.orderId}")
        // 비즈니스 로직: 주문 상태 업데이트
    }

    private fun handleOrderCompleted(event: OrderEvent.OrderCompleted) {
        logger.info("Processing order completed: ${event.orderId}")
        // 비즈니스 로직: 최종 처리
    }

    private fun handleOrderFailed(event: OrderEvent.OrderFailed) {
        logger.error("Order failed: ${event.orderId}, reason: ${event.reason}")
        // 비즈니스 로직: 보상 트랜잭션, 알림 등
    }
}