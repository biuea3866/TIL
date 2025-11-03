package com.biuea.practice.kafka.domain

import com.biuea.practice.kafka.infra.TransactionKafkaProducer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class OrderService(
    private val transactionProducer: TransactionKafkaProducer
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun createOrder(
        customerId: String,
        productId: String,
        quantity: Int,
        amount: BigDecimal
    ): Order {
        val orderId = UUID.randomUUID().toString()

        val orderCreatedEvent = OrderEvent.OrderCreated(
            orderId = orderId,
            customerId = customerId,
            productId = productId,
            quantity = quantity,
            amount = amount
        )

        try {
            // 트랜잭션으로 여러 토픽에 이벤트 발행
            transactionProducer.sendOrderTransaction(orderCreatedEvent)

            logger.info("Order created successfully: $orderId")

            return Order(
                orderId = orderId,
                customerId = customerId,
                productId = productId,
                quantity = quantity,
                amount = amount,
                status = OrderStatus.CREATED
            )

        } catch (e: Exception) {
            logger.error("Failed to create order", e)
            throw IllegalStateException("Order creation failed", e)
        }
    }

    fun completePayment(orderId: String) {
        val transactionId = UUID.randomUUID().toString()

        try {
            transactionProducer.sendPaymentCompleted(orderId, transactionId)
            logger.info("Payment completed for order: $orderId")

        } catch (e: Exception) {
            logger.error("Failed to complete payment", e)
            throw e
        }
    }
}