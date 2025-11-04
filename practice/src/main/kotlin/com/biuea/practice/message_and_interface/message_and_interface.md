# 메시지와 인터페이스
## 협력과 메시지
### 클라이언트 - 서버
협력은 어떤 객체가 다른 객체에게 요청하면서 시작된다.   
메시지를 매개로 메시지 전송자, 메시지 수신자가 구성되고 요청과 응답의 조합이 두 객체의 협력을 구성한다.   
이러한 협력 관계를 클라이언트(메시지 전송자) - 서버(메시지 수신자) 모델이라 부른다.

### 메시지와 메시지 전송
메시지는 객체들이 협력하기 위한 소통 수단이다.   
메시지는 오퍼레이션명, 인자로 구성되고 메시지 전송은 추가적으로 메시지 수신자를 더한 것이다.   
```kotlin
isSatisfiedBy(screening) // 메시지
condition.isSatisfiedBy(screening) // 메시지 전송
```

### 메시지와 메서드
메시지가 전송되고, 메시지 수신자는 수신을 받아 실제로 어떤 함수를 실행할지 결정하고, 이를 메서드라 부른다.   
메시지를 전송할 때 수신자.메시지와 같은 방식으로 전송하게 된다.   
여기서 수신자는 실제 구현체(컴파일과 런타임 시점의 메서드 동일)일 수 있고, 인터페이스(컴파일과 런타임 시점의 메서드가 다름)일수도 있다.  
즉, 인터페이스라면 메시지를 수신받고, 실 객체 타입에 따라 실행되는 메서드가 달라질 수 있다.   
이런 전송, 수신 관계는 전송자 입장에서는 단지 메시지 전송만을, 수신자는 메시지가 도착했음을 알면 되므로 느슨한 결합을 유도한다.  

### 퍼블릭 인터페이스와 오퍼레이션
외부에 공개한 메시지들의 집합을 퍼블릭 인터페이스라 부른다.   
그리고 오퍼레이션은 수행 가능한 행동의 추상화이고, 부를 때는 이름과 파라미터의 목록을 합쳐 시그니쳐라 부른다.   

## 인터페이스와 설계 품질
최소한의 인터페이스는 필요한 오퍼레이션만 인터페이스에 포함하고, 추상적인 인터페이스는 무엇을 하는지를 표현한다.  

### 디미터 법칙

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
이 코드는 ReservationAgency::reserve에 전달된 인자 간의 결합도가 너무 높아 인자들의 변경이 ReservationAgency에게 영향을 주게 된다.   
디미터의 법칙은 협력 경로를 제한하여 2번 이상의 포인터 체인을 걸지 않도록 한다.   
이렇게 될 경우 1번의 포인터만 가리키므로 경로를 제한하게 되면서 2번째 포인터의 내부 구현과의 결합에서 벗어나게 된다.
```kotlin
class ReservationAgency {
    fun reserve(screening: Screening, customer: Customer, audienceCount: Int): Reservation {
        val fee = screening.calculate(audienceCount)
        return Reservation(customer, screening, fee, audienceCount)
    }
}
```
디미터 법칙은 결국 캡슐화를 표현한 것이고, 메시지 수신자의 내부 상태와의 결합을 피하고 퍼블릭 인터페이스로만 소통함을 의미한다.   
그리고 캡슐화는 내부 상태를 외부에 노출시키지 않음으로써 자신의 책임을 외부로 노출시키지 않음을 의미한다.   

### 의도를 드러내는 인터페이스
메서드를 명명하는 방법은 '메서드가 작업을 어떻게 수행하는지를 나타내도록 명명하는 방법', '무엇을 하는지를 드러내는 방법' 2가지가 있다.   
첫 번째 방법은 메서드의 네이밍이 내부 구현 방법을 드러낸다.
```kotlin
class PeriodCondition {
    fun isSatisfiedByPeriod(screening: Screening): Boolean
}

class SequenceCondition {
    fun isSatisfiedBySequence(screening: Screening): Boolean
}
```
이는 클라이언트에게 객체의 종류를 전부 알게 함을 강제한다.  
그리고 구현체의 변경이 클라이언트에게 변경의 영향을 전파한다.   

