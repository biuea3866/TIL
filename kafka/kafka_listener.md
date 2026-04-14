# KafkaListener 동작

## 전체 흐름 요약

```
@EnableKafka
  → KafkaListenerConfigurationSelector
    → KafkaBootstrapConfiguration
      → KafkaListenerAnnotationBeanPostProcessor
        → @KafkaListener 메서드 탐색
          → KafkaListenerEndpointRegistry에 등록
            → ConcurrentMessageListenerContainer 생성
              → KafkaMessageListenerContainer × concurrency 수
                → ListenerConsumer (Runnable)
                  → 무한 루프: poll() → invoke listener method
```

---

### 1. @EnableKafka

`@EnableKafka` 어노테이션을 활성화하게 되면 내부에 `KafkaListenerConfigurationSelector`를 Import하게 된다.

`KafkaListenerConfigurationSelector`은 `KafkaBootstrapConfiguration`의 클래스 네임을 찾아서 등록하고, `KafkaBootstrapConfiguration`는 `KafkaListenerAnnotationBeanPostProcessor`를 BeanDefinition에 등록하게 된다.

### 2. KafkaListenerAnnotationBeanPostProcessor

```java
@Override
public Object postProcessAfterInitialization(final Object bean, final String beanName) throws BeansException {
    buildEnhancer();
    if (!this.nonAnnotatedClasses.contains(bean.getClass())) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        Collection<KafkaListener> classLevelListeners = findListenerAnnotations(targetClass);
        final boolean hasClassLevelListeners = !classLevelListeners.isEmpty();
        final List<Method> multiMethods = new ArrayList<>();
        Map<Method, Set<KafkaListener>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
                (MethodIntrospector.MetadataLookup<Set<KafkaListener>>) method -> {
                    // 카프카 리스너 어노테이션이 붙은 메서드들을 찾는다.
                    Set<KafkaListener> listenerMethods = findListenerAnnotations(method);
                    return (!listenerMethods.isEmpty() ? listenerMethods : null);
                });
        if (hasClassLevelListeners) {
            Set<Method> methodsWithHandler = MethodIntrospector.selectMethods(targetClass,
                    (ReflectionUtils.MethodFilter) method ->
                            AnnotationUtils.findAnnotation(method, KafkaHandler.class) != null);
            multiMethods.addAll(methodsWithHandler);
        }
        if (annotatedMethods.isEmpty() && !hasClassLevelListeners) {
            this.nonAnnotatedClasses.add(bean.getClass());
            this.logger.trace(() -> "No @KafkaListener annotations found on bean type: " + bean.getClass());
        }
        else {
            // Non-empty set of methods
            for (Map.Entry<Method, Set<KafkaListener>> entry : annotatedMethods.entrySet()) {
                Method method = entry.getKey();
                for (KafkaListener listener : entry.getValue()) {
                    // 카프카 리스너들을 실행시킨다.
                    processKafkaListener(listener, method, bean, beanName);
                }
            }
            this.logger.debug(() -> annotatedMethods.size() + " @KafkaListener methods processed on bean '"
                    + beanName + "': " + annotatedMethods);
        }
        if (hasClassLevelListeners) {
            processMultiMethodListeners(classLevelListeners, multiMethods, bean, beanName);
        }
    }
    return bean;
}
```

`findListenerAnnotations`로 카프카 리스너 어노테이션이 붙은 메서드들을 추출한다.
그리고 `processKafkaListener`로 카프카 리스너들에 컨슈머 그룹, 토픽, 파티션 등 설정들을 세팅하고, `KafkaListenerEndpointRegistry`에 엔드포인트(리스너 인스턴스를 생성하는 `MethodKafkaListenerEndpoint`)와 KafkaListenerContainerFactory(KafkaMessageListenerContainer를 만드는)들을 등록한다.

### 3. ConcurrentMessageListenerContainer

개발자는 커스텀한 KafkaConfiguration 클래스를 생성하고, `ConcurrentKafkaListenerContainerFactory`를 등록한다.

