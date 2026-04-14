# ApplicationEventPublisher

옵저버 패턴을 스프링 프레임워크에서 구현한 인터페이스입니다.
클라이언트는 ApplicationEventPublisher.publishEvent로 옵저버들(EventListener)에게 이벤트가 발생했음을 알립니다.

옵저버 패턴인 발행 - 구독 모델을 사용하기 때문에 구독자들을 등록하고, 구독자들에 구현되어있는 전파 메서드를 호출하여 이벤트가 발생했음을 알립니다.

ApplicationEventPublisher 인터페이스는 모든 리스너 구현체들을 참조하지 않아도, 이벤트를 전달해주며 느슨한 결합을 구현합니다.

## 동작 방식

### 1. EventListenerMethodProcessor

EventListener가 붙은 메서드들을 조회하고, `ApplicationEventListener`들을 생성합니다.
`EventListenerFactory`인터페이스를 이용하여 `ApplicationEventListener`가 트랜잭션 이벤트 리스너인지, 기본 이벤트 리스너인지 판단하고, 객체(EventListener -> `ApplicationListenerMethodAdapter`, TransactionEventListener -> `TransactionalApplicationListenerMethodAdapter`)를 생성합니다.

그리고 이 Adapter에는 Method 객체를 전달받고, 자신의 멤버 변수에 어떤 메서드인지 등록합니다.
(+ TransactionEventListener라면 어떤 TransactionPhase 설정 값인지를 등록합니다.)

```java
private void processBean(final String beanName, final Class<?> targetType) {
    if (!this.nonAnnotatedClasses.contains(targetType) &&
        ...

        Map<Method, EventListener> annotatedMethods = null;

        // 이벤트 리스너가 붙은 메서드들을 찾는다.
        try {
            annotatedMethods = MethodIntrospector.selectMethods(targetType,
                    (MethodIntrospector.MetadataLookup<EventListener>) method ->
                            AnnotatedElementUtils.findMergedAnnotation(method, EventListener.class));
        }
        ...
        else {
            ...
            for (Method method : annotatedMethods.keySet()) {
                for (EventListenerFactory factory : factories) {
                    if (factory.supportsMethod(method)) {
                        // 이벤트 리스너가 붙은 메서드 정보를 리플렉션을 이용하여 Method 객체로 생성한다.
                        Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
                        // 이벤트 리스너 매서드 어댑터를 호출하고, ApplicationEventListener를 반환한다.
                        ApplicationListener<?> applicationListener =
                                factory.createApplicationListener(beanName, targetType, methodToUse);
                        // 그리고 ApplicationListener를 ApplicationContext에 이벤트 리스너들을 등록한다.
                        context.addApplicationListener(applicationListener);
                        break;
                    }
                }
            }
        }
    }
}
```

### 2. ApplicationEventListenerMethodAdapter

`factory.createApplicationListener(beanName, targetType, methodToUse)` 구현체인 Factory는 `ApplicationEventListenerMethodAdapter`, `TransactionApplicationEventListenerMethodAdapter`를 반환합니다.

그리고 이 Adapter들은 `onApplicationEvent`를 구현하고, `processEvent`를 호출합니다.
`processEvent`는 최종적으로 Method 객체의 `invoke` 메서드를 수행하여 이벤트 리스너가 붙은 메서드가 동작합니다.
즉, applicationListener들은 ApplicationEvent를 전달받고, 이벤트를 처리하는 메서드를 구현하고 있는 상태입니다.

```java
@Override
public void onApplicationEvent(ApplicationEvent event) {
    processEvent(event); // ApplicationEventListenerAdapter에서 구현된 메서드이고, 구독자가 알림받는 메서드
}
```

### 3. AbstractApplicationContext

`context.addApplicationListener(applicationListener);`

생성된 이벤트 리스너들을 applicationEventMulticaster에 applicationListener들을 등록하고, ApplicationContext는 이벤트를 받을 준비를 합니다.

```java
@Override
public void addApplicationListener(ApplicationListener<?> listener) {
    Assert.notNull(listener, "ApplicationListener must not be null");
    if (this.applicationEventMulticaster != null) {
        this.applicationEventMulticaster.addApplicationListener(listener);
    }
    this.applicationListeners.add(listener);
}
```

### 4. ApplicationContext

`ApplicationContext`가 `ApplicationEventPublisher.publishEvent`를 구현합니다.
`ApplicationEvent`를 상속받아 이벤트 객체를 만드는 방법이 있고, 단순 객체를 전달하여 내부적으로 `PayloadApplicationEvent`로 변환하여 이벤트를 전달하는 방법이 있습니다.

3번에서 applicationEventMulticaster에 이벤트 리스너들을 등록했었는데, ApplicationEventPublisher.publishEvent를 호출하여 등록된 이벤트 리스너들에게 이벤트를 전파합니다.

```java
protected void publishEvent(Object event, @Nullable ResolvableType typeHint) {
    ...
    ApplicationEvent applicationEvent;

    // ApplicationEvent를 구현한 이벤트 객체 분기
    if (event instanceof ApplicationEvent applEvent) {
        applicationEvent = applEvent;
        eventType = typeHint;
    }
    // ApplicationEvent를 구현하지 않고, 이벤트를 퍼블리싱할 경우 PayloadApplicationEvent로 객체 생성
    else {
        ResolvableType payloadType = null;
        if (typeHint != null && ApplicationEvent.class.isAssignableFrom(typeHint.toClass())) {
            eventType = typeHint;
        }
        else {
            payloadType = typeHint;
        }
        applicationEvent = new PayloadApplicationEvent<>(this, event, payloadType);
    }

    ...

    // Multicaster로 이벤트 발행
    else if (this.applicationEventMulticaster != null) {
        this.applicationEventMulticaster.multicastEvent(applicationEvent, eventType);
    }

    ...
}
```

### 5. ApplicationEventMulticaster

발행 - 구독 모델에서 발행자 역할을 합니다.

4번에서 이벤트를 퍼블리싱하면 Multicaster는 전달받은 event 타입에 맞는 등록된 이벤트 리스너들을 조회하고, onApplicationEvent를 수행하도록 합니다. (옵저버들에게 이벤트가 발생했음을 알림)

```java
@Override
public void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType) {
    ResolvableType type = (eventType != null ? eventType : ResolvableType.forInstance(event));
    Executor executor = getTaskExecutor();
    for (ApplicationListener<?> listener : getApplicationListeners(event, type)) {
        if (executor != null && listener.supportsAsyncExecution()) {
            try {
                executor.execute(() -> invokeListener(listener, event));
            }
            catch (RejectedExecutionException ex) {
                invokeListener(listener, event);
            }
        }
        else {
            invokeListener(listener, event);
        }
    }
}

protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
    ErrorHandler errorHandler = getErrorHandler();
    if (errorHandler != null) {
        try {
            doInvokeListener(listener, event);
        }
        catch (Throwable err) {
            errorHandler.handleError(err);
        }
    }
    else {
        doInvokeListener(listener, event);
    }
}

private void doInvokeListener(ApplicationListener listener, ApplicationEvent event) {
    try {
        // 2번의 Adapter에서 구현된 onApplicationEvent
        listener.onApplicationEvent(event);
    }
    catch (ClassCastException ex) {
        ...
    }
}
```
