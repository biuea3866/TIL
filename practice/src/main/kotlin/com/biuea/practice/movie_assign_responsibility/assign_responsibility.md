# 책임 할당하기
책임을 누군가에게 어떤 책임을 할당할 것인가는 어려운 문제이다.  
책임을 할당함은 문맥과 상황에 따라 달라지기에 GRASP 패턴을 통해서 기준을 잡고, 책임을 할당하도록 한다.   

## 책임 주도 설계
객체는 데이터가 아니라 외부에 어떤 행동을 제공할 것인지가 중요하다.   
이 어떤 행동은 협력이라는 이름 안에서 어떤 책임을 가진 객체들이 외부에 제공하는 행위이다.   
그리고 이 행동은 송신자가 행동을 올바르게 책임질 수 있는 수신자를 정하고, 책임을 할당한다.   

## GRASP(General Responsibility Assignment Software Pattern) 패턴
첫 설계를 시작하는 단계에서는 큰 틀에서 이 애플리케이션이 어떤 기능을 제공해야하는지를 파악하고, 이를 책임으로 간주한다.   
영화 예매 시스템 예제에서의 가장 큰 기능은 영화를 예매하는 것이다.   
그렇기 때문에 첫 시작은 "예매한다"부터 시작하게 된다.   
그리고 "예매한다"를 누가 가장 올바르게 책임질지를 결정하고, 할당하게 된다.   
GRASP에서는 정보 전문가 패턴을 이용하여 정보를 가장 많이 알고 있는 객체에게 책임을 할당한다.   
그렇기 때문에 "예매한다"는 Screening에게 할당되고, Screening은 자신이 처리할 수 있는 책임과 그러지 못할 책임을 우선 가려야 한다.   
처리할 수 있는 책임은 예매를 하기 위한 책임이고, 처리할 수 없는 책임은 예매를 하기 위해 영화 요금을 계산하는 책임일 것이다.   
이렇게 분할된 처리할 수 없는 책임은 다시 그에 맞는 객체에게 할당하는 과정을 반복한다.   
다시 돌아가서 어떠한 정보를 알지도 못하고 Screening에게 "예매한다" 라는 책임을 할당하였는데, 만약 책임을 할당했는데 그에 맞는 정보가 없다면 어떡할 것인가라는 문제에 빠지게 된다.   
이는 Screening에서 "예매한다"라는 요구사항을 참고하여 정보를 구성하고, 메시지를 구현하는 과정이 필요하다.   

## 구현
```kotlin
class Screening(
    private var _movie: Moive,
    private var _sequence: Int,
    private var _whenScreened: LocalDateTime
) {
    fun reservation(customer: Customer, audienceCount: Int): Reservation {
        return Reservation(customer, this, calculateFee(audienceCount), audienceCount)
    }
    
    private fun calculateFee(audienceCount: Int): Money {
        return _movie.calculateMovieFee(this).times(audienceCount)
    }
}

class Movie() {
    fun calculateMovieFee(screening: Screening): Money {
        
    }
}
```
Screening은 Movie의 내부 구현이 어떻게 되어있는지 관계 없이 calculateMovieFee라는 메시지를 결정했다.   
이로써 Movie의 내부 구현을 캡슐화하고, calculateMovieFee라는 인터페이스만 노출시켜 둘 간의 결합도를 느슨하게 유지할 수 있다.   

이후 Movie는 calculateMovieFee를 구현하기 위해 요구사항을 참고하여 어떤 정보가 필요한지 결정해야한다.   

```kotlin
class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _discountConditions: List<DiscountCondition>,
    private var _movieType: MovieType,
    private var _discountAmount: Money,
    private var _discountPercent: Double
) {
    fun calculateMovieFee(screening: Screening): Money {
        if (this.isDiscountable(screening)) {
            return this._fee.minus(this.calculateDiscountAmount())
        }
        
        return this._fee
    }
    
    private fun isDiscountable(screening: Screening): Boolean {
        return this._discountConditions.any { it.isSatisfiedBy(screening) }
    }
    
    private fun calculateDiscountAmount(): Money {
        when (this._movieType) {
            AMOUNT_DISCOUNT -> this.calculateAmountDiscountAmount()
            PERCENT_DISCOUNT -> this.calculatePercentDiscountAmount()
            NONE_DISCOUNT -> this.calculateNoneDiscountAmount()
        }
    }

    private fun calculateAmountDiscountAmount(): Money {
        return this._discountAmount
    }
    
    private fun calculatePercentDiscountAmount(): Money {
        return this._fee.times(this._discountPercent)
    }
    
    private fun calculateNoneDiscountAmount(): Money {
        return Money.ZERO
    }
}

enum class MovieType {
    AMOUNT_DISCOUNT,
    PERCENT_DISCOUNT,
    NONE_DISCOUNT
}
```
Movie는 calculateMovieFee를 구현하기 위해 상태를 구성하고, 조건식(MovieType)에 맞추어 내부를 구현한다.   
그리고 이 과정에서 자신이 처리할 수 없는 책임인 `isSatisfiedBy`이 만들어진다.   

