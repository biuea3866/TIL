# 합성과 유연한 설계
상속 관계는 부모 클래스를 상속받아 부모 클래스의 코드를 이용할 수 있지만, 부모 클래스와 강결합되는 특성을 가진다.   
이 관계는 자식 클래스가 부모 클래스 내부 구현에 대해 상세히 알아야하며, 부모 클래스의 변경이 자식 클래스에게 영향을 주게 된다.  
반면 합성 관계는 내부에 퍼블릭 인터페이스를 의존함시킴으로써 느슨한 결합을 유지할 수 있다.

## 상속 조합 폭발
상속은 클래스 간의 결합도를 높이고, 부모 클래스의 변경이 영향도가 모든 자식 클래스에게 영향을 줄 수 있다.

### 핸드폰 요금 예제
* 기본 정책: 일반 요금제, 심야 할인 요금제
* 부가 정책: 세금 정책, 기본 요금 할인 정책
* 세금 정책은 기본 정책의 계산이 끝난 이후 세금을 부과할 수 있거나 하지 않아도 된다.
* 기본 정책에 여러 부가 정책을 적용할 수 있다.
    * 세금 정책만 적용하거나, 기본 요금 할인 정책만 적용하거나, 둘 다 적용하거나 그리고 순서에 관계없이 적용가능하다.

```kotlin
abstract class Phone(
    private val calls: List<Call> = emptyList()
) {
    fun calculateFees(): Money {
        var result = Money.ZERO
        this.calls.forEach {
            result = result.plus(this.calculateCallFee(it))
        }

        return result
    }

    protected abstract fun calculateCallFee(call: Call): Money

    protected open fun afterCalculated(fee: Money): Money {
        return fee
    }
}

class RegularPhone(
    private val amount: Money,
    private val seconds: Duration
) : Phone() {
    override fun calculateCallFee(call: Call): Money {
        return this.amount.times(call.duration.seconds / seconds.seconds)
    }
}

class NightlyDiscountPhone(
    private val nightlyMoney: Money,
    private val regularMoney: Money,
    private val seconds: Duration
) : Phone() {
    override fun calculateCallFee(call: Call): Money {
        if (this.call.from.hour >= 22) {
            return this.nigthlyMoney.times(call.duration.seconds / seconds.seconds)
        }

        return this.regularMoney.times(call.duration.seconds / seconds.seconds)
    }
}
```
기본 정책을 구현하기 위해 Phone이라는 추상 클래스를 상속받아 각 요금제에 대한 구현체들은 요금을 계산하는 메서드 훅을 오버라이드한다.   
이 기본 정책에 부가 정책을 적용해야 한다면, 각 기본 정책 클래스를 한번 더 상속 시켜 부가 정책 구현체들을 구성해야한다.

```kotlin
class TaxableRegularPhone(private val taxRate: Double): RegularPhone() {
    override fun afterCalculated(fee: Money): Money {
        return fee.plus(fee.times(taxRate))
    }
}

class TaxableNightlyDiscountPhone(private val taxRate: Double): NightlyDiscountPhone() {
    override fun afterCalculated(fee: Money): Money {
        return fee.plus(fee.times(taxRate))
    }
} 
```
부가 정책이 기본 정책을 상속함에 따라 다양한 조합이 탄생함을 알 수 있다.
* RegularPhone
    * TaxableRegularPhone
    * RateDiscountableRegularPhone
* NightlyDiscountPhone
    * TaxableNightlyDiscountPhone
    * RateDiscountableNightlyDiscountPhone

m * n으로 이루어지는 조합은 케이스를 추가할 때마다 구현체들을 동등하게 만들어주어야하고, 중복 코드를 대량으로 생산하게 된다.

