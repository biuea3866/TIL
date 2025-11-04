# [객체지향] 메시지, 인터페이스, 그리고 설계 원칙 (디미터, CQS)

- **Tags:** #ObjectOriented #OOP #DesignPrinciples #LawOfDemeter #CQS

---

### 무엇을 배웠는가?
객체는 **메시지**를 주고받으며 협력하며, 이 메시지들의 집합이 곧 객체의 퍼블릭 인터페이스(Public Interface)가 된다는 것을 배웠습니다.   
좋은 설계 품질을 위해 **디미터 법칙**과 **'의도를 드러내는 인터페이스'** 명명법이 캡슐화를 지키고 결합도를 낮추는 핵심 도구임을 이해했습니다.   
또한, 모든 원칙에는 트레이드오프가 존재하며, **명령-쿼리 분리(CQS)** 원칙의 중요성에 대해 학습했습니다.

---

### 왜 중요하고, 어떤 맥락인가?
객체의 내부 구현을 `getter` 등으로 무분별하게 노출시키는 코드는 **결합도가 극도로 높아져** 변경에 매우 취약합니다.   
`ReservationAgency` 예시처럼, 한 객체가 다른 객체들의 내부를 `.`을 찍어가며 2~3단계씩 파고드는(e.g., `screening.getMovie().getDiscountConditions()`) 코드는 유지보수를 어렵게 합니다.

좋은 설계 원칙을 적용하면, 객체가 자율성을 갖고 자신의 책임을 수행하게 만들어 **응집도가 높고 유연한** 시스템을 만들 수 있습니다.   
객체는 "묻지 말고 시켜라(Tell, Don't Ask)"라는 격언처럼, 내부 데이터를 꺼내서 처리하게 하는 것이 아니라, **요청(메시지)을 받아 스스로 처리**해야 합니다.

---

### 상세 내용

#### 1. 협력의 시작: 메시지
객체지향 애플리케이션의 기능은 객체 간의 협력으로 구현됩니다.   
이 협력은 한 객체(클라이언트)가 다른 객체(서버)에게 **메시지를 전송**하면서 시작됩니다.

* **메시지**: 객체가 수행할 **오퍼레이션명**과 **인자**로 구성됩니다. (e.g., `isSatisfiedBy(screening)`)
* **메시지 전송**: 수신자, 오퍼레이션명, 인자의 조합입니다. (e.g., `condition.isSatisfiedBy(screening)`)
* **메서드**: 메시지를 수신했을 때 실제로 실행되는 코드 블록입니다.

#### 2. 설계 품질과 디미터 법칙
**디미터 법칙**은 객체가 "단 하나의 `.`만 사용하라"는 규칙으로, 객체 간의 결합도를 낮추기 위한 원칙입니다.

* **위반 사례**: `ReservationAgency`가 `screening` 객체를 받아 `screening.getMovie().getDiscountConditions()`처럼 여러 단계의 `.`을 통해 다른 객체의 내부 정보에 깊숙이 접근하고 있습니다. 이는 `ReservationAgency`가 `Screening`, `Movie`, `DiscountCondition`의 내부 구현에 모두 강하게 결합되었음을 의미합니다.
```kotlin
class ReservationAgency {
    fun reserve(screening: Screening, customer: Customer, audienceCount: Int): Reservation {
        val movie = screening.getMovie()
        var discountable = false

        movie.getDiscountConditions().forEach {
            discountable = if (condition.getType() == DiscountConditionType.PERIOD) {
                (screening.getWhenScreened().getDayOfWeek() == condition.getDayOfWeek()
                        && condition.getStartTime() <= screening.getWhenScreened()
                        && condition.getEndTime() >= screening.getWhenScreened())
            } else {
                condition.getSequence() == screening.getSequence()
            }

            if (discountable) break
        }
    }
}
```

* **준수 사례**: `ReservationAgency`는 `screening` 객체에게 `calculate(audienceCount)`라는 메시지만 전송합니다. `screening`이 요금을 어떻게 계산하는지는 `ReservationAgency`가 알 필요가 없습니다.
```kotlin
class ReservationAgency {
    fun reserve(screening: Screening, customer: Customer, audienceCount: Int): Reservation {
        val fee = screening.calculate(audienceCount)
        return Reservation(customer, screening, fee, audienceCount)
    }
}
```

