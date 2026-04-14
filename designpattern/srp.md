# SRP

SRP는 단일 책임 원칙에 대한 약자이다.
단일 책임 원칙은 객체는 하나의 책임만 가져야 하는 것이며, 변경 또한 하나의 사유로만 변경되어야 함을 의미한다.
이를 역으로 생각한다면 수많은 책임을 가진 객체는 수많은 사유로 변경이 될 수 있음을 의미한다.

### 이점

* 변경 격리: 한 곳만 수정하면 된다.
* 재사용성: 하나의 책임을 가진 클래스이므로 재사용성이 증가한다.
* 테스트 용이: 하나의 클래스만 테스트 가능
* 높은 가독성: 책임이 하나만 존재하기 때문에 가독성이 높고, 작다.

### AS-IS: 하나의 복잡한 책임을 가진 객체

```kotlin
class OrderService(
    private val paymentGateway: PaymentGateway,
    private val orderRepository: OrderJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val stockRepository: StockJpaRepository
) {
    fun order(userId: Long, items: List<Item>) {
        val productIds = items.map { it.productId }.distinct()
        val productMap = productRepository.findAllByIdIn(productIds)
            .associateBy { it.id }
        val totalAmount = items.fold(0L) { acc, item ->
            val productPrice = productMap[item.productId]?.price?: 0
            acc + (productPrice.toLong() * item.quantity)
        }
        val order = orderRepository.save(Order(
            id = 0L,
            totalPrice = totalAmount,
            productNames = productMap.values.map { it.name }
        ))
        paymentGateway.pay(order)
        stockRepository.findByProductIdIn(productIds).forEach { stock ->
            val item = items.find { stock.productId == it.productId }?: return@forEach
            stock.decreaseQuantity(item.quantity)
        }
    }
}

data class Item(
    val productId: Long,
    val quantity: Long
)

interface PaymentGateway {
    fun pay(order: Order)
}

interface OrderJpaRepository {
    fun save(order: Order): Order
}

interface ProductJpaRepository {
    fun findAllByIdIn(ids: List<Long>): List<Product>
}

interface StockJpaRepository {
    fun findByProductIdIn(productIds: List<Long>): List<Stock>
}
```

OrderService의 order 메서드는 상품 리스트를 조회하고, 총 계산액을 계산하고, 결제를 하고 등등 여러 책임을 가진다.
만약 totalAmount를 계산하기 위해 쿠폰 적용이라는 비즈니스 로직이 들어가게 된다면, order 메서드에 변경이 생기게 된다.

요구사항은 총 계산액에 대한 정책에 단지 쿠폰 적용을 추가했을 뿐인데, 주문이라는 메서드에는 변경이 생기게 된 것이다.

또한 order 메서드 테스트 코드에도 쿠폰 정책을 추가해야하므로 테스트 코드에도 영향을 미치게 된다.

### TO-BE: 단일 책임으로 분리된 객체와 협력

```kotlin
class OrderService(
    private val paymentGateway: PaymentGateway,
    private val orderCreator: OrderCreator,
    private val productRepository: ProductJpaRepository,
    private val stockManager: StockManager
) {
    fun order(userId: Long, items: List<Item>) {
        val productIds = items.map { it.productId }.distinct()
        val productMap = productRepository.findAllByIdIn(productIds)
            .associateBy { it.id }
        val totalAmount = ItemsCalculator.calculate(productMap, items)
        val order = orderCreator.create(totalAmount, productMap.values.map { it.name })
        paymentGateway.pay(order)
        stockManager.decrease(productIds, items)
    }
}

data class Item(
    val productId: Long,
    val quantity: Long
)

interface PaymentGateway {
    fun pay(order: Order)
}

class OrderCreator(private val orderRepository: OrderJpaRepository) {
    fun create(totalAmount: Long, productNames: List<String>): Order {
        return orderRepository.save(Order(
            id = 0L,
            totalPrice = totalAmount,
            productNames = productNames
        ))
    }
}

interface OrderJpaRepository {
    fun save(order: Order): Order
}

interface ProductJpaRepository {
    fun findAllByIdIn(ids: List<Long>): List<Product>
}

object ItemsCalculator {
    fun calculate(productMap: Map<Long?, Product>, items: List<Item>): Long {
        return items.fold(0L) { acc, item ->
            val productPrice = productMap[item.productId]?.price?: 0
            acc + (productPrice.toLong() * item.quantity)
        }
    }
}

interface StockJpaRepository {
    fun findByProductIdIn(productIds: List<Long>): List<Stock>
}

class StockManager(private val stockRepository: StockJpaRepository) {
    fun decrease(productIds: List<Long>, items: List<Item>) {
        stockRepository.findByProductIdIn(productIds).forEach { stock ->
            val item = items.find { stock.productId == it.productId }?: return@forEach
            stock.decreaseQuantity(item.quantity)
        }
    }
}
```

