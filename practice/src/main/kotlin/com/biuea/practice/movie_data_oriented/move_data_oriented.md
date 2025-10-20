# 설계 품질과 트레이드오프
객체지향은 3요소로 구성된다.
* 협력: 기능 구현을 위해 메시지를 주고 받는 객체 간의 상호작용
* 책임: 다른 객체와 협력하기 위한 행동
* 역할: 대체 가능한 책임의 집합

특히 객체지향 설계에서 책임은 결합도와 응집도와 관련이 높으며, 이 책임을 통해 변경을 용이하게 만들 수 있다.  
객체를 단순히 데이터의 집합으로만 바라보는 시각(데이터 중심)과 달리 책임은 객체의 행동을 나타낸다.

## 데이터 중심의 영화 예매 시스템
데이터 중심 관점에서는 객체는 자신이 가지고 있는 데이터에 대해서만 조작하고, 필요한 오퍼레이션을 정의한다.    
데이터 중심 설계는 객체가 가지고 있어야 할 데이터가 무엇인지부터 정의한다.

```kotlin
class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _discountConditions: List<DiscountCondition>,
    private var _movieType: MovieType,
    private var _discountPercent: Double,
    private var _discountAmount: Money
) {
    val title: String get() = this._title
    val runningTime: Duration get() = this._runningTime
    val fee: Money get() = this._fee
    val discountConditions: List<DiscountCondition> get() = this._discountConditions
    val movieType: MovieType get() = this._movieType
    val discountPercent: Double get() = this._discountPercent
    val discountAmount: Money get() = this._discountAmount

    fun setTitle(title: String) {
        this._title = title
    }

    fun setRunningTime(runningTime: Duration) {
        this._runningTime = runningTime
    }

    fun setFee(fee: Money) {
        this._fee = fee
    }

    fun setDiscountConditions(discountConditions: List<DiscountCondition>) {
        this._discountConditions = discountConditions
    }

    fun setMovieType(movieType: MovieType) {
        this._movieType = movieType
    }

    fun setDiscountPercent(discountPercent: Double) {
        this._discountPercent = discountPercent
    }

    fun setDiscountAmount(discountAmount: Money) {
        this._discountAmount = discountAmount
    }
}

enum class MovieType {
    AMOUNT_DISCOUNT,
    PERCENT_DISCOUNT,
    NONE_DISCOUNT
}
```
Movie는 movieType과 discountPercent, discountAmount를 이용해 어떤 할인 조건을 선택하고, 영화표를 계산할 수 있다.  
데이터 중심 접근 방법은 이와 같이 객체의 데이터를 가지고 오퍼레이션을 정의하게 된다.   
그리고 객체는 외부의 참조, 변경 등을 막기 위해 캡슐화가 유지되어야 하지만 책임이 전제되지 않고, 데이터가 우선적으로 정의되었으므로 setter, getter 인터페이스를 제공한다.   

Movie에서의 중요한 기능은 영화를 예매하는 것이다.   
데이터 중심의 객체를 구성으로 하여 영화 예매 기능을 구성하면 다음과 같다.
```kotlin
class ReservationAgency {
    fun reserve(
        screening: Screening,
        customer: Customer,
        audienceCount: Int,
    ): Reservation {
        val movie = screening.movie
        var discountable = false
        
        for (condition in movie.discountConditions) {
            discountable = if (condition.type == DiscountConditionType.PERIOD) {
                screening.whenScreened.dayOfWeek == condition.dayOfWeek &&
                        condition.startTime <= screening.whenScreened &&
                        condition.endTime >= screening.whenScreened
            } else {
                condition.sequence == screening.sequence
            }
            
            if (discountable) break 
        }
        
        var fee: Money
        
        if (discountable) {
            var discountAmount = Money.ZERO

            discountAmount = when(movie.movieType) {
                MovieType.AMOUNT_DISCOUNT -> movie.discountAmount
                MovieType.PERCENT_DISCOUNT -> movie.fee.times(movie.discountPercent)
                MovieType.NONE_DISCOUNT -> Money.ZERO
            }
            
            fee = movie.fee.minus(discountAmount)
        } else {
            fee = movie.fee
        }
        
        return Reservation(customer, screening, fee, audienceCount)
    }
}
```
영화 예매 기능은 여러 객체들 데이터를 이용하여 구성이 되기 때문에 기능 구성을 위한 데이터를 가지고 있는 모든 객체들이 한 기능에 참여하게 된다.

## 설계 트레이드오프
### 캡슐화
상태와 행동을 객체 안에 모으는 이유는 내부 구현을 외부로부터 감추기 위해서이다.   
객체 지향은 한 곳에서의 변경이 시스템 전체에 영향을 주지 않는 목적 또한 존재한다.    
즉, 변경 가능성이 높은 구현은 내부에 감추고, 행동(추상화)을 외부로 노출시키고 이러한 고수준의 인터페이스에 의존시키게 함으로써 변경에 강하게 한다.   
캡슐화는 불안정한 구현을 안정적인 인터페이스 뒤에 감추는 것을 의미한다.   

