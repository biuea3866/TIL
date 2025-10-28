# [객체지향] GRASP와 다형성을 이용한 책임 할당 설계

- **Tags:** #ObjectOriented #OOP #GRASP #ResponsibilityDrivenDesign #Polymorphism #ProtectedVariations #SRP

---

### 무엇을 배웠는가?
객체지향 설계 시 **책임 주도 설계**와 **GRASP** 패턴을 사용해 초기 책임을 할당하고, 이 과정에서 발생하는 변경에 취약한 지점들을 **다형성**과 **Protected Variations** 패턴을 적용하여 유연한 구조로 개선하는 과정을 학습했습니다.

---

### 왜 중요하고, 어떤 맥락인가?
설계의 핵심은 객체 간의 협력을 설계하는 것이며, 이 협력은 객체들이 맡은 **책임**을 통해 이루어집니다.    
만약 객체가 자신의 책임(행동)이 아닌 데이터에만 집중하면, 캡슐화가 깨지고 시스템 전체의 결합도가 높아집니다.

가장 큰 문제는 하나의 객체가 **여러 변경의 이유**를 갖게 되는 것입니다.    
예를 들어, `Movie` 객체가 `MovieType`에 따라 `when`(switch) 문으로 할인 로직을 분기 처리한다면, 새로운 할인 유형이 추가될 때마다 `Movie` 코드를 직접 수정해야 합니다. 이는 변경에 매우 취약한 설계입니다.

---

### 상세 내용

#### 1. 책임 주도 설계와 GRASP
설계의 시작은 애플리케이션의 가장 큰 기능, 즉 "예매한다"는 책임을 식별하는 것입니다.

* **GRASP (General Responsibility Assignment Software Pattern)**: 이 책임을 누구에게 할당할지 결정하는 기준으로, **정보 전문가(Information Expert) 패턴**을 사용할 수 있습니다.
* **정보 전문가 패턴**: 책임을 수행하는 데 필요한 정보를 가장 많이 알고 있는 객체에게 책임을 할당하는 원칙입니다.
* **적용**: "예매한다"는 행위에 필요한 정보(상영 순번, 시간 등)를 가장 잘 아는 것은 `Screening`이므로, `Screening`에게 `reservation` 책임을 할당합니다.

#### 2. 책임의 위임과 협력
`Screening`은 `reservation` 책임을 받았지만, 모든 것을 스스로 처리할 수 없습니다. 예를 들어 "영화 요금 계산"은 `Movie`가 더 잘 아는 정보입니다.

* `Screening`은 자신이 처리할 수 없는 책임을 `Movie`에게 **위임**합니다.
* 이때 `Screening`은 `Movie`의 내부 구현(`if`문, `when`문 등)을 전혀 몰라도 되며, 단지 `calculateMovieFee`라는 **메시지(인터페이스)만 결정하고 호출**합니다.
* 이를 통해 두 객체 간의 캡슐화가 지켜지고 결합도가 느슨하게 유지됩니다.

#### 3. 변경에 취약한 지점 식별
책임을 위임받은 `Movie`와 `DiscountCondition`이 내부적으로 타입을 체크하며 분기하는 로직을 가질 수 있습니다.

```kotlin
// Movie.kt - MovieType에 따라 분기
private fun calculateDiscountAmount(): Money {
    when (this._movieType) {
        AMOUNT_DISCOUNT -> ...
        PERCENT_DISCOUNT -> ...
    }
}

// DiscountCondition.kt - type에 따라 분기
fun isSatisfiedBy(screening: Screening): Boolean {
    if (this._type == DiscountCondition.PERIOD) {
        return isSatisfiedByPeriod(screening)
    }
    return isSatisfiedBySequence(screening)
}
```
MovieType이나 DiscountConditionType이 새로 추가되면 해당 클래스의 내부 코드를 직접 수정해야 하기 때문에 변경에 매우 약합니다.

#### 4. 다형성을 통한 리팩토링 (Protected Variations)
이 문제는 다형성을 통해 해결할 수 있습니다. 변경 가능성이 높은 지점을 안정적인 인터페이스 뒤로 숨기는 것입니다.
* Protected Variations 패턴: 변하는 개념(특정 할인 조건, 특정 할인 금액 계산법)을 캡슐화하고, 안정된 인터페이스(DiscountCondition) 뒤로 위치시키는 패턴입니다. 
* DiscountCondition 리팩토링:
  * DiscountCondition을 isSatisfiedBy 메서드만 가진 interface로 만듭니다. 
  * PeriodCondition과 SequenceCondition이 이 인터페이스를 각각 구현합니다. 
  * Movie는 이제 구체적인 Condition 클래스가 아닌, DiscountCondition 인터페이스(역할)에만 의존합니다.
* Movie 리팩토링:
  * Movie를 abstract class로 만들고 calculateDiscountAmount를 abstract method로 선언합니다. 
  * AmountDiscountMovie와 PercentDiscountMovie가 Movie를 상속받아 각자의 로직으로 메서드를 구현합니다.

### 핵심
설계는 책임 주도로 시작하며, **GRASP**를 통해 객체에 책임을 할당합니다.    
객체가 책임을 구현할 때 if나 when을 통해 타입을 확인하고 분기한다면, 이는 **단일 책임 원칙**를 위반할 가능성이 높습니다.    
이 때 다형성을 적용하여 **변하는 부분을 안정된 인터페이스 뒤로 캡슐화**하면, 각 구현체가 단 하나의 책임만 갖게 되어 유연하고 확장 가능한 설계가 됩니다.