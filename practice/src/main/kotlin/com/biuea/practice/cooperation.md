# 일관성 있는 협력

---

## 객체지향의 목표
객체지향은 재사용이 가능한 각자의 책임이 존재하는 객체들을 모으고, 서로 협력하게 한다.   
만약 구현으로만 구성된 객체들을 서로 협력시키게 한다면 어떻게 될까? 아마 추가적인 요구사항이 생길 경우 패턴과 일관성이 무너질 것이다.

```kotlin
// 구현으로만 구성
class SellerIntegrationService {
    fun integrateWith11st(apiKey: String) {
        // 11번가 연동 로직
        val token = get11stToken(apiKey)
        val products = fetch11stProducts(token)
        saveProducts(products)
    }

    fun integrateWithNaver(clientId: String, clientSecret: String) {
        // 네이버 연동 로직 (완전히 다른 흐름)
        val oauthToken = getNaverOAuthToken(clientId, clientSecret)
        val items = fetchNaverItems(oauthToken)
        saveProducts(items)
    }

    // 쿠팡, 카카오... 계속 추가되면서 패턴이 무너짐
}

// 재사용 가능한 협력 패턴
class SellerIntegrationService(
    private val authenticator: StoreAuthenticator,
    private val productFetcher: ProductFetcher,
    private val productRepository: ProductRepository
) {
    fun integrate() {
        val credentials = authenticator.authenticate()   // 흐름 1: 인증
        val products = productFetcher.fetch(credentials) // 흐름 2: 조회
        productRepository.saveAll(products)              // 흐름 3: 저장
    }
}
```

예시처럼 객체지향은 재사용 가능한 코드를 모아 패턴을 만들고, 시스템을 이해하기 쉽게, 확장되기 쉽게 하는 것을 목표로 한다.    
코드의 가독성이 높아지고 흐름을 이해하기 쉽다는 것은 유지보수와 온보딩에 큰 시간을 쏟지 않고 이해도를 높일 수 있게 한다.

## 객체의 책임
객체지향은 각자의 책임을 가진 객체들을 모아 협력을 하는데, 이 책임을 어떻게 정할지부터 고민을 해야한다.   
주로 정보를 가장 잘 알고있는 객체에게 책임을 할당시키고, 책임을 수행하게 한다.   
그러면 정보를 가장 잘 아는 객체이므로 정보를 먼저 정의하는 것이 옳은 것인지, 아니면 책임을 먼저 할당시키는 것이 좋은지 고민하게 된다.    
다음과 같은 순서로 객체에게 책임을 할당한다.

1. 시스템을 설계한다.
2. 시스템을 동작시키기 위한 책임들의 흐름을 구성하게 된다.
3. 이 책임들을 수행하기 위한 정보는 무엇이 필요할지 정의한다. (이 시점엔 도메인 모델링을 통한 정보들이 존재하는 상황)
4. 책임과 정보에 어울리는 도메인 모델(객체)를 탐색하고, 이를 할당한다. 만약 존재하지 않는다면 새로운 객체를 만들어 할당한다.
5. 만약 책임이 과하거나, 책임과 정보에 모순이 생긴다면 2 ~ 4 과정을 반복한다.

정보 전문가 패턴이란 것은 정보(데이터)를 가장 잘 아는 객체에게 책임을 할당한다. 라는 패턴인데, 객체지향에서는 정보를 먼저 정의하지말고, 책임을 먼저 정의하는 것이라고 한다.   
객체의 퍼블릭 인터페이스를 먼저 만들고, 이 인터페이스를 수행하기 위한 정보를 할당하라는 의미이다.   
모순이 존재하는 것 같지만, 도메인 모델링(사전 정보, 책임 정의)이 이 모순을 보완해주는 것 같다.   
사전에 이미 정의해놓은 것이 있기 때문에 책임을 정의할 수 있고, 정보를 뒤따라서 정의해줄 수 있는 의미로 추측된다.

```kotlin
// 정보만 있고 책임이 없는 객체
data class Store(
    val id: Long,
    val name: String,
    val storeType: String,  // "11ST", "NAVER", "KAKAO"
    val apiKey: String?,
    val clientId: String?,
    val clientSecret: String?
)

// 외부에서 Store의 정보를 꺼내 사용
class IntegrationService {
    fun integrate(store: Store) {
        when (store.storeType) {
            "11ST" -> authenticate11st(store.apiKey!!)
            "NAVER" -> authenticateNaver(store.clientId!!, store.clientSecret!!)
            // Store의 내부 정보를 외부에서 직접 사용
        }
    }
}

---
// 정보를 가장 잘 아는 객체가 책임을 수행
class Store(
    val id: Long,
    val name: String,
    private val credentials: StoreCredentials  // 인증 정보를 가장 잘 아는 StoreCredentials에게 인증 책임 할당
) {
    // Store가 자신의 인증 방법을 가장 잘 알고 있음
    fun authenticate(): AuthToken {
        return credentials.authenticate()
    }

    // Store가 자신이 어떤 타입인지 가장 잘 알고 있음
    fun isExternalStore(): Boolean {
        return credentials.isType(ExternalStoreCredentials::class)
    }
}

// 사용처
class IntegrationService {
    fun integrate(store: Store) {
        val token = store.authenticate()  // Store에게 책임을 위임
        // ...
    }
}
```

