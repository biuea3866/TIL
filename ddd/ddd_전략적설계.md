# [DDD] 전략적 설계 — 서브도메인, Bounded Context, Context Mapping

DDD에서 전략적 설계는 "무엇을 만들 것인가"보다 "어디에 집중할 것인가"를 결정하는 과정이다. 도메인을 서브도메인으로 분해하고, 각 서브도메인에 적절한 경계(Bounded Context)를 설정하며, 경계 간의 관계(Context Mapping)를 정의한다. 코드를 한 줄도 작성하지 않은 단계에서 프로젝트의 성패가 갈릴 수 있는 영역이다.

---

## Problem Space와 Solution Space

전략적 설계의 출발점은 문제 영역과 해결 영역을 분리하는 것이다.

```
┌────────────────────────────────────────────────────────────────┐
│  ┌─────────── Problem Space ───────────┐                      │
│  │  "우리가 해결해야 할 비즈니스 문제"   │                      │
│  │                                     │                      │
│  │  Domain (도메인)                    │                      │
│  │  └── Subdomain (서브도메인)          │                      │
│  │       ├── Core Subdomain            │                      │
│  │       ├── Supporting Subdomain      │                      │
│  │       └── Generic Subdomain         │                      │
│  └─────────────────────────────────────┘                      │
│                     │                                          │
│                     │ 모델링                                   │
│                     ▼                                          │
│  ┌─────────── Solution Space ──────────┐                      │
│  │  "우리가 만드는 소프트웨어 해결책"    │                      │
│  │                                     │                      │
│  │  Bounded Context (경계 맥락)        │                      │
│  │  └── Domain Model (도메인 모델)      │                      │
│  │       ├── Aggregate                 │                      │
│  │       ├── Entity / Value Object     │                      │
│  │       └── Domain Event              │                      │
│  └─────────────────────────────────────┘                      │
│                                                                │
│  이상적: 1 Subdomain = 1 Bounded Context                       │
│  현실: 복잡한 서브도메인은 여러 BC로 나뉠 수 있고,              │
│        여러 서브도메인이 하나의 BC에 포함될 수도 있다.           │
│        (후자는 경계 재검토 필요 신호)                            │
└────────────────────────────────────────────────────────────────┘
```

서브도메인은 "우리가 풀어야 할 문제"를 분류한 것이고, Bounded Context는 "그 문제를 풀기 위한 소프트웨어 경계"이다. 이상적으로는 1:1로 대응하지만, 현실에서는 복잡한 서브도메인이 여러 BC로 나뉘거나, 반대로 여러 서브도메인이 하나의 BC에 들어가기도 한다. 후자의 경우는 경계를 다시 검토해야 한다는 신호이다.

---

## 서브도메인의 세 가지 유형

도메인을 서브도메인으로 분해했다면, 각 서브도메인의 유형을 판별해야 한다. 모든 서브도메인에 동일한 노력을 쏟는 것은 자원 낭비이기 때문이다. Eric Evans는 서브도메인을 세 가지 유형으로 분류한다.

| 유형 | 특징 | 전략 |
|------|------|------|
| **Core Domain** | 회사의 경쟁 우위를 만드는 핵심 영역. 기성 솔루션으로 대체 불가. Evans 추정: 전체 가치의 ~20%, 코드의 ~5%, 노력의 ~80% | 최고 인력 배치, DDD 전면 적용, 헥사고날/클린 아키텍처 |
| **Supporting Subdomain** | Core를 지원하지만 차별화 요소는 아님. 기성 솔루션이 딱 맞지 않아 자체 개발 필요 | 적절한 수준의 설계, 레이어드 아키텍처 |
| **Generic Subdomain** | 모든 회사가 같은 방식으로 하는 범용 기능. 기성 솔루션/오픈소스 활용 가능 | 구매 또는 OSS 활용, 최소한의 설계 |

여기서 핵심적인 인사이트가 있다. **같은 서브도메인이라도 회사에 따라 유형이 달라진다.** "스케줄링"은 일반 회사에는 Supporting이지만, 스케줄링 SaaS 회사에는 Core Domain이다. 유형 분류의 기준은 기능의 복잡도가 아니라 **"이것이 시장에서 우리를 차별화하는가?"** 이다.

