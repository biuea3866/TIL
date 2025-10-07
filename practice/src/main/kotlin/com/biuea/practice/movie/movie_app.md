# 영화 예매 애플리케이션
## 요구사항
1. 영화는 영화에 대한 정보를 표현한다.
   * 제목, 상영시간, 가격 정보와 같은 정보를 갖는다.
2. 상영은 실제 관객들이 영화를 관람하는 사건을 표현한다.
   * 상영 일자, 시간, 순번 등과 같은 정보를 갖는다.
3. 한 영화는 하루에 한 번 이상 상영될 수 있다.
4. 영화 할인 조건은 2가지의 규칙이 존재한다.
   * 할인 조건
     * 순서 조건, 기간 조건 2종류로 나뉜다.
       * 순서 조건: 상영 순번을 이용해 할인 여부를 결정한다.
       * 기간 조건: 영화 상셩 시작을 이용해 할인 여부를 결정한다. 
   * 할인 정책: 영화별로 하나의 할인 정책만 할당할 수 있다.
     * 금액 할인 정책, 비율 할인 정책 2종류로 나뉜다.
       * 금액 할인 정책: 예매 요금에서 일정 금액을 할인해주는 방식이다.
       * 비율 할인 정책: 정가에서 일정 비율의 요금을 할인해주는 방식이다.
   * 할인을 적용하기 위해서는 할인 조건, 할인 정책을 조합해서 사용할 수 있다.
   * 할인 조건이 만족된다면 할인 정책이 적용 가능하나, 만족하지 못한다면 정책이 적용되지 않는다.

---
## 객체 지향 프로그래밍에 들어가며
### 1. 어떤 클래스가 필요한지 고민하기 전에 어떤 객체가 필요한지를 고민해야한다.
클래스는 공통적인 상태와 행동을 공유하는 객체들을 추상화한 것이므로 어떤 객체들이 어떤 상태, 행동을 가지는지 전부 나열하고 결정해야한다.   
나열된 객체들을 분류하고 범주화함으로써 추상화된 클래스를 결정할 수 있다.

### 2. 객체를 독립적인 존재가 아니라 기능을 구현하기 위해 협력하는 공동체의 일원으로 봐야 한다.
객체는 다른 객체에게 도움을 주거나 의존하면서 살아가는 협력적인 존재이다.  
즉, 객체를 고립된 존재로 보지않고 협력에 참여하는 협력자로 바라보아야 한다.

### 3. 도메인은 문제 해결을 위해 사용자가 프로그램에서 사용하는 분야이다.
요구사항과 프로그램을 객체라는 동일한 관점에서 바로볼 수 있기에 도메인을 구성하는 개념들이 객체와 클래스로 연결될 수 있다.    
(네이밍, 비즈니스 규칙 ..)

---
## 클래스, 객체
### 자율적인 객체
1. 객체는 상태와 행동을 가진 복합적인 존재이다.
2. 객체는 스스로 판단하고, 행동하는 자율적인 존재이다.

객체 지향은 객체를 이용하여 데이터와 기능을 한 덩어리로 묶어 문제 영역의 아이디어를 표현한다. 이렇게 데이터와 기능을 객체 내부로 함께 묶는 것을 캡슐화라고 한다.   
캡슐화와 접근 제어를 통해 얻을 수 있는 이점은 객체를 자율적인 존재로 만들 수 있다.  
자율적인 존재란 스스로 상태를 관리하고, 판단하고, 행동함을 의미하는데 접근 제어와 캡슐화를 통해 외부의 간섭을 없애어 객체 스스로 행동할 수 있게 된다.   

### 협력하는 객체
객체는 퍼블릭 인터페이스를 통해 외부에 내부 상태 접근을 허용할 수 있다.   
객체가 다른 객체와 상호작용할 수 있는 방법은 메시지를 전송이며, 메시지를 수신하여 객체 스스로 요청을 처리할 수 있다.

---
## 코드
### Movie

```kotlin
class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _discountPolicy: DiscountPolicy
) {
    val fee get() = this._fee
    
    fun calculateMovieFee(screening: Screening): Money {
        return this._fee.minus(_discountPolicy.calculateDiscountAmount(screening))
    }
}
```
movie 클래스에서 영화 요금을 계산하기 위해 영화의 현재 할인 정책을 알아야 한다.  
이 코드에서는 discountPolicy라는 인터페이스를 이용하여 이 할인 정책에 대한 책임을 전가한다.

### Screening
```kotlin
class Screening(
    private var _movie: Movie,
    private var _sequence: Int,
    private var _whenScreened: LocalDateTime
) {
    val startTime get() = this._whenScreened
    val sequence get() = this._sequence
    val movieFee get() = this._movie.fee

    fun reservation(customer: Customer, audienceCount: Int): Reservation {
        return Reservation(customer, this, calculateFee(audienceCount), audienceCount)
    }

    private fun calculateFee(audienceCount: Int): Money {
        return _movie.calculateMovieFee(this).times(audienceCount.toDouble())
    }
}
```
screening의 reserve 메서드는 예매 후 예매 정 보를 담고 있는 Reservation 인스턴스를 생성 후 반환한다.    

