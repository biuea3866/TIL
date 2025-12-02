## 📝 [TIL] 상속과 코드 재사용: DRY 원칙과 상속의 양면성

### 1. 문제의 발단: 중복 코드의 발생

객체지향 프로그래밍에서 가장 경계해야 할 것은 **중복 코드**입니다.
`RegularTax`와 `MedicalTax`는 세율(`PERCENTAGE`)만 다를 뿐, 계산 로직은 완전히 동일합니다.

```kotlin
// Bad Practice: 로직이 중복된 두 클래스
class RegularTax {
    val PERCENTAGE = 10
    fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0 // 요구사항 추가: 20세 이하 면제
        return amount * PERCENTAGE / 100
    }
}

class MedicalTax {
    val PERCENTAGE = 20
    fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0 // 중복 발생!
        return amount * PERCENTAGE / 100
    }
}
```

> **DRY (Don't Repeat Yourself)**
> "동일한 지식을 반복하지 말라."
> 코드가 중복되면 로직 수정 시 모든 곳을 찾아 고쳐야 하며, 이는 유지보수의 악몽이 됩니다.

<br>

### 2. 첫 번째 시도: 타입을 이용한 분기 처리

타입(`enum` 등)을 파라미터로 받아 분기 처리(`when/if`)를 하면 코드를 한곳에 모을 수 있습니다.

```kotlin
class TaxCalculator {
    fun calculate(amount: Int, age: Int, taxType: TaxType): Int {
        if (age <= 20) return 0
        
        return when(taxType) {
            REGULAR -> amount * 10 / 100
            MEDICAL -> amount * 20 / 100        
        }
    }
}
```

* **장점:** 중복 코드가 제거되고 로직이 한곳에 모입니다.
* **단점:** 세금 종류가 늘어날수록 분기문이 비대해지고, `TaxCalculator`가 너무 많은 책임을 지게 됩니다. (OCP 위반 가능성)

<br>

### 3. 두 번째 시도: 상속을 이용한 해결 (Template Method Pattern)

공통된 정책(20세 이하 면제)은 부모가, 세부 구현(세율 계산)은 자식이 담당하게 합니다.    
이를 통해 **결합도는 높이지만 중복은 효과적으로 제거**할 수 있습니다.

```kotlin
abstract class Tax {
    abstract val PERCENTAGE: Int

    // 공통 로직 (템플릿)
    fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0
        return performCalculation(amount) // 구체적인 계산은 자식에게 위임
    }
    
    protected abstract fun performCalculation(amount: Int): Int
}

class RegularTax: Tax() {
    override val PERCENTAGE get() = 10
    override fun performCalculation(amount: Int) = amount * PERCENTAGE / 100
}
```

이 방식은 새로운 세금 클래스를 추가할 때 부모의 코드를 건드리지 않아도 된다는 강력한 장점이 있습니다.

<br>

-----

### 4. ⚠️ 상속의 함정 (Anti-Patterns)

상속은 강력하지만, 잘못 사용하면 코드를 망가뜨리는 지름길이 됩니다. 작성하면서 배운 **상속 사용 시 피해야 할 3가지 케이스**입니다.

#### ① 부모 구현에 대한 강한 결합 (`super` 남용)

자식 클래스가 부모의 내부 구현(`super.agePolicy`)을 호출해야만 동작한다면, 자식은 부모의 로직을 정확히 알고 있어야 합니다.

```kotlin
class MedicalTax: RegularTax() {
    override fun calculate(amount: Int, age: Int): Int {
        super.agePolicy(age, amount) // 부모의 로직 호출 강제
        return amount * PERCENTAGE / 100
    }
}
```

* 부모의 메서드 시그니처나 로직이 바뀌면 자식 코드도 깨지게 됩니다.

#### ② 불필요한 인터페이스 상속 (LSP 위반)

단순히 코드를 재사용하기 위해, 맥락이 다른 클래스를 상속받으면 치명적인 버그를 낳습니다.

```kotlin
// 정산 계산기가 세금 정책을 상속받아 엉뚱한 결과(0)를 리턴함
class AdjustmentCalculator: Tax() { ... }

val calculator = AdjustmentCalculator()
// 나이 제한 정책이 적용되어 0원이 반환되는 버그 발생
calculator.calculate(age = 10, amount = 100) 
```

* `AdjustmentCalculator`는 `Tax`가 아님에도 상속을 받아, 필요 없는 `age` 검증 로직까지 물려받았습니다. 이는 **리스코프 치환 원칙(LSP)** 위배이자 **인터페이스 분리 원칙(ISP)** 위배입니다.

#### ③ 부모와 자식의 동시 수정 (Fragile Base Class)

부모의 상태(`totalAmount`)를 자식이 공유하는 구조에서, 부모의 로직만 수정되면 자식의 결과값이 틀어질 수 있습니다.    
이는 부모와 자식을 동시에 수정해야 하는 상황을 초래합니다.

<br>

### 5. 💡 결론 및 배운 점

상속을 통해 중복을 제거할 수 있지만, 이는 **강한 결합**이라는 비용을 지불해야 합니다.

* **상속은 캡슐화를 깨뜨린다:** 자식이 부모의 내부 구현을 알아야 한다면 좋은 상속이 아니다.
* **IS-A 관계 확인:** 단순히 코드를 줄이기 위해서가 아니라, 진짜 부모-자식 관계일 때만 상속을 사용해야 한다.
* **상태 공유 지양:** 부모의 가변 필드(`var`)를 자식이 의존하게 하지 말자.