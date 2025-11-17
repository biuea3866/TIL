# 의존성 관리하기
## 1. 의존성 이해하기
객체의 협력을 위해서는 객체 간의 의존성이 생김을 의미한다.   
그리고 이 의존성은 실행 시점(런타임), 구현 시점(컴파일)으로 나뉘게 된다.   
단방향으로 흐르는 의존성은 변경 시 의존하고 있는 코드에도 전파된다.  

이는 의존성 전이이며, 얼마나 여파를 미칠지는 변경의 방향과 캡슐화의 정도에 따라 달라진다.

```kotlin
// JPA 레포지토리 직접 의존
class Product(
    val id: Long
) {
    lateinit var productImageRepository: ProductJpaImageRepository
    
    fun fetchProductImage(): ProductImage {
        return this.productImageRepository.findAllByProductId(this.id)
    }
}

// Repository 인터페이스 의존
class Product(
    val id: Long
) {
    lateinit var productImageRepository: ProductImageRepository

    fun fetchProductImage(): ProductImage {
        return this.productImageRepository.fetchBy(this.id)
    }
}
```
이와 같이 실제 구현체를 의존하느냐, 구현을 캡슐화시킨 인터페이스를 의존하느냐에 따라서 변경의 여파가 달라진다.   
만약 위 예시에서 JPA 레포지토리가 아닌 S3로 변경하게 될 경우 productImageRepository부터 시작해서 이를 참조하고 있는 모든 메서드에 영향을 미치게 된다.   
하지만 인터페이스를 의존하고 있는 케이스는 언제든지 외부에서 구현체를 달리 주입해주면, 유연하게 이를 사용할 수 있다.

이렇게 런타임은 애플리케이션이 실행되고, 외부에서 분기에 의한 주입, 직접적인 주입 등 다양한 시점에 클라이언트가 원하는 객체를 주입하고 이를 활용하기 때문에 유연하다.  
그리고 컴파일은 코드 관점에서 어떤 인터페이스를 의존할 것인지, 어떤 추상 클래스를 의존할 것인지에 대한 문제이고, 어떤 객체인지는 전혀 알지 못한다.   
즉, 컴파일과 런타임 시점에서의 의존성은 확연한 차이가 있다.

### 컨텍스트 독립성
구체적인 클래스(ProductJpaImageRepository)에 의존한다는 것은 특정 컨텍스트에 강하게 결합됨을 의미한다.   
이러한 의존성은 구체적인 정보를 많이 알고 있기 때문에 다른 문맥(S3)으로 변경에 유연하게 대응하기가 어렵다.

```kotlin
class Product(
    val id: Long
) {
    // 만약 성능 향상을 위해 Cache 의존성을 추가해야한다면?
    lateinit var productImageRepository: ProductJpaImageRepository
    
    fun setProductImageRepository(productImageRepository: ProductJpaImageRepository) {
        this.productImageRepository = productImageRepository
    }
    
    fun fetchProductImage(): ProductImage {
        return this.productImageRepository.findAllByProductId(this.id)
    }
}
```

## 2. 유연한 설계
객체지향은 객체들이 서로 협력하는 것이고, 협력을 위해서는 서로의 존재와 수행 가능한 책임을 알아야 한다.   
사실 의존성이 존재한다는 것은 나쁘지 않다. 객체들이 서로 협력해야할 응집도가 올라가기 때문이다.   
다만, 응집도와 별개로 그 정도가 구체적일수록 재사용성이 떨어지게 된다.

```kotlin
class Product(
    val id: Long
) {
    // 만약 성능 향상을 위해 Cache 의존성을 추가해야한다면?
    // 만약 S3에서 presigned url을 가져와야한다면?
    lateinit var productImageRepository: ProductJpaImageRepository

    fun setProductImageRepository(productImageRepository: ProductJpaImageRepository) {
        this.productImageRepository = productImageRepository
    }

    fun fetchProductImage(): ProductImage {
        return this.productImageRepository.findAllByProductId(this.id)
    }
}

---

class Product(val id: Long) {
    lateinit var productImageRepository: ProductImageRepository

    fun setProductImageRepository(productImageRepository: ProductImageRepository) {
        this.productImageRepository = productImageRepository
    }

    fun fetchProductImage(): List<ProductImage> {
        return this.productImageRepository.findAllByProductId(this.id)
    }
}

interface ProductImageRepository {
    fun fetchAllBy(productId: Long): List<ProductImage>
}

interface ProductImageJpaRepository : JpaRepository<ProductImage, Long>, ProductImageRepository

class ProductImageS3Repository(private val s3Client: S3Client) : ProductImageRepository {
    override fun fetchAllBy(productId: Long): List<ProductImage> {}
}

class ProductImageCacheRepository(private val cacheManager: CacheManager) : ProductImageRepository {
    override fun fetchAllBy(productId: Long): List<ProductImage> {}
}

fun main() {
    val product = Product(0L).setProductImageRepository(ProductImageJpaRepository)
    val jpaImages = product.fetchProducmtImage()
    
    product.setProductImageRepository(ProductImageS3Repository())
    val s3Images = product.fetchProductImage()
}
```
예시와 같이 재사용성이 쉬운 의존성을 가진다면, 언제든지 구현체를 변경함으로써 유연하게 원하는 정보를 가져올 수 있다.   
이렇듯 객체가 알고있어야 할 지식이 낮아지고, 추상화에 의존함으로써 낮은 결합도를 유지할 수 있다.   
그리고 다른 실행 컨텍스트를 추가해야한다면 추상화를 상속받아 새로운 구현체를 만들고, 언제든지 클라이언트가 구현체 주입을 수정할 수 있다.   

객체는 언제나 변경이 가능하고, 어떤 객체와 협력하느냐에 따라 행동이 달라진다.   
따라서 항상 재사용이 가능하게 객체를 설계해야하고, 이러한 설계는 애플리케이션의 기능을 쉽게 확장시킬 수 있다.