# ddd 애플리케이션 서비스와 트랜잭션

## ddd에서의 트랜잭션
---
DDD를 코드로 옮길 때 가장 자주 혼동되는 지점이 "트랜잭션을 누가 책임지는가"이다. "Aggregate가 트랜잭션의 경계이다"라는 명제와 "Application Service에 `@Transactional`을 붙인다"라는 관례가 처음에는 충돌해 보이고, MSA로 넘어가면 그 등식 자체가 깨진다.

이 문서는 세 가지를 분리해서 본다.

- 트랜잭션을 **여는 위치** — Application Service
- 한 트랜잭션이 **수정하는 단위** — Aggregate 하나
- 트랜잭션이 **다루는 범위** — 한 Bounded Context 안

## 세 레이어의 책임 분리
---

### 책임 표
|레이어|책임|단위|트랜잭션|
|---|---|---|---|
|**Application Service**|유즈케이스 흐름 조립|유즈케이스|연다 (`@Transactional`)|
|**Domain Service**|여러 Aggregate에 걸친 도메인 규칙|규칙|모른다|
|**Aggregate**|자기 자신의 일관성 보호|일관성 경계|모른다|

이 셋이 자기 책임만 들고 있어야 도메인이 인프라에서 독립적으로 살아남고, 같은 도메인 규칙이 여러 유즈케이스에서 재사용된다.

### Application Service?
Application Service는 하나의 유즈케이스를 표현하는 진입점이다. 사용자가 "대여를 승인한다"라는 행위를 시스템에 요청했을 때, 트랜잭션을 열고, 필요한 애그리거트를 Repository로 꺼내고, 도메인 객체에게 일을 시키고, 결과를 저장하고, 이벤트를 발행하기까지의 흐름 자체를 조립하는 책임을 가진다.

핵심은 Application Service가 비즈니스 규칙을 직접 갖지 않는다는 점이다. "요청 상태에서만 승인할 수 있다", "자신의 상품은 대여할 수 없다" 같은 규칙은 모두 엔티티/도메인 서비스가 들고 있고, Application Service는 그저 그것을 적절한 순서로 호출할 뿐이다.

### Application Service 예시
"대여를 승인한다"라는 유즈케이스는 도메인 규칙 검증, 영속화, 도메인 이벤트 발행이 한 트랜잭션 안에서 일어나야 한다.

```kotlin
@Service
class ApproveRentalService(
    private val rentalRepository: RentalRepository,
    private val rentalReservationPolicy: RentalReservationPolicy,
    private val eventPublisher: DomainEventPublisher,
) : ApproveRentalUseCase {

    @Transactional
    override fun approve(command: ApproveRentalCommand): RentalResult {
        val rental = rentalRepository.findById(command.rentalId)
            ?: throw RentalNotFoundException(command.rentalId)

        rentalReservationPolicy.ensureApprovable(rental)  // 도메인 서비스: 규칙
        rental.approve()                                  // Aggregate: 자기 행위

        val saved = rentalRepository.save(rental)
        eventPublisher.publishAll(rental.pullEvents())
        return RentalResult.from(saved)
    }
}
```

- 비즈니스 규칙은 `Rental.approve()`와 `rentalReservationPolicy.ensureApprovable()` 안에 캡슐화되어 있고, Application Service는 "꺼내서 → 규칙 검증 → 승인 → 저장 → 이벤트 발행" 흐름만 조립한다.
- `@Transactional`은 Application Service에 붙는다. 도메인 객체와 도메인 서비스는 트랜잭션을 모른다. 트랜잭션 경계는 곧 유즈케이스 경계이다.
- 입력은 `ApproveRentalCommand`로 받고 출력은 `RentalResult`로 돌려준다. 도메인 엔티티(`Rental`)가 Controller나 외부로 그대로 새어 나가지 않게 막는 장치이다.

## "Aggregate가 트랜잭션 경계"의 정확한 의미
---

### 자주 하는 오해
"Aggregate가 트랜잭션의 경계"라는 말을 들으면 "Aggregate가 트랜잭션을 연다"로 이해할 수 있다. 그래서 자연스럽게 "그럼 Aggregate를 다루는 단위(Domain Service)에 `@Transactional`을 붙여야 하는 거 아닌가?"라는 의문이 따라온다.

