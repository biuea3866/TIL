# [객체지향] 자율적인 객체를 통한 협력 설계: 티켓 판매 예제

- **Tags:** #ObjectOriented #OOP #Design #Refactoring #Encapsulation #Autonomy

---

### 무엇을 배웠는가?
하나의 객체가 다른 모든 객체의 내부를 훤히 들여다보고 통제하는 절차적인 코드에서 각 **객체에게 자율성을 부여**하여 **스스로의 일을 처리하게 만드는 리팩토링** 과정을 학습했습니다.   
소극장 티켓 판매 애플리케이션 예제를 통해, 객체 간의 올바른 협력 관계를 설계하는 방법을 이해했습니다.

---

###  왜 중요하고, 어떤 맥락인가?
초기 설계에서 **소극장(`Theater`)** 객체는 관객의 가방을 마음대로 열어보고, 판매원의 매표소에 직접 접근하는 등 모든 것을 통제합니다.  
이는 현실 세계의 상식과 맞지 않을뿐더러, `Theater`가 너무 많은 책임을 져서 변경에 매우 취약한 구조를 만듭니다.

객체지향 설계의 핵심은 객체를 **명령에 따라 움직이는 수동적인 존재가 아닌, 스스로 판단하고 행동하는 자율적인 존재**로 바라보는 것입니다.   
각 객체가 자신의 책임에만 집중할 때, 전체 시스템은 더 유연하고 이해하기 쉬워지며 변경에 강해집니다.

---

### 상세 내용

#### 1. 문제 상황: 모든 것을 통제하는 `Theater`
초기 코드에서 `Theater`의 `enter` 메서드는 모든 일을 직접 처리합니다.

* 관객(`Audience`)의 가방을 직접 열어 초대장 유무를 확인합니다.
* 판매원(`TicketSeller`)을 거쳐 매표소(`TicketOffice`)의 티켓과 돈에 직접 접근합니다.

```kotlin
// Problematic Code
class Theater(private var _ticketSeller: TicketSeller) {
    fun enter(audience: Audience) {
        if (audience.bag.hasInvitation()) {
            val ticket = this._ticketSeller.ticketOffice.getTicket() // 2-depth 참조
            audience.bag.setTicket(ticket) // 외부에서 내부 상태를 직접 조작
        } else {
            val ticket = this._ticketSeller.ticketOffice.getTicket()
            audience.bag.minusAmount(ticket.fee)
            this._ticketSeller.ticketOffice.plusAmount(ticket.fee)
            audience.bag.setTicket(ticket)
        }
    }
}
```
이러한 

2-depth 이상의 참조는 높은 결합도를 만들어, TicketOffice나 Bag의 변경이 Theater에까지 영향을 미치는 문제를 낳습니다. 

#### 2. 1차 개선: 판매원(TicketSeller)에게 책임 위임
Theater가 하던 판매 관련 업무를 실제 책임자인 TicketSeller에게 위임합니다.

```kotlin

// 1st Refactoring
class Theater(private var _ticketSeller: TicketSeller) {
   fun enter(audience: Audience) {
      // 판매원에게 책임을 위임
      this._ticketSeller.sellTo(audience)
   }
}

class TicketSeller(private var _ticketOffice: TicketOffice) {
   fun sellTo(audience: Audience) {
      // 판매원이 자신의 매표소와 관객의 가방을 직접 처리
      if (audience.bag.hasInvitation()) {
         val ticket = this._ticketOffice.getTicket()
         audience.bag.setTicket(ticket)
      } else {
         // ...
      }
   }
}
```
이제 Theater는 TicketSeller가 판매를 수행한다는 사실만 알 뿐, TicketOffice의 존재는 몰라도 됩니다. 

결합도가 낮아지고 TicketSeller의 캡슐화가 강화되었습니다. 

#### 3. 2차 개선: 관객(Audience)이 스스로 구매
아직 TicketSeller는 관객의 가방을 접근합니다. 이 책임 또한 Audience에게 위임하여 각 객체의 자율성을 더욱 높입니다.

```kotlin
// 2nd Refactoring
class Audience(private var _bag: Bag) {
    // 관객 스스로 티켓을 구매하고, 가방을 관리한다.
    fun buy(ticket: Ticket): Long {
        if (this.bag.hasInvitation()) {
            this.bag.setTicket(ticket)
            return 0L // 초대권으로 교환했으므로 지불할 금액은 0원
        } else {
            this.bag.minusAmount(ticket.fee)
            this.bag.setTicket(ticket)
            return ticket.fee // 지불한 금액을 반환
        }
    }
}

class TicketSeller(private var _ticketOffice: TicketOffice) {
   fun sellTo(audience: Audience) {
      // 관객이 티켓을 구매한 후 지불한 금액만큼 매표소의 돈을 증가시킨다.
      this._ticketOffice.plusAmount(audience.buy(this._ticketOffice.getTicket()))
   }
}
```
이제 TicketSeller는 관객의 가방에 대해 알 필요가 없어졌고, 단지 관객에게 티켓을 판매하고 그 대가로 돈을 받습니다.   
모든 객체가 자신의 일만 책임지게 되어 응집도가 높아지고 자율성이 보장되었습니다. 

### 고찰: 자율성을 높이는 것이 항상 정답인가?
자율성을 높이는 리팩토링을 통해, 중앙 집중적이었던 의존성(Theater → All)이 객체 간의 협력 관계(Theater → Seller → Audience)로 분산되었습니다. 

자율성을 높인다고 해서 시스템의 전체 의존성이 반드시 낮아지는 것은 아닙니다. 오히려 객체 간의 의존성이 새로 생겨나기도 합니다.   
하지만 중요한 것은, 이러한 의존과 결합이 객체 간의 상호작용을 더욱 의미 있게 만들고, 각 객체가 자신의 책임에만 집중하게 함으로써 변경에 더 용이한 구조를 만든다는 점입니다.   
결국 좋은 설계란 의존성을 없애는 것이 아니라, 의미 있고 관리 가능한 의존성을 만드는 것입니다.