### 이커머스 서브도메인 분류 예시

```
┌─────────── Core ───────────┐
│  주문 관리 (Order)          │  ← 주문 흐름이 핵심 경쟁력
│  상품 카탈로그 (Catalog)    │  ← 상품 큐레이션이 차별화
│  가격/프로모션 (Pricing)    │  ← 가격 전략이 경쟁 우위
└────────────────────────────┘

┌──────── Supporting ────────┐
│  재고 관리 (Inventory)      │  ← 맞춤 개발 필요하지만 범용
│  배송 (Shipping)            │  ← 비즈니스별 특수 로직 존재
│  고객 서비스 (CS)           │  ← 자체 CS 프로세스 존재
└────────────────────────────┘

┌──────── Generic ───────────┐
│  인증/인가 (Auth)           │  ← Keycloak, Auth0 등 활용
│  결제 PG 연동 (Payment)     │  ← 토스, 아임포트 등 사용
│  알림 (Notification)        │  ← 이메일/SMS 외부 서비스
│  회계 (Accounting)          │  ← 법적 요건에 따른 표준화
└────────────────────────────┘
```

참고로, Core Domain에 소프트웨어 구현이 없을 수도 있다. 인적 네트워크가 경쟁 우위인 사업이라면 코드로 구현할 Core가 없을 수 있다.

### 서브도메인 분류 판단 매트릭스

어떤 서브도메인이 Core인지 헷갈릴 때는 비즈니스 차별화와 복잡도 두 축으로 판단한다.

```
              비즈니스 차별화
              높음 ◄─────────────► 낮음

   복    높  ┌──────────────┬──────────────┐
   잡    음  │              │              │
   도        │  CORE        │  GENERIC     │
             │  Domain      │  Subdomain   │
             │              │  (복잡하지만  │
             │              │   범용 해법)  │
             ├──────────────┼──────────────┤
        낮  │              │              │
        음  │  SUPPORTING  │  GENERIC     │
             │  Subdomain   │  Subdomain   │
             │              │  (단순 CRUD) │
             └──────────────┴──────────────┘

→ 차별화 높고 복잡하면 Core
→ 차별화 낮으면 복잡도와 무관하게 Generic 또는 Supporting
→ 복잡도가 높다고 Core는 아니다! 차별화 여부가 핵심
```

---

## Core Domain 디스틸레이션

디스틸레이션(Distillation)은 큰 시스템에서 Core Domain을 명확히 식별하고 분리하는 전략적 활동이다. 증류가 혼합물에서 본질을 추출하듯, 도메인에서 핵심을 걸러내는 과정이다.

### 네 가지 기법

**1. Domain Vision Statement** — Core Domain의 가치를 간결하게(1페이지 이내) 설명하는 선언문이다. 차별화 요소에 집중하며, 범용적 특성(인증, 로깅 등)은 제외한다. 프로젝트 초기에 작성하고, 새로운 인사이트가 생길 때마다 갱신한다.

**2. Highlighted Core** — 코드에서 Core Domain 요소를 명시적으로 표시한다. 핵심 개념과 상호작용을 설명하는 Distillation Document를 작성하거나, 코드 저장소에서 Core 요소에 직접 표시(Flagged Core)하는 방법이 있다.

**3. Segregated Core** — 리팩토링을 통해 Core 개념을 별도 모듈로 물리적 분리한다. Generic/Supporting 서브도메인은 별도 모듈/패키지로, Cohesive Mechanism은 별도 라이브러리로 분리하여 Core가 코드에서 직접 보이도록 한다.

**4. Abstract Core** — 가장 근본적인 개념을 추상 클래스/인터페이스로 표현하는 가장 야심적인 기법이다. 복잡한 Core Domain에 적합하다.

### Generic Subdomain vs Cohesive Mechanism