각 책임별로 객체를 분리하고, 이들의 협력으로 order 메서드를 구성하도록 하였다.
as-is에서의 문제점이었던 쿠폰 정책을 적용하려면 `ItemsCalculator`의 로직을 변경하면 되고, 테스트 코드 또한 해당 부분만 변경하면 된다.

또한 다른 메서드에서 총 계산액 정책 비즈니스를 사용한다면 `ItemsCalculator`를 사용하면 되므로 코드의 재사용성이 높아지게 되었다.

### SRP를 지키기 위한 원칙

* 같은 책임을 가진 응집도가 높은 단위로 객체를 구성한다.
* 객체의 변경 사유는 1가지만 구성되게 객체를 구성한다.

---

## ApplicantsServiceImpl

SRP에 따라 ApplicantsServiceImpl을 살펴보면 다음과 같은 위배 사항들이 존재한다.

### 높은 의존성

ApplicantsServiceImpl 기본 생성자에 주입된 의존성을 보면 64개의 인터페이스들이 의존되어있다.
이는 64개의 인터페이스를 활용한 메서드들이 서비스 내에서 구현되어 있음을 의미한다.

차라리 이 인터페이스들이 하나의 책임을 가진 클래스들이었다면, 그나마 괜찮았을 것 같지만 단순 데이터 조회, 쓰기만의 책임을 가진 인터페이스들이기 때문에 좋은 응집도를 가진 인터페이스들이라 볼 수 없다.

### 복합적인 책임을 가진 메서드

한 가지 예시로 TRM으로부터 후보자를 넘겨받아 ATS 지원서로 등록하는 메서드이다.

```kotlin
override fun importTRMApplicant(command: ApplicantsServiceCommand.ImportTRMApplicant): Applicants {
    // 1. 워크스페이스 만료일 정책을 조회하고, 지원자를 생성한다.
    val expireDay = workspaceClient.findAtsApplicantConfigurationByWorkspaceId(workspaceId = command.workspace.id)
        .applicantExpireAfterNDays
    var applicant = this.applicantsStore.save(
        command.toApplicantEntity(
            expireDate = ZonedDateTime.now().plusDays(expireDay),
            applyConfigSnapshotKey = getApplyConfigSnapshotUseCase
                .getLatestApplyConfigSnapshotBy(openingId = command.opening.id.toLong())
                .id
        )
    )

    // 2. 진행할 채용 단계를 조회하고, 지원자를 이동시킨다.
    val processOnOpening: ProcessesOnOpening

    when (command.processOnOpeningId) {
        null ->
            processOnOpening =
                processesOnOpeningReader.getActiveProcesses(command.opening.id)
                    .find { it.procedure == 0 }
                    ?: throw NotFoundException(ApplicantErrors.NO_OPENING_PROCESSES_ARE_AVAILABLE.message)

        else ->
            processOnOpening =
                processesOnOpeningReader.getActiveProcesses(command.opening.id)
                    .find { it.id == command.processOnOpeningId }
                    ?: throw NotFoundException(ApplicantErrors.NO_OPENING_PROCESSES_ARE_AVAILABLE.message)
    }

    applicant.processOnOpeningId = processOnOpening.id
    applicant = this.applicantsStore.save(applicant)

    // 3. 지원자 태그를 생성한다.
    this.tagsOnApplicantStore.saveAll(
        command.toTagsEntity(
            applicant
        )
    )

    // 4. 지원자 이력서 파일을 등록한다.
    val datasOnApplicant = command.toDatasOnApplicantEntity(
        applicant
    )

    val copiedData =
        datasOnApplicant.copyDocsFromTRMCandidate(
            command.opening,
            applicant,
            s3Constant.getDocsS3(),
            command.docs
        )

    this.dataOnApplicantPersistencePort.save(datasOnApplicant)

    copiedData.forEach {
        s3Executor.copyObject(
            destBucket = it.destBucket,
            destKey = it.destKey,
            orgBucket = it.orgBucket,
            orgKey = it.orgKey,
        )
    }

    // 5. 지원자의 평가 상태와 평가를 할당한다.
    val hasEvaluators = processEvaluatorReader.existsByProcessOnOpeningId(processOnOpening.id)
    applicantEvaluationStatusStore.save(
        command.toEvaluationStatusModel(
            applicantId = applicant.id,
            hasEvaluatorsInProcess = hasEvaluators
        )
    )
    this.evaluationService.assign(
        EvaluationService.AssignCommand(
            applicantId = applicant.id,
            processId = processOnOpening.id,
        )
    )

    // 6. 지원서 등록 이벤트 (Type = TRM)를 발행한다.
    publishImportTRMApplicantEvent(applicant, command)

    // 7. 지원서 변경 내역 저장
    applicationEventPublisher.publishEvent(
        SaveApplicantInfoChangedHistoryEvent(
            action = ApplicantChangedActionType.CREATED,
            changedUserType = ApplicantChangedUserType.USER,
            changedUserId = command.user.id.toLong(),
            originalApplicant = applicant.toAtsDomain(),
            basicInfo = applicant.toAtsDomain().markAsChanged(),
            documents = datasOnApplicant.docs?.map { it.toAtsDomain() }?.markAsChanged(),
        )
    )

    return applicant
}
```

