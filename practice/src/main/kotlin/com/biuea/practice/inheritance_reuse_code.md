# 상속과 코드 재사용
객체지향에서는 코드 재사용을 위해 상속을 통한 새로운 코드를 추가하는 방법을 사용한다.   
상속을 사용한다는 것은 중복된 코드를 제거한다는 의미이기도 한데, 가령 비슷한 형식이지만 구현의 차이가 있는 요구사항이 생기게 되었을 때 빠른 방법은 기존의 코드를 복사한 후 구현을 달리하는 방법이 있다.

```kotlin
class RegularTax {
    val PERCENTAGE = 10
    
    fun calculate(amount: Int): Int {
        return amount * PERCENTAGE / 100
    }
}

class MedicalTax {
    val PERCENTAGE = 20
    
    fun calculate(amount: Int): Int {
        return amount * PERCENTAGE / 100
    }
}
```

요구사항으로 세금 계산 시 전부 20살 이하일 경우 세금 계산 면제가 추가되었다.


```kotlin
class RegularTax {
    val PERCENTAGE = 10
    
    fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0
        
        return amount * PERCENTAGE / 100
    }
}

class MedicalTax {
    val PERCENTAGE = 20
    
    fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0
        
        return amount * PERCENTAGE / 100
    }
}
```
구현은 빠르게 되겠지만 가장 큰 문제는 중복 코드가 생성되고, 이 중복 코드는 유지 보수 시 2곳을 건드려야 한다는 의미이고, 실제로 2곳의 로직이 수정되었다.   
그리고 이런 구현이 많아질수록 건드려야하는 포인트들이 많아지고, 유지보수를 어렵게 만든다.
때문에 이런 중복을 제거하는 원칙을 DRY(Don't Repeat Yourself)라고 한다.   
동일한 지식을 반복하지 않음으로써 중복된 코드를 없애라는 의미이다.

### 타입을 이용한 중복 제거
중복을 제거하기 위해 여러 방법이 있고, 그 중에 타입을 이용하여 중복을 제거하는 방법이 있다.

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

enum class TaxType {
    REGULAR,
    MEDICAL
}
```
타입을 이용할 경우 분기를 이용하여 중복 코드를 제거할 수 있다.   
위의 예제에서 확인할 수 있듯 2개의 Tax 클래스가 TaxCalculator로 합쳐졌고, 로직이 하나의 메서드 안에서 동작하도록 변경되었다.   
하지만 분기가 늘어날수록 내부에서 private method로 코드를 분리할지라도, TaxCalculator가 다소 복잡해질 수 있는 여지가 존재한다.   
타입이 늘어날수록 요구사항이 늘어날수록 TaxCalculator의 기능이 복잡해지고, 이럴 경우 유지보수 시 어디를 건드려야할지 모르는 순간이 올지도 모른다.

### 상속을 이용한 중복제거
상속은 결합도를 높이지만, 비슷한 특성을 가진 클래스들의 군집들에 대해 인터페이스만 잘 정의된다면 클래스를 추가하는 것만으로 코드의 중복을 없앨 수 있다.

```kotlin
abstract class Tax {
    abstract val PERCENTAGE: Int

    override fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0
        
        return this.amount(amount)
    }
    
    protected abstract fun calculate(amount: Int)
}

class RegularTax: Tax() {
    override val PERCENTAGE get() = 10
    
    fun calculate(amount: Int, age: Int): Int {
        return amount * PERCENTAGE / 100
    }
}

class MedicalTax: Tax() {
    override val PERCENTAGE get() = 20
    
    override fun calculate(amount: Int, age: Int): Int {
        return amount * PERCENTAGE / 100
    }
}
```
예제의 경우 공통된 정책인 20대 이하의 로직은 부모 클래스로 옮기고, 자식 클래스는 실제 구현의 내용에 맞는 계산만 구현하였다.   
이렇게 상속은 구현 클래스를 추가하는 것만으로 중복된 로직을 제거할 수 있다.   
다만, 상속을 이용했을 때 안좋은 점은 자식 클래스가 부모의 구현을 참조하는 케이스이다.   
RegularTax, MedicalTax는 부모의 지식에 관계없이 오롯이 자신의 구현을 하였기 때문에 결합일지라도 적은 코드로 신규 기능을 빠르게 추가할 수 있었다.
하지만 super 키워드를 이용하여 자식 클래스가 부모 클래스를 참조해야할 일이 생긴다면 부모의 지식까지 참조하기 때문에 이런 케이스는 피해야 한다.

```kotlin
open class RegularTax {
    open val PERCENTAGE get() = 10
    
