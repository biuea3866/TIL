package com.biuea.practice.kafka.domain

import com.biuea.practice.PracticeApplication
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal

@SpringBootTest(classes = [PracticeApplication::class])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
    partitions = 3,
    topics = ["orders", "inventory", "payments", "test-topic"],
    brokerProperties = [
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1"
    ]
)
class OrderServiceTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Test
    fun `주문 생성 - 성공`() {
        // given
        val customerId = "customer-123"
        val productId = "product-456"
        val quantity = 3
        val amount = BigDecimal("30000")

        // when
        val order = orderService.createOrder(customerId, productId, quantity, amount)

        // then
        order shouldNotBe null
        order.orderId shouldNotBe null
        order.customerId shouldBe customerId
        order.productId shouldBe productId
        order.quantity shouldBe quantity
        order.amount shouldBe amount
        order.status shouldBe OrderStatus.CREATED
    }

    @Test
    fun `결제 완료 - 성공`() {
        // given
        val orderId = "order-123"

        // when & then (예외가 발생하지 않으면 성공)
        orderService.completePayment(orderId)
    }
}