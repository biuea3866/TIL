package com.biuea.practice.kafka.domain

import java.math.BigDecimal
import java.time.LocalDateTime

data class Order(
    val orderId: String,
    val customerId: String,
    val productId: String,
    val quantity: Int,
    val amount: BigDecimal,
    val status: OrderStatus,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAYMENT_COMPLETED,
    INVENTORY_RESERVED,
    COMPLETED,
    FAILED,
    CANCELLED
}