이 명제는 "Aggregate가 트랜잭션을 연다"가 아니라 "한 트랜잭션이 수정해야 하는 Aggregate는 하나여야 한다" 라는 일관성 경계 원칙이다. Aggregate는 "변경 단위(consistency boundary)"이지 "트랜잭션을 여는 주체"가 아니다.

Application Service에 `@Transactional`을 붙인다고 해서 "그 안에서 여러 Aggregate를 수정해도 된다"는 뜻은 아니다.

### Application Service의 트랜잭션 안에 들어가는 것들
원칙대로면 한 트랜잭션 안에서는 한 Aggregate만 변경한다. 그런데도 `@Transactional`이 필요한 이유는:

- `find + save`가 하나의 단위로 묶여야 하기 때문 (중간 실패 시 같이 롤백)
- 조회 후 변경하는 동안 다른 트랜잭션이 끼어들면 안 되는 일관성 보호 (낙관적/비관적 락이 같은 트랜잭션 안에서 의미를 가진다)
- Outbox Pattern을 쓴다면 "Aggregate 저장 + 이벤트 row 저장"이 같은 트랜잭션이어야 한다

즉 트랜잭션은 "여러 Aggregate를 묶기 위해" 있는 게 아니라, "한 Aggregate를 변경하는 한 번의 행위를 원자적으로 만들기 위해" 있다.

## 한 트랜잭션 = 한 Aggregate 원칙
---

### 기술적 제약이 아니라 설계 규율이다
이 원칙은 처음 들으면 직관과 충돌한다. "트랜잭션은 원자성을 보장하기 위한 명령어들의 집합이고, 한 유즈케이스가 N개의 Aggregate를 다루면 그것들이 다 한 트랜잭션에 묶이는 게 자연스럽지 않은가?" 라는 의문이 자연스럽다.

|관점|트랜잭션의 정의|
|---|---|
|**DBMS 관점**|원자적으로 처리되어야 할 SQL 명령어들의 집합 (ACID)|
|**DDD 관점**|하나의 Aggregate의 일관성을 보호하는 단위|

DBMS 관점에서는 사실 N Aggregate를 한 트랜잭션에 묶는 게 전혀 문제없다. JPA·RDBMS는 한 트랜잭션 안에서 100개의 다른 Aggregate를 수정해도 막지 않는다. 기술적으로 가능하고, 모놀리스의 흔한 그림이기도 하다.

DDD가 "한 트랜잭션 = 한 Aggregate"라고 말할 때, 그것은 RDBMS의 기술적 제약이 아니라 개발자가 의도적으로 지키는 설계 규율이다.

### 그럼 유즈케이스는 한 트랜잭션이 아닌가
모놀리스의 관습은 "1 유즈케이스 = 1 트랜잭션 = N Aggregate 변경"이다. DDD는 이걸 다음과 같이 바꾼다.

```
1 유즈케이스 = N개의 트랜잭션 (각각 1 Aggregate만 변경)
            ↓
    Domain Event로 이어진다
```

즉 DDD는 "트랜잭션 수 = Aggregate 변경 수"로 보고, "유즈케이스"는 그 트랜잭션들을 묶는 더 큰 흐름으로 본다. 유즈케이스 수준의 일관성은 트랜잭션이 아니라 이벤트 + 보상으로 보장한다.

|기준|모놀리스 관습 (한 TX, N Aggregate)|DDD 권고 (N TX, 각 1 Aggregate)|
|---|---|---|
|원자성|강함 (실패 시 자동 롤백)|약함 (각자 롤백, 보상 필요)|
|구현 복잡도|단순|복잡 (이벤트, 보상, 멱등성)|
|확장성|모놀리스에 갇힘|MSA로 떼어내기 쉬움|
|동시성|락 충돌 늘어남|락이 작게 나뉨|
|일관성 경계|코드가 결정 (트랜잭션이 묶는 대로)|설계가 결정 (Aggregate가 묶는 대로)|

### Vernon의 4원칙 중 가장 자주 깨지는 것
Vaughn Vernon이 강조하는 Aggregate 설계 4원칙은 다음과 같다.
1. 작은 Aggregate를 설계하라
2. ID로 다른 Aggregate를 참조하라
3. **한 트랜잭션 안에서는 하나의 Aggregate만 수정하라**
4. 트랜잭션 밖의 변경은 결과적 일관성을 사용하라