`ConcurrentKafkaListenerContainerFactory`는 `createContainerInstance`를 이용하여 `ConcurrentMessageListenerContainer`를 생성한다.

`ConcurrentMessageListenerContainer`는 doStart메서드를 구현하여 `KafkaMessageListenerContainer`를 시작시킨다.

```java
@Override
protected void doStart() {
    if (!isRunning()) {
        checkTopics();
        ContainerProperties containerProperties = getContainerProperties();
        TopicPartitionOffset[] topicPartitions = containerProperties.getTopicPartitions();
        if (topicPartitions != null && this.concurrency > topicPartitions.length) {
            this.logger.warn(() -> "When specific partitions are provided, the concurrency must be less than or "
                    + "equal to the number of partitions; reduced from " + this.concurrency + " to "
                    + topicPartitions.length);
            this.concurrency = topicPartitions.length;
        }
        setRunning(true);

        for (int i = 0; i < this.concurrency; i++) {
            KafkaMessageListenerContainer<K, V> container =
                    constructContainer(containerProperties, topicPartitions, i);
            configureChildContainer(i, container);
            if (isPaused()) {
                container.pause();
            }
            container.start();
            this.containers.add(container);
        }
    }
}
```

### 4. KafkaMessageListenerContainer

KafkaMessageListenerContainer는 doStart메서드를 실행시키고, consumerExecutor를 이용하여 listenerConsumer를 수행시킨다.

```java
@Override
protected void doStart() {
    if (isRunning()) {
        return;
    }
    if (this.clientIdSuffix == null) { // stand-alone container
        checkTopics();
    }
    ContainerProperties containerProperties = getContainerProperties();
    checkAckMode(containerProperties);

    Object messageListener = containerProperties.getMessageListener();
    AsyncTaskExecutor consumerExecutor = containerProperties.getListenerTaskExecutor();
    if (consumerExecutor == null) {
        consumerExecutor = new SimpleAsyncTaskExecutor(
                (getBeanName() == null ? "" : getBeanName()) + "-C-");
        containerProperties.setListenerTaskExecutor(consumerExecutor);
    }
    GenericMessageListener<?> listener = (GenericMessageListener<?>) messageListener;
    ListenerType listenerType = determineListenerType(listener);
    ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
    ApplicationContext applicationContext = getApplicationContext();
    if (applicationContext != null && containerProperties.isObservationEnabled()) {
        ObjectProvider<ObservationRegistry> registry =
                applicationContext.getBeanProvider(ObservationRegistry.class);
        ObservationRegistry reg = registry.getIfUnique();
        if (reg != null) {
            observationRegistry = reg;
        }
    }
    // ListenerConsumer를 생성하고, 스레드 풀에서 Consumer 스레드를 수행시킨다.
    this.listenerConsumer = new ListenerConsumer(listener, listenerType, observationRegistry);
    setRunning(true);
    this.startLatch = new CountDownLatch(1);
    this.listenerConsumerFuture = consumerExecutor.submitCompletable(this.listenerConsumer);
    try {
        if (!this.startLatch.await(containerProperties.getConsumerStartTimeout().toMillis(),
                TimeUnit.MILLISECONDS)) {

            this.logger.error("Consumer thread failed to start - does the configured task executor "
                    + "have enough threads to support all containers and concurrency?");
            publishConsumerFailedToStart();
        }
    }
    catch (@SuppressWarnings(UNUSED) InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}
```

### 5. ListenerConsumer

`ListenerConsumer`는 `Runnable` 인터페이스를 상속받아 run 메서드를 구현하여 스레드를 동작시킨다.

run 메서드에서는 무한 루프를 돌면서 poll 메서드를 수행한다.
이 poll메서드는 KafkaConsumer 클래스가 구현한 메서드로 끝까지 타고들어가면 NetworkClient로 TCP 통신을 수행하며 레코드를 받아온다.

그리고 메시지를 받아 카프카 리스너 어노테이션이 붙은 메서드에 전달하고 실행시킨다.

#### pollAndInvoke 핵심 동작