```kotlin
data class Money(val amount: BigDecimal) {
    fun plus(amount: Money): Money {
        return this.copy(this.amount.add(amount.amount))
    }

    fun minus(amount: Money): Money {
        return this.copy(this.amount.subtract(amount.amount))
    }

    fun times(percent: Double): Money {
        return this.copy(this.amount.multiply(BigDecimal.valueOf(percent)))
    }

    fun isLessThan(other: Money): Boolean {
        return this.amount < other.amount
    }

    fun isGreaterThan(other: Money): Boolean {
        return this.amount > other.amount
    }

    companion object {
        val ZERO: Money = Money.wons(0)

        fun wons(amount: Long): Money {
            return Money(BigDecimal.valueOf(amount))
        }

        fun wons(amount: Double): Money {
            return Money(BigDecimal.valueOf(amount))
        }
    }
}
```
금액을 구현하기 위해 Long이라는 primitive 타입을 사용했다. 하지만 이는 변수의 크기나 연산자의 종류와 같이 추상적인 수준에서의 표현만 가능하다.   
money 클래스는 실제 금액을 구현하고, 의미를 전달할 수 있다.

```kotlin
class Reservation(
    private var _customer: Customer,
    private var _screening: Screening,
    private var fee: Money,
    private var audienceCount: Int
) {
}
```
영화를 예매하기 위해 Screening, Movie, Reservation 인스턴스들이 각자의 메서드를 호출하여 상호작용한다.   
즉, 시스템의 어떤 기능을 구현하기 위해 객체들 사이에 의존과 상호작용이 구성되고 이를 협력이라 부를 수 있다.

### DiscountPolicy
```kotlin
abstract class DiscountPolicy(
    private val conditions: MutableList<DiscountCondition> = mutableListOf()
) {
    fun calculateDiscountAmount(screening: Screening): Money {
        this.conditions.forEach {
            if (it.isSatisfiedBy(screening)) {
                return this.getDiscountAmount(screening)
            }
        }

        return Money.ZERO
    }

    protected abstract fun getDiscountAmount(screening: Screening): Money
}

class AmountDiscountPolicy(
    private var discountAmount: Money,
    private var conditions: MutableList<DiscountCondition>
): DiscountPolicy(conditions) {
    override fun getDiscountAmount(screening: Screening): Money {
        return this.discountAmount
    }
}

class PercentDiscountPolicy(
    private var percent: Double,
    private var conditions: MutableList<DiscountCondition>
): DiscountPolicy(conditions) {
    override fun getDiscountAmount(screening: Screening): Money {
        return screening.movieFee.times(percent)
    }
}

interface DiscountCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class SequenceCondition(private var sequence: Int): DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return screening.isSequence(this.sequence)
    }
}

class PeriodCondition(
    private var dayOfWeek: DayOfWeek,
    private var startTime: LocalDateTime,
    private val endTime: LocalDateTime
): DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return screening.startTime.dayOfWeek.equals(this.dayOfWeek)
                && this.startTime <= screening.startTime
                && this.endTime >= screening.startTime
    }
}
```
Movie에서 fee를 계산하기 위해 discountPolicy에게 calculateDiscountAmount 메시지를 전송하여 할인 요금을 계산했다.   
Movie가 직접 fee를 계산하는 것이 아닌 discountPolicy를 통해 계산의 책임을 위임하는 형식이다.  
이는 상속, 다형성, 추상화를 이용하여 객체지향적인 코드를 구성한 것이다.

할인 정책은 금액 할인 정책, 비율 할인 정책으로 구분되고 이들의 공통점은 어떻게 할인을 시킬 것이냐이다.   
이 공통을 뽑아냄으로써 DiscountPolicy라는 추상화가 만들어질 수 있다.    
그리고 이 추상을 상속받음으로써 구체적인 정책들은 다형성을 이용하여 언제든지 변경이 가능하다.

## 상속과 다형성
앞선 코드를 살펴보면 Movie 클래스 어디에서도 어떤 할인 정책인지 확인하지 않는다.  
Movie는 단지 DiscountPolicy라는 추상화된 저수준의 인터페이스만 의존할 뿐이고, 구체는 런타임에서만 인스턴스를 전달하는 방식이다.  
이러한 설계는 런타임에 어떤 코드를 배치하면 되기 때문에 유연성이 높고, 재사용이 좋다는 장점이 존재한다.  
다만, 코드를 이해할 때는 현재 런타임에 어떤 구현체를 의존하고 있는지를 파악하기 어렵기 때문에 다소 이해하기 어려울 수 있다.   

상속은 기존 클래스의 속성과 행동을 상속받아 쉽게 부모 클래스를 이어갈 수 있다.  
떄문에 외부에서 자식 클래스를 바라보기 때문에 자식 클래스를 부모 클래스와 동일하게 간주할 수 있다.
movie의 discountPolicy::calculateDiscountAmount도 마찬가지로 자식 크래스들이 부모 클래스를 물려받기 때문에 컴파일러는 부모 클래스가 호출되는 곳에 자식 클래스를 사용하는 것을 허용한다.   
이처럼 자식 클래스가 부 클래스를 대체하는 것을 업캐스팅이라고 한다.   

이를 이용하여 런타임 시 Movie 인스턴스는 실제 상호작용하는 구현체 인스턴스에 대한 의존이 생기고, 다형성이 가능해진다.   
이는 지연 바인딩 혹은 동적 바인딩이라 불린다.  

## 합성
Movie는 DiscountPolicy 인터페이스만을 의존하고, 메시지를 이용함으로써 할인 요금을 계산한다.   
이처럼 인터페이스와 메시지만으로 코드를 재사용하는 방법을 합성이라 한다.   

합성은 인터페이스에 정의된 메시지를 통해서만 재사용이 가능하여 구현을 효과적으로 캡슐화할 수 있다.   
또한, 의존하는 인스턴스를 교체하는 것이기에 설계를 유연하게 만든다.   