```kotlin
class DiscountCondition(
    private var _type: DiscountConditionType,
    private var _sequence: Int,
    private var _dayOfWeek: DayOfWeek,
    private var _startTime: LocalTime,
    private var _endTime: LocalTime
) {
    fun isSatisfiedBy(screening: Screening): Boolean {
        if (this._type == DiscountCondition.PERIOD) {
            return isSatisfiedBy(screening)
        }
        
        return isSatisfiedBySequence(screening)
    }
    
    fun isSatisfiedByPeriod(screening: Screening): Boolean {
        return _dayOfWeek == screening.whenScreened.dayOfWeek &&
                _startTime <= screening.whenScreened &&
                _endTime >= screening.whenScreened
    }
    
    private fun isSatisfiedBySequence(screening: Screening): Boolean {
        return _sequence == screening.sequence
    }
}

enum class DiscountConditionType {
    SEQUENCE,
    PERIOD
}
```
DiscountCondition의 구현은 완료되었지만 여러 이유에 따라 DiscountCondition의 구현이 변경될 가능성이 있으므로 변경에 약한 객체이다.   
하나의 메시지를 위해 내부 상태 전부가 사용되지 못하고, 그룹이 나뉘어 내부 상태가 사용된다면 이것 자체로 여러 이유가 되므로 이 그룹에 맞추어 객체를 분리할 수 있다.   
다만 구현을 분리할 뿐이지, 책임에 대한 분리는 아니다.   
다형성을 이용하여 period, sequence 그룹에 맞추어 구현을 분리하고, 동일한 책임은 그대로 둔다.

```kotlin
fun interface DiscountCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class PeriodCondition : DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        TODO("Not yet implemented")
    }
}

class SequenceCondition : DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        TODO("Not yet implemented")
    }
}

class Movie(
    private var _discountConditions: List<DiscountCondition>
) {
    fun calculateMovieFee(screening: Screening): Money {
        if (this.isDiscountable(screening)) {
            return this._fee.minus(this.calculateDiscountAmount())
        }

        return this._fee
    }

    private fun isDiscountable(screening: Screening): Boolean {
        return this._discountConditions.any { it.isSatisfiedBy(screening) }
    }
}
```
원래 였다면 Movie는 PeriodCondition, SequenceCondition 전부를 의존하여 새로운 Condition이 추가되면 이 또한 의존되는 형태였다.   
하지만 이렇게 다형성을 이용하여 구현을 분리하고, 책임을 의존하여 응집도있게 문제를 해결할 수 있게 된다.   
이는 PROTECTED VARIATIONS 패턴이라 불리며, 변하는 개념을 캡슐화(특정 Condition 구현체)하고, 안정된 인터페이스 뒤로 위치시키는 것이다.

```kotlin
abstract class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _discountConditions: List<DiscountCondition>
) {
    fun calculateMovieFee(screening: Screening): Money {
        if (this.isDiscountable(screening)) {
            return this._fee.minus(this.calculateDiscountAmount())
        }

        return this._fee
    }

    private fun isDiscountable(screening: Screening): Boolean {
        return this._discountConditions.any { it.isSatisfiedBy(screening) }
    }

    protected abstract fun calculateDiscountAmount(): Money
}

class AmountDiscountMovie(
    _title: String,
    _runningTime: Duration,
    _fee: Money,
    _discountConditions: List<DiscountCondition>,
    private var _discountAmount: Money
) : Movie(
    _title,
    _runningTime,
    _fee,
    _discountConditions
) {
    override fun calculateDiscountAmount(): Money {
        return this._discountAmount
    }
}

class PercentDiscountMovie(
    _title: String,
    _runningTime: Duration,
    _fee: Money,
    _discountConditions: List<DiscountCondition>,
    private var _percent: Double
) : Movie(
    _title,
    _runningTime,
    _fee,
    _discountConditions
) {
    override fun calculateDiscountAmount(): Money {
        return this._fee.times(_percent)
    }
}
```

Movie 또한 마찬가지로 MovieType이라는 분기를 없애고, 다형성을 통해 캡슐화를 구성할 수 있다.   
그리고 이 구현체들은 각기 하나의 이유로 변경의 이유를 가지며, SRP를 충족하게 된다.