이 중 3번이 가장 자주 깨진다. JPA는 한 트랜잭션에서 100개의 다른 Aggregate를 수정해도 막지 않기 때문이다. 막는 것은 프레임워크가 아니라 **설계 규율**이다.

### 원칙을 깨면 무엇이 망가지는가
두 Aggregate를 한 트랜잭션에서 같이 바꾼다는 것은, 사실상 그 둘이 한 일관성 경계 안에 있다는 뜻이다. 그렇다면 처음부터 하나의 Aggregate로 묶었어야 했다는 뜻이기도 하다. 경계를 나눠놓고 트랜잭션으로 다시 묶는 것은 가장 흔한 안티패턴이다.

원칙이 깨지면:
- Aggregate 경계의 의미가 사라진다 (왜 나눴는지 알 수 없게 된다)
- 락 충돌이 증가한다 (한 트랜잭션이 두 Aggregate의 락을 잡는다)
- 트랜잭션이 커지면서 데드락 가능성이 늘어난다
- MSA로 전환할 때 가장 먼저 깨지는 부분이 된다 (어차피 분리 못 함)

## 여러 Aggregate를 다뤄야 한다면 (모놀리스)
---

### 두 가지 신호로 판단
Application Service에서 두 개 이상의 Aggregate를 손대고 싶어지는 순간이 반드시 온다. 그때 던질 질문은 두 가지이다.

**1. 강한 일관성이 정말 필요한가?**
사용자가 같은 순간에 두 사실을 모두 봐야 한다면, 두 Aggregate가 사실은 한 Aggregate일 가능성이 높다. 다시 경계를 그어볼 수 있다.

**2. 결과적 일관성(Eventual Consistency)을 허용하는가?**
한 트랜잭션에서는 한 Aggregate만 수정하고, 다른 Aggregate는 **도메인 이벤트**로 알려서 별도 트랜잭션에서 처리한다.

### Domain Event + 별도 트랜잭션 예시
대여가 승인되면 상품 통계가 갱신되어야 한다. `Rental`과 `ProductStatistics`는 서로 다른 Aggregate이므로 같은 트랜잭션에서 같이 수정하지 않는다.

```kotlin
@Service
class ApproveRentalService(
    private val rentalRepository: RentalRepository,
    private val eventPublisher: DomainEventPublisher,
) : ApproveRentalUseCase {

    @Transactional
    override fun approve(command: ApproveRentalCommand): RentalResult {
        val rental = rentalRepository.findById(command.rentalId)
            ?: throw RentalNotFoundException(command.rentalId)

        rental.approve()
        val saved = rentalRepository.save(rental)

        // ProductStatistics는 여기서 직접 건드리지 않는다.
        eventPublisher.publishAll(rental.pullEvents())  // RentalApproved
        return RentalResult.from(saved)
    }
}

@Component
class ProductStatisticsHandler(
    private val productStatisticsRepository: ProductStatisticsRepository,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: RentalApproved) {
        val stats = productStatisticsRepository.findByProductId(event.productId)
            ?: ProductStatistics.empty(event.productId)
        stats.incrementApprovedCount()
        productStatisticsRepository.save(stats)
    }
}
```

`Rental`과 `ProductStatistics`는 각자의 트랜잭션 안에서만 변경된다. 한쪽이 실패해도 다른 쪽이 망가지지 않고, 실패한 쪽은 재시도하거나 보상 트랜잭션으로 복구할 수 있다. 다만 트레이드오프는 분명히 존재한다 — "승인은 됐는데 통계는 아직 반영 안 됨"이라는 짧은 시간이 있고, 이걸 받아들일 수 있는 도메인에만 적용해야 한다.

## Domain Service에 `@Transactional`을 붙이지 않는 이유
---

### 자연스러운 의문
"Aggregate가 트랜잭션 경계 → 그 Aggregate를 다루는 단위에 트랜잭션을 붙여야 한다"는 추론은 자연스럽다. 그리고 Domain Service가 그 단위처럼 보인다. 하지만 DDD에선 트랜잭션 책임은 Application Service에 둔다이다.

### 이유 1 — 트랜잭션 전파 통제권을 호출자에게 둬야 한다
같은 `RentalReservationPolicy.ensureApprovable()`이 다음 두 유즈케이스 모두에서 호출될 수 있다.

