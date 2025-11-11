# 객체 분해
문제 해결에 있어 좋은 방법은 단기 기억에 적당한 양의 정보로 문제를 해결하는 것이다.   
이런 작업은 추상화이며, 불필요한 정보를 제거하고 문제 해결에 필요한 핵심만 남기는 작업이다.   
추상화를 위해서는 큰 문제를 작은 문제로 계속해서 분해해야하고, 이는 한번에 처리할 수 있는 정보의 양으로 줄이는 것이다.   

## 프로시저 추상화와 데이터 추상화
프로시저 추상화는 소프트웨어가 무엇을 해야하는지를 추상화하고, 기능 분해를 중심으로 추상화를 진행한다. 

데이터 추상화는 소프트웨어가 무엇을 알아야하는지 추상화하고, 데이터를 중심으로 타입 추상화(추상 데이터 타입), 데이터를 중심으로 프로시저 추상화(객체 지향 프로그래밍)로 나눌 수 있다.

## 프로시저 추상화와 기능 분해
기능 분해에서 추상화 단위는 프로시저이고, 이 단위로 분해된다.   
프로시저는 반복적으로 실행되거나 유사하게 실행되는 작업들을 하나의 장소에 모아놓아 로직을 재사용하고 중복을 방지하는 방법이다.  
전통적으로 하향식 접근법으로 시스템을 분해하고, 최상위 기능을 정의하고 점차 세분화된 하위 기능들을 분해해나가는 방식이다.   

### 급여 관리 시스템
급여 관리 시스템의 최상위 프로시저는 '직원의 급여를 계산한다.' 이다.  
이를 더 작은 단위로 분해하면 다음과 같다.   

```
직원의 급여를 계산한다.
    ├─ 사용자로부터 소득세율을 입력받는다.
    ├─ 직원의 급여를 계산한다.
    └─ 양식에 맞게 결과를 출력한다.
```
각 단계는 여전히 추상적이며, 이 수준을 감소시켜 더욱 구체적인 문장들로 분해한다.
```
직원의 급여를 계산한다.
    ├─ 사용자로부터 소득세율을 입력받는다.
        ├─ "세율을 입력하세요: "라는 문장을 화면에 출력한다.
        └─ 키보드를 통해 세율을 입력받는다.
    ├─ 직원의 급여를 계산한다.
        ├─ 전역 변수에 저장된 직원의 기본급 정보를 얻는다.
        └─ 급여를 계산한다.
    └─ 양식에 맞게 결과를 출력한다.
        └─ "이름: {직원명}, 급여: {계산된 금액}" 현식에 따라 출력 문자열을 생성한다.
```

기능 분해를 위한 하향식 접근법은 필요한 기능을 생각하고, 이 때 필요한 데이터 종류와 저장 방식을 정한다.

### 구현
```kotlin
val employees = ["직원A", "직원B", "직원C"]
val basePays = [400, 300, 250]

fun main() {
//    직원의 급여를 계산한다.
//    ├─ 사용자로부터 소득세율을 입력받는다.
//        ├─ "세율을 입력하세요: "라는 문장을 화면에 출력한다.
//        └─ 키보드를 통해 세율을 입력받는다.
//    ├─ 직원의 급여를 계산한다.
//        ├─ 전역 변수에 저장된 직원의 기본급 정보를 얻는다.
//        └─ 급여를 계산한다.
//    └─ 양식에 맞게 결과를 출력한다.
//        └─ "이름: {직원명}, 급여: {계산된 금액}" 현식에 따라 출력 문자열을 생성한다.
    val taxRate = getTaxRate()
    val pay = calculatePayFor(name, taxRate)
    println(describeResult(name, pay))
}

fun getTaxRate(): Double {
    print("세율을 입력하세요: ")
    return readLine()?.toDoubleOrNull() ?: 0.0
}

fun calculatePayFor(name: String, taxRate: Double): Double {
    val index = employees.indexOf(name)
    val basePay = basePays.getOrNull(index) ?: 0
    return basePay * (basePay - taxRate)
}

fun describeResult(name: String, pay: Double): String {
    return "이름: $name, 급여: $pay"
}
```
하향식 기능 분해는 최상위부터 최하위까지 트리 형태로 분해를 하며, 구조적이고 체계적인 방법으로 보인다.   
다만 이 방법은 다음과 같은 여러 문제에 봉착한다.  
* 시스템은 하나의 메인 함수로 구성되어 있지 않다.
* 기능 추가나 요구사항 변경으로 인해 메인 함수를 빈번하게 수정해야 한다.
* 비즈니스 로직이 사용자 인터페이스와 강하게 결합된다.
* 하향식 분해는 너무 이른 시기에 함수들의 실행순서를 고정시키기 때문에 유연성과 재사용성이 저하된다.
* 데이터 형식이 변경될 경우 파급 효과를 예측할 수 없다.

하향식 기능 분해는 최상위의 프로시저를 중심으로 하위로 시스템을 설계해나간다.   
하지만 시스템은 하나의 최상위 프로시저로 구성되지 않고, 언제든지 하위 노드가 분리되어 최상위가 될 수 있고, 때로는 필요에 의해 제거될 수 있다.   
이런 관점에서 하향식 기능 분해는 시스템의 유연성을 저하시킨다.

