# 스레드 로컬

## 스레드 로컬이란?

`ThreadLocal`은 각 스레드가 독립적으로 값을 가질 수 있도록 하는 저장소이다. 동일한 `ThreadLocal` 객체에 접근하더라도, 스레드마다 서로 다른 값을 읽고 쓸 수 있다.

```java
public class ThreadLocal<T> {
    public T get() { ... }
    public void set(T value) { ... }
    public void remove() { ... }
    protected T initialValue() { ... }
}
```

기본적인 사용 예시는 다음과 같다.

```java
private static final ThreadLocal<String> userContext = new ThreadLocal<>();

public void handleRequest(String userId) {
    userContext.set(userId);         // 현재 스레드에 값 저장
    process();
    userContext.remove();            // 사용 후 반드시 제거
}

private void process() {
    String userId = userContext.get(); // 같은 스레드에서 어디서든 접근 가능
    System.out.println("처리 중인 사용자: " + userId);
}
```

### 내부 구조

`ThreadLocal`의 핵심은 각 `Thread` 객체가 내부적으로 `ThreadLocalMap`을 가지고 있다는 것이다.

```java
public class Thread implements Runnable {
    // Thread 클래스 내부
    ThreadLocal.ThreadLocalMap threadLocals = null;
}
```

```
Thread-1                              Thread-2
┌─────────────────────┐              ┌─────────────────────┐
│ threadLocals (Map)   │              │ threadLocals (Map)   │
│ ┌─────────┬────────┐│              │ ┌─────────┬────────┐│
│ │ TL-ref1 │ "userA"││              │ │ TL-ref1 │ "userB"││
│ ├─────────┼────────┤│              │ ├─────────┼────────┤│
│ │ TL-ref2 │  conn1 ││              │ │ TL-ref2 │  conn2 ││
│ └─────────┴────────┘│              │ └─────────┴────────┘│
└─────────────────────┘              └─────────────────────┘
         ↑                                     ↑
    ThreadLocal 객체 자체는 같지만, 각 스레드의 Map에 별도의 값이 저장된다
```

`ThreadLocal.get()`을 호출하면 다음과 같은 과정을 거친다.

```java
public T get() {
    Thread t = Thread.currentThread();           // 1. 현재 스레드 획득
    ThreadLocalMap map = getMap(t);              // 2. 스레드의 ThreadLocalMap 획득
    if (map != null) {
        ThreadLocalMap.Entry e = map.getEntry(this); // 3. this(ThreadLocal 인스턴스)를 키로 검색
        if (e != null) {
            T result = (T) e.value;
            return result;                       // 4. 값 반환
        }
    }
    return setInitialValue();                    // 5. 없으면 초기값 설정 후 반환
}
```

여기서 `ThreadLocalMap`의 Entry는 `WeakReference<ThreadLocal<?>>`를 키로 사용한다.

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;
    Entry(ThreadLocal<?> k, Object v) {
        super(k);   // key는 WeakReference
        value = v;  // value는 강한 참조
    }
}
```

키를 WeakReference로 감싸는 이유는, `ThreadLocal` 객체에 대한 외부 참조가 사라졌을 때 GC가 키를 수거할 수 있도록 하기 위함이다. 다만 이 구조가 메모리 누수의 원인이 되기도 하는데, 이는 동시성 섹션에서 다룬다.

### 스택 지역 변수와의 차이

스레드의 스택 지역 변수와 스레드 로컬은 모두 "스레드별 독립 데이터"를 다루지만, 근본적으로 다른 메커니즘이다.

| 구분 | 스택 지역 변수 | ThreadLocal |
|------|---------------|-------------|
| 저장 위치 | 스택 메모리 (스택 프레임 내부) | 힙 메모리 (Thread 객체의 ThreadLocalMap) |
| 생존 범위 | 메서드 호출 동안만 유효 | 스레드가 살아있는 동안 유효 |
| 접근 범위 | 해당 메서드 내부에서만 접근 가능 | 같은 스레드 내 어디서든 접근 가능 |
| 전파 방식 | 메서드 매개변수로 전달해야 함 | 매개변수 없이 `get()`으로 접근 |
| 메모리 관리 | 메서드 종료 시 자동 해제 | `remove()`를 호출하여 명시적으로 해제 필요 |

스택 지역 변수는 **메서드 스코프**, 스레드 로컬은 **스레드 스코프**에 묶여 있다. 깊은 호출 체인에서 매번 매개변수로 값을 전달하지 않고도 특정 값을 공유해야 하는 상황에서 스레드 로컬이 필요하다.

```
예) Spring MVC에서 HTTP 요청 처리 흐름

