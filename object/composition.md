# 📝 TIL: 상속의 한계와 합성(Composition)을 통한 유연한 설계

### 📅 날짜: 2025. 12. 16

### 🏷️ 태그: #Kotlin #OOP #DesignPattern #Composition #Inheritance

## 1. 문제 정의 (Problem)

객체지향 설계에서 코드를 재사용하기 위해 흔히 상속(Inheritance)을 사용하지만, 무분별한 상속은 부모-자식 클래스 간의 강결합(Tight Coupling)을 유발합니다.    
이는 요구사항 변경 시 코드 수정 범위를 넓히고, 다양한 기능을 조합해야 할 때 클래스 수가 기하급수적으로 늘어나는 '클래스 폭발(Class Explosion)' 문제를 야기합니다.

## 2. 원인 분석: 상속의 문제점 (Inheritance Issues)

* **강결합:** 자식 클래스가 부모 클래스의 내부 구현을 상세히 알아야 하며, 부모의 변경이 자식에게 파급됩니다.
* **조합 폭발:** 핸드폰 요금제 예시처럼 '기본 정책'과 '부가 정책(세금, 할인 등)'을 조합해야 할 때, 상속을 사용하면 모든 경우의 수만큼 클래스를 만들어야 합니다. ($M \times N$ 조합 발생)
* **중복 코드:** 조합별로 유사한 코드가 여러 클래스에 분산되어 중복이 발생합니다.

### 🚫 Bad Case: 상속을 이용한 설계

> 부가 정책(세금 등)을 적용하기 위해 기존 클래스를 계속 상속받아 구현하면, 새로운 정책이 추가될 때마다 클래스가 폭발적으로 늘어납니다.

```
Phone
├── RegularPhone
│    ├── TaxableRegularPhone (세금+일반)
│    ├── DiscountableRegularPhone (할인+일반)
│    ├── TaxableDiscountableRegularPhone (세금+할인+일반)
│    └── DiscountableTaxableRegularPhone (할인+세금+일반)
└── NightlyDiscountPhone
├── TaxableNightlyPhone (세금+심야)
├── DiscountableNightlyPhone (할인+심야)
├── TaxableDiscountableNightlyPhone ...
└── ...
```

## 3. 해결책: 합성 (Composition) & 전략 패턴 (Strategy Pattern)

**합성**은 기능을 상속받는 대신, 필요한 기능을 가진 객체를 외부에서 주입(Dependency Injection)받아 사용하는 방식입니다.

* **느슨한 결합:** 구체적인 구현체 대신 퍼블릭 인터페이스(Interface)에 의존힙니다.
* **런타임 유연성:** 컴파일 시점이 아닌 런타임에 의존성이 결정되므로, 동적으로 정책을 변경하거나 조합하기 쉽습니다.

### ✅ Good Case: 합성과 데코레이터 패턴을 이용한 설계

> `RatePolicy` 인터페이스를 정의하고, 이를 구현한 정책들을 조립(Composition)하여 사용합니다.

```kotlin
// 1. 요금 정책 인터페이스
interface RatePolicy {
    fun calculateFee(phone: Phone): Money
}

// 2. 기본 요금 정책 (BasicRatePolicy 구현)
class RegularPolicy(private val amount: Money, private val seconds: Duration) : BasicRatePolicy() { ... }
class NightlyDiscountPolicy(...) : BasicRatePolicy() { ... }

// 3. 부가 정책 (데코레이터 패턴 적용)
// 다른 RatePolicy를 감싸서(next) 실행 순서를 제어하고 부가 기능을 수행함
abstract class AdditionalRatePolicy(
    private val next: RatePolicy 
) : RatePolicy {
    override fun calculateFee(phone: Phone): Money {
        val fee = this.next.calculateFee(phone) // 다음 정책 위임
        return this.afterCalculated(fee)      // 부가 기능 수행
    }
}

// 4. 사용 (조합)
// 세금 정책(Taxable) -> 할인 정책(RateDiscountable) -> 일반 요금(Regular) 순으로 조립
val phone = Phone(
    TaxablePolicy(0.05, 
        RateDiscountablePolicy(Money.wons(1000), 
            RegularPolicy(...)
        )
    )
)
```

## 4. 핵심 요약 (Key Takeaways)

* **상속은 부모-자식 간의 결합도를 높인다:** 부모의 변경이 자식에게 영향을 주므로 유연성이 떨어집니다.
  * 상속은 is-a 관계의 군집을 이루고, 수직적으로 1개의 뎁스이거나 n개의 뎁스가 생성될 때는 $M \times 1$ 조합일 때 고려해볼 수 있습니다.
* **합성은 유연하다:** 인터페이스를 통해 약한 결합을 유지하며, 런타임에 객체를 교체하거나 조합할 수 있습니다.
* **설계 원칙:** "Has-a(포함)" 관계가 "Is-a(상속)" 관계보다 유연한 설계를 가능하게 합니다.