- 사용자가 직접 대여를 요청하는 유즈케이스 (단건 트랜잭션)
- 큐레이터가 대여 묶음을 일괄 등록하는 배치 유즈케이스 (10건을 한 트랜잭션에 묶고 싶음)

"100건을 한 트랜잭션으로 묶을지, 100개 트랜잭션으로 나눌지, 50개씩 묶을지"는 유즈케이스 레벨의 결정이지 Domain Service의 결정이 아니다. Domain Service에 `@Transactional`이 박혀 있으면 호출 컨텍스트마다 동작이 달라진다 — `REQUIRED`로는 어떻게든 합쳐지지만, 누군가 `REQUIRES_NEW`나 다른 격리 수준을 지정하는 순간 호출자의 의도가 가로채진다.

즉 트랜잭션 어노테이션은 "이 메소드는 자기 트랜잭션을 가진다"는 선언이고, 그 선언을 Domain Service가 하면 호출자의 선택을 좁힌다.

### 이유 2 — 트랜잭션 경계 = 유즈케이스 경계
사용자 관점에서 "한 번의 요청 = 한 번의 결과(전부 성공 or 전부 실패)"가 보장되어야 하는 단위가 트랜잭션이다. 그 단위는 Aggregate가 아니라 유즈케이스가 정한다. Domain Service는 유즈케이스보다 작은 조각이라 트랜잭션을 책임지기에 단위가 안 맞는다. 유즈케이스 위치에서 "이 흐름은 한 단위로 묶여야 한다"고 선언해야 의미가 일관된다.

### 보조 근거 — 인프라 의존성
`@Transactional`은 Spring 어노테이션이라 Domain Service가 이를 가지면 도메인 계층이 Spring에 묶이는 것은 맞다. 다만 이건 본질적인 이유라기보다 부수적 효과다. JPA Entity를 도메인 엔티티로 쓰는 선택지처럼, "어노테이션이 박힌다"는 사실 자체만으로는 항상 결정타가 되지 않는다. 결정타는 위의 세 가지 실용적 비용이다.

### Domain Service는 트랜잭션을 모른다
```kotlin
// Domain Service — Spring 어노테이션 없음, 순수 도메인 규칙
class RentalReservationPolicy(
    private val rentalRepository: RentalRepository,
) {
    fun ensureApprovable(rental: Rental) {
        if (rentalRepository.existsOverlapping(rental.productId, rental.period)) {
            throw RentalPeriodConflictException(rental.productId, rental.period)
        }
    }
}
```

`@Service`도, `@Transactional`도 없다. Domain Service는 "이 대여를 승인해도 되는가?"라는 **규칙**만 답하고, 트랜잭션도 영속화도 모른다. Application Service가 그것을 받아 `rental.approve()` 호출과 저장을 한 트랜잭션으로 묶는다.

## MSA 확장 — 유즈케이스 트랜잭션이 깨지는 순간
---

### 모놀리스에서만 성립하는 모델
"Application Service에 `@Transactional`을 붙이고, 그 안에서 한 Aggregate만 수정한다"는 모델은 모놀리스의 그림이다. MSA로 넘어가는 순간 이 등식이 깨진다.

핵심 변화: 각 마이크로서비스가 자기 DB와 자기 로컬 트랜잭션을 가지기 때문에, 여러 서비스를 가로지르는 유즈케이스는 하나의 트랜잭션으로 묶이지 못한다.

|층위|의미|
|---|---|
|**도메인 서비스 (MSA)**|하나의 Bounded Context를 배포한 마이크로서비스. 자기 DB·자기 트랜잭션을 가짐.|
|**Domain Service (DDD 전술)**|마이크로서비스 내부에 존재하는 객체. 여러 Aggregate에 걸친 규칙을 담음.|

### 1. 마이크로 서비스의 트랜잭션

```
Controller → Application Service (@Transactional) → Domain Service → Aggregate → Repository
                                     ↑
                          서비스 내부 한 트랜잭션 = 한 Aggregate
```

`Rental Service` 안의 "대여 승인" 유즈케이스는 그 서비스 내부에서 보면 여전히 Application Service에 `@Transactional`을 붙이고 Rental Aggregate 하나만 다룬다. 이 층위에서는 모놀리스 때와 똑같다.