TRM으로부터 등록 메서드를 대략적으로 살펴본 결과 7가지 정도의 책임이 존재한다.
만약 태그 생성 시 첫 번째 태그는 'TRM으로부터 등록된 지원자입니다.' 라는 비즈니스 정책이 들어가면 어떻게 될까?
태그를 등록하는 요구사항임에도 불구하고, 해당 메서드의 수정이 불가피해질 것이다.

그리고 범위를 넓혀서 모든 지원자 생성 플로우에서 타입별로 'ooo으로 인해 생성된 지원자입니다.'라는 비즈니스 규칙이 추가된다면 TRM 뿐만 아니라 관련된 모든 지원자 생성 플로우에 변경 사항이 생길 것이다.

즉, 태그를 생성한다는 책임을 가진 객체가 존재한다면 해당 클래스만을 수정함으로써 이 요구사항들을 빠르고, 부담없이 대처할 수 있을 것이다.

### 낮은 가독성

처음 클래스 코드를 살펴 보았을 때 64개의 의존성이 있다라는 것은 도대체 이 클래스는 무엇을 하는 것인지를 알기가 어려울 것이다.

그리고 TRM 후보자 등록 메서드를 보면 7개의 책임이 존재하고, 긴 라인을 가진 메서드이기 때문에 가독성이 상당히 떨어진다. (그리고 메서드 내부 추상화 레벨이 맞지 않아 가독성이 떨어지기도 함)

---

## SRP와 SOLID

SRP는 SOLID 원칙의 첫 번째로, 나머지 원칙들의 토대가 된다.

| 원칙 | 약자 | 핵심 |
|------|------|------|
| **Single Responsibility** | S | 하나의 변경 사유만 가져야 한다 |
| Open-Closed | O | 확장에 열려 있고, 수정에 닫혀 있어야 한다 |
| Liskov Substitution | L | 하위 타입은 상위 타입을 대체할 수 있어야 한다 |
| Interface Segregation | I | 사용하지 않는 인터페이스에 의존하지 않아야 한다 |
| Dependency Inversion | D | 추상화에 의존해야 하고, 구체화에 의존하지 않아야 한다 |

SRP가 지켜지지 않으면 OCP(개방-폐쇄)도 무너진다. 여러 책임이 하나의 클래스에 있으면 하나의 변경이 관련 없는 기능에 영향을 미치게 되어 수정에 닫혀 있을 수 없다.

## SRP 위반 징후 체크리스트

* 클래스의 의존성(생성자 주입)이 7개 이상
* 메서드 하나에 주석으로 단계를 구분하고 있다면 ("// 1. xxx", "// 2. xxx")
* 클래스명에 "Manager", "Processor", "Handler" 같은 포괄적 이름 사용
* 하나의 변경 요구사항에 여러 메서드를 수정해야 함
* 테스트 코드 작성 시 목(mock) 객체가 과도하게 많음
