# ddd 애플리케이션 설계

## 애플리케이션 설계?
---
전략적 설계가 시스템의 큰 그림(바운디드 컨텍스트, 컨텍스트 맵)을 다루고, 전술적 설계가 한 컨텍스트 안에서 도메인을 어떻게 객체로 표현할지(Entity, Value Object, Aggregate)를 다룬다면, 애플리케이션 설계는 이 도메인 모델을 가지고 어떤 구조의 코드를 만들 것인가에 대한 것이다.

같은 `Rental` 애그리거트를 가지고도 컨트롤러가 도메인을 직접 끌어다 쓰는 방식, 트랜잭션 스크립트로 쓰는 방식, 유스케이스를 명시적으로 만드는 방식, 헥사고날로 도메인을 격리하는 방식이 모두 가능하다.

## Application Service
---

Application Service는 하나의 유스케이스(use case)를 표현하는 진입점이다. 트랜잭션을 열고, 애그리거트를 Repository로 꺼내고, 도메인 객체에게 일을 시키고, 결과를 저장·발행하기까지의 흐름을 조립한다. 비즈니스 규칙은 직접 갖지 않고 엔티티/도메인 서비스에 위임한다. 아래 계층형·헥사고날 논의에 등장하는 "UseCase / Application" 레이어가 바로 이것이다.

> Application Service의 책임, Domain Service와의 차이(표), 트랜잭션 경계, 예시 코드는 별도 문서 [ddd 애플리케이션 서비스와 트랜잭션](ddd_애플리케이션서비스와_트랜잭션.md) 에서 깊게 다룬다. 이 문서는 그 위에서 "어떤 아키텍처로 코드를 구성하는가"에 집중한다.

## 계층형 아키텍처
---

### 4계층 구조
가장 전통적인 DDD 아키텍처는 4계층 구조이다. 위에서 아래로 단방향 의존을 가진다.

```
Presentation  (Controller, REST/GraphQL)
     ↓
Application   (UseCase, Transaction)
     ↓
Domain        (Entity, VO, Aggregate, Domain Service)
     ↓
Infrastructure (JPA, External API, Messaging)
```

각 계층은 바로 아래 계층에만 의존하고, 도메인 계층은 어떤 외부에도 의존하지 않는 게 원칙이다. 예를 들어 Controller는 UseCase를 호출하고, UseCase는 Repository 인터페이스를 쓰고, Repository 구현은 Infrastructure에 있다.

### 한계
계층형의 가장 큰 함정은 **Domain → Infrastructure 의존이 역방향으로 새는 것**이다. 도메인이 외부에 의존하지 않으려면 Repository 인터페이스가 도메인 계층에 있어야 하는데, 실제로 작업하다 보면 도메인이 점점 JPA, Jackson, Spring 어노테이션에 묶이게 된다.

또 하나, 4계층은 **수직 의존**만 정의할 뿐 "외부에서 들어오는 길(Inbound)"과 "내가 외부로 나가는 길(Outbound)"을 구분하지 않는다. 그래서 Controller가 들어오는 길인지, 메시지 컨슈머가 들어오는 길인지, 둘 다 똑같이 "Presentation"으로 분류되어 책임이 흐려진다. 이 한계가 헥사고날로 넘어가는 동기가 된다.

## 헥사고날 아키텍처 (Ports & Adapters)
---

### 헥사고날?
헥사고날은 계층을 세로로 쌓는 대신, **도메인을 한가운데에 두고 그 주위를 외부 세계가 둘러싼 형태**로 본다. 도메인은 외부 세계에 대해 아무것도 모르고, 외부와의 모든 통신은 **Port(인터페이스)** 를 통해서만 이뤄진다. Port의 구현체가 **Adapter** 이다.

```
       ┌────── Inbound Adapter (Controller, Kafka Consumer) ──────┐
       │                                                          │
       │              ┌─── Inbound Port ───┐                       │
       │              │     (UseCase)      │                       │
       │              └────────────────────┘                       │
       │                       ↓                                   │
       │              ┌────────────────────┐                       │
       │              │   Domain Model     │                       │
       │              └────────────────────┘                       │
       │                       ↓                                   │
       │              ┌─── Outbound Port ──┐                       │
       │              │   (Repository)     │                       │
       │              └────────────────────┘                       │
       │                                                          │
       └─── Outbound Adapter (JPA, Kafka Producer, External API) ──┘
```