둘 다 Core Domain의 부담을 줄이는 역할이지만 성격이 다르다. Generic Subdomain은 여전히 도메인의 일부(예: 회계, 인증)인 반면, Cohesive Mechanism은 도메인의 일부가 아닌 순수 기술적 알고리즘(예: 그래프 탐색 프레임워크, 상태 머신 라이브러리)이다.

```
┌──────────────────────────────────────┐
│       Core Domain                    │
│  ┌────────────┐ ┌────────────────┐  │
│  │ 핵심 모델   │ │ 핵심 규칙      │  │
│  └─────┬──────┘ └───────┬────────┘  │
│        │ 사용            │ 사용      │
└────────┼────────────────┼───────────┘
         ▼                ▼
┌──────────────┐  ┌──────────────┐
│ Generic      │  │ Cohesive     │
│ Subdomain    │  │ Mechanism    │
│ (회계 모듈)  │  │ (규칙 엔진)  │
└──────────────┘  └──────────────┘
```

---

## Bounded Context

### Bounded Context란

Bounded Context(BC)는 특정 도메인 모델과 유비쿼터스 언어가 적용되는 명시적 경계이다. 같은 용어라도 맥락에 따라 의미가 완전히 달라진다는 점에서 출발한다.

```
"상품(Product)"이라는 단어가 맥락에 따라 다르게 쓰인다

┌─── 카탈로그 Context ──┐  ┌─── 주문 Context ────┐
│                       │  │                      │
│  Product              │  │  Product             │
│  • name               │  │  • productId         │
│  • description        │  │  • price (확정가)     │
│  • images             │  │  • quantity           │
│  • category           │  │                      │
│  • price (정가)       │  └──────────────────────┘
│  • specifications     │
└───────────────────────┘  ┌─── 배송 Context ────┐
                            │                      │
                            │  Product             │
                            │  • productId         │
                            │  • weight            │
                            │  • dimensions        │
                            │  • fragile           │
                            └──────────────────────┘

각 Context는 자신만의 모델, 유비쿼터스 언어, 경계를 가진다
→ 전체 시스템을 하나의 통합 모델로 만들려는 시도는 실패한다
```

카탈로그에서의 "상품"은 이름, 설명, 이미지, 카테고리가 중요하다. 주문에서의 "상품"은 확정 가격과 수량이 중요하다. 배송에서의 "상품"은 무게, 크기, 파손 여부가 중요하다. 이 세 가지를 하나의 Product 클래스로 통합하려는 시도는 거대한 God Object를 만들 뿐이다.

### Bounded Context 식별 기준

BC를 식별하는 데 사용할 수 있는 다섯 가지 휴리스틱이 있다.

1. **언어 경계 (가장 중요)** — 같은 단어가 다르게 쓰이거나, 대화 주제가 바뀌는 지점. "그 단어, 여기서는 다른 뜻이에요"라는 말이 나오면 BC 경계 후보이다.

2. **비즈니스 역량** — 독립적인 비즈니스 기능 단위. "이건 우리 팀 소관이 아닙니다"라는 말이 나오면 BC 경계 후보이다.

3. **팀 경계 (Conway의 법칙)** — 하나의 BC는 하나의 팀이 소유해야 한다. 여러 팀이 하나의 BC를 수정하고 있다면 분리가 필요하다는 신호이다.

4. **데이터 소유권** — "이 데이터의 원본(Source of Truth)은 누가 관리하는가?"로 판단한다.

5. **Event Storming에서의 피벗 이벤트** — 언어가 바뀌고 다른 프로세스가 시작되는 지점. "주문이 생성됨" → "결제가 처리됨"과 같은 언어/프로세스 전환이 발생하는 곳이다.

흔한 실수로는 하나의 거대한 모델을 모든 곳에서 공유하는 것(Big Ball of Mud), DB 테이블 단위로 Context를 나누는 것, 기술 레이어(API, DB, MQ)를 Context로 착각하는 것 등이 있다. BC의 본질은 **유비쿼터스 언어의 적용 범위**라는 점을 잊지 말아야 한다.

### Bounded Context Canvas

BC를 블랙박스처럼 다루어 입출력과 내부 처리를 시각화하는 도구이다. Nick Tune이 제안한 모델링 기법으로, 각 BC의 책임을 명확히 정의한다.

