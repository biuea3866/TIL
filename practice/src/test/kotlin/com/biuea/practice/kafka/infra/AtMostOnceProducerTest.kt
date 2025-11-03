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
class AtMostOnceProducerTest {

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var atMostOnceProducer: AtMostOnceProducer

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
        }

        consumer = KafkaConsumer(consumerProps)
        consumer.subscribe(listOf("test-topic"))
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }

    @Test
    fun `At Most Once - 메시지 전송 성공`() {
        // given
        val topic = "test-topic"
        val key = "test-key"
        val message = "test-message"

        // when
        atMostOnceProducer.send(topic, key, message)
        Thread.sleep(1000)  // 메시지 도착 대기

        // then
        val records = consumer.poll(Duration.ofSeconds(5))
        records.count() shouldBe 1

        val record = records.first()
        record.key() shouldBe key
        record.value() shouldBe message
    }

    @Test
    fun `At Most Once - 빠른 연속 전송`() {
        // given
        val topic = "test-topic"
        val messageCount = 100

        // when
        repeat(messageCount) { i ->
            atMostOnceProducer.send(topic, "key-$i", "message-$i")
        }

        // then
        val records = consumer.poll(Duration.ofSeconds(5))
        // At Most Once는 일부 메시지가 유실될 수 있음
        println("Received ${records.count()} out of $messageCount messages")
        records.count() shouldBe messageCount  // 정상 환경에서는 모두 도착
    }
}
