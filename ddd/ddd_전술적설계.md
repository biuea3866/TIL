# ddd 전술적 설계

## 전술적 설계?
---
전략적 설계가 시스템의 큰 그림을 다룬다면, 전술적 설계는 각 바운디드 컨텍스트 내 구현에 대한 이야기이다. 

## Entity
---

### Entity?
엔티티는 고유 식별자를 가지며, 시간이 지나도 객체의 동일성이 유지되는 도메인 객체이다. 여기서의 동일성은 내부 속성이 동일한 객체가 여럿일지라도, 이들을 구분하는 건 식별자임을 의미한다. 

이들은 생명주기, 상태의 변경, 비즈니스 로직을 다루기 때문에 내부 속성이 가변성을 가진다. 시간이 흐름에 따라 상태가 변경되고, 비즈니스 로직에 의해 내부 속성이 변경되기 때문이다. 

각 엔티티는 하나의 개념을 표현해야하고, setter등과 같은 것으로 상태를 변경시키기보단 엔티티의 행위를 통해 상태를 변경시켜야 한다. 그리고 잘못된 속성을 갖지 않도록 팩토리 메소드, 생성자 수준에서 유의미한 검증이 이루어져야 한다.

### Entity의 식별성
만약 엔티티의 식별성을 database와 연관지어 pk를 그들의 식별자라고 할 경우 어느정도 통용이 될 수 있으며, 간단하게 생각할 수 있다. 다만, database에 insert되기 전 이들의 pk는 0일 것이고, 만약 pk가 0인 N개의 엔티티가 아직 insert가 되기 전이라면 이들은 고유한 식별자를 가지지 않는 상태이다. 물론 바로 insert를 할 것이고, 로직에 의해 식별자가 보장이 될 순 있겠으나 빡빡하게 식별자를 보장하고 싶다면, UUID같은 것으로 논리적인 식별자를 가지고 가는 것도 좋다.

### Jpa Entity는 도메인 엔티티로 사용할 수 없나
jpa entity는 spring에서 제공하는 DB 영속화를 위한 ORM이다. DB 테이블에 매핑이 되어 종속되며, 헥사고날의 도메인은 인프라 의존성이 없기 때문에 순수한 도메인 모델 관점에서 위배된다. 때문에 jpa entity를 도메인 엔티티로 사용허하지 않고 분리하자는 이야기도 많다. 

하지만 분리하게 되었을 때 jpa에서 제공하는 여러 기술의 이점을 활용할 수 없다는 점(OneToMany Lazy 조회), 이를 대체하기 위한 CQRS와 같은 조회 모델(기술적인 복잡성과 유지보수의 어려움), 도메인 엔티티로 변환시키기 위한 매핑 로직(매핑 필드 누락) 등에 대한 문제점이 더 큰 것 같아 너무 복잡한 도메인 엔티티가 아니라면 jpa entity를 사용하는 것도 적합해 보인다.

### Entity 예시
대여(Rental)는 식별자 `id`로 구분되고, 시간이 흐름에 따라 `REQUESTED → APPROVED → PAID → IN_USE → RETURNED`로 상태가 변해가는 전형적인 엔티티이다. 같은 사용자/같은 상품/같은 기간으로 대여 두 건이 존재할 수 있고, 그 둘은 분명히 별개의 사건이므로 동등성은 속성이 아닌 식별자로 결정된다.

```kotlin
@Entity
@Table(name = "rental")
class Rental private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "renter_id", nullable = false)
    val renterId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Embedded
    val period: RentalPeriod,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: RentalStatus,
) {
    fun approve() {
        require(status == RentalStatus.REQUESTED) { "요청 상태에서만 승인할 수 있습니다" }
        status = RentalStatus.APPROVED
    }

    fun cancel() {
        require(status != RentalStatus.RETURNED) { "이미 반납된 대여는 취소할 수 없습니다" }
        status = RentalStatus.CANCELLED
    }

    companion object {
        fun request(renterId: Long, productId: Long, period: RentalPeriod): Rental =
            Rental(renterId = renterId, productId = productId, period = period, status = RentalStatus.REQUESTED)
    }
}
```