가령 급여 관리 시스템에 모든 직원들의 기본급 총합을 구하는 기능을 추가하면 다음과 같다.

```kotlin
val employees = listOf("직원A", "직원B", "직원C")
val basePays = listOf(400, 300, 250)

fun main(operation: String) {
    val taxRate = getTaxRate()
    val pay = calculatePayFor(name, taxRate)
    when (operation) {
        "calculatePay" -> println(describeResult(name, pay))
        "sumBasePays" -> println("기본급 총합: ${sumOfBasePays()}")
        else -> println("알 수 없는 작업입니다.")
    }
}

fun getTaxRate(): Double {
    print("세율을 입력하세요: ")
    return readLine()?.toDoubleOrNull() ?: 0.0
}

fun calculatePayFor(name: String, taxRate: Double): Double {
    val index = employees.indexOf(name)
    val basePay = basePays.getOrNull(index) ?: 0
    return basePay * (basePay - taxRate)
}

fun describeResult(name: String, pay: Double): String {
    return "이름: $name, 급여: $pay"
}

fun sumOfBasePays(): Double {
    return basePays.reduce { acc, pay -> acc + pay }
}
```
하나의 기능을 추가했는데, 클라이언트의 코드가 변경이 되었다.   
이는 비즈니스와 사용자 인터페이스가 결합되었기 때문이고, 비즈니스의 변경이 클라이언트에까지 영향을 미침을 시사한다.   
또한 주석에서도 볼 수 있듯 프로시저의 실행 순서가 고정되어 있어 시간적인 제약이 반영되어 있다.   
그리고 데이터 변경이 발생할 경우 관련된 모든 부분을 수정해야하는데, 어떤 프로시저가 어떤 데이터를 참조하고 있는지 알기 어렵다.  

이 문제를 해결하기 위해 논리적인 제약을 기준으로 변경하고, 여러 객체들에게 제어를 분산시켜야 한다.   
그리고 데이터 변경 영향을 최소화하기 위해 정의된 퍼블릭 인터페이스를 통해 데이터에 접근하도록 해야 한다.

## 모듈
### 정보 은닉과 모듈
정보 은닉은 시스템을 모듈 단위로 분해하기 위한 원리로, 자주 변경되는 부분(구현)을 상대적으로 덜 변경되는 인터페이스 뒤로 감추는 것이 핵심이다.   
즉, 변경 가능의 여지가 높아도 인터페이스(고수준)를 의존하기 때문에 변경과 확장에 자유로워진다.   

하향식 기능 분해와 달리 모듈은 감춰야할 데이터를 결정하고, 이 데이터를 조작하는 데 필요한 함수를 결정한다.

## 데이터 추상화와 추상 데이터 타입
### 추상 데이터 타입

```kotlin
class Employee(
    private val name: String,
    private val basePay: Double,
    private val hourly: Boolean,
    private val timeCard: Int
) {
    fun calculatePay(taxRate: Double): Double {
        return if (houlry) {
            this.calculateHourlyPay(taxRate)
        } else {
            calculateSalaryPay(taxRate)
        }
    }
    
    fun calculateHourlyPay(taxRate: Double): Double {
        return if (hourly) {
            (basePay * timeCard) - (basePay * timeCard) * taxRate
        } else {
            0.0
        }
    }
    
    fun calculateSalaryPay(taxRate: Double): Double {
        return basePay - (basePay * taxRate)
    }
    
    fun monthlyBasPay(): Double {
        return if (hourly) {
            0
        } else {
            basePay
        }
    }
}

val employees = listOf(
    Employee("직원A", 400.0, false, 0),
    Employee("직원B", 300.0, false, 0),
    Employee("직원C", 20.0, true, 160),
    Employee("직원D", 25.0, true, 120),
    Employee("직원E", 30.0, true, 100),
    Employee("직원F", 350.0, false, 0)
)

fun calculatePay(name: String) {
    val taxRate = getTaxRate()
    val employee = employees.find { it.name == name }
    if (employee != null) {
        val pay = employee.calculatePay(taxRate)
        println("이름: ${employee.name}, 급여: $pay")
    } else {
        println("해당 이름의 직원이 없습니다.")
    }
}
```
추상 데이터 타입은 시스템의 상태를 저장할 데이터를 표현한다.   
추상 데이터 타입으로 표현된 데이터를 이용해서 기능을 구현하는 핵심 로직은 추상 데이터 타입 외부에 존재한다.

가령 스택, 큐, 리스트 등의 자료 구조들은 interface로 정의되고 내부 push, pop, isEmpty등의 메서드 또한 body가 없는 인터페이스이다.  
외부 클라이언트는 내부 구현을 알 필요없이 이 인터페이스만을 사용한다.

## 객체지향
추상 데이터 타입은 오퍼레이션(메서드)을 기준으로 타입을 묶는다면, 객체지향은 타입을 기준으로 오퍼레이션을 묶는다.  
즉, 객체지향은 정규 직원과 아르바이트 직원에 대한 구현을 정의하고 각 클래스들이 동일한 오퍼레이션을 구현하는 형태이다.   
그리고 구현을 대표하는 인터페이스가 있기에 새로운 요구사항 구현에 대해서 기존 코드에 아무런 영향을 미치지 않고, 대처할 수 있다.   
이를 개방 - 폐쇄 원칙이라 부른다.