```
┌──────────────── [주문 Context] ────────────────────┐
│                                                    │
│  Name: 주문 관리 (Order Management)                │
│  Purpose: 고객 주문의 생성, 변경, 취소 처리        │
│  Classification: Core Domain                       │
│                                                    │
│  ── Inbound Communication ──                       │
│  • PlaceOrderCommand (from API Gateway)            │
│  • CancelOrderCommand (from CS System)             │
│  • ProductPriceChangedEvent (from Catalog BC)      │
│                                                    │
│  ── Internal Building Blocks ──                    │
│  • Aggregate: Order, OrderLine                     │
│  • Policy: DiscountPolicy, RefundPolicy            │
│                                                    │
│  ── Outbound Communication ──                      │
│  • OrderPlacedEvent → Payment BC, Inventory BC     │
│  • OrderCancelledEvent → Payment BC, Notification  │
│                                                    │
│  ── Ubiquitous Language ──                         │
│  주문, 주문 항목, 배송 정보, 주문 확정, 주문 취소  │
└────────────────────────────────────────────────────┘
```

### Bounded Context와 마이크로서비스

BC가 곧 마이크로서비스는 아니지만, 좋은 출발점이다. Microsoft Azure 가이드에서는 "마이크로서비스는 Aggregate보다 작지 않고, Bounded Context보다 크지 않게 설계하라"고 권장한다.

```
가능한 매핑:
• 1 BC = 1 마이크로서비스 (이상적)
• 1 BC = N 마이크로서비스 (큰 BC를 분리)
• N BC = 1 모놀리스 (모듈러 모놀리스)

┌─────────── 모듈러 모놀리스 ──────────┐
│  ┌────────┐ ┌────────┐ ┌────────┐   │
│  │ 주문   │ │ 카탈로그│ │ 배송   │   │
│  │ Module │ │ Module │ │ Module │   │
│  └───┬────┘ └───┬────┘ └───┬────┘   │
│      │          │          │         │
│  ────┴──────────┴──────────┴────     │
│         공유 인프라 (DB, MQ)          │
└──────────────────────────────────────┘

→ 모놀리스에서도 BC 경계를 잘 지키면
  나중에 마이크로서비스로 분리가 수월하다
```

---

## Context Mapping

여러 BC가 존재한다면, 그 사이의 관계를 정의해야 한다. Context Map은 팀 간 협업 방식(조직적 관계)과 기술적 통합 방식 모두를 포함한다. 두 개의 서로 다른 BC에는 두 개의 유비쿼터스 언어가 존재하고, 매핑 라인은 이 두 언어 사이의 번역을 나타낸다.

### 9가지 Context Mapping 패턴

#### 협력적 패턴

| 패턴 | 설명 |
|------|------|
| **Partnership** | 두 팀이 대등하게 협력한다. 함께 성공하거나 함께 실패하며, CI로 통합 상태를 유지한다. 높은 커뮤니케이션 비용이 수반된다. |
| **Shared Kernel** | 두 BC가 공통 모델(코드)을 공유한다. 변경 시 양쪽 합의가 필요하며, 가능한 작게 유지해야 한다. |

#### 상류-하류(Upstream-Downstream) 패턴

| 패턴 | 설명 |
|------|------|
| **Customer-Supplier** | 상류(Supplier)가 하류(Customer)에 API를 제공한다. 하류의 요구사항을 상류가 수용할 의향이 있다. |
| **Conformist** | 하류가 상류 모델을 그대로 따른다. 협상력이 없을 때(외부 API 등) 선택하며, 번역 비용을 절약하는 대신 자율성을 포기한다. |
| **Anti-Corruption Layer (ACL)** | 하류가 번역 계층을 두어 자신의 모델을 보호한다. 실무에서 가장 많이 쓰이는 패턴이다. |
| **Open Host Service (OHS)** | 상류가 공개 프로토콜/API를 정의하여 다수의 하류에 제공한다. RESTful API, gRPC 서비스 등이 해당한다. |
| **Published Language (PL)** | 문서화된 공유 데이터 형식이다. JSON Schema, Protobuf, Avro 등이며, OHS와 함께 사용되는 경우가 많다. |