세 가지 포인트를 의도적으로 드러냈다.
- `status` 같은 가변 상태가 있지만, 외부에서 `rental.status = ...`로 직접 바꾸지 못하도록 행위 메소드(`approve()`, `cancel()`)로만 노출한다.
- 모든 행위 안에 “지금 이 상태에서 이게 가능한가?”라는 불변식 검증을 둔다. 상태 머신이 엔티티 자신에게 캡슐화된다.
- 생성자를 `private`으로 막고 `request()` 팩토리 메소드로만 만들도록 해, 잘못된 초기 상태(예: 처음부터 `RETURNED`인 대여)가 만들어지는 것을 원천 차단한다.

## Value Object
---

### Value Object?
값 객체란 식별자가 없고, 속성 값으로 동등성을 판단하는 불변 객체이다. 엔티티와 달리 이들은 식별자가 없기 때문에 N개의 객체가 동일한 값을 가진다면, 이들은 동일한 객체이다. 

그리고 불변 객체이기 때문에 내부 속성들은 가변적이지 않다. 즉, 엔티티와 달리 상태의 변화가 생긴다면, 새로운 값 객체를 만들어 교체를 함으로써 불변을 보장한다. 

값 객체는 도메인적인 개념을 표현하기 때문에 개념을 표현한 타입이 될 수 있고, 일반적인 원시 타입 나열보다 풍부학 맥락과 비즈니스를 제공할 수 있다.

### 엔티티와의 차이
||Entity|Value Object|
|---|----|----|
|동등성|식별자(ID) 기반|속성 값 기반|
|가변성|O|X|
|생명주기|있음|없음 (교체)|
|사이드 이펙트|가능|없음|

엔티티와 값 객체는 서로 상이한 점이 존재하나 도메인을 보다 풍부하게 표현할 수 있고, 비즈니스 로직을 캡슐화시키고, 행위로 이를 표현할 수 있다는 점에서 공통점이 존재한다.


### Value Object 예시
대여 기간(`RentalPeriod`)은 시작일과 종료일이 같으면 같은 기간으로 봐야 하는 값 객체이다. 식별자가 없고, 두 인스턴스의 동등성은 오직 속성 값으로만 결정된다. 또 “기간은 시작일이 종료일보다 빠를 수 없다”라는 도메인 규칙을 그 자체로 들고 있어야, 원시 타입 두 개를 그대로 끌고 다닐 때보다 의도가 훨씬 풍부하게 드러난다.

```kotlin
@Embeddable
data class RentalPeriod(
    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    val endDate: LocalDate,
) {
    init {
        require(!startDate.isAfter(endDate)) {
            "대여 시작일은 종료일보다 늦을 수 없습니다 (start=$startDate, end=$endDate)"
        }
    }

    fun days(): Long = ChronoUnit.DAYS.between(startDate, endDate)

    fun overlaps(other: RentalPeriod): Boolean =
        !endDate.isBefore(other.startDate) && !startDate.isAfter(other.endDate)
}
```

세 가지 포인트가 드러난다.
- `data class` + 모든 필드 `val` — 동등성은 자동으로 속성 기반이 되고, 한 번 만든 객체는 절대 안에서 바뀌지 않는다.
- 잘못된 값(시작일 > 종료일)은 `init` 블록에서 거부한다. “유효하지 않은 RentalPeriod”라는 상태가 메모리 어디에도 존재하지 못한다.
- `days()`, `overlaps()`처럼 기간 자체에 자연스럽게 붙는 행위를 같이 둔다. 호출부에서 매번 `ChronoUnit.DAYS.between(...)`를 다시 쓰는 일이 사라지고, 도메인 어휘가 코드에 그대로 남는다.