Controller → Service → Repository → Util
    ↓            ↓          ↓          ↓
  모든 레이어에서 현재 사용자 정보가 필요하다면?

방법 1: 매개변수로 전달 (번거로움)
  controller(userId) → service(userId) → repository(userId) → util(userId)

방법 2: ThreadLocal 사용 (깔끔함)
  SecurityContextHolder.set(userInfo)  ← 한 번 저장
  controller() → service() → repository() → util()
                                              ↓
                                SecurityContextHolder.get()  ← 어디서든 접근
```

---

## 동시성

### 스레드 로컬에서의 동시성 문제

`ThreadLocal`은 스레드마다 독립적인 값을 가지므로, 동시성(race condition) 문제가 발생하지 않을 것 같지만, 실제 운영 환경에서는 다음과 같은 문제가 발생할 수 있다.

#### 1. 스레드 풀 재사용에 의한 데이터 오염

가장 흔하고 치명적인 문제이다. 스레드 풀에서 스레드는 재사용되기 때문에, 이전 요청에서 설정한 `ThreadLocal` 값이 다음 요청에서도 남아있을 수 있다.

```java
private static final ThreadLocal<String> userContext = new ThreadLocal<>();

// 요청 1: Thread-1이 처리
userContext.set("userA");
// ... 비즈니스 로직 ...
// remove()를 호출하지 않음!

// 요청 2: Thread-1이 재사용되어 처리
String user = userContext.get(); // "userA"가 반환됨 → 다른 사용자의 데이터 노출!
```

이 문제는 보안 사고로 직결된다. 사용자 A의 인증 정보가 사용자 B의 요청에서 노출될 수 있다. 따라서 `ThreadLocal`을 사용한 후에는 **반드시 `remove()`를 호출**해야 한다.

```java
// 올바른 사용 패턴: try-finally로 반드시 정리
public void handleRequest(String userId) {
    try {
        userContext.set(userId);
        process();
    } finally {
        userContext.remove();  // 반드시 제거
    }
}
```

#### 2. 메모리 누수

앞서 `ThreadLocalMap`의 Entry가 `WeakReference`를 키로 사용한다고 설명하였다. `ThreadLocal` 객체에 대한 외부 참조가 사라지면 GC가 키를 수거하지만, **값(value)은 강한 참조로 남아있다**.

```
Entry 상태 변화:

정상 상태:
  Entry { key: WeakRef(ThreadLocal) → [살아있음], value: Object }

ThreadLocal 참조 해제 후:
  Entry { key: WeakRef(ThreadLocal) → null (GC 수거됨), value: Object ← 여전히 강한 참조 }