### 2. 유즈케이스에서 마이크로서비스 호출

여러 마이크로서비스에 걸치는 "End-to-end 유즈케이스"는 분산 트랜잭션이 아니라 Saga로 풀어야 한다.

```
[Rental Service]       [Payment Service]     [Notification Service]
  approve()              charge()              send()
  TX#1 (local)           TX#2 (local)          TX#3 (local)
     ↓ event                ↓ event               ↓
  RentalApproved   →    PaymentCompleted  →    NotificationSent
```

각 서비스는 자기 트랜잭션만 다루고, 서비스 사이의 일관성은 트랜잭션이 아니라 이벤트 흐름으로 보장한다. "유즈케이스 단위 트랜잭션"은 존재하지 않고, 대신 유즈케이스 단위 Saga가 존재한다.

> **분산 트랜잭션이란** 
>
> 분산 트랜잭션은 둘 이상의 독립된 자원(서로 다른 DB, MQ, 외부 시스템)에 걸친 작업을 하나의 원자적 단위로 묶는 것이다. 예: "MySQL 결제 테이블 변경 + Kafka 결제완료 이벤트 발행"을 둘 다 성공하거나 둘 다 실패하게 만든다. 단일 DB 트랜잭션의 ACID를 여러 시스템에 걸쳐 보장하려는 시도이다.
>
> 1. **Prepare** — 코디네이터가 모든 참여자에게 "준비됐니?" 묻고, 참여자는 자기 변경을 락으로 잡아 둔 채 OK라고 답한다.
> 2. **Commit** — 모두 OK면 코디네이터가 "그럼 실행해라" 명령을 내리고, 참여자들이 동시에 commit한다. 하나라도 NO면 모두 rollback.
>
> ```
> 코디네이터 ─prepare?→ A: OK (락 잡음)
>             ─prepare?→ B: OK (락 잡음)
>             ─commit!→ A, B 동시 commit, 락 해제
> ```
>
> **MSA에서 잘 사용하지 않는 이유**
>
> - 블로킹 프로토콜: prepare 후 한 참여자가 응답이 늦거나 죽으면 다른 참여자들은 락을 잡은 채로 무한 대기한다. 코디네이터가 죽으면 참여자들이 "in-doubt" 상태(prepare는 했는데 commit/rollback 명령이 안 옴)에 빠져 자기 판단으로 풀 수 없게 된다 — 잘못 풀면 데이터가 어긋나기 때문이다. CAP 관점에서 강한 일관성(C)을 위해 가용성(A)을 명시적으로 희생하는 방식이다.
> - 성능 저하 — 네트워크 왕복이 두 번씩 곱해지고 그 사이 락이 잡힌다. MSA의 수평 확장 이점이 무력화된다.
> - MSA 자율성 훼손 — 모든 참여 서비스가 같은 트랜잭션 매니저를 공유해야 한다. 한 서비스의 prepare 실패가 다른 서비스를 막고, 독립 배포·스케일이 어려워진다. 모놀리스의 단점만 가져오고 MSA의 이점이 사라진다.
> - 코디네이터 SPOF — 코디네이터가 죽으면 전체가 멈춘다. Paxos/Raft로 복제할 수는 있지만 운영 복잡도가 크게 오른다.
>

### 트랜잭션 자리를 차지하는 것들

|모놀리스|MSA|
|---|---|
|`@Transactional` (ACID)|Saga (Eventual Consistency)|
|롤백 (자동)|보상 트랜잭션 (Compensating Transaction) — 이미 커밋된 일을 되돌리는 명시적 트랜잭션|
|단일 DB 일관성|Outbox Pattern — 자기 DB 커밋과 이벤트 발행을 같은 트랜잭션으로 묶음|
|메시지 한 번 처리|멱등성 (Idempotency Key) — 같은 이벤트를 여러 번 받아도 같은 결과|
|동기 호출 + 트랜잭션|비동기 이벤트 (Choreography) 또는 Saga Coordinator (Orchestration)|

### Saga란
Saga는 여러 로컬 트랜잭션을 이어 붙여 하나의 비즈니스 트랜잭션을 만드는 패턴이다.