    open fun agePolicy(age: Int, amount: Int): Int {
        if (age <= 20) return 0 
        return amount
    }
    
    open fun calculate(amount: Int, age: Int): Int {
        return amount * PERCENTAGE / 100
    }
}

class MedicalTax: RegularTax() {
    override val PERCENTAGE get() = 20
    
    override fun calculate(amount: Int, age: Int): Int {
        super.agePolicy(age, amount)
        return amount * PERCENTAGE / 100
    }
}
```
세금 계산에는 20대 이하일 경우 세금 면제라는 정책이 존재한다.   
MedicalTax는 이를 충족시키기 위해 부모 클래스가 어떻게 동작하는지 파악하고, 세액을 계산할 때 super.agePolicy를 이용하는 문제를 볼 수 있다.

### 불필요한 인터페이스 상속
인터페이스와 그를 상속하는 자식 클래스가 유사한 기능을 가진 군집이라면 중복 코드를 제거하고, 유지보수를 용이하게 하지만 그렇지 못한다면, 자식은 불필요한 구현과 오류를 맞이할 수 있다.

```kotlin
abstract class Tax {
    abstract val PERCENTAGE: Int

    override fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0
        
        return this.amount(amount)
    }
    
    protected abstract fun calculate(amount: Int): Int
}

class RegularTax: Tax() {
    override val PERCENTAGE get() = 10
    
    fun calculate(amount: Int): Int {
        return amount * PERCENTAGE / 100
    }
}

class MedicalTax: Tax() {
    override val PERCENTAGE get() = 20

    override fun calculate(amount: Int): Int {
        return amount * PERCENTAGE / 100
    }
}

class AdjustmentCalculator: Tax() {
    override val PERCENTAGE get() = 20

    override fun calculate(amount: Int): Int {
        return amount * PERCENTAGE / 100
    }
}

fun main() {
    val calculator = AdjustmentCalculator()
    calculator.calculate(age = 10, amount = 100)
}
```
위의 예제에서는 정산 계산기가 Tax라는 인터페이슬르 잘못 상속받고, 구현한 결과 뜬금없는 age를 입력받고 갑자기 0을 반환함을 알 수 있다.   
AdjustmentCalculator는 불필요한 오퍼레이션이 존재하는 인터페이스를 상속받았고, 심각한 버그를 초래하게 되었다.    
상속은 자식 클래스의 구조를 깨트릴 수 있다.

### 부모 클래스와 자식 클래스의 동시 수정 문제
부모 클래스를 잘못 상속받고, 메소드를 오버라이드하게 되면 자식은 부모의 상태를 수정해야하는 경우가 있다.

```kotlin
abstract class Tax {
    protected var totalAmount = 100000
    abstract val PERCENTAGE: Int

    override fun calculate(amount: Int, age: Int): Int {
        if (age <= 20) return 0
        
        return this.amount(amount)
    }
    
    protected abstract fun calculate(amount: Int): Int
}

class RegularTax: Tax() {
    override val PERCENTAGE get() = 10
    
    fun calculate(amount: Int): Int {
        return amount * PERCENTAGE / 100
    }
}
```
이렇게 단순 계산만했을 뿐이지만 RegularTax는 totalAmount를 추가적으로 수정하지 않았으므로 totalAmount를 이용할 일이 생긴다면, 잘못 정산된 최종 결과값을 얻게 된다.    
이 또한 자식 클래스가 부모 클래스의 상태를 알아야하는 강하게 결합된 상태로 부모와 자식이 동시에 수정되는 문제를 야기한다.

여러 예제를 살펴봤고, 올바르게 상속된 예제를 살펴본 결과 다음의 결과를 알게 되었다.
* 부모 내부의 상태를 자식에게 결합시키면 안된다. 결합될 경우 자식의 구현을 망가트릴 수 있다.
* 상속의 오용은 예기치 못한 오류를 발생시킨다.
* 잘못된 중복 로직 제거는 자식 계층에게 영향을 끼치고, 부모의 지식을 알아야만 하는 문제를 발생시킨다.