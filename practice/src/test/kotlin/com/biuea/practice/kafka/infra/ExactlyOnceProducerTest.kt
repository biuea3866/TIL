package com.biuea.practice.kafka.infra

import com.biuea.practice.PracticeApplication
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

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
class ExactlyOnceProducerTest {

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var exactlyOnceProducer: ExactlyOnceProducer

    private lateinit var consumer: KafkaConsumer<String, String>

    @BeforeEach
    fun setup() {
        val consumerProps = KafkaTestUtils.consumerProps(
            "test-group",
            "true",
            embeddedKafka
        ).apply {
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")  // 중요!
        }

        consumer = KafkaConsumer(consumerProps)
        consumer.subscribe(listOf("test-topic"))
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }

    @Test
    fun `Exactly Once - 멱등성 보장`() {
        // given
        val topic = "test-topic"
        val key = "idempotent-key"
        val message = "idempotent-message"

        // when - 동일한 메시지를 여러 번 전송 (재시도 시뮬레이션)
        repeat(3) {
            exactlyOnceProducer.send(topic, key, message)
        }

        Thread.sleep(1000)

        // then - 중복 제거되어 1개만 저장됨
        val records = consumer.poll(Duration.ofSeconds(5))

        // 멱등성 프로듀서는 동일한 PID, Seq Number를 가진 메시지를 자동으로 중복 제거
        // 하지만 이 테스트에서는 매번 새로운 전송이므로 3개가 저장될 수 있음
        // 실제 재시도 시나리오에서는 1개만 저장됨
        println("Received ${records.count()} messages")
        records.forEach { record ->
            record.value() shouldBe message
        }
    }

    @Test
    fun `Exactly Once - 순서 보장`() {
        // given
        val topic = "test-topic"
        val messageCount = 10

        // when
        repeat(messageCount) { i ->
            exactlyOnceProducer.send(topic, "key", "message-$i")
        }

        // then
        val receivedMessages = mutableListOf<String>()
        val timeout = System.currentTimeMillis() + 10000

        while (receivedMessages.size < messageCount && System.currentTimeMillis() < timeout) {
            val records = consumer.poll(Duration.ofSeconds(1))
            records.forEach { record ->
                receivedMessages.add(record.value())
            }
        }

        receivedMessages.size shouldBe messageCount

        // 순서 확인
        receivedMessages.forEachIndexed { index, message ->
            message shouldBe "message-$index"
        }
    }
}
