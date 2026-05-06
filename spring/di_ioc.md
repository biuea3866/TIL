# [Spring] DI, IoC

## 의존성

---

### 의존성있는 코드의 문제

소프트웨어에서 의존성은 한 클래스가 다른 클래스를 직접 참조하는 관계를 칭한다.

```kotlin
class OrderService {
	private val paymentService = KakaoPay()
    
    fun order(item: String) {
    	paymentService.pay(item)
    }
}
```

의존성이 있다는 것은 다른 인스턴스로의 변경을 위해선 코드의 변경이 불가피하다. 가령 NaverPay로 모든 클래스에서 의존관계를 변경해야한다면, KakaoPay를 참조하고있는 모든 코드를 변경하게 된다.

또한, 구체 클래스에 대한 의존은 새로운 변경에 취약하다. 위의 예시처럼 KakaoPay에 대한 의존은 다른 결제수단으로의 변경을 어렵게 만들며, 변경한다하더라도 새로운 결제수단으로의 변경 또한 어렵게 만든다.

## 의존성 주입

---

### 의존성을 해소하기 위한 스프링의 주입 방법

스프링에서는 이런 의존성 문제를 해소하기 위해 인터페이스와 상속 그리고 외부에서 의존성 주입하는 방법을 제공한다.

```kotlin
interface PaymentService {
	fun pay(item: String)
}

@Component
class KakaoPay: PaymentService {
	override fun pay(item: STring) { ... }
}

@Component
class NaverPay: PaymentService {
	override fun pay(item: STring) { ... }
}
```

PaymentService라는 인터페이스를 정의하고, 하위 구체 클래스들을 구현한다. 그리고 의존성의 방향을 인터페이스로 흐르게 한다. 이렇게 된다면, 인터페이스라는 껍데기만 참조하고 있기 때문에 외부에서 구현체만 찾아서 의존성 주입을 해주면 유연하게 기능을 변경할 수 있다.

#### 1. 생성자 주입

```kotlin
@Service
class OrderService(
	private val paymentService: PaymentService
) {
	fun order(item: String) {
    	paymentService.pay(item)
    }
}
```

가장 자연스러운 의존성 주입 방법이다. ApplicationContext에서 최하위 의존성부터 시작해서 OrderService에 의존성을 주입해주고, 빈으로 관리된다. 즉, 처음부터 끝까지 개발자는 OrderService를 생성하기 위해 어떤 객체 생성도 할 필요가 없다는 것을 의미한다.

#### 2. 세터 주입

```kotlin
@Service
class OrderService {
	private lateinit var paymentService: PaymentService
    
    @Autowired
    fun setPaymentService(paymentService: PaymentService) {
    	this.paymentService = paymentService
    }
    
	fun order(item: String) {
    	paymentService.pay(item)
    }
}
```

세터를 통해 의존성을 주입하는 방법이다. 마찬가지로 ApplicationContext에서 의존성을 관리해주지만 setter가 public하게 열려있기 때문에 언제든지 임의로 구현체를 변경할 수 있다는 점이 존재한다.

#### 3. 필드 주입

```kotlin
@Service
class OrderService {
    @Autowired
	private lateinit var paymentService: PaymentService
        
	fun order(item: String) {
    	paymentService.pay(item)
    }
}
```

필드로 의존성을 관리하기 때문에 목 객체를 구성하고, 주입하기 어렵다는 단점이 존재한다.

## 제어의 역전

---

의존성 주입 예시를 통해서 Spring에서 어떻게 인스턴스에 의존성을 주입하는지에 대한 방법을 알아보았다. @Autowired 마킹은 인스턴스 구성을 위해서 어떤 의존성이 필요한지 명시해준다. 그리고 이를 기반으로 실제 인스턴스 생성을 개발자가 하지않고, 프레임워크에서 해주며 객체의 생명주기까지 관리해준다.

이를 제어의 역전이라고한다. 만약 제어권이 개발자에게 있었다면 서비스 인스턴스를 생성하기 위해 의존성을 가진 모든 인스턴스를 생성하고, 생성자든 세터든 주입을 해주어야 했을 것이다. 하지만 프레임워크가 객체의 생명주기를 통제함으로써 개발자는 비즈니스 코드에만 집중할 수 있게 된다.

### 제어의 역전 장점

#### 1. 인터페이스 의존

인터페이스를 의존시키고, 프레임워크에서 구현체를 주입해줌으로써 인스턴스 간의 의사소통은 오로지 인터페이스로만 이루어진다. 즉, 필요에 따라 구현체를 변경시켜도 의사소통을 구성하는 코드를 변경할 필요가 없다는 것을 의미한다.