- 한 비즈니스 흐름을 N개의 작은 로컬 트랜잭션 T1, T2, ... Tn으로 쪼갠다.
- 각 Ti는 자기 서비스·자기 DB 안에서만 원자적으로 처리된다.
- 중간에 Ti가 실패하면 이미 커밋된 T1 ~ T(i-1)을 보상 트랜잭션 C1 ~ C(i-1) 로 되돌린다.
- 전체적으로는 ACID가 아니라 ACD(Atomicity가 빠진 결과적 일관성) 만 보장된다.

```
정상 흐름:    T1  →  T2  →  T3  →  T4   (모두 성공, commit)
실패 흐름:    T1  →  T2  →  T3  ✗
                              ↓
              C1  ←  C2  ←  C3            (역순으로 보상)
```

분산 트랜잭션(2PC)이 "모두가 prepare 후 동시에 commit"하는 락 기반 강일관성 방식이라면, Saga는 각자 즉시 commit하고 잘못되면 보상하는 비동기·non-blocking 방식이다. 그래서 가용성과 성능을 얻는 대신, 보상 로직을 도메인이 직접 책임진다.

1. 보상 트랜잭션이 정의 가능해야 한다 — `approve()`에 대응하는 `revertApproval()`이 도메인적으로 의미 있게 존재해야 한다. 어떤 작업은 되돌릴 수 없으므로(이메일 발송, 외부 결제 게이트웨이 호출 등) 되돌릴 수 없는 단계를 가장 마지막에 두는 식의 순서 설계가 중요하다.
2. 각 단계가 멱등(Idempotent)해야 한다 — 메시지 재전송·재시도가 일어나도 같은 결과여야 한다. Idempotency Key를 함께 들고 다니는 것이 일반적이다.
3. 중간 상태가 외부로 노출되는 것을 허용해야 한다 — 정상 흐름 도중에 "결제는 됐지만 알림은 아직 안 감"이 잠시 보이는 것을 받아들일 수 있는 도메인이어야 한다.

이 셋을 갖추면 Saga는 분산 환경에서 트랜잭션 없이도 비즈니스 일관성을 다룰 수 있다. 그렇지 않으면 다시 분산 트랜잭션을 고려하거나, 경계 자체를 다시 그어야 한다.

### Saga 두 가지 스타일

**Orchestration** — 별도의 Saga Coordinator가 흐름을 명시적으로 지휘한다.

```kotlin
@Service
class ApproveRentalSaga(
    private val rentalClient: RentalClient,
    private val paymentClient: PaymentClient,
) {
    fun execute(command: ApproveRentalCommand) {
        val rental = rentalClient.approve(command.rentalId)        // TX#1 in Rental Service
        try {
            paymentClient.charge(rental.id, rental.amount)         // TX#2 in Payment Service
        } catch (e: Exception) {
            rentalClient.revertApproval(rental.id)                 // 보상 TX in Rental Service
            throw SagaFailedException(e)
        }
    }
}
```

**Choreography** — 코디네이터 없이 도메인 이벤트로 서비스끼리 흐름을 이어간다.

```
Rental Service: approve() → RentalApproved event
   ↓ (Kafka)
Payment Service: on(RentalApproved) → charge() → PaymentCompleted (or PaymentFailed)
   ↓ (Kafka)
Rental Service: on(PaymentFailed) → revertApproval()  ← 보상
```

세 가지 트레이드오프가 갈린다.
- Orchestration은 흐름 추적이 쉽지만 Coordinator가 모든 서비스를 알고 있어야 한다 (강한 결합).
- Choreography는 서비스 간 결합도가 낮지만 전체 흐름이 코드 어디에도 모이지 않아 추적이 어렵다.
- 단계가 적으면 Choreography, 단계가 많고 분기가 복잡하면 Orchestration이 유리하다.

### 결론
> "도메인 서버는 애당초 로컬 트랜잭션이므로 유즈케이스 단위의 트랜잭션은 안 맞지 않은가?"

1. 서비스 내부 유즈케이스 — 여전히 Application Service + `@Transactional` (모놀리스 패턴 그대로). 자기 Bounded Context 안의 Aggregate만 다룸.
2. 서비스 간 유즈케이스 — 트랜잭션이 아니라 Saga. "End-to-end 일관성"은 보상 트랜잭션 + Outbox + 멱등성으로 만든다.