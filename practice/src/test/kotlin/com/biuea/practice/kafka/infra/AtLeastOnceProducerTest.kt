package com.biuea.practice.kafka.infra

import com.biuea.practice.PracticeApplication
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
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import java.time.Duration

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
    partitions = 3,
    topics = ["orders", "inventory", "payments", "test-topic"],
    brokerProperties = [
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1"
    ]
)
class AtLeastOnceProducerTest {

    @Autowired
    private lateinit var embeddedKafka: EmbeddedKafkaBroker

    @Autowired
    private lateinit var atLeastOnceProducer: AtLeastOnceProducer

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
    fun `At Least Once - 동기 전송 성공`() {
        // given
        val topic = "test-topic"
        val key = "test-key"
        val message = "test-message"

        // when
        val metadata = atLeastOnceProducer.sendSync(topic, key, message)

        // then
        metadata shouldNotBe null
        metadata.topic() shouldBe topic
        println("Message sent to partition: ${metadata.partition()}, offset: ${metadata.offset()}")

        val records = consumer.poll(Duration.ofSeconds(5))
        records.count() shouldBe 1
        records.first().value() shouldBe message
    }

    @Test
    fun `At Least Once - 비동기 전송 성공`() {
        // given
        val topic = "test-topic"
        val key = "test-key"
        val message = "test-message"

        // when
        val future = atLeastOnceProducer.sendAsync(topic, key, message)
        val metadata = future.get()  // 비동기 결과 대기

        // then
        metadata shouldNotBe null
        metadata.topic() shouldBe topic

        val records = consumer.poll(Duration.ofSeconds(5))
        records.count() shouldBe 1
    }

    @Test
    fun `At Least Once - 메시지 유실 없음 보장`() {
        // given
        val topic = "test-topic"
        val messageCount = 100

        // when
        repeat(messageCount) { i ->
            atLeastOnceProducer.sendSync(topic, "key-$i", "message-$i")
        }

        // then
        var totalReceived = 0
        val timeout = System.currentTimeMillis() + 10000

        while (System.currentTimeMillis() < timeout && totalReceived < messageCount) {
            val records = consumer.poll(Duration.ofSeconds(1))
            totalReceived += records.count()
        }

        totalReceived shouldBe messageCount  // 모든 메시지 수신 보장
    }
}