# 📝 TIL: 객체 간의 관계를 조직하는 구조 패턴 (Structural Patterns)

**Tags:** #DesignPattern #Kotlin #SoftwareArchitecture #OOP

## 📌 개요

구조 패턴은 클래스나 객체들을 조합하여 더 큰 구조를 만드는 패턴입니다.    
단순히 상속을 통한 확장이 아닌, 객체 간의 효율적인 결합을 통해 유연하고 유지보수하기 쉬운 시스템을 만드는 방법을 다룹니다.

-----

## 1. 어댑터 패턴 (Adapter Pattern)

### 💡 개념

호환되지 않는 인터페이스를 가진 클래스들이 함께 작동할 수 있도록 인터페이스를 변환해주는 패턴입니다.

### 💻 코드 예시 (Kotlin)

어댑터 패턴은 주로 합성(Composition)을 이용하거나 상속(Inheritance)을 이용하여 구현합니다.

```kotlin
// 1. 합성을 이용한 어댑터 (권장)
class PaymentApiClient { fun pay() { ... } }

interface PaymentRepository { fun pay() }

class PaymentRepositoryImpl(
    private val paymentApiClient: PaymentApiClient
): PaymentRepository {
    override fun pay() {
        // 클라이언트는 PaymentRepository에 의존하므로, 내부 구현체(ApiClient)가 바뀌어도 영향받지 않음
        paymentApiClient.pay()
    }
}

// 2. 상속을 이용한 어댑터
class PaymentRepositoryInheritImpl : PaymentRepository, PaymentApiClient() {
    override fun pay() {
        this.callPayApi() // 부모 클래스의 메서드 호출
    }
}
```

### 🔑 핵심 요약

* **장점:** 기존 코드를 변경하지 않고(OCP) 새로운 인터페이스와 연결할 수 있습니다.
* **특징:** 클라이언트는 어댑터 인터페이스에만 의존하므로 결합도가 낮아집니다.

-----

## 2. 브릿지 패턴 (Bridge Pattern)

### 💡 개념

추상화(Abstraction)와 구현(Implementation)을 분리하여 각각 독립적으로 변형 및 확장할 수 있게 하는 패턴입니다.

### 💻 코드 예시 (Kotlin)

```kotlin
interface Order {
    val orderItemRepository: OrderItemRepository
    fun fetchBy(): OrderItem
}

interface OrderItemRepository {
    fun fetchBy(id: Long): OrderItem
}

// 구현부 (Implementation): DB, 캐시 등 구체적인 저장소 구현
class OrderItemCacheRepository(private val cacheManager: CacheManager): OrderItemRepository {
    override fun fetchBy(id: Long): OrderItem { ... }
}

// 추상화부 (Abstraction): 기능 확장 (예: 일반 주문, 예약 주문 등)
class OrderImpl(
    override val orderItemRepository: OrderItemRepository,
    private val id: Long
): Order {
    override fun fetchBy(): OrderItem {
        // 구체적인 구현(DB인지 캐시인지)은 모른 채 인터페이스만 사용
        return this.orderItemRepository.fetchBy(this.id)
    }
}
```

### 🔑 핵심 요약

* **장점:** 추상화와 구현이 분리되어 독립적인 확장이 가능하며, 결합도가 낮아집니다.
* **단점:** 계층 구조가 늘어나 코드가 복잡해질 수 있습니다.

-----

## 3. 컴포지트 패턴 (Composite Pattern)

### 💡 개념

객체들을 트리 구조로 구성하여 개별 객체(Leaf)와 복합 객체(Composite)를 클라이언트가 동일하게 다룰 수 있도록 하는 패턴입니다.

### 💻 코드 예시 (Kotlin)

```kotlin
interface Node {
    fun print()
}

// 복합 객체 (Composite)
class Folder : Node {
    private val files: MutableList<Node> = mutableListOf()

    fun add(file: Node) = this.files.add(file)
    
    // 재귀적으로 자식들의 print 수행
    override fun print() {
        this.files.forEach { it.print() }
    }
}

// 개별 객체 (Leaf)
class File : Node {
    override fun print() {
        println("$this 호출")
    }
}

fun main() {
    val folder = Folder()
    folder.add(File()) 
    folder.add(Folder().apply { add(File()) }) // 폴더 안에 폴더 넣기 가능
    folder.print()
}
```

### 🔑 핵심 요약

* **장점:** 재귀를 통해 복잡한 트리 구조를 단순하게 제어할 수 있습니다.
* **단점:** 공통 인터페이스 설계가 까다롭고, 특수한 제약을 걸기 어렵습니다.

-----

## 4\. 데코레이터 패턴 (Decorator Pattern)

### 💡 개념

객체에 동적으로 새로운 책임을 추가(확장)할 때 사용하는 패턴입니다. 상속 대신 합성을 사용하여 유연하게 기능을 덧붙입니다.