기간을 바꾸고 싶다면 기존 객체를 수정하는 대신 새로운 `RentalPeriod`로 교체한다. 즉 `rental`이 새 기간을 받게 되는 것이지, `period.startDate`를 갈아끼우는 일은 일어나지 않는다.

## Aggregate
---
### Aggregate?
데이터 변경의 사이클이 동일한 객체들의 묶음이다. 트랜잭션 경계의 역할을 하며, 애그리거트를 통해 내부 객체에 접근할 수 있다. 애그리거트라고 해서 여러 엔티티, 값 객체들을 묶어서 하나의 Aggregate를 구성해야하는 것이 아니라 엔티티 하나가 얼마든지 Aggregate로써 동작을 할 수 있다. 

Aggregate는 트랜잭션으로의 경계 역할을 하기 때문에 내부 객체들과 논리적인 일관성을 가져야하며, 이들의 생애주기는 동일하다. (애그리거트 삭제 시 하위 엔티티도 삭제 등)

여러 Aggregate는 서로 인터페이스 간의 통신을 통해 비즈니스를 구성할 수 있다. 때문에 어느 정도의 참조가 필요한데, 애그리거트를 직접 참조하는 식이 아닌 식별자 정도만 참조하여 트랜잭션을 명확히 나누어야 한다. 이렇게 식별자만 참조하여 Application Layer에서 여러 Aggregate 조합으로 비즈니스를 구성할 수 있다. 

### Aggregate 예시
상품(Product)은 그 자체로 엔티티이지만 “상품”을 온전히 표현하려면 단가표(`ProductPrice`)와 이미지(`ProductImage`)가 함께 따라다녀야 한다. 단가가 일부만 저장되거나 이미지가 빠진 상태로 노출되면 그 자체로 도메인적 모순이기 때문에, 이 셋은 한 트랜잭션 안에서 함께 살고 함께 죽어야 한다. 이 묶음의 루트가 `Product`이고, 외부에서는 항상 루트를 거쳐서만 내부 객체에 접근/변경할 수 있다.

```kotlin
@Entity
@Table(name = "product")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ProductStatus,

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val prices: MutableList<ProductPrice> = mutableListOf(),

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val images: MutableList<ProductImage> = mutableListOf(),
) {
    fun changePrices(newPrices: List<ProductPrice>) {
        require(status == ProductStatus.DRAFT) { "임시저장 상태에서만 가격을 수정할 수 있습니다" }
        require(newPrices.isNotEmpty()) { "가격은 최소 1개 이상 등록해야 합니다" }
        prices.clear()
        prices.addAll(newPrices)
    }

    fun addImage(image: ProductImage) {
        require(status == ProductStatus.DRAFT) { "임시저장 상태에서만 이미지를 추가할 수 있습니다" }
        require(images.size < MAX_IMAGES) { "이미지는 최대 ${MAX_IMAGES}장까지 첨부할 수 있습니다" }
        images.add(image)
    }

    companion object { private const val MAX_IMAGES = 10 }
}
```

세 가지 포인트가 드러난다.
- 외부에서 `product.prices.add(...)`로 내부 컬렉션을 건드리지 못하도록 `prices` / `images`를 `private`으로 감추고, 변경은 반드시 루트의 행위 메소드를 통해서만 일어난다.
- “DRAFT 상태에서만 수정 가능”, “이미지 최대 10장” 같은 불변식이 루트 안에 모여 있다. 어디서 변경 흐름이 들어와도 같은 규칙을 통과한다.
- 다른 애그리거트(예: `Rental`)는 `Product` 객체 자체를 들고 있지 않고 `productId: Long`만 참조한다. 이렇게 식별자로만 참조하면 트랜잭션 경계가 의도치 않게 두 애그리거트로 번지는 사고를 막을 수 있다.

## Repository
---
### Repository?
레포지토리는 데이터 저장소를 추상화하기 위한 개념이다. 도메인 계층은 데이터를 어떻게 저장하는지 몰라도 되기 때문에 Repository라는 인터페이스를 통해 인프라스트럭쳐 계층과 통신을 하면 된다. 즉, 데이터를 저장하기 위한 저장소와의 느슨한 결합을 위한 장치이다. 