이는 결국 캡슐화를 강화하는 행위로, 객체는 내부 상태를 감추고 퍼블릭 인터페이스로만 소통해야 함을 의미합니다.

#### 3. 의도를 드러내는 인터페이스
메서드 이름은 "어떻게" 하는지가 아니라 "**무엇을**" 하는지를 드러내야 합니다.

* **위반 사례**: `isSatisfiedByPeriod()`, `isSatisfiedBySequence()`.
    * 이름에 구현 방식(Period, Sequence)이 노출됩니다.
    * 클라이언트는 객체의 구체적인 타입을 확인하고, 올바른 메서드를 호출해야 하는 책임을 집니다.
```kotlin
class PeriodCondition {
    fun isSatisfiedByPeriod(screening: Screening): Boolean
}

class SequenceCondition {
    fun isSatisfiedBySequence(screening: Screening): Boolean
}
```

* **준수 수례**: `isSatisfiedBy()`.
    * "무엇을 하는지"(할인 조건 만족 여부 확인)만 드러냅니다.
    * 클라이언트는 구체적인 타입을 몰라도 되며, `PeriodCondition`과 `SequenceCondition`이 **같은 책임**을 수행함을 명확히 알 수 있습니다.
    * 이는 `DiscountCondition`이라는 공통 인터페이스로 추상화하여 캡슐화를 강화하는 기반이 됩니다.

```kotlin
interface DiscountCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class PeriodCondition: DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean
}

class SequenceCondition: DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean
}
```

#### 4. 원칙의 함정: 디미터 법칙 vs. 응집도
원칙을 맹목적으로 따르는 것이 항상 좋은 설계로 이어지지는 않습니다.

* `PeriodCondition`이 `screening.getWhenScreened()...`처럼 `screening`의 내부를 참조하는 것은 디미터 법칙 위반입니다.
* 이를 해결하기 위해 `screening`에게 `isDiscountable(dayOfWeek, ...)` 메서드를 만들어 책임을 위임할 수 있습니다.
* **하지만** 이 해결책은 `Screening` 객체의 **응집도를 낮춥니다**. `Screening`의 원래 책임은 "상영"인데, 이제 "할인 여부 판단"이라는 **엉뚱한 책임**까지 떠맡게 되었습니다.

설계는 트레이드오프입니다. 때로는 응집도를 높이고 책임을 올바른 객체에 할당하기 위해, 디미터 법칙을 어기고 데이터를 요청하는 것이 더 나은 설계일 수 있습니다.

#### 5. 명령-쿼리 분리 (CQS) 원칙
명령 - 쿼리 분리 원칙은 오퍼레이션이 부수효과를 발생시키는 명령이거나 부수효과를 발생시키지 않는 쿼리 중 하나여야 함을 의미합니다.   
즉, 오퍼레이션이 부수효과를 발생시키면서 결과를 반환하지 않아야 합니다.

만약 이를 어기게 된다면, 쿼리의 결과를 예측하기가 어렵고(멱등성 x), 많은 버그를 유발하며, 정해진 체이닝, 디버깅이 어려워 질 수 있습니다.

---

### 핵심
* 객체는 "묻지 말고 시켜라" 원칙에 따라 자율적으로 책임을 수행해야 합니다.
* **디미터 법칙**은 객체 간의 결합도를 낮추는 유용한 가이드지만, **응집도**와 책임 할당을 고려하여 맹목적으로 따르지 않도록 주의해야 합니다.
* 메서드는 "어떻게"가 아닌 "**무엇을**" 하는지 드러내도록 명명하여, 협력의 의도를 명확히 해야 합니다.
* **명령(Command)과 쿼리(Query)를 분리**하여 메서드의 부수효과를 예측 가능하게 만드는 것이 버그가 적고 안정적인 코드를 작성하는 지름길입니다.