## 합성 관계
합성은 내부에 퍼블릭 인터페이스를 의존시키고, 런타임때 외부에서 주입받는 구체적인 인스턴스로 실제 기능을 동작시킨다.   
이러한 특성을 이용하여 각 책임을 갖는 퍼블릭 인터페이스들을 분리시키고, 이들을 조합해서 사용한다면 컴파일 타임에서는 구현체에 의존하지 않아도 된다.    
그리고 앞서 조합들을 하나의 상속으로 해결하려 했던 조합 폭발 문제 또한 해결이 가능하다.

```kotlin
interface RatePolicy {
    fun calculateFee(phone: Phone): Money
}

abstract class BasicRatePolicy : RatePolicy {
    override fun calculateFee(phone: Phone): Money {
        var result = Money.ZERO
        phone.forEach {
            result = result.plus(this.calculateCallFee(it))
        }

        return result
    }

    protected abstract fun calculateCallFee(call: Call): Money
}

class RegularPolicy(
    private val amount: Money,
    private val seconds: Duration
) : BasicRatePolicy() {
    override fun calculateCallFee(call: Call): Money {
        return this.amount.times(call.duration.seconds / seconds.seconds)
    }
}

class NightlyDiscountPolicy(
    private val nightlyMoney: Money,
    private val regularMoney: Money,
    private val seconds: Duration
) : Phone() {
    override fun calculateCallFee(call: Call): Money {
        if (this.call.from.hour >= 22) {
            return this.nigthlyMoney.times(call.duration.seconds / seconds.seconds)
        }

        return this.regularMoney.times(call.duration.seconds / seconds.seconds)
    }
}

class Phone(
    private val ratePolicy: RatePolicy, // 계산 정책 퍼블릭 인터페이스
    private val calls: MutableList<Call> = mutableListOf()
) {
    fun calculateFee(): Money {
        return ratePolicy.calculateFee(this)
    }
}

fun main() {
    val phone1 = Phone(RegularPolicy(Money.wons(10), Duration.ofSeconds(10)))
    val phone2 = Phone(NightlyDiscountPolicy(Money.wons(10), Money.wons(10), Duration.ofSeconds(10)))
}
```
Phone에 RatePolicy라는 퍼블릭 인터페이스를 의존시킴으로써 런타임에 언제든지 필요한 계산 정책을 주입받을 수 있다.   
즉, Phone을 계속해서 상속시켜 세금정책이 적용된 구현체를 만들 필요가 없어졌다.
그리고 여기에 부가 정책을 추가하게 되면 다음과 같다.

```kotlin
abstract class AdditionalRatePolicy(
    private val next: RatePolicy // 여러 부가 정책이 적용될 수 있으므로 데코레이터 패턴을 이용한다.
) : RatePolicy {
    override fun calculateFee(phone: Phone): Money {
        val fee = this.next.calculateFee(phone)
        return this.afterCalculated(fee)
    }

    protected abstract fun afterCalculated(fee: Money): Money
}

class TaxablePolicy(private val taxRatio: Double) : AdditionalRatePolicy() {
    override fun afterCalculated(fee: Money): Money {
        return fee.plus(fee.times(this.taxRatio))
    }
}

fun main() {
    val phone = Phone(TaxablePolicy(0.05, RateDiscountablePolicy(Money.wons(1000), RegularPolicy(...))))
}
```
다른 Policy 구현체들을 AdditionalRatePolicy를 상속하게 바꾸면 순서에 관계 없이 세금 정책 부과를 할 수 있다.   
AdditionalRatePolicy 클래스 내부엔 RatePolicy 인터페이스를 의존시킴으로써 데코레이터 패턴을 적용하여, 스택 형식으로 순서를 강제할 수 있다.  
원할 경우 인스턴스의 순서를 바꿈으로써 순서에 관계 없이 세금 정책 부과도 적용할 수 있다.   
이런 방식을 이용하여 새로운 정책이 추가되더라도, 개발자 입장에서는 하나의 인스턴스만 구현하고 외부에서 주입시켜주는 것만으로 요구사항을 달성할 수 있다.   
즉, 합성을 이용하여 결합에 약하고 유지보수에 강한 코드를 만들 수 있다. 