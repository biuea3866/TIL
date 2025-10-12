# [객체지향] 협력, 상속, 합성을 이용한 영화 예매 시스템 설계

- **Tags:** #ObjectOriented #OOP #DesignPatterns #Inheritance #Composition #Polymorphism

---

### 무엇을 배웠는가?
객체들이 **서로 협력**하여 영화 예매 기능을 구현하는 과정을 학습했습니다.   
특히, 자주 변경될 수 있는 **할인 정책** 요구사항을 **추상화, 상속, 그리고 합성**을 이용하여 유연하고 확장 가능하게 설계하는 방법을 코드를 통해 이해했습니다.    
객체는 고립된 존재가 아니라, 각자의 책임을 가지고 협력하는 공동체의 일원이라는 점을 명확히 알게 되었습니다.

---

### 왜 중요하고, 어떤 맥락인가?
애플리케이션의 요구사항, 특히 할인 정책과 같은 비즈니스 규칙은 언제든지 변경될 수 있습니다.   
만약 영화(`Movie`) 클래스 내부에 할인 로직을 `if/else`로 모두 하드코딩했다면, 새로운 할인 정책이 추가될 때마다 `Movie` 코드를 직접 수정해야만 합니다.    
이는 OCP(개방-폐쇄 원칙)를 위반하며 유지보수를 매우 어렵게 만듭니다.

이 문제를 해결하기 위해, **어떻게 할인할 것인가**에 대한 책임을 `Movie`에서 완전히 분리하여 별도의 `DiscountPolicy` 객체에게 위임합니다.  
이를 통해 `Movie`는 할인 정책의 구체적인 내용에 대해 전혀 알 필요 없이, 단지 할인 계산을 요청하기만 하면 됩니다.   
이런 설계는 새로운 할인 정책이 추가되거나 변경되어도 `Movie` 코드는 전혀 영향을 받지 않는 유연한 구조를 만들어줍니다.

---

### 상세 내용
#### 1. 책임의 분리와 위임
할인 정책 요구 사항을 해결하기 위해 **책임을 어떻게 분리할 것인가**을 중심으로 객체를 설계합니다.

* **`Movie`의 책임**: 상영 정보를 받아 최종 가격을 계산한다.
* **`DiscountPolicy`의 책임**: 할인 조건을 판단하고, 할인 금액을 계산한다.

`Movie`는 최종 가격을 계산할 책임이 있지만, 할인 금액을 계산하는 구체적인 방법까지 알 필요는 없습니다.    
따라서 `Movie`는 `DiscountPolicy`에게 할인 금액 계산을 **위임**합니다.

```kotlin
// Movie.kt
class Movie(
    // ...
    private var _discountPolicy: DiscountPolicy // 구체적인 할인 정책이 아닌, 추상화에 의존
) {
    fun calculateMovieFee(screening: Screening): Money {
        // 할인 금액 계산을 discountPolicy에게 위임한다.
        return this._fee.minus(_discountPolicy.calculateDiscountAmount(screening))
    }
}
```

#### 2. 상속과 다형성: 다양한 할인 정책 구현
할인 정책은 '금액 할인'과 '비율 할인' 두 종류가 있습니다. 이 둘의 공통점은 '할인 금액을 계산한다'는 것이고, 차이점은 '어떻게' 계산하는가 입니다.

* 추상화: 공통 로직을 담은 DiscountPolicy 추상 클래스를 만들고, 할인 조건을 순회하며 만족하는지 검사하는 공통 로직을 가집니다.
* 상속: AmountDiscountPolicy와 PercentDiscountPolicy가 DiscountPolicy를 상속받아, getDiscountAmount라는 추상 메서드를 각자의 방식대로 구체화합니다.
* 다형성: Movie는 컴파일 타임엔 DiscountPolicy에만 의존하고, 런타임엔 어떤 자식 클래스(AmountDiscountPolicy 또는 PercentDiscountPolicy)가 주입되든, Movie는 동일한 메시지(calculateDiscountAmount)를 보내고 원하는 결과를 얻을 수 있습니다.

```Kotlin
// DiscountPolicy.kt
abstract class DiscountPolicy(...) {
    // 공통 로직: 조건을 만족하면 할인 금액을 계산
    fun calculateDiscountAmount(screening: Screening): Money { ... }

    // 자식 클래스가 구체화해야 할 부분
    protected abstract fun getDiscountAmount(screening: Screening): Money
}

class AmountDiscountPolicy(...): DiscountPolicy(...) {
    override fun getDiscountAmount(screening: Screening): Money {
        return this.discountAmount // 고정 금액 반환
    }
}

class PercentDiscountPolicy(...): DiscountPolicy(...) {
    override fun getDiscountAmount(screening: Screening): Money {
        return screening.movieFee.times(percent) // 비율에 따라 계산된 금액 반환
    }
}
```

#### 3. 인터페이스와 합성: 다양한 할인 조건 조합
할인 정책은 '순번 조건'과 '기간 조건' 중 하나 이상을 만족해야 적용됩니다.    
이는 할인 정책(DiscountPolicy)이 할인 조건(DiscountCondition)들을 부품처럼 가진다는 의미로 해석할 수 있습니다.    
이것이 **합성**입니다.

* 인터페이스: 모든 할인 조건이 만족해야 하는 isSatisfiedBy라는 행동을 DiscountCondition 인터페이스로 정의합니다.
* 합성: DiscountPolicy는 DiscountCondition 인터페이스 타입의 리스트를 멤버 변수로 가지고, 이를 통해 어떤 구체적인 조건들이든 상관없이 조합하여 사용할 수 있습니다.

```Kotlin
// DiscountPolicy는 DiscountCondition 리스트를 '소유(합성)'한다.
abstract class DiscountPolicy(
    private val conditions: MutableList<DiscountCondition> = mutableListOf()
) { ... }

// 모든 조건들은 이 인터페이스를 구현한다.
interface DiscountCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class SequenceCondition(...): DiscountCondition { ... }
class PeriodCondition(...): DiscountCondition { ... }
```

이러한 설계 덕분에, "주중 3회차 상영"과 같은 새로운 복합 조건이 생겨도 DiscountPolicy의 코드를 변경할 필요 없이 새로운 DiscountCondition 구현체만 추가하면 됩니다.

### 핵심 요약
* 객체는 자율적이어야 한다: 각 객체는 자신의 데이터를 스스로 책임지고 관리해야 합니다(캡슐화).
* 객체는 협력해야 한다: 기능 구현은 하나의 거대한 객체가 아닌, 각자의 책임을 가진 작은 객체들의 협력을 통해 이루어져야 합니다.
* 상속보다 합성을 선호하라: 합성은 런타임에 부품(객체)을 교체할 수 있게 해주어 상속보다 훨씬 유연한 설계를 가능하게 합니다.
* 구현이 아닌 인터페이스에 의존하라: Movie가 구체적인 할인 정책 클래스가 아닌 DiscountPolicy라는 추상화에 의존했기 때문에, 시스템의 유연성과 확장성이 극대화되었습니다.