### 응집도와 결합도
응집도는 모듈 내의 요소들이 하나의 목적을 위해 긴밀하게 협력함을 의미하고, 객체 또는 클래스에 얼마나 관련 높은 책임들을 할당했는지를 나타낸다.    
결합도는 의존성의 정도를 나타내고, 다른 모듈에 얼마나 많은 지식을 가지고 있는지 나타낸다.   
변경의 관점에서 응집도가 높음은 하나의 요구사항 변경을 반영하기 위해 하나의 모듈 수정을 요구하고, 결합도의 낮음은 모듈의 변경이 오직 자신만 영향받음을 의미한다.   

### 데이터 중심의 영화 예매 시스템 문제점
데이터 중심 설계는 무분별한 getter, setter를 만들었기 때문에 캡슐화라고 볼 수 없다.   
내부 구현이 인터페이스 그대로 노출되었기 때문이다.
```kotlin
fun getFee(): Int = this._fee
```
이는 캡슐화가 아닌 퍼블릭하게 내부 상태를 그대로 제공하는 것과 다름이 없다.   
데이터 중심에서 이런 설계가 나타내는 이유는 다음과 같다.   
* 객체의 행동이 아닌 상태에 초점에 맞춰졌다.
* 어떤 행동을 할지 모르고, 외부에서 어떤 행동을 기대할지 모르기 때문에 자신의 모든 상태를 외부에 제공한다.

그리고 이러한 설계는 객체 간 높은 결합도를 만들고, 낮은 응집도를 만들어 변경에 유연하게 대처하지 못하게 된다.   
가령 getFee에 의존하고 있는 ReservationAgency는 getFee의 반환 타입 변경되면 이에 맞게 구현 또한 변경되어야 한다. (가장 기본적으로는 컴파일 에러가 발생할 것)    

## 자율적인 객체
객체는 스스로의 상태를 책임지고, 외부에는 인터페이스만을 제공하여 이 인터페이스만이 상태를 제어할 수 있어야 한다.   
그리고 협력에 참여하면서 수행할 책임인 오퍼레이션을 정의해야한다.   

앞서 정의된 데이터 중심의 객체들을 자율적인 객체로 구성하고, 책임을 전가하면 다음과 같은 예매 기능이 구현된다.
```kotlin
class ReservationAgency {
    fun reserve(
        screening: Screening,
        customer: Customer,
        audienceCount: Int,
    ): Reservation {
        val fee = screening.calculateFee(audienceCount)

        return Reservation(customer, screening, fee, audienceCount)
    }
}
```

각 객체들이 자신의 상태를 관리하고, 행동을 제공함으로써 1차적인 설계보단 나아졌으나 여전히 메서드 시그니쳐에서 자신의 상태를 노출하고 있다는 점이 존재한다.
```kotlin
class DiscountCondition(
    private var _type: DiscountConditionType,
    private var _sequence: Int,
    private var _dayOfWeek: DayOfWeek,
    private var _startTime: LocalDateTime,
    private var _endTime: LocalDateTime,
) {
    val type get() = this._type
    val sequence get() = this._sequence
    val dayOfWeek get() = this._dayOfWeek
    val startTime get() = this._startTime
    val endTime get() = this._endTime

    fun setType(type: DiscountConditionType) {
        this._type = type
    }

    fun setSequence(sequence: Int) {
        this._sequence = sequence
    }

    fun setDayOfWeek(dayOfWeek: DayOfWeek) {
        this._dayOfWeek = dayOfWeek
    }

    fun setStartTime(startTime: LocalDateTime) {
        this._startTime = startTime
    }

    fun setEndTime(endTime: LocalDateTime) {
        this._endTime = endTime
    }

    fun isDiscountable(
        dayOfWeek: DayOfWeek,
        time: LocalDateTime
    ): Boolean {
        if (this._type != DiscountConditionType.PERIOD) {
            throw IllegalArgumentException()
        }

        return dayOfWeek == this._dayOfWeek &&
                    this._startTime <= time &&
                    this._endTime >= time
    }
    
    fun isDiscountable(sequence: Int): Boolean {
        if (this._type == DiscountConditionType.SEQUENCE) {
            throw IllegalArgumentException()
        }
        
        return this._sequence == sequence
    }
}
```
isDiscountable은 시그니쳐로 자신의 상태와 일치하는 DayOfWeek, LocalDateTime, Int와 같은 형들을 요구한다.   
이는 자신의 상태의 타입 변경이 시그니쳐의 변경, 외부 참조의 변경으로 이어짐을 시사한다.   

## 데이터 중심 설계 문제점
첫 번째, 두 번째 설계는 전부 캡슐화가 위반되었기 때문에 변경에 대한 취약점이 노출되었다.   
그리고 근본적으로 데이터 자체도 구현의 일부이기 때문에 데이터를 먼저 결정하고, 처리하는데 필요한 오퍼레이션을 후에 결정하는 건 데이터에 관한 지식이 객체 메서드 인터페이스에 드러나게 된다.   
결과적으로 캡슐화가 깨지고, 변경에 약해진다.   
