# [Kafka] Spring Kafka Consumer

## Spring Kafka Consumer

---

Spring Kafka Consumer는 Apache Kafka로부터 메시지를 수신하고, 처리하기 위한 Spring 기반의 라이브러리이다. 이를 이용하여 Kafka 메시지를 수신과 처리를 다양하게 활용할 수 있다.

- @kafkaListener: 어노테이션만으로 토픽, 그룹ID, 병렬 등 다양한 처리

- Spring Transaction: Spring의 @Transactional과 묶어서 사용 가능

- 에러 핸들링: DefaultErrorHandler를 이용하여 재시도 로직 사용

## Spring Kafka 기본 동작

---

**@EnableKafka**

KafkaListener를 활성화시키는 어노테이션이다.

**1. @EnableKafka: 카프카 리스너 활성화**

```less
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(KafkaBootstrapConfiguration.class)
public @interface EnableKafka {
}
```

**2. 카프카 리스너를 엔드포인트로 등록하는 설정**

```less
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class KafkaBootstrapConfiguration {

    @Bean(name = KafkaListenerConfigUtils.KAFKA_LISTENER_ANNOTATION_PROCESSOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public KafkaListenerAnnotationBeanPostProcessor kafkaListenerAnnotationProcessor() {
        return new KafkaListenerAnnotationBeanPostProcessor();
    }

    @Bean(name = KafkaListenerConfigUtils.KAFKA_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME)
    public KafkaListenerEndpointRegistry defaultKafkaListenerEndpointRegistry() {
        return new KafkaListenerEndpointRegistry();
    }
}
```

- KafkaListenerAnnotationBeanPostProcessor: @kafkaListener가 붙은 메서드를 스캔하고, 리스너 엔드포인트로 등록
- KafkaListenerEndpointRegistry: 모든 리스너 컨테이너를 관리하는 레지스트리

**@KafkaListener**

메시지를 수신할 메서드를 지정하는 어노테이션이다.

**1. 카프카 리스너 지정 어노테이션**

```typescript
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@MessageMapping
@Documented
public @interface KafkaListener {

    String id() default "";

    String containerFactory() default "";

    String[] topics() default {};

    String topicPattern() default "";

    TopicPartition[] topicPartitions() default {};

    String containerGroup() default "";

    String errorHandler() default "";

    String groupId() default "";

    boolean idIsGroup() default true;

    String clientIdPrefix() default "";

    String beanRef() default "__listener";

    String concurrency() default "";

    String autoStartup() default "";

    String[] properties() default {};
}
```

**2. 카프카 리스너 후처리기**

```oxygene
public class KafkaListenerAnnotationBeanPostProcessor<K, V>
        implements BeanPostProcessor, Ordered, BeanFactoryAware {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);

        // @KafkaListener 메서드 찾기
        Map<Method, Set<KafkaListener>> annotatedMethods = 
            MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<Set<KafkaListener>>) method -> {
                    Set<KafkaListener> listenerMethods = 
                        findListenerAnnotations(method);
                    return (!listenerMethods.isEmpty() ? listenerMethods : null);
                });

        // 각 메서드에 대해 엔드포인트 생성
        annotatedMethods.forEach((method, listeners) ->
            listeners.forEach(listener -> 
                processKafkaListener(listener, method, bean, beanName)));

        return bean;
    }

    protected void processKafkaListener(KafkaListener kafkaListener, 
                                       Method method, Object bean, String beanName) {
        // MethodKafkaListenerEndpoint 생성
        MethodKafkaListenerEndpoint<K, V> endpoint = 
            new MethodKafkaListenerEndpoint<>();
        endpoint.setMethod(method);
        endpoint.setBean(bean);
        endpoint.setTopics(resolveTopics(kafkaListener));
        endpoint.setGroupId(getEndpointGroupId(kafkaListener, beanName));

        // 레지스트리에 등록
        this.registrar.registerEndpoint(endpoint, factory);
    }
}
```

@KafkaListener가 붙은 메서드들을 레지스트리에 등록하는 빈 후처리기이다. 어노테이션이 붙은 메서드 정보들을 스캔하여 MethodKafkaListenerEndpoint에 등록하고, 레지스트리에 endpoint를 등록하게 된다.

**MessageListenerContainer**

메시지 리스너의 생명주기를 관리하는 컴포넌트이다.

**1. 인터페이스**