그리고 Aggregate는 트랜잭션의 경계이기 때문에 이 단위로 Repository를 만들어 전체 엔티티들을 저장하게 된다.

### Repository 예제
도메인은 “데이터를 어디에 어떻게 저장하는가”를 알 필요가 없어야 한다. 그래서 도메인 계층에는 인터페이스만 두고, 실제 구현체(JPA, MyBatis, in-memory 등)는 인프라 계층에 둔다. 그리고 Repository는 애그리거트 루트 단위로 만든다 — 즉 “Rental Repository”는 있어도 “RentalPayment Repository”를 별도로 외부에 노출하지는 않는다. 결제는 대여를 통해서만 다뤄지기 때문이다.

```kotlin
// 도메인 계층 (인프라 의존 X)
interface RentalRepository {
    fun save(rental: Rental): Rental
    fun findById(id: Long): Rental?
    fun existsOverlapping(productId: Long, period: RentalPeriod): Boolean
}
```

```kotlin
// 인프라 계층 (JPA 구현)
@Repository
class JpaRentalRepository(
    private val jpaRentalEntityRepository: JpaRentalEntityRepository,
) : RentalRepository {
    override fun save(rental: Rental): Rental = jpaRentalEntityRepository.save(rental)
    override fun findById(id: Long): Rental? = jpaRentalEntityRepository.findById(id).orElse(null)
    override fun existsOverlapping(productId: Long, period: RentalPeriod): Boolean =
        jpaRentalEntityRepository.existsOverlapping(productId, period.startDate, period.endDate)
}
```

세 가지 포인트가 드러난다.
- 인터페이스의 메소드 시그니처에는 인프라 흔적이 없다. `Rental`, `RentalPeriod` 같은 도메인 타입만 오간다. 도메인 테스트는 `FakeRentalRepository`를 주입해 DB 없이도 로직을 검증할 수 있다.
- 메소드는 “어떻게 가져오는가(SQL/JPQL)”가 아니라 “무엇을 묻는가(겹치는 대여가 존재하는가)”로 표현된다. 도메인 어휘가 그대로 시그니처에 남는다.
- 한 애그리거트가 트랜잭션 경계이므로, Repository도 애그리거트 단위로 묶는다. 내부 엔티티(`RentalPayment`)는 루트(`Rental`)를 통해 같이 저장/로드된다.

## Domain Service
---
### Domain Service?
Domain Service는 도메인 계층의 엔티티 혹은 애그리거트에서 표현하기 애매한, 동일 도메인 계층의 애그리거트 간 통신을 통한 조합 등의 책임을 가진 역할을 한다. 

순수한 도메인 규칙을 가지며, 재사용이 가능한 형태이다. 때문에 전체적인 트랜잭션을 다루기 보다는 도메인 로직에만 집중하는 역할을 한다. 

### Domain Service 예시
“요청한 기간에 이미 다른 대여가 있는지” 같은 검증은 `Rental` 한 건만 봐서는 답할 수 없다. 여러 대여를 가로질러야 알 수 있는 규칙이기 때문이다. 이런 책임은 어느 한 엔티티에도 자연스럽게 들어가지 못하므로 도메인 서비스로 빼낸다.

```kotlin
@Service
class RentalReservationService(
    private val rentalRepository: RentalRepository,
) {
    fun reserve(renterId: Long, product: Product, period: RentalPeriod): Rental {
        require(product.ownerId != renterId) { "자신의 상품은 대여할 수 없습니다" }

        if (rentalRepository.existsOverlapping(product.id, period)) {
            throw RentalPeriodConflictException(product.id, period)
        }

        return Rental.request(renterId = renterId, productId = product.id, period = period)
    }
}
```

