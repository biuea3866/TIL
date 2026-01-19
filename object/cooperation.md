## 협력과 책임, 패턴

**작성일:** 2026. 01. 19

**태그:** #OOP #Kotlin #Refactoring #DesignPattern

---
### 1. 일관성 있는 협력 (Cooperation)

#### 🛑 문제점: 구현에 의존한 코드

객체가 서로 협력할 때, 구체적인 구현(Implementation)에 의존하면 요구사항이 늘어날수록 코드가 변경에 대응이 힘들어집니다. 즉, **일관성이 무너집니다.**

```kotlin
// 새로운 연동처가 생길 때마다 메서드가 추가되고 흐름이 제각각임
class SellerIntegrationService {
    fun integrateWith11st(apiKey: String) { /* 11번가 로직 */ }
    fun integrateWithNaver(clientId: String, secret: String) { /* 네이버 로직 */ }
    // 쿠팡, 카카오가 추가된다면...?
}

```

#### ✅ 해결책: 역할과 책임의 분리

구현이 아닌 **역할**에 의존하게 하여 협력의 패턴을 만듭니다.

```kotlin
// '인증 -> 조회 -> 저장'이라는 협력의 패턴을 고정
class SellerIntegrationService(
    private val authenticator: StoreAuthenticator, // 역할 1
    private val productFetcher: ProductFetcher,    // 역할 2
    private val productRepository: ProductRepository
) {
    fun integrate() {
        val credentials = authenticator.authenticate()   // 흐름 1: 인증
        val products = productFetcher.fetch(credentials) // 흐름 2: 조회
        productRepository.saveAll(products)              // 흐름 3: 저장
    }
}

```

---

### 2. 책임 할당과 정보 전문가 (Responsibility)

객체에게 책임을 할당하는 순서는 시스템 설계의 핵심입니다.

1. **시스템 설계:** 전체 그림을 그린다.
2. **책임 흐름 구성:** 무엇을 해야 하는가?
3. **정보 정의:** 그 책임을 수행하려면 어떤 정보가 필요한가?
4. **객체 할당 (Information Expert):** 그 정보를 가장 잘 아는 객체에게 책임을 준다.

#### Information Expert 패턴에 대한 생각

정보를 가장 잘 아는 객체에게 책임을 준다"는 말은 자칫 데이터(정보)를 먼저 만들라는 것처럼 들립니다. 하지만 객체지향은 **책임(행동)이 우선**입니다.    
뭔가 역설적인 것처럼 들리지만 도메인 모델링이 있기 때문에 이 모순을 보완시켜준 것 같습니다.

1. 시스템을 설계하고 -> 2. 도메인을 모델링(정보와 책임을 사전에 조사)하고 -> 3. 책임을 정의하고 할당한다.

즉, 사전에 이미 정보와 책임을 알고 있기 때문에 코드에서 책임을 어떤 객체에게 할당시킬지 결정할 수 있습니다.
```kotlin
// 1. 시스템 설계
// "셀러가 외부 스토어(11번가, 네이버 등)를 연동할 수 있어야 한다"

// 2. 도메인 모델링 (사전 조사)
// - Store 개념 존재: id, name, storeType
// - Store는 "인증 정보"를 가지고 있음
// - Store는 "인증하는 방법"을 알고 있어야 함
// - 11번가는 API Key 방식, 네이버는 OAuth 방식
// - Store는 "상품 목록"을 가지고 있음
// - Store는 "상품을 동기화하는 방법"을 알고 있어야 함

// 3. 책임 정의 및 할당 (코드 작성)
class Store(
    val id: Long,
    val name: String,
    private val storeType: StoreType,
    private val authInfo: AuthenticationInfo  // 사전에 파악한 정보
) {
    // Store가 "인증 정보"를 가지고 있으므로
    // "인증한다"는 책임을 Store에게 할당
    fun authenticate(): AuthToken {
        return when (storeType) {
            StoreType.ST11 -> authenticateWithApiKey()
            StoreType.NAVER -> authenticateWithOAuth()
            StoreType.KAKAO -> authenticateWithOAuth()
        }
    }

    // Store가 "상품 목록"을 알고 있으므로
    // "상품을 가져온다"는 책임을 Store에게 할당
    fun fetchProducts(token: AuthToken): List<Product> {
        return when (storeType) {
            StoreType.ST11 -> fetch11stProducts(token)
            StoreType.NAVER -> fetchNaverProducts(token)
            StoreType.KAKAO -> fetchKakaoProducts(token)
        }
    }

    private fun authenticateWithApiKey(): AuthToken {
        // authInfo에서 API Key를 꺼내 인증
        return AuthToken("Bearer ${authInfo.apiKey}")
    }

    private fun authenticateWithOAuth(): AuthToken {
        // authInfo에서 OAuth 정보를 꺼내 인증
        val code = requestAuthCode(authInfo.clientId)
        return requestAccessToken(code, authInfo.clientSecret)
    }
}
```