### 💻 코드 예시 (Kotlin)

```kotlin
interface OrderService { fun order() }
class OrderServiceImpl : OrderService { 
    override fun order() { println("주문 생성") } 
}

// 데코레이터 추상 클래스
abstract class OrderServiceDecorator(
    private val orderService: OrderService
) : OrderService {
    override fun order() = orderService.order()
}

// 기능 추가 1: 포인트 적립
class PointAccumulatorDecorator(service: OrderService) : OrderServiceDecorator(service) {
    override fun order() {
        super.order()
        println("포인트 적립")
    }
}

// 기능 추가 2: 알림 발송
class NotificationDecorator(service: OrderService) : OrderServiceDecorator(service) {
    override fun order() {
        super.order()
        println("메일 발송")
    }
}

fun main() {
    // 주문 -> 포인트 적립 -> 알림 발송 순으로 기능 결합
    val service = NotificationDecorator(PointAccumulatorDecorator(OrderServiceImpl()))
    service.order()
}
```

### 🔑 핵심 요약

* **장점:** 런타임에 유연하게 기능을 조합하거나 변경할 수 있습니다.
* **단점:** 작은 객체들이 많이 생성되며, 데코레이터 제거 시 순서 의존성 문제가 발생할 수 있습니다.

-----

## 5. 퍼사드 패턴 (Facade Pattern)

### 💡 개념

복잡한 서브 시스템들의 인터페이스를 통합하여, 사용하기 쉬운 하나의 간략한 인터페이스를 제공하는 패턴입니다.

### 💻 코드 예시 (Kotlin)

```kotlin
class OrderFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService
) {
    // 클라이언트는 이 메서드 하나만 호출하면 복잡한 흐름이 자동 처리됨
    fun placeOrder() {
        orderService.create()
        paymentService.pay()
    }
}

class OrderController(private val orderFacade: OrderFacade) {
    fun pay() {
        orderFacade.placeOrder()
    }
}
```

### 🔑 핵심 요약

* **장점:** 클라이언트와 복잡한 서브 시스템 간의 결합도를 낮춥니다.
* **단점:** 퍼사드 객체가 모든 의존성을 짊어진 'God Object'가 될 위험이 있습니다.

-----

## 6. 플라이웨이트 패턴 (Flyweight Pattern)

### 💡 개념

많은 수의 유사한 객체들을 생성해야 할 때, 공통된 부분을 공유하여 메모리 사용량을 줄이는 패턴입니다.

### 💻 코드 예시 (Kotlin)

```kotlin
interface PaymentGateway { fun pay() }

// 메모리에 로드해두고 재사용할 객체들
class TossPayment : PaymentGateway { override fun pay() { ... } }
class NaverPayment : PaymentGateway { override fun pay() { ... } }

class PaymentService {
    // 이미 생성된 인스턴스를 저장하는 캐시 저장소
    private val pgMap = mutableMapOf<String, PaymentGateway>()

    fun getPaymentGateway(type: String): PaymentGateway {
        return pgMap.computeIfAbsent(type) { 
            when(it) {
                "TOSS" -> TossPayment()
                "NAVER" -> NaverPayment()
                else -> throw IllegalArgumentException()
            }
        }
    }
}
```

### 🔑 핵심 요약

* **장점:** 인스턴스 생성을 줄여 메모리를 최적화하고 성능을 향상시킵니다.
* **단점:** 공유 객체의 상태 관리가 복잡해질 수 있어, 스레드 안전성(Thread-Safety)에 유의해야 합니다.

-----

## 7. 프록시 패턴 (Proxy Pattern)

### 💡 개념

실제 객체에 대한 접근을 제어하거나 기능을 추가하기 위해 대리자(Proxy) 객체를 사용하는 패턴입니다.

### 💻 코드 예시 (Kotlin)

```kotlin
interface OrderService {
    fun fetchBy(id: Long): Order
}

class OrderServiceImpl : OrderService {
    override fun fetchBy(id: Long): Order { 
        println("DB 조회")
        return Order() 
    }
}

class CachingOrderServiceProxy(
    private val cacheManager: CacheManager,
    private val targetService: OrderService
) : OrderService {
    override fun fetchBy(id: Long): Order {
        // 1. 캐시 확인 (접근 제어 및 부가 기능)
        return cacheManager.get(id) 
            ?: targetService.fetchBy(id).also { 
                // 2. 실제 객체 호출 후 캐싱
                cacheManager.put(id, it) 
            }
    }
}
```

### 🔑 핵심 요약

* **장점:** 원래 코드를 수정하지 않고 접근 제어, 캐싱, 로깅 등의 전/후처리가 가능합니다.
* **단점:** 호출 단계를 한 번 더 거치므로 응답 속도가 미세하게 느려질 수 있습니다.