# [Design Pattern] 객체 생성 패턴 (Creational Patterns) 정리

- **Tags:** #DesignPattern #CreationalPattern #Kotlin #OOP #Refactoring

---

### 무엇을 배웠는가?
객체지향 프로그래밍에서 **객체의 생성 과정**을 추상화하여, 객체 생성과 조합을 캡슐화하는 5가지 주요 생성 패턴(Creational Patterns)에 대해 학습했습니다.    
각 패턴이 해결하고자 하는 문제 상황과 장단점, 그리고 Kotlin 구현 예제를 정리합니다.

---

### 상세 내용

#### 1. 싱글톤 패턴 (Singleton Pattern)
클래스의 인스턴스를 **오직 하나만 생성**하여 전역에서 접근 가능하게 하는 패턴입니다.

* **장점**:
    * 메모리 낭비 방지 (고정된 메모리 영역 사용).
    * 데이터 공유가 쉬움.
    * 상태가 없는(Stateless) 유틸리티 클래스나 순수 계산 로직에 적합.
* **주의점 (동시성 문제)**:
    * 상태(State)를 가진 객체를 싱글톤으로 만들면, 멀티 스레드 환경에서 동시성 문제(Race Condition)가 발생할 수 있습니다.
    * 해결책: 상태를 갖지 않게 설계하거나, `synchronized` 키워드, 불변 객체 등을 활용해야 합니다.

```kotlin
// 🚫 Bad Case: 상태를 가진 싱글톤 (동시성 문제 발생 위험)
@Service
class OrderService(private val orderRepository: OrderRepository) {
    var totalAmount = 0L // 공유 변수

    fun order(amount: Long) {
        // 여러 스레드가 동시에 접근 시 데이터 정합성이 깨짐
        totalAmount += amount 
    }
}

// ✅ Good Case: 상태가 없는 싱글톤 (지역 변수 활용)
@Service
class OrderService(private val orderRepository: OrderRepository) {
    fun order(amount: Long) {
        var totalAmount = 0L // 스레드마다 별도의 스택 메모리에 생성됨
        totalAmount += amount 
        // 로직 수행...
    }
}
````

#### 2. 팩토리 메서드 패턴 (Factory Method Pattern)

객체 생성을 서브 클래스(팩토리)에 위임하여, **클라이언트가 구체적인 클래스에 의존하지 않게** 하는 패턴입니다.

* **특징**: 조건에 따라 동적으로 인스턴스를 결정하여 반환합니다.
* **장점**:
    * **OCP(개방-폐쇄 원칙)** 준수: 새로운 결제 수단이 추가되어도 클라이언트 코드는 변경할 필요가 없습니다.
    * 결합도를 낮추고 유연성을 높임.

```kotlin
class PaymentGatewayFactory(private val payments: List<PaymentServce>) {
    private val paymentMap = payments.associatedBy { it.type }
    
    fun route(type: PaymentType): PaymentServce {
        return this.paymentMap[type]?: throw NotSupportedException()
    }
}
interface PaymentService {
    val type: PaymentType
}

enum class PaymentType { TOSS, DANAL, NAVER }

class TossPayment: PaymentService {
    override val type: PaymentType get() = PaymentType.TOSS
}
class Danal: PaymentService {
    override val type: PaymentType get() = PaymentType.DANAL
}
class NaverPay: PaymentService {
    override val type: PaymentType get() = PaymentType.NAVER

```

#### 3. 추상 팩토리 패턴 (Abstract Factory Pattern)

관련된 객체들의 군집(Family)을 생성하기 위한 인터페이스를 제공하는 패턴입니다.

* **특징**: 팩토리 메서드가 하나의 객체를 만든다면, 추상 팩토리는 관련된 여러 객체(`ReviewCreator`, `ReviewDestroyer`)를 한 번에 묶어서 생성합니다.
* **장점**: 관련된 객체들 간의 일관성을 보장합니다.
* **단점**: 새로운 종류의 제품(인터페이스)을 추가하기 어렵습니다. (모든 팩토리 구현체를 수정해야 함).

```kotlin
interface AbstractReviewFactory {
    fun createReview(): ReviewCreator
    fun deleteReview(): ReviewDestroyer
}

// 배민 관련 객체 군집 생성
class BaeminReviewFactory : AbstractReviewFactory {
    override fun createReview() = BaeminReviewCreator()
    override fun deleteReview() = BaeminReviewDestroyer()
}
```

#### 4. 빌더 패턴 (Builder Pattern)

복잡한 객체의 생성 과정과 표현 방법을 분리하여, **동일한 생성 절차에서 서로 다른 표현 결과**를 만들 수 있게 하는 패턴입니다.

* **사용 이유**:
    * 생성자 인자가 많을 때 가독성 향상.
    * 필요한 데이터만 설정하여 객체 생성 가능.
    * 객체 생성의 불변성 확보 및 검증 로직 캡슐화.
* **단점**: 코드가 다소 장황해질 수 있음 (Kotlin에서는 `named argument`나 `apply`로 대체되는 경우가 많음).

```kotlin
class OrderBuilder {
    private var id: Long = 0L
    // ... 기타 필드

    fun id(id: Long): OrderBuilder {
        this.id = id
        return this
    }
    // ... 기타 메서드 (Chaining 지원)

    fun build(): Order {
        // 최종적으로 캡슐화된 객체 반환
        return Order(id, amount, productNames, status)
    }
}
```

#### 5. 프로토타입 패턴 (Prototype Pattern)

기존 인스턴스를 복제(Clone)하여 새로운 객체를 생성하는 패턴입니다.

* **사용 이유**:
    * 객체 생성 비용(DB 조회 등)이 높을 때 효율적.
    * 객체의 현재 상태(Snapshot)를 그대로 보존하여 복사하고 싶을 때.
* **구현**: Java의 `Cloneable` 인터페이스를 사용하거나, Kotlin의 `data class`가 제공하는 `copy()` 메서드를 활용합니다.

```kotlin
data class Order(val id: Long) // Kotlin data class는 copy() 자동 지원

fun main() {
    val original = Order(1L)
    
    // 내부 상태를 유지하며 새로운 인스턴스 생성
    val clone = original.copy(id = 2L) 
}
```

---

### 핵심

* **싱글톤**은 상태 관리에 각별히 유의해야 하며, 가급적 무상태(Stateless)로 설계해야 한다.
* **팩토리 패턴**류(메서드, 추상)는 객체 생성의 책임을 분리하여 코드의 결합도를 낮추는 데 탁월하다.
* **빌더**는 복잡한 객체 생성의 가독성을, **프로토타입**은 객체 생성의 비용 절감을 도와준다.
* 상황에 맞는 생성 패턴을 적절히 사용하여, **유지보수성**과 **확장성**이 뛰어난 코드를 작성하자.