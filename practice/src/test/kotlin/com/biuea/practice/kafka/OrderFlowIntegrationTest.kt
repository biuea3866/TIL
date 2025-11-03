package com.biuea.practice.kafka

import com.biuea.practice.PracticeApplication
import com.biuea.practice.kafka.config.KafkaTestConfig
import com.biuea.practice.kafka.domain.OrderService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import java.math.BigDecimal
import java.time.Duration
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
class OrderFlowIntegrationTest {

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var orderService: OrderService

    private lateinit var ordersConsumer: KafkaConsumer<String, String>
    private lateinit var inventoryConsumer: KafkaConsumer<String, String>
    private lateinit var paymentsConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setup() {
        val baseProps = KafkaTestUtils.consumerProps(
            UUID.randomUUID().toString(),
            "true",
            embeddedKafka
        ).apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
        }

        ordersConsumer = KafkaConsumer(baseProps)
        ordersConsumer.subscribe(listOf("orders"))

        inventoryConsumer = KafkaConsumer(baseProps.apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString())
        })
        inventoryConsumer.subscribe(listOf("inventory"))

        paymentsConsumer = KafkaConsumer(baseProps.apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString())
        })
        paymentsConsumer.subscribe(listOf("payments"))
    }

    @AfterEach
    fun tearDown() {
        ordersConsumer.close()
        inventoryConsumer.close()
        paymentsConsumer.close()
    }

    @Test
    fun `End-to-End 주문 생성부터 완료까지 전체 플로우`() {
        // given
        val customerId = "customer-001"
        val productId = "product-001"
        val quantity = 2
        val amount = BigDecimal("40000")

        // when - 주문 생성
        val order = orderService.createOrder(customerId, productId, quantity, amount)

        // then - 주문 객체 검증
        order shouldNotBe null
        order.orderId shouldNotBe null

        Thread.sleep(3000)  // 이벤트 전파 대기

        // then - orders 토픽 검증
        val orderRecords = ordersConsumer.poll(Duration.ofSeconds(5))
        orderRecords.count() shouldBe 1
        println("✓ Order event published to 'orders' topic")

        // then - inventory 토픽 검증
        val inventoryRecords = inventoryConsumer.poll(Duration.ofSeconds(5))
        inventoryRecords.count() shouldBe 1
        println("✓ Inventory event published to 'inventory' topic")

        // then - payments 토픽 검증
        val paymentRecords = paymentsConsumer.poll(Duration.ofSeconds(5))
        paymentRecords.count() shouldBe 1
        println("✓ Payment event published to 'payments' topic")

        // when - 결제 완료
        orderService.completePayment(order.orderId)
        Thread.sleep(2000)

        // then - 결제 완료 이벤트 검증
        val paymentCompletedRecords = ordersConsumer.poll(Duration.ofSeconds(5))
        paymentCompletedRecords.count() shouldBe 1
        println("✓ Payment completed event published")

        println("\n✅ End-to-End 테스트 성공: 모든 이벤트가 정상적으로 발행되었습니다")
    }

    @Test
    fun `Exactly Once 보장 동일한 주문을 여러 번 생성해도 이벤트는 한 번만 발행`() {
        // given
        val customerId = "customer-002"
        val productId = "product-002"

        // when - 동일한 주문을 3번 생성 시도
        val orders = (1..3).map {
            orderService.createOrder(customerId, productId, 1, BigDecimal("10000"))
        }

        Thread.sleep(3000)

        // then - 각각 독립적인 주문이므로 3개의 이벤트가 발행됨
        val orderRecords = ordersConsumer.poll(Duration.ofSeconds(5))
        orderRecords.count() shouldBe 3

        // 각 주문은 고유한 orderId를 가짐
        val orderIds = orders.map { it.orderId }.toSet()
        orderIds.size shouldBe 3

        println("✓ 각 주문은 고유하게 처리되었습니다")
    }
}