# 패턴, 흐름 만들기

---

## 인터페이스와 구현

객체들의 책임들을 배치하여 흐름을 만들고 이를 패턴화시킬 수 있다.   
이 책임들 중에는 간단하게 산다와 같은 책임도 있고, 유사하지만 구현이 다른 책임들이 존재한다.    
가령 물건을 탐색한다. -> (1000원 이상이면 물건을 산다. or 2000원 이상이면 물건을 산다.) 와 같이 물건을 산다라는 책임은 동일하지만 구현이 다른 경우가 존재한다.    
만약 물건을 탐색한다. -> 1000원 이상이면 물건을 산다. 로 흐름을 고정하게 된다면 어떻게 될까? 2000원 이상이면 물건을 산다. 라는 흐름을 위한 중복되는 코드를 만들게 되고 패턴화시킬 수 가 없게 된다.    
이를 방지하기 위해 물건을 산다.(인터페이스), ~이상이면 이라는 구현부를 나누고, 인터페이스에 흐름을 의존시킴으로써 패턴화시킬 수 있다.   

```kotlin
// 구현에 의존하면 패턴화 불가능
class IntegrationService {
    fun integrate11st(store: Store) {
        val token = authenticate11stWithApiKey(store.apiKey)
        val products = fetch11stProducts(token)
        save(products)
    }

    fun integrateNaver(store: Store) {
        val token = authenticateNaverWithOAuth(store.clientId, store.clientSecret)
        val products = fetchNaverProducts(token)
        save(products)
    }
    // 중복되는 흐름, 패턴화 불가능
}

// 인터페이스로 패턴화
interface StoreAuthenticator {
    fun authenticate(): AuthToken
}

interface ProductFetcher {
    fun fetch(token: AuthToken): List<Product>
}

// 구현체들
class ApiKeyAuthenticator(
    private val apiKey: String
) : StoreAuthenticator {
    override fun authenticate(): AuthToken {
        // 11번가, 토스 스토어 방식
        return AuthToken("Bearer $apiKey")
    }
}

class OAuthAuthenticator(
    private val clientId: String,
    private val clientSecret: String
) : StoreAuthenticator {
    override fun authenticate(): AuthToken {
        // 네이버, 카카오 방식
        val code = requestAuthCode(clientId)
        return requestAccessToken(code, clientSecret)
    }
}

// 통일된 패턴!
class IntegrationService(
    private val authenticator: StoreAuthenticator,
    private val fetcher: ProductFetcher
) {
    fun integrate() {
        val token = authenticator.authenticate()  // 어떤 구현체든 상관없음
        val products = fetcher.fetch(token)
        save(products)
    }
}
```

## 캡슐화
인터페이스를 이용해 언제든지 구현체를 변경할 수 있고, 코드의 흐름을 변경시킬 필요가 없게 되었다.    
이렇게 중복 코드를 줄이고, 안정적인 인터페이스 뒤에 구현체를 위치시킴(캡슐화)으로써 코드의 수정에 대한 위험도가 줄어든다.    
이를 구현하는 방법은 주로 상속과 합성이 존재한다.    
상속을 이용하면 타입을 카테고라이징화시킬 수 있으나 자칫 미묘하게 다른 구현이 생겨버린다면, 변경이 수반될 수도 있다. 하지만 타입을 확정(is-a)시킬 수 있다면 좋은 선택지이다.   
그리고 명확히 타입을 카테고라이징화시킬 수 없다면 합성을 사용할 수 있다. 책임 혹은 정보를 인터페이스화시켜 이 인터페이스를 기반으로 객체 간 소통하는 방식이다.   

```kotlin
// 상속 예시
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

// 합성 예시
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

## 협력

앞서 인터페이스를 이용해 흐름을 만들고, 추가 조건이 생긴다면 구현을 만들어 흐름의 변경 없이 협력을 가능하게 만들 수 있었다.     
이렇듯 유사해보이는 흐름들이 반복된다면, 인터페이스와 구현을 통해 변경과 확장에 강한 코드를 만들 수 있다.