```java
public interface MessageListenerContainer extends SmartLifecycle {

    void setupMessageListener(Object messageListener);

    // SmartLifecycle 메서드들
    void start();
    void stop();
    boolean isRunning();
}
```

**2. 구현체**

```angelscript
public class ConcurrentMessageListenerContainer<K, V> extends AbstractMessageListenerContainer<K, V> {

    private final List<KafkaMessageListenerContainer<K, V>> containers = new ArrayList<>();

    private int concurrency = 1; // 동시 실행 수

    @Override
    protected void doStart() {
        if (!isRunning()) {
            ContainerProperties containerProperties = getContainerProperties();
            TopicPartitionOffset[] topicPartitions = containerProperties.getTopicPartitions();

            if (topicPartitions != null && this.concurrency > topicPartitions.length) {
                this.concurrency = topicPartitions.length;
            }

            // concurrency 수만큼 KafkaMessageListenerContainer 생성
            for (int i = 0; i < this.concurrency; i++) {
                KafkaMessageListenerContainer<K, V> container = constructContainer(containerProperties, topicPartitions, i);
                this.containers.add(container);
                container.start();
            }
        }
    }
}
```

concurrency는 설정값만큼 KafkaMessageListenerContainer 객체를 생성하는 옵션이다. 다만, 무작정 concurrency를 늘린다고 해서 리스너 인스턴스가 늘어난다 하더라도 파티션의 갯수가 받쳐주지 못하면 불필요하게 객체만 생성할 뿐이다.

가령 KafkaMessageListenerContainer가 2개일 때, 1개의 파티션을 구독하고 메시지를 2개씩 읽는 것처럼 해석할 수 있다. 하지만 그렇지 않고 객체 1개만 파티션 1개를 구독하게 된다. concurrency는 단일 컨슈머(서버 인스턴스)에서 n개의 파티션을 할당할 수 있는 방법이기 때문에 예상처럼 동작하지 않는다. 코드 레벨에서 살펴보면 다음과 같다.

```java
// 자동 할당 시에는 Partition Assignment 전략을 사용
CONFIG = new ConfigDef().define(PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    Type.LIST,
    List.of(RangeAssignor.class, CooperativeStickyAssignor.class),
    new ConfigDef.NonNullValidator(),
    Importance.MEDIUM,
    PARTITION_ASSIGNMENT_STRATEGY_DOC
)

@Override
public Map<String, List<TopicPartition>> assignPartitions(Map<String, List<PartitionInfo>> partitionsPerTopic,
                                                          Map<String, Subscription> subscriptions) {
    Map<String, List<MemberInfo>> consumersPerTopic = consumersPerTopic(subscriptions);
    Map<String, String> consumerRacks = consumerRacks(subscriptions);
    List<TopicAssignmentState> topicAssignmentStates = partitionsPerTopic.entrySet().stream()
            .filter(e -> !e.getValue().isEmpty())
            // TopicAssignmentState 객체를 만들면서 컨슈머당 할당할 파티션 계산
            .map(e -> new TopicAssignmentState(e.getKey(), e.getValue(), consumersPerTopic.get(e.getKey()), consumerRacks))
            .collect(Collectors.toList());

    Map<String, List<TopicPartition>> assignment = new HashMap<>();
    subscriptions.keySet().forEach(memberId -> assignment.put(memberId, new ArrayList<>()));

    boolean useRackAware = topicAssignmentStates.stream().anyMatch(t -> t.needsRackAwareAssignment);
    if (useRackAware)
        assignWithRackMatching(topicAssignmentStates, assignment);

	// 범위 할당
    topicAssignmentStates.forEach(t -> assignRanges(t, (c, tp) -> true, assignment));

    if (useRackAware)
        assignment.values().forEach(list -> list.sort(PARTITION_COMPARATOR));
    return assignment;
}

private void assignRanges(TopicAssignmentState assignmentState,
                              BiFunction<String, TopicPartition, Boolean> mayAssign,
                              Map<String, List<TopicPartition>> assignment) {
    for (String consumer : assignmentState.consumers.keySet()) {
        if (assignmentState.unassignedPartitions.isEmpty())
            break;
        List<TopicPartition> assignablePartitions = assignmentState.unassignedPartitions.stream()
                .filter(tp -> mayAssign.apply(consumer, tp))
                .limit(assignmentState.maxAssignable(consumer))
                .collect(Collectors.toList());
        if (assignablePartitions.isEmpty())
            continue;

        assign(consumer, assignablePartitions, assignmentState, assignment);
    }
}

// 파티션을 수동으로 직접 지정했을 때
private TopicPartitionOffset[] partitionSubset(ContainerProperties containerProperties, int index) {
    TopicPartitionOffset[] topicPartitions = containerProperties.getTopicPartitions();

    if (topicPartitions == null) {
        return null;  // 자동 할당 모드 (subscribe)
    } else if (this.concurrency == 1) {
        return topicPartitions;  // 모든 파티션 반환
    } else {
        // concurrency > 1: 파티션을 나눠서 할당
        int numPartitions = topicPartitions.length;
        if (numPartitions == this.concurrency) {
            return new TopicPartitionOffset[]{topicPartitions[index]};  // 1:1 매핑
        } else {
            int perContainer = numPartitions / this.concurrency;  2 = 4 / 2
            int start = index * perContainer;                     0 = 0 * 2, 2 = 1 * 2
            int end = index == this.concurrency - 1 ? numPartitions : start + perContainer; 2 = 0 + 2, 4
            return Arrays.copyOfRange(topicPartitions, start, end);  // 범위 할당 => 2개 할당
        }
    }
}
```

