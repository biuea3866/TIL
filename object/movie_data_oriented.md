# [객체지향] 데이터 중심 설계의 함정과 책임 주도 설계

- **Tags:** #ObjectOriented #OOP #Design #Encapsulation #Cohesion #Coupling

---

###  무엇을 배웠는가?
객체지향 설계를 할 때, 객체가 가져야 할 데이터(상태)를 먼저 정의하는 '데이터 중심 설계'의 문제점을 배웠습니다.   
이 접근법은 객체의 캡슐화를 깨뜨리고, 응집도를 낮추며, 결합도를 높여 변경에 매우 취약한 시스템을 만듭니다.

---

### 왜 중요하고, 어떤 맥락인가?
"영화(`Movie`) 객체는 어떤 데이터를 가져야 할까?" (제목, 상영 시간, 가격...)라고 질문하며 설계를 시작하기 쉽습니다.    
하지만 이 접근법은 객체를 자율적인 존재가 아닌, **단순한 데이터 집합**으로 전락시킵니다.

그 결과, `Movie` 객체는 자신의 데이터를 `getter`와 `setter`로 외부에 노출시키고, 실제 비즈니스 로직(e.g., 영화 예매)은 `ReservationAgency` 같은 **별개의 관리자 객체가 처리**하게 됩니다.   
이 관리자 객체는 다른 객체들의 내부 데이터를 모두 가져와 로직을 수행하므로, 시스템의 모든 객체가 서로의 내부에 강하게 의존하는 심각한 설계 문제를 야기합니다.

---

### 상세 내용

#### 1. 데이터 중심 설계의 진행과 결과
먼저 객체의 상태에 초점을 맞추면, 객체의 행동(책임)은 그 상태를 조작하는 `getter`/`setter`가 됩니다.

```kotlin
// 데이터 중심의 Movie 객체
class Movie(
    private var _title: String,
    private var _fee: Money,
    private var _movieType: MovieType,
    private var _discountAmount: Money
    // ... 수많은 다른 데이터들
) {
    // 모든 내부 상태를 외부로 노출시킨다
    val fee: Money get() = this._fee
    val movieType: MovieType get() = this._movieType
    val discountAmount: Money get() = this._discountAmount
    // ... 수많은 다른 getter와 setter
}
이 Movie 객체를 사용하는 ReservationAgency는 다음과 같이 됩니다.

Kotlin

// 비즈니스 로직을 처리하는 외부 관리자 객체
class ReservationAgency {
    fun reserve(...) {
        val movie = screening.movie
        // ...
        
        // Movie의 내부 데이터를 모두 가져와서 직접 로직을 처리
        if (discountable) {
            discountAmount = when(movie.movieType) { // 1. movieType에 의존
                MovieType.AMOUNT_DISCOUNT -> movie.discountAmount // 2. discountAmount에 의존
                MovieType.PERCENT_DISCOUNT -> movie.fee.times(...) // 3. fee에 의존
                // ...
            }
        }
    }
}
```

#### 2. 설계 품질 문제점
* 캡슐화 위반: getFee()는 캡슐화가 아니고, 내부 구현(상태)을 외부로 그대로 노출하는 행위일 뿐입니다. 캡슐화는 불안정한 구현을 안정적인 인터페이스 뒤에 감추는 것을 의미합니다.
* 낮은 응집도: 객체는 자신의 상태를 스스로 처리하며 하나의 목적을 가져야 합니다. 하지만 위 설계에서 할인 로직은 ReservationAgency에 있고, 관련 데이터는 Movie에 있습니다. 즉, 데이터와 행동이 분리되어 응집도가 낮습니다.
* 높은 결합도: ReservationAgency는 Movie의 movieType, discountAmount 등 내부 구현(데이터 구조)에 강하게 결합됩니다. 만약 Movie의 요금 할인 방식이 변경되면(e.g., movieType이 사라짐), Movie뿐만 아니라 ReservationAgency도 반드시 수정되어야 합니다.

#### 3. '자율적인' 객체의 함정
설령 객체에 행동을 부여하더라도, 여전히 데이터 중심적으로 생각할 수 있습니다.    
예를 들어 DiscountCondition에 할인 여부를 판단하는 메서드를 추가해봅시다.

```Kotlin
class DiscountCondition(
    private var _type: DiscountConditionType,
    private var _sequence: Int,
    private var _dayOfWeek: DayOfWeek,
    // ...
) {
    // 이런 메서드 시그니처는 여전히 내부 상태를 노출한다
    fun isDiscountable(sequence: Int): Boolean { ... }
    fun isDiscountable(dayOfWeek: DayOfWeek, time: LocalDateTime): Boolean { ... }
}
```

isDiscountable 메서드는 파라미터로 Int, DayOfWeek 등을 요구합니다.     
이는 DiscountCondition이 **sequence(Int)와 dayOfWeek(DayOfWeek)를 가지고 있다**고 외부에 광고하는 것과 같습니다.   
결국 이 메서드를 호출하는 외부 객체는 DiscountCondition의 내부 상태를 알아야만 하므로, 결합도는 여전히 높습니다.

### 핵심
데이터는 구현의 일부고, 객체의 데이터(상태)를 먼저 결정하는 것은 구현에 의존하여 설계를 시작하는 것과 같습니다.   
데이터 중심 설계는 캡슐화를 깨뜨리고, 내부 구현을 외부로 노출시켜 높은 결합도를 만듭니다.   
올바른 설계는 객체의 책임(행동)을 먼저 정의하는 '책임 주도 설계'에서 시작해야 합니다.    
객체에게 "무엇을 할 것인지"를 먼저 묻고, 그 행동을 수행하는 데 필요한 데이터는 객체 스스로 내부에 감추도록 해야 합니다.