#### 비통합 패턴

| 패턴 | 설명 |
|------|------|
| **Separate Ways** | 통합하지 않기로 결정한다. 비용 대비 효과가 없을 때 각자 독립적으로 구현한다. |
| **Big Ball of Mud** | 경계가 불분명한 혼합 모델이다. 레거시 시스템에서 흔히 발견되며, ACL로 격리하고 점진적으로 개선한다. |

### Anti-Corruption Layer (ACL) 상세

실무에서 가장 자주 접하게 되는 패턴이다. 외부 시스템의 모델이 우리 도메인 모델을 오염시키지 않도록 번역 계층을 두는 것이 핵심이다.

```
┌─── 외부 결제 시스템 ───┐     ┌─── 우리 주문 시스템 ───┐
│                        │     │                         │
│  PaymentTransaction    │     │   Payment               │
│  • txn_id              │     │   • paymentId           │
│  • amt                 │     │   • amount (Money)      │
│  • ccy                 │     │   • method              │
│  • stat (0,1,2)        │     │   • status (Enum)       │
│                        │     │   • completedAt         │
└──────────┬─────────────┘     └──────────┬──────────────┘
           │                               ▲
           │        ┌──── ACL ────┐        │
           └───────→│ Translator  │────────┘
                    │ Adapter     │
                    │ Facade      │
                    └─────────────┘
```

ACL은 세 가지 역할을 수행한다.
- **Adapter**: 외부 API 호출을 캡슐화한다.
- **Translator**: 외부 모델을 내부 도메인 모델로 변환한다.
- **Facade**: 외부 시스템의 장애를 격리한다.

#### ACL 구현 예시

```java
// 외부 결제 API의 응답 (외부 모델)
public record PgPaymentResponse(
    String txn_id, long amt, String ccy,
    int stat, String mthd
) {}

// ACL: 외부 모델 → 내부 모델 변환
@Component
public class PaymentGatewayAdapter implements PaymentGateway {

    private final PgApiClient pgClient;

    @Override
    public Payment requestPayment(Order order) {
        PgPaymentResponse response = pgClient.pay(
            order.totalAmount().value(),
            order.currency().code());
        return toDomain(response);
    }

    private Payment toDomain(PgPaymentResponse res) {
        return Payment.builder()
            .id(PaymentId.of(res.txn_id()))
            .amount(Money.of(res.amt(), Currency.of(res.ccy())))
            .method(PaymentMethod.from(res.mthd()))
            .status(mapStatus(res.stat()))
            .build();
    }

    private PaymentStatus mapStatus(int stat) {
        return switch (stat) {
            case 0 -> PaymentStatus.PENDING;
            case 1 -> PaymentStatus.COMPLETED;
            case 2 -> PaymentStatus.FAILED;
            default -> throw new UnknownPaymentStatusException(stat);
        };
    }
}
```

외부의 `txn_id`, `amt`, `ccy`, `stat` 같은 축약된 필드명과 정수형 상태 코드가 우리 도메인에 침투하지 않는다. 내부에서는 `PaymentId`, `Money`, `PaymentStatus` 같은 도메인에 맞는 타입으로 변환하여 사용한다.

---

## Context Map 시각화

실제 이커머스 시스템의 Context Map을 그려보면 다음과 같다.

```
       ┌──────────┐    OHS/PL     ┌──────────┐
       │ 카탈로그  │──────────────→│  주문     │
       │ (U)      │               │  (D)      │
       └──────────┘               └────┬─────┘
                                       │
                              Customer │ Supplier
                                       │
                                       ▼
       ┌──────────┐    ACL       ┌──────────┐
       │ 외부 PG  │◄────────────│  결제     │
       │ (외부)   │              │  (D)      │
       └──────────┘              └──────────┘
                                       │
                              Partnership
                                       │
                                       ▼
       ┌──────────┐  Conformist  ┌──────────┐
       │  재고    │◄────────────│  배송     │
       │  (U)     │              │  (D)      │
       └──────────┘              └──────────┘

       ┌──────────┐
       │ 레거시   │  Big Ball of Mud → ACL로 격리
       │ ERP      │
       └──────────┘

U = Upstream, D = Downstream
OHS/PL = Open Host Service + Published Language
ACL = Anti-Corruption Layer
```

