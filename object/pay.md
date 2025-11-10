# [OOP] 객체 분해
- **Tags:** #OOP #ObjectOriented #Design #Abstraction #Procedural #ADT

---

### 무엇을 배웠는가?
문제를 해결하기 위해 큰 문제를 작은 단위로 나누는 **추상화**의 두 가지 접근법, 즉 `프로시저 추상화(기능 분해)`와 `데이터 추상화(객체 지향)`의 차이점을 학습했습니다.    
특히, 전통적인 하향식 기능 분해 방식이 왜 변경에 취약한지와 데이터 추상화가 이 문제를 어떻게 해결하는지 이해했습니다.

---

### 왜 중요하고, 어떤 맥락인가?
초기 소프트웨어 설계는 '어떻게' 동작하는지에 초점을 맞춘 **프로시저 추상화**를 기반으로 했습니다.    
이는 '직원의 급여를 계산한다'와 같은 최상위 기능을 정의하고, 이를 '세율 입력', '급여 계산', '결과 출력' 등 작은 하위 기능(프로시저)으로 계속 분해하는 **하향식 접근법**입니다.

하지만 이 방식은 **심각한 문제**를 가집니다.
1.  **경직성**: 기능의 실행 순서가 너무 이른 시점에 고정되어 유연성이 떨어집니다.
2.  **변경에 취약**: '기본급 총합 구하기' 같은 새 기능이 추가되자, 최상위 `main` 함수에 `when`(분기)문이 추가되는 등 기존 코드가 수정되어야 했습니다.
3.  **높은 결합도**: 비즈니스 로직(급여 계산)과 사용자 인터페이스(출력)가 `main` 함수에 강하게 결합됩니다.
4.  **데이터 파급 효과**: 모든 프로시저가 `employees`, `basePays` 같은 전역 데이터에 의존하면, 이 데이터의 형식이 변경될 때 관련된 모든 함수를 찾아 수정해야 합니다.

이러한 문제들은 **데이터와 행동을 분리**했기 때문에 발생합니다. 객체지향은 이 문제를 해결하기 위해 데이터 추상화를 사용합니다.

---

### 상세 내용

#### 1. 프로시저 추상화 (기능 분해)의 한계
기능 분해는 **무엇을 해야 하는지**를 추상화합니다. "급여 관리 시스템" 예제에서, `main` 함수는 모든 하위 프로시저를 제어합니다.

```kotlin
// 기능 분해: main 함수가 모든 로직을 제어
fun main(operation: String) {
    when (operation) {
        "calculatePay" -> {
            val taxRate = getTaxRate() // 1. UI 로직
            val pay = calculatePayFor(name, taxRate) // 2. 비즈니스 로직
            println(describeResult(name, pay)) // 3. UI 로직
        }
        "sumBasePays" -> { // 4. 기능 추가로 인한 main 수정
            println("기본급 총합: ${sumOfBasePays()}")
        }
        else -> println("알 수 없는 작업입니다.")
    }
}
```
위 코드처럼, 새 기능 sumBasePays가 추가되자 main 함수가 직접 수정되어야 했습니다.    
이는 시스템이 유연하지 못하고 변경에 취약함을 의미합니다.

#### 2. 데이터 추상화 (무엇을 알아야 하는가)
데이터 추상화는 **무엇을 알아야 하는지**를 추상화하며, 데이터와 행동을 하나로 묶어 문제를 해결합니다.   
* 정보 은닉 (Information Hiding): 자주 변경되는 부분(구현)을 덜 변경되는 안정적인 인터페이스 뒤로 감춥니다.
* 추상 데이터 타입 (ADT): 데이터(상태)와 해당 데이터를 조작하는 오퍼레이션(행동)을 하나로 묶습니다.

외부 클라이언트는 ADT의 인터페이스(e.g., push, pop)만 사용할 뿐, 내부 구현(e.g., 배열, 리스트)은 알 필요가 없습니다.   
Employee 클래스 예시에서 calculatePay 로직이 Employee 클래스 내부로 이동했습니다.    
이제 외부에서는 employee.calculatePay()를 호출하기만 하면 됩니다.

```Kotlin
// Employee 클래스가 스스로의 데이터를 책임진다.
class Employee(
    private val name: String,
    private val basePay: Double,
    private val hourly: Boolean,
    //...
) {
    // 행동(오퍼레이션)이 데이터와 함께 묶여있음
    fun calculatePay(taxRate: Double): Double {
        return if (hourly) {
            this.calculateHourlyPay(taxRate) // 내부 로직
        } else {
            calculateSalaryPay(taxRate) // 내부 로직
        }
    }
    // ...
}
```

#### 3. 객체지향 (ADT에서 한 걸음 더)
추상 데이터 타입은 오퍼레이션을 기준으로 타입을 묶지만 객체지향은 타입을 기준으로 오퍼레이션을 묶습니다.   
위 Employee ADT 예제에는 여전히 if (hourly)라는 분기문이 남아있습니다. 이는 변경에 취약한 지점입니다.   
객체지향은 다형성을 사용해 이 문제를 해결합니다.   
Employee를 인터페이스로 만들고, SalariedEmployee(정규직)와 HourlyEmployee(아르바이트)가 각자 calculatePay를 구현하도록 합니다.

* 변경 전 (ADT): 클라이언트가 calculatePay 호출 -> Employee가 if문으로 분기 
* 변경 후 (OOP): 클라이언트가 calculatePay 호출 -> 객체의 실제 타입(SalariedEmployee or HourlyEmployee)이 알아서 올바른 구현을 실행

이러한 설계는 새로운 직원 타입(e.g., CommissionedEmployee)이 추가되어도 기존 calculatePay 호출 코드는 전혀 수정할 필요가 없게 만듭니다.    
이것이 `개방-폐쇄 원칙(OCP)`입니다.

### 핵심
프로시저 추상화(기능 분해)는 데이터와 행동을 분리시켜, 변경에 취약하고 높은 결합도를 가진 시스템을 만듭니다.   
데이터 추상화는 데이터와 행동을 정보 은닉을 통해 하나의 모듈(객체)로 묶어 캡슐화합니다.    
객체지향은 여기서 더 나아가 다형성을 이용, if/when 같은 조건 분기 로직을 객체 타입 자체로 대체함으로써, 개방-폐쇄 원칙(OCP)을 만족하는 유연하고 확장 가능한 설계를 가능하게 합니다.