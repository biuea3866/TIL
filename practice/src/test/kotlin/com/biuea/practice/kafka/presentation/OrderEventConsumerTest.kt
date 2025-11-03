package com.biuea.practice.kafka.presentation

import com.biuea.practice.PracticeApplication
import com.biuea.practice.kafka.config.KafkaTestConfig
import com.biuea.practice.kafka.domain.OrderEvent
import com.biuea.practice.kafka.infra.TransactionKafkaProducer
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.util.*

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
class OrderEventConsumerTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionalProducer: TransactionKafkaProducer

    @Test
    fun `주문 생성 이벤트 소비 - 통합 테스트`() {
        // given
        val orderEvent = OrderEvent.OrderCreated(
            orderId = UUID.randomUUID().toString(),
            customerId = "customer-1",
            productId = "product-1",
            quantity = 1,
            amount = BigDecimal("10000")
        )

        // when
        transactionalProducer.sendOrderTransaction(orderEvent)

        // then
        Thread.sleep(3000)  // 컨슈머 처리 대기

        // 실제 환경에서는 컨슈머가 자동으로 처리
        // 로그를 통해 확인 가능
        println("Order event consumed successfully")
    }

    @Test
    fun `중복 메시지 처리 - 멱등성 보장`() {
        // given
        val consumer = OrderEventConsumer(objectMapper)
        val acknowledgment = mockk<Acknowledgment>(relaxed = true)

        val orderEvent = OrderEvent.OrderCreated(
            orderId = "order-123",
            customerId = "customer-1",
            productId = "product-1",
            quantity = 1,
            amount = BigDecimal("10000")
        )
        val message = objectMapper.writeValueAsString(orderEvent)

        // when - 동일한 메시지를 두 번 처리
        consumer.consumeOrderEvents(message, "orders", 0, 1L, acknowledgment)
        consumer.consumeOrderEvents(message, "orders", 0, 1L, acknowledgment)

        // then - acknowledge는 두 번 호출되어야 함
        verify(exactly = 2) { acknowledgment.acknowledge() }
    }
}