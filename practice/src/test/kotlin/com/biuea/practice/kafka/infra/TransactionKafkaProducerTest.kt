package com.biuea.practice.kafka.infra

import com.biuea.practice.PracticeApplication
import com.biuea.practice.kafka.config.KafkaTestConfig
import com.biuea.practice.kafka.domain.OrderEvent
import com.fasterxml.jackson.databind.ObjectMapper
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
class TransactionKafkaProducerTest {

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var transactionalProducer: TransactionKafkaProducer

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var ordersConsumer: KafkaConsumer<String, String>
    private lateinit var inventoryConsumer: KafkaConsumer<String, String>
    private lateinit var paymentsConsumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setup() {
        val consumerProps = KafkaTestUtils.consumerProps(
            UUID.randomUUID().toString(),
            "true",
            embeddedKafka
        ).apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")  // 트랜잭션 메시지만 읽기
        }

        ordersConsumer = KafkaConsumer(consumerProps)
        ordersConsumer.subscribe(listOf("orders"))

        inventoryConsumer = KafkaConsumer(consumerProps.apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString())
        })
        inventoryConsumer.subscribe(listOf("inventory"))

        paymentsConsumer = KafkaConsumer(consumerProps.apply {
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
    fun `트랜잭션 - 정상 커밋 시 모든 메시지 전송`() {
        // given
        val orderId = UUID.randomUUID().toString()
        val orderEvent = OrderEvent.OrderCreated(
            orderId = orderId,
            customerId = "customer-1",
            productId = "product-1",
            quantity = 2,
            amount = BigDecimal("20000")
        )

        // when
        transactionalProducer.sendOrderTransaction(orderEvent)

        // then - orders 토픽
        val orderRecords = ordersConsumer.poll(Duration.ofSeconds(5))
        orderRecords.count() shouldBe 1
        val orderJson = orderRecords.first().value()
        orderJson shouldNotBe null
        println("Order event: $orderJson")

        // then - inventory 토픽
        val inventoryRecords = inventoryConsumer.poll(Duration.ofSeconds(5))
        inventoryRecords.count() shouldBe 1
        val inventoryEvent = objectMapper.readValue(
            inventoryRecords.first().value(),
            OrderEvent.InventoryReserved::class.java
        )
        inventoryEvent.orderId shouldBe orderId
        inventoryEvent.productId shouldBe "product-1"
        inventoryEvent.quantity shouldBe 2

        // then - payments 토픽
        val paymentRecords = paymentsConsumer.poll(Duration.ofSeconds(5))
        paymentRecords.count() shouldBe 1
        val paymentEvent = objectMapper.readValue(
            paymentRecords.first().value(),
            OrderEvent.PaymentRequested::class.java
        )
        paymentEvent.orderId shouldBe orderId
        paymentEvent.amount shouldBe BigDecimal("20000")
        paymentEvent.customerId shouldBe "customer-1"
    }

    @Test
    fun `트랜잭션 - 원자성 보장 (All or Nothing)`() {
        // given
        val orderId = UUID.randomUUID().toString()
        val orderEvent = OrderEvent.OrderCreated(
            orderId = orderId,
            customerId = "customer-1",
            productId = "product-1",
            quantity = 1,
            amount = BigDecimal("10000")
        )

        // when
        transactionalProducer.sendOrderTransaction(orderEvent)

        // then - 세 토픽 모두에 메시지가 있어야 함 (원자성)
        val orderCount = ordersConsumer.poll(Duration.ofSeconds(3)).count()
        val inventoryCount = inventoryConsumer.poll(Duration.ofSeconds(3)).count()
        val paymentCount = paymentsConsumer.poll(Duration.ofSeconds(3)).count()

        orderCount shouldBe 1
        inventoryCount shouldBe 1
        paymentCount shouldBe 1

        println("Transaction committed atomically across 3 topics")
    }

    @Test
    fun `트랜잭션 - 결제 완료 이벤트 전송`() {
        // given
        val orderId = UUID.randomUUID().toString()
        val transactionId = UUID.randomUUID().toString()

        // when
        transactionalProducer.sendPaymentCompleted(orderId, transactionId)

        // then
        val records = ordersConsumer.poll(Duration.ofSeconds(5))
        records.count() shouldBe 1

        val event = objectMapper.readValue(
            records.first().value(),
            OrderEvent.PaymentCompleted::class.java
        )
        event.orderId shouldBe orderId
        event.transactionId shouldBe transactionId
    }
}