**ConsumerFactory**

Kafka Consumer를 생성하는 팩토리이다.

```dart
public class DefaultKafkaConsumerFactory<K, V> implements ConsumerFactory<K, V> {

    private final Map<String, Object> configs;

    private Supplier<Deserializer<K>> keyDeserializerSupplier;

    private Supplier<Deserializer<V>> valueDeserializerSupplier;

    public DefaultKafkaConsumerFactory(Map<String, Object> configs) {
        this.configs = new HashMap<>(configs);
    }

    @Override
    public Consumer<K, V> createConsumer(
            @Nullable String groupId,
            @Nullable String clientIdPrefix,
            @Nullable String clientIdSuffix,
            @Nullable Properties properties) {

        Map<String, Object> configProps = new HashMap<>(this.configs);

        if (groupId != null) {
            configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }

        if (clientIdPrefix != null) {
            configProps.put(ConsumerConfig.CLIENT_ID_CONFIG, 
                clientIdPrefix + (clientIdSuffix != null ? clientIdSuffix : ""));
        }

        // Deserializer 생성
        Deserializer<K> keyDeserializer = 
            this.keyDeserializerSupplier.get();
        Deserializer<V> valueDeserializer = 
            this.valueDeserializerSupplier.get();

        // KafkaConsumer 생성
        return new org.apache.kafka.clients.consumer.KafkaConsumer<>(
            configProps,
            keyDeserializer,
            valueDeserializer
        );
    }
}
```

**컴포넌트 다이어그램**

![](./img/kafka_consumer_1.png)

## Spring Kafka 처리량 증대

---

Kafka Consumer는 다양한 처리 방법이 존재한다. 일반적인 단건 처리, 배치 크기(byte)만큼 처리하는 배치 처리, 병렬 컨슈머를 이용한 처리 등 다양하게 메시지를 수신하여 처리가 가능하다.

**배치 컨슈머**

Kafka Consumer는 매순간 브로커로부터 poll() 요청을 하고, 메시지를 읽어오는 방식이다. 즉, while문이 동작하면서 네트워크 요청이 수행되고 처리 후 다시 요청하는 방식이 반복된다. 너무 잦은 네트워크 요청, 브로커의 disk i/o는 브로커의 성능을 저하시킬 수 있고, 메시지 퍼블리싱 이후 커밋 지연이라던지 다른 컨슈머 그룹에서의 메시지 컨슘 등에서의 문제를 야기시킬 수 있다.

이를 해결하기 위해 Spring Kafka Consumer는 메시지를 읽어올 때 특정 byte 혹은 레코드 갯수만큼 읽어올 수 있다.

```java
@Configuration
class BatchConsumerConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Bean
    fun batchKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, Any> {
	    val configProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "batch-events-consumer",
			ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.qualifiedName,
			ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.qualifiedName,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500,
            ConsumerConfig.FETCH_MIN_BYTES_CONFIG to 1048576,
            ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG to 500
        )

        val consumerFactory = DefaultKafkaConsumerFactory<String, Any>(configProps)
        
        return ConcurrentKafkaListenerContainerFactory<String, Any>().apply {
            setConsumerFactory(batchEventConsumerFactory())
            setConcurrency(1)
            setBatchListener(true)
            setCommonErrorHandler(
                DefaultErrorHandler(
                    { record, ex ->
                        log.error("Batch consumer error - topic: {}, value: {}", record?.topic(), record?.value(), ex)
                    },
                    FixedBackOff(1000L, 3L)
                )
            )
        }
    }
}
```