```kotlin
@Primary
@Component
class TossPay : PaymentService {
    override fun pay(item: String) { ... }
}

@Component
class NaverPay: PaymentService {
    override fun pay(item: String) { ... }
}

@Service
class OrderService(
    private val paymentService: PaymentService  // NaverPay를 사용하고 싶다면, Primary 위치 변경
) {
    fun order(item: String) {
        paymentService.pay(item)
    }
}
```

#### 2. 테스트 용이성

Mock 객체가 쉽게 주입 가능해지고, 단위 테스트가 편해진다.

```kotlin
class OrderServiceTest {
	@Mock
    lateinit var paymentService: PaymentService = mockk<PaymentService>(relaxed = true)
    
    @InjectMocks
    lateinit var orderService: OrderService
    
    @Test
    fun `주문 테스트`() {
    	...
    }
}
```

#### 3. 관심사 분리

개발자는 서비스 인스턴스를 생성하기 위한 코드를 직접 작성하지 않아도 된다. 인스턴스 생성을 위한 의존성 그래프부터 시작해서 생명주기를 프레임워크에서 관리해주고 의존성을 주입해주기 때문이다. 이 때문에 프레임워크에서 생성해준 인스턴스를 이용하여 개발자는 비즈니스 코드에 집중할 수 있다는 장점이 있다.

## 스프링 컨텍스트의 의존성 관리

---

![](./img/di_ioc/di_ioc_1.png)

#### 1. ComponentScan

```kotlin
...
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    excludeFilters = {@Filter(
    type = FilterType.CUSTOM,
    classes = {TypeExcludeFilter.class}
), @Filter(
    type = FilterType.CUSTOM,
    classes = {AutoConfigurationExcludeFilter.class}
)}
)
public @interface SpringBootApplication {
    ...
}
```

스프링 부트 애플리케이션을 시작할 때, @SpringBootApplication 어노테이션이 존재하는데 내부에는 ComponentScan이라는 어노테이션이 붙어있다. 이 어노테이션은 명시된 패키지 경로를 스캔하며 Component가 붙어있는 클래스들을 검색하는 역할을 한다.

#### 2. BeanDefinition

```kotlin
protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
		for (String basePackage : basePackages) {
			Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
			for (BeanDefinition candidate : candidates) {
				ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
				candidate.setScope(scopeMetadata.getScopeName());
				String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
				if (candidate instanceof AbstractBeanDefinition abstractBeanDefinition) {
					postProcessBeanDefinition(abstractBeanDefinition, beanName);
				}
				if (candidate instanceof AnnotatedBeanDefinition annotatedBeanDefinition) {
					AnnotationConfigUtils.processCommonDefinitionAnnotations(annotatedBeanDefinition);
				}
				if (checkCandidate(beanName, candidate)) {
					BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
					definitionHolder =
							AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
					beanDefinitions.add(definitionHolder);
					registerBeanDefinition(definitionHolder, this.registry);
				}
			}
		}
		return beanDefinitions;
	}

---

@Override
	public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
			throws BeanDefinitionStoreException {

		Assert.hasText(beanName, "'beanName' must not be empty");
		Assert.notNull(beanDefinition, "BeanDefinition must not be null");
		this.beanDefinitionMap.put(beanName, beanDefinition);
	}
```

패키지를 순회하며, 탐색된 컴포넌트들은 BeanDefinitionHolder -> BeanDefinition으로 생성되어 beanDeifintionMap에 등록된다.

#### 3. 의존성 그래프 분석, 순서 결정

ApplicationContext가 동작하면서 빈을 생성하는 과정이 시작된다. 진행 과정 중에 registerBeanPostProcessor -> deGetBean 메서드를 호출하고, Bean 생성을 위한 의존성 정보 파악을 위해 DefaultSingletonBeanRegistry::registerDependentBean을 호출한다. 해당 메서드에서는 재귀적으로 getBean을 호출하며, 의존성 정보를 파악하고 등록한다.

#### 4. 빈 생성

의존성 그래프 분석이 끝나고나면, Bean 생성 로직으로 넘어간다. (doGetBean 메서드에서 함께 동작) createBean 메서드를 호출하며, 최종적으로는 리플렉션(ConstructAccessor)을 이용하여 인스턴스를 생성한다.

인스턴스를 생성하기 위해선 new 키워드를 이용하여 의존성 그래프를 가진 인스턴스를 생성하고, 생성자 파라미터로 넘겨야 한다. 하지만 스프링 빈은 런타임에 인스턴스를 만들어야 하므로 전자와 같은 방법으로 객체를 생성하긴 어렵다. 따라서 파라미터 정보와 클래스 정보만으로 객체를 생성해줄 수 있는 리플렉션을 이용하고, 이것을 해주는 장치가 ConstructorAccessor이다.
