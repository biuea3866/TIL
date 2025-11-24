 # 유연한 설계
## 개방-폐쇄 원칙
개방 폐쇄 원칙은 변화에 유연하게 대응하는 원칙 중 하나이다.   
* 확장에 대해 열려있다: 요구사항이 변경될 때 이 변경에 맞게 새로운 동작을 추가하여 애플리케이션을 확장할 수 있다.
* 수정에 대해 닫혀 있다: 기존의 코드를 수정하지 않고 애플리케이션의 동작을 추가하거나 변경할 수 있다.

기존의 코드를 수정하지 않고 확장을 하지 않는다느 것은 코드를 컴파일 수준에서 고정시키고 런타임에서 의존성을 유연하게 변경함을 의미한다.

```kotlin
class Order(
     val id: Long,
     var amount: Long
) {
    fun pay(payment: Payment) {
        payment.pay(amount)
    }
}

interface Payment {
    fun pay(amount: Long)
}

class TossPayment : Payment {
     override fun pay(amount: Long) { ... }
}

class NaverPay: Payment {
    override fun pay(amount: Long) { ... }
}

fun main() {
    val order = Order(0L, 100)
    order.pay(TossPayment())
}
```

Order 객체는 최종적인 결제를 위해 컴파일 타임엔 Payment라는 인터페이스를 의존하고, 런타임에 클라이언트로부터 구현체를 전달받는다.   
물론 클라이언트가 결국엔 구현체를 의존하게 되므로 저수준에 의존하지만, 당장 Order의 관점에서는 고수준에 의존하여 애플리케이션 확장을 하여도 코드의 변함은 없다.   

개방-폐쇄 원칙은 변하지 않는 결제라는 추상화와 변하는 결제 수단을 분리하고, 추상화에 의존시킴으로써 확장에 유연하게 대응할 수 있다.

## 생성 사용 분리
위의 예제는 여전히 클라이언트가 고수준인 `TossPayment`에 의존하는 문제점이 존재한다.   
이는 새로운 결제 수단을 추가할 경우 클라이언트의 코드 또한 변경될 수 있음을 시사한다.   

이 상황을 없애기 위해 생성, 사용을 분리하여 생성을 별도의 컨텍스트로 구성할 수 있다.  
별도의 컨텍스트로 구성함으로써 객체 생성의 지식을 클라이언트는 몰라도 되며, 이를 Factory라 부른다.   

```kotlin
class Order(
     val id: Long,
     var amount: Long
) {
    fun pay(payment: Payment) {
        payment.pay(amount)
    }
}

enum class PaymentType {
    TOSS,
    NAVER
}

class PaymentFactory {
    fun getPayment(type: PaymentType) {
        return when(type) {
            TOSS -> TossPayment()
            NAVER -> NaverPay()
        }
    }
}

interface Payment {
    fun pay(amount: Long)
}

class TossPayment : Payment {
     override fun pay(amount: Long) { ... }
}

class NaverPay: Payment {
    override fun pay(amount: Long) { ... }
}

fun main() {
    val order = Order(0L, 100)
    order.pay(PaymentFactory().getPayment(PaymentType.TOSS))
}
```
Factory를 분리함으로써 클라이언트는 사용만할 뿐 어떤 구현체를 알아야하는지에 대한 지식이 필요없게 되었다.   

도메인 모델에 담겨 있는 개념과 관계르 ㄹ따라 도메인과 소프트웨어 간의 표현적 차이를 최소화하는 것을 표현적 분해라고한다.  
표현적 분해는 도메인 객체에게 다양한 책임을 부여하고, 문제를 해결하기 위해 시스템을 분해하는 것이다.   
하지만 도메인 모델만 분리한다고해서 완벽하게 분해가 되지 않을뿐더러 소프트웨어가 완벽하게 구성되지 않는다.   
이럴 때 편의를 위해 임의의 객체에게 책임을 할당하여 문제를 해결하는데 이를 순수한 가공물이라 부르며, 행위적 분해라 표현하고 위의 Factory가 이에 대한 결과물이다.  
Factory가 없었다면 클라이언트는 계속해서 저수준 구현체를 의존하여, 애플리케이션 확장 시 항상 코드를 변경해야하는 위험 부담이 있었겠지만 Factory라는 순수한 가공물에 생성 책임을 줌으로써 높은 응집도와 낮은 결합도를 달성했다.   

