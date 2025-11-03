package com.biuea.practice.kafka.domain

import java.math.BigDecimal
import java.time.LocalDateTime

sealed class OrderEvent {
    abstract val orderId: String
    abstract val timestamp: LocalDateTime

    data class OrderCreated(
        override val orderId: String,
        val customerId: String,
        val productId: String,
        val quantity: Int,
        val amount: BigDecimal,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class InventoryReserved(
        override val orderId: String,
        val productId: String,
        val quantity: Int,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class PaymentRequested(
        override val orderId: String,
        val amount: BigDecimal,
        val customerId: String,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class PaymentCompleted(
        override val orderId: String,
        val transactionId: String,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class OrderCompleted(
        override val orderId: String,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()

    data class OrderFailed(
        override val orderId: String,
        val reason: String,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : OrderEvent()
}