1. `KafkaConsumer.poll(timeout)` → 브로커에서 레코드 배치 가져옴
2. 가져온 레코드를 `MessageListener.onMessage()` 혹은 `BatchMessageListener.onMessage()`로 전달
3. AckMode에 따라 오프셋 커밋 수행 (BATCH, RECORD, MANUAL 등)

```java
@Override // NOSONAR complexity
public void run() {
    initialize();
    Throwable exitThrowable = null;
    boolean failedAuthRetry = false;
    this.lastReceive = System.currentTimeMillis();
    while (isRunning()) {
        try {
            pollAndInvoke();
            if (failedAuthRetry) {
                publishRetryAuthSuccessfulEvent();
                failedAuthRetry = false;
            }
        }
        catch (NoOffsetForPartitionException nofpe) {
            this.fatalError = true;
            ListenerConsumer.this.logger.error(nofpe, "No offset and no reset policy");
            exitThrowable = nofpe;
            break;
        }
        catch (AuthenticationException | AuthorizationException ae) {
            if (this.authExceptionRetryInterval == null) {
                ListenerConsumer.this.logger.error(ae,
                        "Authentication/Authorization Exception and no authExceptionRetryInterval set");
                this.fatalError = true;
                exitThrowable = ae;
                break;
            }
            else {
                ListenerConsumer.this.logger.error(ae,
                        "Authentication/Authorization Exception, retrying in "
                                + this.authExceptionRetryInterval.toMillis() + " ms");
                publishRetryAuthEvent(ae);
                failedAuthRetry = true;
                sleepFor(this.authExceptionRetryInterval);
            }
        }
        catch (FencedInstanceIdException fie) {
            this.fatalError = true;
            ListenerConsumer.this.logger.error(fie, "'" + ConsumerConfig.GROUP_INSTANCE_ID_CONFIG
                    + "' has been fenced");
            exitThrowable = fie;
            break;
        }
        catch (StopAfterFenceException e) {
            this.logger.error(e, "Stopping container due to fencing");
            stop(false);
            exitThrowable = e;
        }
        catch (Error e) { // NOSONAR - rethrown
            this.logger.error(e, "Stopping container due to an Error");
            this.fatalError = true;
            wrapUp(e);
            throw e;
        }
        catch (Exception e) {
            handleConsumerException(e);
        }
        finally {
            clearThreadState();
        }
    }
    wrapUp(exitThrowable);
}
```

---

## Concurrency 모델

`ConcurrentMessageListenerContainer`의 concurrency 설정은 내부적으로 `KafkaMessageListenerContainer`를 여러 개 생성하는 것이다.

```
concurrency = 3 일 때:

ConcurrentMessageListenerContainer
  ├── KafkaMessageListenerContainer-0  →  ListenerConsumer (Thread-0)  →  Partition 0, 1
  ├── KafkaMessageListenerContainer-1  →  ListenerConsumer (Thread-1)  →  Partition 2, 3
  └── KafkaMessageListenerContainer-2  →  ListenerConsumer (Thread-2)  →  Partition 4, 5
```

* concurrency가 파티션 수보다 크면 자동으로 파티션 수로 줄어든다 (남는 스레드는 idle)
* 각 ListenerConsumer는 독립 스레드에서 자체 KafkaConsumer 인스턴스를 보유
* KafkaConsumer는 thread-safe하지 않으므로 반드시 하나의 스레드에서만 사용해야 한다

## 주요 설정 프로퍼티

| 프로퍼티 | 설명 | 기본값 |
|---------|------|--------|
| `concurrency` | 컨테이너당 컨슈머 스레드 수 | 1 |
| `ackMode` | 오프셋 커밋 방식 (BATCH, RECORD, MANUAL 등) | BATCH |
| `pollTimeout` | poll() 최대 대기 시간 | 5000ms |
| `idleBetweenPolls` | poll 사이 대기 시간 | 0 |
| `consumerStartTimeout` | 컨슈머 스레드 시작 대기 시간 | 30s |
```