세 가지 포인트가 드러난다.
- 도메인 서비스는 **여러 애그리거트(또는 여러 인스턴스)에 걸친 규칙**만 담는다. “자기 상품은 대여 불가” 정도의 단일 객체 규칙은 도메인 서비스가 아니라 엔티티 자신이 들고 있어야 한다.
- 메소드 이름은 도메인 동사(`reserve`)로 쓴다. `RentalCreator.create()`처럼 기술적인 이름이 아니라 도메인 어휘가 시그니처에 남도록 한다.
- 트랜잭션을 직접 열지 않는다(`@Transactional`이 메소드에 붙어 있지 않음). 트랜잭션은 상위 UseCase 레이어가 책임지고, 도메인 서비스는 “순수한 도메인 규칙”만 다룬다. 그래서 단위 테스트도 가짜 Repository만으로 가볍게 돌릴 수 있다.

## Domain Event
---
### Domain Event?
도메인에서 발생한 사건을 나타태는 불변 객체이다. 발생한 사건이기 때문에 과거형으로 명명한다. 애그리거트 루트에서 어떤 사건이 발생했는지 외부로 전파할 수 있으며, 관심 그룹에서 이 사건을 구독하여 자신의 비즈니스를 처리할 수 있다. 즉, 도메인 이벤트를 통해 느슨한 결합을 만들어 낼 수 있다.

### Domain Event 예시
대여가 승인되면 알림 발송, 정산 예약, 상품 통계 갱신 같은 부수 처리가 따라붙는다. 이 모든 것을 `Rental.approve()` 안에서 직접 호출하면 도메인이 알림 시스템·정산 시스템에 그대로 묶여버린다. 그래서 “승인되었다”라는 **사실 그 자체**를 불변 객체로 만들어 밖에 알리고, 후속 처리는 관심 있는 쪽이 구독해서 처리하도록 둔다.

```kotlin
interface DomainEvent {
    val occurredAt: Instant
}

data class RentalApproved(
    val rentalId: Long,
    val renterId: Long,
    val productId: Long,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
```

이벤트 이름은 반드시 **이미 일어난 사건**의 과거형(`RentalApproved`, `ProductRejected`)으로 둔다. 그리고 모든 필드는 `val`로 잠긴 불변 객체이다. 발행은 애그리거트 루트 안에서 일어나는데, 곧바로 외부 시스템에 쏘지 않고 일단 자기 안에 모아둔 뒤 한꺼번에 꺼낸다.

```kotlin
@Entity
@Table(name = "rental")
class Rental private constructor(/* ... */) {

    @Transient
    private val events: MutableList<DomainEvent> = mutableListOf()

    fun approve() {
        require(status == RentalStatus.REQUESTED) { "요청 상태에서만 승인할 수 있습니다" }
        status = RentalStatus.APPROVED
        events.add(RentalApproved(rentalId = id, renterId = renterId, productId = productId))
    }

    fun pullEvents(): List<DomainEvent> = events.toList().also { events.clear() }
}
```

`@Transient`로 도메인 이벤트 컬렉션을 영속화 대상에서 제외해, 이벤트가 DB 컬럼으로 새지 않으면서도 엔티티가 자기 사건을 메모리에 들고 있을 수 있게 한다.

세 가지 포인트가 드러난다.
- `Rental`은 “무슨 일이 일어났는지”만 알면 되고, 누가 그것을 어떻게 처리하는지는 모른다. 알림/정산은 이벤트를 구독해서 자기 일을 한다 — 결합도가 낮게 유지된다.
- 이벤트는 엔티티 행위(`approve()`)가 끝난 직후 그 안에서 만들어진다. “상태가 바뀐 그 순간”에 묶여 있어야 의미가 정확하기 때문이다.
- 발행 자체는 도메인이 하지 않는다. 도메인 서비스 또는 애플리케이션 레이어가 저장이 성공한 뒤 `pullEvents()`로 꺼내서 발행한다. 트랜잭션이 롤백되면 이벤트도 사라지므로, “저장은 됐는데 알림은 안 갔다”거나 그 반대 상황을 막을 수 있다.