핵심 효과는 **의존성 역전(Dependency Inversion)** 이다. 도메인이 JPA에 의존하지 않고, JPA Adapter가 도메인이 정의한 Repository 인터페이스를 구현하는 형태가 된다. 도메인은 외부 기술 선택과 분리된 채로 살아남는다.

### Port와 Adapter
- **Inbound Port (Driving Port)** — 외부 세계가 시스템을 호출하는 진입점 인터페이스. 보통 UseCase 인터페이스로 표현된다.
- **Inbound Adapter (Driving Adapter)** — Inbound Port를 호출하는 외부 진입 구현체. REST Controller, Kafka Consumer, Scheduler 등.
- **Outbound Port (Driven Port)** — 시스템이 외부에 의존해야 할 때 사용하는 인터페이스. Repository, EventPublisher, ExternalPaymentClient 등.
- **Outbound Adapter (Driven Adapter)** — Outbound Port의 실제 구현체. JpaRentalRepository, KafkaEventPublisher 등.

이 구분 덕분에 같은 UseCase를 REST에서도, Kafka 메시지에서도, 배치에서도 동일하게 호출할 수 있고, 같은 Repository Port를 JPA로도, in-memory로도 구현할 수 있다. 테스트에서는 Adapter만 바꿔 끼우면 된다.

### 헥사고날 예시
"대여 승인" 유스케이스를 헥사고날 구조로 다시 짜본다. UseCase 자체를 인터페이스로 정의하고, 구현은 그대로 두지만 의존하는 Repository와 EventPublisher도 도메인 계층의 인터페이스에 의존하도록 한다.

```kotlin
// domain layer — 어떤 외부 기술에도 의존하지 않는다
package com.example.rental.domain.port.`in`

interface ApproveRentalUseCase {
    fun approve(command: ApproveRentalCommand): RentalResult
}

package com.example.rental.domain.port.out

interface RentalRepository {
    fun findById(id: Long): Rental?
    fun save(rental: Rental): Rental
}

interface DomainEventPublisher {
    fun publishAll(events: List<DomainEvent>)
}
```

```kotlin
// application layer — UseCase 구현, Port에만 의존
package com.example.rental.application

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
        eventPublisher.publishAll(rental.pullEvents())
        return RentalResult.from(saved)
    }
}
```

```kotlin
// inbound adapter — REST 진입점
package com.example.rental.adapter.`in`.web

@RestController
@RequestMapping("/rentals")
class RentalController(
    private val approveRentalUseCase: ApproveRentalUseCase,
) {
    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long, @RequestBody request: ApproveRentalRequest): RentalResponse =
        approveRentalUseCase.approve(ApproveRentalCommand(id, request.approverId))
            .let(RentalResponse::from)
}
```

```kotlin
// outbound adapter — JPA 구현
package com.example.rental.adapter.out.persistence

@Repository
class JpaRentalRepositoryAdapter(
    private val jpaRepository: JpaRentalEntityRepository,
) : RentalRepository {
    override fun findById(id: Long): Rental? = jpaRepository.findById(id).orElse(null)
    override fun save(rental: Rental): Rental = jpaRepository.save(rental)
}
```

세 가지 포인트가 드러난다.
- 도메인 패키지(`domain/port/in`, `domain/port/out`)는 Spring·JPA·Kafka 같은 어떤 외부 라이브러리도 import하지 않는다. 도메인은 자기 자신만으로 컴파일이 된다.
- Controller는 `ApproveRentalUseCase`라는 **인터페이스**에 의존한다. 구현체를 바꿔도 Controller는 영향을 받지 않고, 테스트에서는 가짜 UseCase 구현체를 주입할 수 있다.
- JPA를 MongoDB로 갈아끼우거나 동기 호출을 Kafka 비동기로 바꾸는 일이 생겨도, 바뀌는 건 Adapter 한 클래스이다. 도메인과 UseCase는 그대로 살아남는다.

## 트랜잭션 경계와 Aggregate
---

"한 트랜잭션 = 한 Aggregate" 원칙, 여러 Aggregate를 다룰 때의 결과적 일관성 처리, Domain Event + 별도 트랜잭션 패턴, MSA로 확장될 때의 Saga는 별도 문서 [ddd 애플리케이션 서비스와 트랜잭션](ddd_애플리케이션서비스와_트랜잭션.md) 에서 전부 다룬다. 이 문서는 중복을 피하고 그 문서를 참조한다.

## CQRS
---

### CQRS?
CQRS(Command Query Responsibility Segregation)는 **상태를 바꾸는 책임(Command)** 과 **상태를 조회하는 책임(Query)** 을 분리하자는 패턴이다.