ConcurrentKafkaListener(concurrency 옵션을 이용한 컨테이너 생성기)를 구성할 때, setBatchListener 옵션을 키고, 최대 레코드 수 그리고 최소 배치 크기를 지정하여 배치 컨슈머를 사용할 수 있다.

**병렬 컨슈머**

앞서 concurrency 설명 때, 하나의 파티션에서 n개의 메시지를 읽는 방식이 아닌가하는 의문(실제론 아니었지만)이 있었다. 병렬 컨슈머는 이 의문을 실제로 사용할 수 있는 방법이다. 즉, 파티션 수를 늘리지 않고 단일 컨슈머가 동시에 처리할 수 있는 크기를 늘릴 수 있다는 것이다.

**1. ThreadPoolExectuor 처리**

별도의 ThreadPoolExectuor를 만들어 비동기 처리 후 결과를 기다리는 방식이다.

```java
@Bean
fun kafkaConsumerExecutor(): ThreadPoolTaskExecutor {
    return ThreadPoolTaskExecutor().apply {
        corePoolSize = 10
        maxPoolSize = 20
        queueCapacity = 1000
        setThreadNamePrefix("kafka-consumer-")
        initialize()
    }
}

@KafkaListener(
    topics = [KafkaTopics.OPTIMIZED_EVENTS],
    groupId = "async-events-consumer",
    containerFactory = "asyncKafkaListenerContainerFactory"
)
fun listen(records: List<ConsumerRecord<String, Event>>, acknowledgment: Acknowledgment) {
    val events = records.map { it.value() }

    val futures = events.chunked(50).map { chunk ->
        CompletableFuture.runAsync({
            eventProcessor.processBatch(chunk)
        }, kafkaConsumerExecutor)
    }

    CompletableFuture.allOf(*futures.toTypedArray()).join()

    acknowledgment.acknowledge()
}
```

메시지들을 비동기로 처리하고, 전부 완료가 될 때까지 대기 후 수동 커밋하는 방식이다. 장점으로는 비동기 처리로 인해 처리량이 증가할 수 있지만, 전부 비동기 처리이기 때문에 순서 보장이 안되고, 실패할 경우 부분적으로 재처리를 할 것인지 전체 재처리를 할 것인지 등에 대한 복잡성 문제가 존재한다.

**2. Confluent Parallel Consumer 처리**