```

키가 null이 된 Entry를 "stale entry"라고 하며, 이 value는 GC의 대상이 되지 않는다. `ThreadLocalMap`은 내부적으로 `get()`, `set()`, `remove()` 호출 시 stale entry를 정리하는 로직이 있지만, 해당 메서드가 호출되지 않으면 메모리 누수가 지속된다.

특히 스레드 풀 환경에서는 스레드가 오래 살아있기 때문에, 스레드에 연결된 `ThreadLocalMap`도 오래 유지되어 누수가 누적될 수 있다.

```java
// 메모리 누수가 발생하는 패턴
public class LeakyService {
    public void process() {
        ThreadLocal<byte[]> local = new ThreadLocal<>();
        local.set(new byte[1024 * 1024]); // 1MB 할당
        // local 변수는 메서드 종료 시 사라지지만
        // ThreadLocalMap 내부의 value(1MB)는 남아있다
        // remove()를 호출하지 않으면 스레드가 살아있는 동안 해제되지 않는다
    }
}
```

#### 3. InheritableThreadLocal의 한계

`InheritableThreadLocal`은 부모 스레드의 값을 자식 스레드가 상속받을 수 있게 해준다.

```java
private static final InheritableThreadLocal<String> context = new InheritableThreadLocal<>();

context.set("parentValue");

Thread child = new Thread(() -> {
    System.out.println(context.get()); // "parentValue" 출력
});
child.start();
```

그러나 스레드 풀 환경에서는 문제가 된다. `InheritableThreadLocal`은 **스레드 생성 시점**에만 부모의 값을 복사하기 때문에, 스레드 풀에서 이미 생성된 스레드가 재사용되면 값이 전파되지 않는다.

```java
ExecutorService pool = Executors.newFixedThreadPool(1);

context.set("request-1");
pool.submit(() -> System.out.println(context.get())); // "request-1" (스레드 최초 생성 시 복사됨)

context.set("request-2");
pool.submit(() -> System.out.println(context.get())); // "request-1" (스레드 재사용, 갱신 안 됨!)
```

---

## 사용처

### Spring SecurityContextHolder

Spring Security는 현재 인증된 사용자 정보를 `SecurityContextHolder`를 통해 관리하며, 기본 전략이 `ThreadLocal`이다.

```java
public class SecurityContextHolder {
    private static final ThreadLocal<SecurityContext> contextHolder = new ThreadLocal<>();

    public static SecurityContext getContext() {
        SecurityContext ctx = contextHolder.get();
        if (ctx == null) {
            ctx = createEmptyContext();
            contextHolder.set(ctx);
        }
        return ctx;
    }
}
```

이를 통해 Controller, Service, Repository 등 어떤 레이어에서든 매개변수 전달 없이 현재 사용자의 인증 정보를 조회할 수 있다.

```java
// Service 레이어에서 현재 사용자 조회
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();
```

Spring Security의 `SecurityContextPersistenceFilter`가 요청 시작 시 `SecurityContext`를 설정하고, 요청 종료 시 `clearContext()`를 호출하여 ThreadLocal을 정리한다.

### Spring TransactionSynchronizationManager

Spring의 트랜잭션 관리에서도 `ThreadLocal`이 핵심적으로 사용된다. `TransactionSynchronizationManager`는 현재 스레드에 바인딩된 데이터베이스 커넥션, 트랜잭션 상태 등을 `ThreadLocal`로 관리한다.

```java
public abstract class TransactionSynchronizationManager {
    private static final ThreadLocal<Map<Object, Object>> resources =
        new NamedThreadLocal<>("Transactional resources");

    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
        new NamedThreadLocal<>("Transaction synchronizations");

    private static final ThreadLocal<String> currentTransactionName =
        new NamedThreadLocal<>("Current transaction name");

    private static final ThreadLocal<Boolean> currentTransactionReadOnly =
        new NamedThreadLocal<>("Current transaction read-only status");

    private static final ThreadLocal<Integer> currentTransactionIsolationLevel =
        new NamedThreadLocal<>("Current transaction isolation level");