DDD 관점에서는 같은 Aggregate가 두 가지 상충되는 요구를 동시에 받는 데서 출발한다. Command 쪽은 도메인 규칙 검증, 불변식 보호, 풍부한 행위가 필요해서 Rich Domain Model이 어울리고, Query 쪽은 화면이 요구하는 다양한 형태의 데이터를 빠르게 조회하는 게 핵심이라 도메인 모델을 그대로 쓰면 오히려 거추장스러워진다. 한 모델로 두 책임을 다 떠안으려 하면 Aggregate가 화면 요구에 끌려가서 변형되거나, 조회 성능을 위해 도메인 모델이 비정상적으로 비대해진다.

### 언제 적용하는가
모든 도메인에 CQRS가 필요하지는 않다. 도입을 고려할 시점은 다음과 같다.

- 같은 Aggregate를 두고 화면별로 요구하는 데이터 형태가 너무 달라, 도메인 모델로 모든 조회를 커버하기 무리일 때
- 조회 트래픽이 쓰기 트래픽보다 압도적으로 많아 별도 최적화가 필요할 때
- 도메인 규칙이 충분히 복잡해서 Command 쪽 모델을 깨끗하게 유지하는 것이 우선순위일 때

반대로 단순 CRUD 위주이거나 조회 형태가 도메인 모델과 거의 일치한다면 CQRS는 과도한 복잡성을 부른다.

### CQRS 예시
Command 쪽은 기존 Application Service / Aggregate 흐름을 그대로 유지하고, Query 쪽만 별도 클래스에서 도메인 모델을 거치지 않고 화면 전용 DTO를 직접 조회하도록 분리한다.

```kotlin
// command side — 도메인 모델을 거친다
@Service
class ApproveRentalService(
    private val rentalRepository: RentalRepository,
) : ApproveRentalUseCase {
    @Transactional
    override fun approve(command: ApproveRentalCommand): RentalResult {
        val rental = rentalRepository.findById(command.rentalId) ?: throw RentalNotFoundException(command.rentalId)
        rental.approve()
        return RentalResult.from(rentalRepository.save(rental))
    }
}
```

```kotlin
// query side — 도메인 모델을 거치지 않고 DTO로 바로 매핑
data class RentalListItem(
    val rentalId: Long,
    val renterName: String,
    val productName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: String,
)

@Repository
class RentalQueryRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) {
    fun findRentalListByRenter(renterId: Long): List<RentalListItem> =
        jdbcTemplate.query(
            """
            SELECT r.id AS rental_id, u.name AS renter_name, p.name AS product_name,
                   r.start_date, r.end_date, r.status
              FROM rental r
              JOIN user u ON u.id = r.renter_id
              JOIN product p ON p.id = r.product_id
             WHERE r.renter_id = :renterId
             ORDER BY r.start_date DESC
            """.trimIndent(),
            mapOf("renterId" to renterId),
        ) { rs, _ ->
            RentalListItem(
                rentalId = rs.getLong("rental_id"),
                renterName = rs.getString("renter_name"),
                productName = rs.getString("product_name"),
                startDate = rs.getDate("start_date").toLocalDate(),
                endDate = rs.getDate("end_date").toLocalDate(),
                status = rs.getString("status"),
            )
        }
}
```

세 가지 포인트가 드러난다.
- Command 쪽은 `Rental` 애그리거트를 거쳐 도메인 규칙을 통과한다. 절대 Query 흐름이 Command 모델을 우회해서 상태를 바꿀 수 없도록 한다.
- Query 쪽은 도메인 모델을 일부러 거치지 않고 화면이 요구하는 모양 그대로 DTO를 만든다. `Rental`, `User`, `Product` 세 애그리거트를 가로지르는 조회도 부담 없이 한 번에 처리할 수 있다.
- 둘은 같은 DB를 봐도 되고(같은 스키마/CQRS Lite), 정말 트래픽이 크면 Query 전용 Read Model을 별도 DB에 쌓고 도메인 이벤트로 동기화해도 된다(Full CQRS). 시작은 같은 DB로 충분하다.

CQRS의 극단까지 가면 Read Model을 별도 저장소에 비동기로 동기화하는 형태(Event Sourcing과 결합)가 되지만, 대부분의 프로젝트는 "같은 DB를 두고 Command/Query 클래스만 분리"하는 가벼운 CQRS만으로도 충분한 효과를 얻는다. 도입 비용 대비 가장 ROI가 높은 지점이다.
