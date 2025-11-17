# [객체지향] 유연한 설계를 위한 의존성 관리: 추상화에 의존하기

- **Tags:** #ObjectOriented #OOP #DependencyInversion #DesignPrinciples #Coupling

---

### 무엇을 배웠는가?
객체지향 설계에서 협력을 위해 **의존성**은 필수적이지만, 그 의존성이 구체적인 구현체(Concrete Class)가 아닌 추상화된 인터페이스(Interface)를 향해야 한다는 것을 배웠습니다.    
또한, 코드의 **컴파일 시점 의존성**과 **런타임 시점 의존성**을 분리하는 것이 왜 중요한지 이해했습니다.

---

### 왜 중요하고, 어떤 맥락인가?
만약 `Product` 객체가 `ProductJpaImageRepository`라는 **구체적인 클래스**에 직접 의존하면, 두 객체는 강하게 결합(Coupling)됩니다.    
이 상태는 `Product`가 'JPA'라는 **특정 컨텍스트에 종속**됨을 의미합니다.

이 설계의 문제는 **변경에 취약하다**는 것입니다. 만약 "JPA가 아니라 S3에서 이미지를 가져와야 한다"는 요구사항이 생기면, `ProductJpaImageRepository`를 참조하는 `Product` 클래스의 내부 코드를 직접 수정해야 합니다.

반면, `Product`가 `ProductImageRepository`라는 **인터페이스**에 의존한다면, `Product`는 "어떻게" 이미지를 가져오는지 알 필요가 없습니다.    
런타임에 `ProductImageS3Repository`나 `ProductImageCacheRepository` 같은 다른 구현체를 주입(교체)하기만 하면 되므로, **컨텍스트에 독립적**이고 **유연하며 재사용성 높은** 설계를 만들 수 있습니다.

---

### 상세 내용

#### 1. 컴파일타임 의존성 vs. 런타임 의존성
객체 간의 의존성은 코드(컴파일) 시점과 실행(런타임) 시점으로 나뉩니다.

* **컴파일타임 의존성**
    * 클래스가 어떤 인터페이스, 추상 클래스에 의존하고 있는지 코드 수준에서의 의존성입니다.
    * 추상화에 대한 의존이므로 실제 런타임에 어떤 인스턴스가 매핑되는지 알 순 없지만, 이 추상화를 이용하여 객체 간의 협력을 만듭니다.
    * `Product` 클래스 코드 내에는 `ProductImageRepository`라는 인터페이스만 존재합니다.
    * `Product`는 `ProductImageRepository`가 `JpaRepository`인지 `S3Repository`인지 **알지 못합니다.**

* **런타임 의존성**
    * 위의 추상화에 실제 객체가 연결되고, 이를 이용하여 어떻게 이미지를 가져올지 방법을 결정합니다. 
    * 실제 어떤 구현체가 사용될지는 애플리케이션이 실행되는 시점(런타임)에 주입을 통해 결정됩니다.

#### 2. 유연한 설계를 위한 추상화
재사용성이 높은 코드를 만들기 위해서는 객체가 구체적인 지식을 적게 갖도록 하고, 추상화에 의존하여 결합도를 낮춰야 합니다.

```kotlin
// 1. 추상화 (인터페이스) 정의
interface ProductImageRepository {
    fun fetchAllBy(productId: Long): List<ProductImage>
}

// 2. 다양한 컨텍스트에 맞는 구현체
class ProductImageJpaRepository(...) : ProductImageRepository { ... }
class ProductImageS3Repository(...) : ProductImageRepository { ... }
class ProductImageCacheRepository(...) : ProductImageRepository { ... }

// 3. 클라이언트는 인터페이스에만 의존
class Product(val id: Long) {
    lateinit var productImageRepository: ProductImageRepository

    fun setProductImageRepository(productImageRepository: ProductImageRepository) {
        this.productImageRepository = productImageRepository
    }

    fun fetchProductImage(): List<ProductImage> {
        // 내부 구현이 JPA든 S3든 상관없이 동일한 메시지 호출
        return this.productImageRepository.fetchAllBy(this.id)
    }
}

// 4. 런타임 구현체 교체
fun main() {
    val product = Product(0L).setProductImageRepository(ProductImageJpaRepository)
    val jpaImages = product.fetchProducmtImage()

    product.setProductImageRepository(ProductImageS3Repository())
    val s3Images = product.fetchProductImage()
}
```

이 구조에서 Product는 컨텍스트 독립성을 갖게 됩니다.    
새로운 요구사항(e.g., Cache)이 추가되어도 ProductImageCacheRepository라는 새 구현체를 만들고 런타임에 주입만 변경하면 되므로, Product 코드는 수정할 필요 없이 기능을 확장할 수 있습니다.

### 핵심
객체 간의 협력을 위한 의존성은 필수적이지만, 의존성의 정도가 구체적일수록 재사용성은 떨어집니다.   
좋은 객체지향 설계는 컴파일 시점에는 안정적인 인터페이스에 의존하게 하고, 런타임 시점에 구체적인 구현체를 주입하여 유연성을 확보하는 것입니다.   
특정 컨텍스트에 의존하지 말고, 추상적인 **역할(인터페이스)**에 의존해야 합니다.****