- 카탈로그 → 주문: 카탈로그가 OHS/PL로 상품 정보를 공개하고, 주문이 이를 소비한다.
- 결제 → 외부 PG: 외부 결제 시스템과의 통합에 ACL을 두어 도메인을 보호한다.
- 결제 ↔ 주문: 두 팀이 긴밀하게 협력하는 Partnership 관계이다.
- 배송 → 재고: 배송이 재고의 모델을 그대로 따르는 Conformist 관계이다.
- 레거시 ERP: Big Ball of Mud로 식별하고, ACL로 격리하며 점진적으로 개선한다.

---

## BC 간 기술적 통합 방식

BC 간의 매핑 패턴이 결정되었다면, 기술적으로 어떻게 통합할지 선택해야 한다.

| 구분 | 동기 (REST/gRPC) | 비동기 (이벤트) |
|------|-------------------|-----------------|
| 결합도 | 높음 (런타임 의존) | 낮음 |
| 일관성 | 강한 일관성 | 최종 일관성 |
| 장애 전파 | 전파됨 | 격리됨 |
| 구현 난이도 | 낮음 | 높음 |
| 적합한 경우 | 즉시 응답 필요 | 느슨한 결합 중시 |
| 구현 예 | REST API, gRPC, GraphQL | Kafka, RabbitMQ, Domain Event |

이벤트 기반 통합에서는 Domain Event와 Integration Event를 구분해야 한다. Domain Event는 BC 내부에서 발생하여 내부 핸들러가 처리하고, Integration Event는 BC 간 통신을 위해 메시지 브로커를 경유한다. Integration Event는 트랜잭션 커밋 후 비동기로 발행하는 것이 일반적이며, Transactional Outbox 패턴을 사용한다.

```
주문 BC ──[OrderPlacedEvent]──→ Kafka ──→ 재고 BC
                                       ──→ 결제 BC
                                       ──→ 알림 BC
```

Core 도메인 간에는 비동기 이벤트 기반 통합이 권장되며, 단순 조회 목적이라면 동기 API 호출도 적절하다.

---

## 서브도메인별 투자 전략

마지막으로, 서브도메인 유형에 따라 설계와 구현에 차등 투자해야 한다는 점을 정리한다.

| 항목 | Core | Supporting | Generic |
|------|------|------------|---------|
| 설계 수준 | DDD 전면 적용 | 적절한 DDD | 최소한 |
| 인력 배치 | 최고 인력 | 일반 수준 | 외주 가능 |
| 아키텍처 | 헥사고날/클린 아키텍처 | 레이어드 아키텍처 | 프레임워크 기본 |
| 테스트 | 높은 커버리지 | 적절한 수준 | 기본 테스트 |
| 모델링 | Rich Domain Model | 간소화 모델 | CRUD |
| 변경 빈도 | 자주 진화 | 가끔 변경 | 거의 불변 |
| 구현 방식 | 자체 개발 | 자체/외주 | 구매/OSS |

Conway의 법칙에 따르면, 시스템 구조는 조직의 커뮤니케이션 구조를 반영한다. 서브도메인 경계를 팀 경계와 일치시키면 효과적이다. 하나의 BC에 여러 팀이 일하거나, 한 팀이 무관한 여러 BC를 맡고 있다면 경계를 재검토해야 한다.

---

## 참조

- Eric Evans, *Domain-Driven Design: Tackling Complexity in the Heart of Software*
- Vaughn Vernon, *Implementing Domain-Driven Design*
- Vaadin, Petter Holmström, DDD Series — EMR 시스템 예시
- Martin Fowler, [BoundedContext](https://martinfowler.com/bliki/BoundedContext.html)
- Nick Tune, [Bounded Context Canvas](https://github.com/ddd-crew/bounded-context-canvas)
- Microsoft Azure Architecture Guide — Microservices and DDD