두 번째 방법은 객체가 협력 안에서 수행해야하는 책임에 관해 고민해야한다.   
```kotlin
class PeriodCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class SequenceCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}
```
어떻게 메시지를 처리할 것인지가 아닌, 무엇을 할 것인지로 변경함에 따라 2개의 객체가 결국 같은 책임을 지는 메시지임을 알게 되었다.   

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
메서드의 네이밍을 의도를 드러내도록 하여 하나의 인터페이스를 두어 캡슐화까지 챙길 수 있게 된다.   

## 원칙의 함정
### 결합도와 응집도의 충돌
```kotlin
class Theater {
    fun enter(audience: Audience) {
        if (audience.getBag().hasInvitation()) {
            val ticket = ticketSeller.getTicketOffice().getTicket()
            audience.getBag().setTicket(ticket)
        } else {
            val ticket = ticketSeller.getTicketOffice().getTicket()
            audience.getBag().minusAmount(ticket.getFee())
            ticketSeller.getTicketOffice().plusAmount(ticket.getFee())
            audience.getBag().setTicket(ticket)
        }
    }
}
```
Theater의 enter 메서드에서는 객체들이 2개 이상의 포인터를 이용하여 내부 구현과 결합되어 있다.   
이는 디미터의 법칙을 위반한 것이고, audience 내부로 책임을 위임하면 다음과 같다.

```kotlin
class Theater {
    fun enter(audience: Audience) {
        val ticket = ticketSeller.getTicketOffice().getTicket()
        audience.buy(ticket)
    }
}

class Audience {
    fun buy(ticket: Ticket): Long {
        if (bag.hasInvitation()) {
            bag.setTicket(ticket)
            return 0L
        } else {
            bag.setTicket(ticket)
            bag.minusAmount(ticket.getFee())
            
            return ticket.getFee()
        }
    }
}
```
Audience에게 책임을 위임한 결과 결합이 낮아지고, 응집도가 높아졌다.  
다만 이렇듯 무조건 위임한다고해서 좋은 결과로 이어지지 않는다.

```kotlin
class PeriodCondition: DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return (screening.getWhenScreened().getDayOfWeek() == condition.getDayOfWeek()
                && condition.getStartTime() <= screening.getWhenScreened()
                && condition.getEndTime() >= screening.getWhenScreened())
    }
}

---
class PeriodCondition: DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return screening.isDiscountable(dayOfWeek, startTime, endTime)
    }
}

class Screening {
    fun isDiscountable(dayOfWeek: DayOfWeek, startTime: LocalTime, endTime: LocalTime): Boolean {
        return (whenScreened.getDayOfWeek() == dayOfWeek
                && startTime <= whenScreened
                && endTime() >= whenScreened)
    }
}
```
PeriodCondition 메서드 내부에선 screening의 내부 구현을 참조하여 책임을 위임한 결과 Screening이 할인의 책임까지 가지게 되었다.   
Screening은 영화를 예매한다는 책임만 있음에도 불구하고, 디미터 법칙을 준수한 결과 할인이라는 책임까지 가지게 되었다.   
이렇듯 매번 법칙을 준수하는 게 좋은 결과를 발생시키지는 않는다.   

## 명령 - 쿼리 분리 원칙
명령 - 쿼리 분리 원칙은 오퍼레이션이 부수효과를 발생시키는 명령이거나 부수효과를 발생시키지 않는 쿼리 중 하나여야 함을 의미한다.   
즉, 오퍼레이션이 부수효과를 발생시키면서 결과를 반환하지 않아야 한다.   

만약 이를 어기게 된다면, 쿼리의 결과를 예측하기가 어렵고(멱등성 x), 많은 버그를 유발하며, 정해진 체이닝, 디버깅이 어려워 질 수 있다.  