Confluent에서 만든 Parallel Consumer 라이브러리(ref. [https://d2.naver.com/helloworld/7181840](https://d2.naver.com/helloworld/7181840))를 사용하는 방법이다. 하나의 파티션을 구독하고 있는 단일 컨슈머가 n개의 메시지를 읽어와 처리하는 컨셉이다.

여러 개의 메시지를 읽어오기 때문에 offset을 어떻게 처리할 것인지에 대한 문제가 존재하는데 병렬로 처리하기 때문에 0, 1, 2 메시지를 순서에 관계 없이 처리할 수도 있고, 마찬가지로 순서 보장에 대한 문제도 존재한다.

offset에 대한 문제는 미처리된 offset을 기준으로 커밋하여 해결한다. 가령 0, 1, 2 중에 0, 2번이 처리되었지만  1번은 미처리이기 때문에 0번 offset까지만 커밋한다.

그리고 순서 보장은 Partition, Key, Unordered의 옵션으로 순서 보장 제약을 느슨하게 하지만 성능을 향상시키는 정책으로 해결한다.

- Partition: 파티션 단위로 병렬처리하는 방식이다. 하나의 토픽에 대해 N개의 파티션을 구독하여 순서대로 메시지를 읽는 방식이다. (concurrency와 n개 파티션과 동일)

- Key: Key 기준으로 병렬처리하는 방식이다. 가령 하나의 파티션에 Key1(0), Key1(1), Key2(2), Key1(3)순서로 메시지들이 들어올 수 있는데, 이 정책은 0, 2번의 메시지를 병렬로 처리할 수 있다.

- Unordered: 순서를 아예 보장하지 않는 방식이다. 앞선 메시지 결과와 관계없이 메시지를 계속해서 읽고 처리하는 방식이다.

내부 구조는 Broker Poller Thread(poll)가 Broker로부터 메시지를 읽어오고, Mailbox에 메시지를 적재한다. 그리고 Controller Thread는 Mailbox로부터 메시지를 읽어와 Worker Thread에게 작업을 할당하고, Broker에게 offset commit을 수행한다.

```java
/**
 * Confluent Parallel Consumer 옵션
 *
 * ProcessingOrder 옵션:
 * - UNORDERED: 최대 병렬성, 순서 보장 없음
 * - KEY: 같은 키를 가진 메시지는 순서 보장 (권장)
 * - PARTITION: 파티션 내 순서 보장 (일반 Consumer와 동일)
 */
@Bean
fun parallelConsumerOptions(
    parallelConsumerKafkaConsumer: Consumer<String, String>
): ParallelConsumerOptions<String, String> {
    return ParallelConsumerOptions.builder<String, String>()
        .consumer(parallelConsumerKafkaConsumer)
        .ordering(ProcessingOrder.KEY)  // 같은 키는 순서 보장
        .maxConcurrency(100)            // 최대 동시 처리 수
        .build()
}

@Bean(destroyMethod = "close")
fun parallelStreamProcessor(
    options: ParallelConsumerOptions<String, String>
): ParallelStreamProcessor<String, String> {
    return ParallelStreamProcessor.createEosStreamProcessor(options)
}

@Component
class ParallelEventListener(
    private val parallelStreamProcessor: ParallelStreamProcessor<String, String>
) {
    @PostConstruct
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("Starting Parallel Consumer for topic: {}", KafkaTopics.PARALLEL_EVENTS)

            // 토픽 구독
            parallelStreamProcessor.subscribe(listOf(KafkaTopics.PARALLEL_EVENTS))

            // 병렬 처리 시작
            parallelStreamProcessor.poll { context: PollContext<String, String> ->
                processMessage(context)
            }
        }
    }
}
```

## 예제

---

최대한 동일한 환경에서 각 컨슈머 전략별로 어떤 성능을 보일지 테스트한 결과이다.

| 컨슈머 전략 | 처리 옵션 | 처리 방식 | TPS | 처리 소요 시간 (100만건 기준) | 순위 |
| --- | --- | --- | --- | --- | --- |
| 일반 컨슈머 | - concurrency: 10 - partition: 10 | 10개의 파티션을 구독하고, 10개를 병렬처리 | 18,867 | 53,000ms | 3 |
| 배치 컨슈머 | - concurrency: 10 - partition: 10 - batch record: 500 | 10개의 파티션을 구독하고, 10개를 병렬처리하면서, 메시지를 읽을 땐 500개 채워서 | 50,000 | 20,000ms | 1 |
| 비동기 컨슈머 | - concurrency: 1 - partition: 1 - batch record: 500 - thread executor: 10 | 1개의 파티션을 구독하고, 500개씩 읽어서 10개의 스레드가 병렬 처리 | 32,258 | 31,000ms | 2 |
| 병렬 컨슈머 | - partition: 1 - max concurrency: 10 - key기반 순서 보장 | 1개의 파티션을 구독하고, 10개를 병렬 처리 | 15,625 | 64,000ms | 4 |

처리 순위를 살펴보면 배치 컨슈머가 가장 높고, 그 다음엔 비동기 컨슈머, 일반 컨슈머, 병렬 컨슈머 순의 처리량을 보여준다. 배치 컨슈머는 10개의 파티션과 한번에 많이 처리하는 구조이기 때문에 가장 좋은 처리량은 예상과 같았다. 그 다음 의외인 것은 비동기 컨슈머가 1개의 파티션을 사용함에도 일반 컨슈머보다 처리량이 좋았던 점(순서 보장이나 전체 롤백같은 건 고려 안함)이다. 그 다음 병렬 컨슈머는 일반 컨슈머와 비교했을 때 최종처리까지 10초 이상이 차이났지만, 1개의 파티션만을 사용해서 이 정도의 처리량을 보여준 건 좋은 결과라 생각한다.

## 참조

---

* parallel consumer 설명: [https://d2.naver.com/helloworld/7181840](https://d2.naver.com/helloworld/7181840)
