
# [객체지향] 유연한 설계를 위한 의존성 관리: 생성과 사용의 분리

- **Tags:** #Object #OOP #OCP #Factory #DIP

-----

### 무엇을 배웠는가?

변화에 유연한 코드를 만들기 위해서는 개방-폐쇄 원칙(OCP)을 지키는 것만으로는 부족하며, 객체의 생성(Creation)과 사용(Use)을 명확히 분리해야 함을 배웠습니다.   
이를 위해 Factory(순수한 가공물)를 도입하여 의존성 생성 책임을 위임하고, 의존성 주입(DI)을 통해 런타임에 동적으로 객체를 결정하는 과정을 코드로 구현했습니다.

-----

### 왜 중요하고, 어떤 맥락인가?

단순히 인터페이스를 도입한다고 해서 결합도가 완전히 사라지는 것은 아닙니다.   
만약 클라이언트가 인터페이스를 사용하더라도, 객체를 생성하는 시점에 `new TossPayment()`와 같이 구체적인 클래스를 호출한다면 여전히 **강한 결합**이 남아있기 때문입니다.

이 문제를 해결하지 않으면, 새로운 결제 수단이 추가될 때마다 클라이언트 코드를 계속 수정해야 합니다.  
따라서 **객체를 생성하는 책임**과 **객체를 사용하는 책임**을 분리하여, 사용하는 쪽(`Order`)에서는 구체적인 구현체에 대해 전혀 알지 못하도록(몰라도 되도록) 만들어야 진정한 유연성을 얻을 수 있습니다.

-----

### 상세 내용

#### 1. 개방-폐쇄 원칙(OCP)과 한계

OCP는 확장에 열려있고 수정에 닫혀 있어야 합니다.    
아래 코드에서 `Order`는 `Payment` 인터페이스에 의존하므로 결제 수단이 늘어나도 `Order`의 코드는 변하지 않을 것 같습니다.

```kotlin
class Order(val id: Long, var amount: Long) {
    // Order는 구체적인 구현체(Toss, Naver)를 모른다. (OCP 준수 시도)
    fun pay(payment: Payment) {
        payment.pay(amount)
    }
}

interface Payment {
    fun pay(amount: Long)
}

class TossPayment : Payment { override fun pay(amount: Long) { ... } }
class NaverPay : Payment { override fun pay(amount: Long) { ... } }
```

하지만 `main` 함수(클라이언트)를 보면 여전히 구체적인 클래스에 의존하고 있습니다.

```kotlin
fun main() {
    val order = Order(0L, 100)
    order.pay(TossPayment()) // 여기서 TossPayment를 직접 생성해야 하는 문제 발생
}
```

#### 2. 생성과 사용의 분리: Factory (순수한 가공물)

이 문제를 해결하기 위해 **생성 책임**만을 전담하는 객체인 `Factory`를 도입합니다. 도메인 모델에는 속하지 않지만, 설계를 위해 인위적으로 만든 순수한 가공물(Pure Fabrication)입니다.

```kotlin
enum class PaymentType { TOSS, NAVER }

// 생성 책임을 전담하는 Factory
class PaymentFactory {
    fun getPayment(type: PaymentType): Payment {
        return when(type) {
            PaymentType.TOSS -> TossPayment()
            PaymentType.NAVER -> NaverPay()
        }
    }
}
```

이제 클라이언트는 구체적인 결제 구현체(`TossPayment`)를 몰라도, `Factory`에게 요청만 하면 됩니다.

#### 3\. 의존성 주입(DI)을 통한 런타임 유연성 확보

이제 `Order` 객체는 스스로 의존성을 결정하지 않고, 외부(`main`이나 프레임워크)에서 주입해 주는 대로 동작합니다.    
이를 통해 컴파일 타임 의존성(Payment)과 런타임 의존성(TossPayment)이 완전히 분리됩니다.

```kotlin
class Order(
     val id: Long,
     var amount: Long
) {
    // 1. 의존성 선언: 구체 클래스가 아닌 인터페이스
    private lateinit var payment: Payment 
    
    // 2. 사용 로직: payment가 무엇이든 상관없이 pay 메시지 전송
    fun pay() {
        this.payment.pay(amount)
    }
    
    // 3. 의존성 주입(DI): 런타임에 의존성을 교체할 수 있는 통로
    fun inject(payment: Payment) {
        this.payment = payment
    }
}

fun main() {
    val order = Order(0L, 100)
    val factory = PaymentFactory()

    // Case A: 토스 결제 주입
    order.inject(factory.getPayment(PaymentType.TOSS))
    order.pay()

    // Case B: 네이버 결제로 런타임 교체 (Order 코드 수정 없음!)
    order.inject(factory.getPayment(PaymentType.NAVER))
    order.pay()
}
```

#### 4. 의존성 역전 원칙 (DIP)

마지막으로 패키지 구조의 관점에서, 고수준 모듈(`Order`)이 저수준 모듈(`TossPayment`)에 의존하는 것을 막아야 합니다.

* **Bad**: `Client Package (Order)` -> `Server Package (Payment Interface + Impl)`
* **Good**: `Client Package (Order + Payment Interface)` <- `Server Package (Payment Impl)`

인터페이스의 소유권을 클라이언트(`Order`) 쪽에 둠으로써 의존성의 방향을 역전시킵니다.

-----

### 핵심

객체지향 설계에서 유연함을 얻기 위해선 다음의 과정을 기억해야 합니다.

1.  **추상화에 의존**: `Order`는 `Payment` 인터페이스만 바라본다.
2.  **생성 분리**: `new` 연산자를 사용하는 생성 책임은 `Factory`에게 위임한다.
3.  **의존성 주입**: 외부에서 의존성을 주입받아 컴파일 타임 의존성(Interface)과 런타임 의존성(Instance)을 분리한다.
4.  **의존성 역전**: 인터페이스 소유권을 통해 고수준 모듈을 저수준 모듈의 변경으로부터 보호한다.