## 의존성 주입
Order는 결제 수단을 전달받아 결제를 사용하는 객체이다.   
스스로 어떤 결제 수단을 결정할지에 대한 결정권은 없으며, 한번 생성되면 변경이 불가하다.   
이를 해결하기 위해 의존성 주입을 통해 런타임에 의존 대상을 교체할 수 있다.

```kotlin
class Order(
     val id: Long,
     var amount: Long
) {
    private lateinit var payment: Payment
    
    fun pay() {
        this.payment.pay(amount)
    }
    
    fun inject(payment: Payment) {
        this.payment = payment
    }
}

enum class PaymentType {
    TOSS,
    NAVER
}

class PaymentFactory {
    fun getPayment(type: PaymentType) {
        return when(type) {
            TOSS -> TossPayment()
            NAVER -> NaverPay()
        }
    }
}

interface Payment {
    fun pay(amount: Long)
}

class TossPayment : Payment {
     override fun pay(amount: Long) { ... }
}

class NaverPay: Payment {
    override fun pay(amount: Long) { ... }
}

fun main() {
    val order = Order(0L, 100)
    order.inject(PaymentFactory().getPayment(PaymentType.TOSS))
    order.pay()
    order.inject(PaymentFactory().getPayment(PaymentType.NAVER))
    order.pay()
}
```
이렇게 inject라는 setter 형태의 메서드 호출 주입을 통해 의존성 주입으로 런타임에 언제든지 의존성을 해결할 수 있다.   

## 의존성 역전 원칙
이제까지의 예제와 설명은 고수준의 인터페이스를 의존하여 저수준의 변경이 고수준에 영향을 미치지 않도록 하는 것이었다.  
만약 고수준이 저수준을 의존한다면, 저수준의 변경에 항상 고수준이 따라옴으로써 변경의 여파가 커지게 된다.   

```kotlin
class Order(
    val id: Long,
    var amount: Long
) {
    private lateinit var payment: NaverPay

    fun pay() {
        this.payment.pay(amount)
    }

    // 고수준이 저수준에 맞추어야하므로 다형성에 어긋나기 때문에 컴파일 에러가 발생한다.
    fun inject(payment: Payment) {
        this.payment = payment
    }
}
```
이 상황에서 만약 DanalPay가 추가되었다면, order 입장에서는 어떻게 DanalPay로 결제할 수 있겠는가?    
결제하기 위해선 Order객체에 강제로 DanalPay를 의존하도록 멤버 변수룰 추가하던지 하는 방법밖에 없을 것이고, pay 메서드는 어떤 결제 수단을 선택할 것인지에 대한 분기 추가에 따른 코드 변경이 필요하다.   
저수준의 확장 하나 때문에 고수준의 Order 객체는 상당한 코드 변경점이 생기게 된다.   

이렇듯 고수준(추상화)을 의존함으로써 확장에 유연하게 대처할 수 있다.   
의존성 역전 원칙은 의존의 방향성과 인터페이스의 소유권을 다룬다.   

Order는 클라이언트 패키지, Payment는 서버 패키지라고 가정해보면 다음과 같다.
```kotlin
[client]       [server]
  Order  -->   Payment <-- NaverPay
                        |- TossPayment
```
전통적인 패러다임 (Restful API)에서는 서버에서 인터페이스를 만들고, 클라이언트가 이 인터페이스를 의존하는 방식이다.  
둘 간의 컴파일 타임은 다르고, 중간에 JSON이라는 추상화 포맷이 있기 때문에 이들 간의 전통적인 패러다임은 올바르게 동작할 수 있다.  

하지만 객체지향에서는 컴파일을 위해 인터페이스 소유권을 변경할 필요가 있다.
Order 객체는 결제를 위해 항상 Payment 인터페이스를 의존해야하기 때문에 고수준임에도 server 패키지를 의존해야하는 문제 상황이 생긴다.  
만약 server 패키지를 의존하지 않는다면 Order는 컴파일에 실패하는 문제가 생긴다.

```kotlin
[client]                   [server]
  Order  -->   Payment <-- NaverPay
                        |- TossPayment
```
그래서 객체지향에선 이와 같이 인터페이스를 고수준에 위치시키고, 저수준에서는 고수준의 인터페이스를 구현하는 방식으로 이 문제를 해결할 수 있다.   
다만, client <> server와 같은 명시적인 고수준, 저수준을 나누었을 뿐이고 이 관계는 언제든지 역전될 수 있다.   
그래서 인터페이스만을 모아둔 별도의 모듈 혹은 패키지를 구성하고, 각 endpoint 패키지들은 이 인터페이스를 의존하여 구현체를 구성한다.