---

### 3. 유연한 흐름 만들기

#### 인터페이스와 구현의 분리

"물건을 산다"는 **책임**은 동일하지만, "1000원 이상일 때"라는 **조건**은 변할 수 있습니다. 변하는 것과 변하지 않는 것을 분리해야 합니다.

* **변하지 않는 것:** 전체적인 흐름 (Template)
* **변하는 것:** 구체적인 로직 (Strategy)

#### 상속(Inheritance) vs 합성(Composition)

책임을 구현체로 캡슐화하는 두 가지 방법입니다.

| 구분 | 상속 (`is-a`) | 합성 (`has-a`) |
| --- | --- | --- |
| **특징** | 타입을 명확히 분류(Categorizing) | 기능을 조립(Assembling) |
| **장점** | 구조가 명확하고 타입 체크 용이 | 런타임에 동적으로 변경 가능, 결합도 낮음 |
| **선택** | `Sealed class`로 타입 확정 시 유리 | 기능의 조합이 다양할 때 유리 |

```kotlin
// 상속 예시: 명확한 is-a 관계
sealed class StoreCredentials {
    abstract fun authenticate(): AuthToken
}

class ApiKeyCredentials(
    private val apiKey: String
) : StoreCredentials() {
    override fun authenticate(): AuthToken {
        return AuthToken("Bearer $apiKey")
    }
}

class OAuthCredentials(
    private val clientId: String,
    private val clientSecret: String
) : StoreCredentials() {
    override fun authenticate(): AuthToken {
        val code = requestAuthCode(clientId)
        return requestAccessToken(code, clientSecret)
    }
}

// 합성 예시: 행위를 조합
class Store(
    val id: Long,
    val name: String,
    private val authenticator: StoreAuthenticator,  // 합성
    private val productSync: ProductSynchronizer    // 합성
) {
    fun integrate() {
        val token = authenticator.authenticate()
        productSync.sync(token)
    }
}

// 다양한 조합 가능
val st11Store = Store(
    id = 1,
    name = "11번가",
    authenticator = ApiKeyAuthenticator("key123"),
    productSync = PollingProductSync()  // 폴링 방식
)

val naverStore = Store(
    id = 2,
    name = "네이버",
    authenticator = OAuthAuthenticator("client", "secret"),
    productSync = WebhookProductSync()  // 웹훅 방식
)
```

---

### Key Takeaways

* **협력의 일관성:** 구현이 아닌 인터페이스에 의존하여 협력 흐름(Pattern)을 고정시켜라.
* **책임 주도 설계:** 데이터를 먼저 정의하지 말고, "누가 이 행동을 가장 잘할 수 있는가?"를 먼저 고민해라.
* **캡슐화 전략:** 명확한 타입 계층이 필요하면 **상속**을, 유연한 행위 조합이 필요하면 **합성**을 사용해라.