    private static final ThreadLocal<Boolean> actualTransactionActive =
        new NamedThreadLocal<>("Actual transaction active");
}
```

`@Transactional`이 선언된 메서드 진입 시 커넥션을 ThreadLocal에 바인딩하고, 같은 트랜잭션 내에서 호출되는 모든 DAO/Repository가 동일한 커넥션을 사용하도록 보장한다. 이것이 선언적 트랜잭션이 작동하는 기반이다.

### MDC (Mapped Diagnostic Context)

Logback, Log4j2 등 로깅 프레임워크에서 제공하는 MDC도 `ThreadLocal`을 기반으로 동작한다. 요청마다 고유한 추적 ID를 설정하면, 해당 스레드에서 출력되는 모든 로그에 자동으로 추적 ID가 포함된다.

```java
// 요청 인터셉터 또는 필터에서 설정
public class RequestTraceFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        try {
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            MDC.put("traceId", traceId);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();  // 반드시 정리
        }
    }
}
```

```xml
<!-- logback.xml -->
<pattern>%d{HH:mm:ss} [%thread] [traceId=%X{traceId}] %-5level %logger - %msg%n</pattern>
```

```
14:23:01 [http-nio-8080-exec-1] [traceId=a3f1b2c4] INFO  UserService - 사용자 조회 시작
14:23:01 [http-nio-8080-exec-1] [traceId=a3f1b2c4] DEBUG UserRepository - SELECT * FROM users WHERE id = ?
14:23:02 [http-nio-8080-exec-1] [traceId=a3f1b2c4] INFO  UserService - 사용자 조회 완료
```

한 요청의 전체 흐름을 하나의 traceId로 추적할 수 있어, 분산 시스템에서의 디버깅에 필수적이다.

---

## 버추얼 스레드와 스레드 로컬

### 버추얼 스레드 환경에서의 문제점

Java 21에서 정식 도입된 버추얼 스레드는 기존 `ThreadLocal`과 함께 사용할 때 몇 가지 문제가 있다.

1. **대량 생성 시 메모리 부담**: 버추얼 스레드는 수백만 개까지 생성할 수 있는데, 각 버추얼 스레드마다 `ThreadLocalMap`이 할당되면 메모리 사용량이 급격히 증가한다.
2. **스레드 풀 재사용 패턴 불일치**: 버추얼 스레드는 스레드 풀링 없이 Task 당 하나씩 생성하는 것이 권장되므로, 기존 스레드 풀 기반의 ThreadLocal 정리 패턴(필터에서 remove)과 맞지 않는다.
3. **Mutable 상태 공유**: ThreadLocal은 변경 가능한(mutable) 값을 저장할 수 있어, 구조화된 동시성(Structured Concurrency)의 원칙과 충돌한다.

### ScopedValue (Java 21 Preview, Java 25 정식)

이러한 문제를 해결하기 위해 `ScopedValue`가 도입되었다. `ScopedValue`는 불변(immutable)이고, 정해진 스코프 내에서만 유효하며, 자식 스레드에 자동으로 전파된다.

```java
private static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

// 값 바인딩: 스코프 내에서만 유효
ScopedValue.runWhere(CURRENT_USER, "userA", () -> {
    System.out.println(CURRENT_USER.get()); // "userA"
    process();  // 하위 호출에서도 접근 가능
});
// 스코프를 벗어나면 자동으로 해제됨 → remove() 필요 없음
```

| 구분 | ThreadLocal | ScopedValue |
|------|-------------|-------------|
| 변경 가능성 | mutable (set으로 수정 가능) | immutable (스코프 내 불변) |
| 생존 범위 | 스레드 전체 | 정해진 스코프(runWhere) 내부 |
| 자식 스레드 전파 | InheritableThreadLocal 필요 | 자동 전파 (StructuredTaskScope) |
| 해제 | remove() 명시 호출 필요 | 스코프 종료 시 자동 해제 |
| 메모리 | 스레드마다 Map 유지 | 스코프 기반, 경량 |

버추얼 스레드 환경에서는 `ScopedValue`가 권장되며, 기존 `ThreadLocal` 기반의 라이브러리들도 점진적으로 마이그레이션되고 있다.

---

## 참조

- ThreadLocal 내부 구조
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html

- ScopedValue (JEP 481)
  - https://openjdk.org/jeps/481

- Spring Security ThreadLocal 전략
  - https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html

- TransactionSynchronizationManager
  - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html
