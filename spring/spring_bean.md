# 스프링 빈

## 스프링 빈

스프링 컨테이너에 의해 관리되는 싱글톤 객체를 의미한다

## 스프링 빈의 역할

* 스프링 컨테이너가 객체를 관리해주어 스프링이 의존성을 관리해준다
    * 의존성 관리라 함은 개발자가 어떤 서비스 인스턴스를 만들 때 일일이 객체를 생성하여 생성자 파라미터에 주입해주지 않아도 됨을 의미한다
* 빈의 생명주기를 관리 해주고, 콜백 메서드를 통해 특정 작업을 수행할 수 있다
    * component와 같은 클래스 형태라면 `@PostConstruct`(초기화), `@PreDestroy`(소멸)
    * bean과 같은 함수 형태라면 `initMethod`, `destroyMethod`
    * 인터페이스 상속하면 `InitializingBean`, `DisposableBean`

## 빈 스코프

* **싱글톤**: 기본 스코프이고, 스프링 컨테이너에서 하나만 존재하며, 컨테이너가 사라질 때 제거됨
* **프로토타입**: 사용될 때마다 새로운 인스턴스가 생성되고, 의존 관계가 주입되고 관리되지 않는다
* **웹스코프**
    * request: Http 요청이 들어올 때마다 빈을 생성
    * session: 요청에서 sessionId를 읽고, 로컬에 저장되어있는 세션을 찾아 빈을 생성
    * application: 웹 애플리케이션 단위로 빈을 생성
    * websocket: 연결된 소켓 단위로 빈을 생성

## 빈 등록과정

1. ApplicationContext 로드
2. AbstractApplicationContext.refresh 함수 실행
3. `invokeBeanFactoryPostProcessors` 메서드 수행
    1. 컴포넌트들을 스캔하고, BeanDefinition 생성
    2. 생성된 BeanDefinition들을 beanDefinitionMap에 저장
4. `finishBeanFactoryInitialization` 메서드 수행
    1. BeanDefinition들을 가져오고, 재귀적으로 의존성 생성 후 의존성 주입
    2. 예시:
       ```
       UserController
       ㄴ UserService
         ㄴ UserRepository
       ㄴ ApplicantService
         ㄴ ApplicantRepository

       1. getBean('userController')
       2. UserController의 생성자 변수 중 autowired가 걸린 변수 확인 (UserService, ApplicantService)
       3. getBean('userService')
       4. UserService 생성자 변수 중 autowired가 걸린 변수 확인 (UserRepository)
       5. UserRepository 인스턴스 생성 후 UserService 주입
       6. getBean('applicantService')
       7. ApplicantService 생성자 변수 중 autowired가 걸린 변수 확인 (ApplicantRepository)
       8. ApplicantRepository 인스턴스 생성 후 ApplicantService 주입
       9. UserService, ApplicantService 주입 후 UserController 인스턴스 생성
       ```
    3. 내부 호출 흐름:
       - `finishBeanFactoryInitialization`
       - `beanFactory.preInstantiateSingletons(DefaultListableBeanFactory::preInstantiateSingletons)`
       - `AbstractBeanFactory::getBean`
       - `AbstractBeanFactory::doGetBean`
       - `AbstractBeanFactory::createBean(AbstractAutowireCapableBeanFactory::createBean)`
       - `AbstractAutowireCapableBeanFactory::doCreateBean`
       - `AbstractAutowireCapableBeanFactory::instantiateBean`
       - `InstantiationStrategy::instantiate(SimpleInstantiationStrategy::instantiate)`
       - `BeanUtils::instantiateClass`
       - `Constructor::newInstance`
       - `Constructor::newInstanceWithCaller`
       - `ConstructorAccessor::newInstance(NativeConstructorAccessorImpl::newInstance)`
       - `NativeConstructorAccessorImpl::newInstance0`
         - jvm내부의 기능을 호출하여 객체를 생성한다
         - 런타임에 클래스와 아큐먼트 정보를 jvm에게 넘겨 객체를 생성토록 함
         - new 키워드는 컴파일 타임에 클래스 정보와 생성자 기반으로 바이트 코드를 미리 생성하여 준비하고 있음

## 어노테이션

### @Configuration

@Bean을 등록할 수 있게 마커 역할을 하는 어노테이션이다

내부적으로는 Component 어노테이션이 붙어있어, 싱글톤 빈으로의 역할이 가능하며, Configuration 어노테이션으로의 역할을 클래스 내부의 Bean이 붙어있는 메서드들을 Bean으로 등록할 수 있게 해준다

### @Bean

특정 라이브러리에 구현체를 주입해주어 원하는 기능을 수동으로 사용하고자 할 때 사용된다

가령 `LazyConnectionDataSourceProxy`를 Bean으로 등록하여 DataSource가 `LazyConnectionDataSourceProxy`로 동작하게 해줌

### @Component

컴포넌트 스캔을 이용하여 자동으로 빈으로 등록되게 해주는 어노테이션

### @Controller

컨트롤러를 나타내는 마커 역할을 하며, 컴포넌트 어노테이션이 붙어있다

HandlerMapping이 Controller들을 스캔하여 `url : 메서드`의 셋을 가진 라우팅 맵을 구성한다

### @RestController

Controller, ResponseBody 어노테이션이 붙어있어 Controller 어노테이션의 역할, 자동으로 Json을 응답해준다

* ResponseBody
    * 메서드의 반환 값을 HTTP 응답 본문에 쓰겠다는 의미
        * 응답 본문을 어떻게 쓸 것인가?
            * HttpMessageConverter를 상속받은 여러 구현체 중 하나를 선택
            * Json 응답이 보편적으로 쓰이고, 클라이언트가 Accept: application/json으로 요청을 보내기 때문에 MappingJackson2HttpMessageConverter를 이용하여 응답 본문을 작성한다
        * MappingJackson2XmlHttpMessageConverter를 빈으로 등록하면 xml로 반환 가능
    * Controller어노테이션이 붙어있고, ResponseBody가 안붙어있다면?
        * 뷰 이름을 반환하고, 뷰 리졸버는 html, jsp, thymeleaf 등 파일을 찾아 렌더링을 한다

### @Service

내부적으로 컴포넌트 어노테이션이 붙어있고, 단순 마커 정도의 역할을 하지만 AOP를 이용하여 부가 기능을 부여할 수 있다

### @Repository

내부적으로 컴포넌트 어노테이션이 붙어있고, 데이터베이스(mysql, oracle, postgresql...)의 예외를 DataAccessException(스프링에서 정의한 예외 표준)하위로 오류를 컨버팅한다

* JpaRepository 인터페이스는 왜 DataAccessException를 반환할 수 있지?
    * JpaRepository를 상속하는 구현체에서 DataAccessException 구조로 예외를 반환

## 기본으로 등록되는 빈

### ApplicationContext

* **SpringBootApplication**
    * 애플리케이션 시작점
    * ApplicationContext 생성
* **ApplicationContext** (AnnotationConfigApplicationContext)
    * 빈 생성, 의존성 주입, 라이프사이클 관리
    * BeanFactory의 확장된 기능
    * 이벤트 발행, 메시지 처리, 리소스 로딩 등
* **BeanFactory**
    * BeanDefinition 저장소
    * 빈 인스턴스 생성 및 캐싱
* **Environment**
    * application.yml, 시스템 변수, 환경 변수 등을 통합 관리
    * 활성 프로필 관리
* **PropertySourcesPlaceholderConfigurer**
    * `@Value`와 같은 플레이스 홀더 값 주입
    * Environment에서 실제 값을 찾아서 플레이스 홀더 대체
* **ConfigurationClassPostProcessor**
    * Configuration 어노테이션이 붙은 클래스를 감지하여, Bean 메서드를 찾아 BeanDefinition 생성
    * ComponentScan 처리
* **AutowiredAnnotationBeanPostProcessor**
    * Autowired 필드, 메서드, 생성자 주입

### Configuration

* **ConfigurationPropertiesBindingPostProcessor**
    * `@ConfigurationProperties`를 처리하여 application.yml의 값을 POJO 객체에 바인딩
* **ConfigFileApplicationListener**
    * 설정 파일 로딩(application.yml)
* **EnvironmentPostProcessor**
    * 설정 값 변환이나 검증 수행

### Event

* **ApplicationEventMulticaster** (SimpleApplicationEventMulticaster)
    * ApplicationEvent를 발행하여 `@EventListener`들에게 이벤트를 전달
    * 비동기 이벤트 처리 지원
* **ApplicationEventPublisher**
    * publishEvent 메서드를 제공하여, 스프링 이벤트 발행 제공

### LifeCycle

* **DefaultLifecycleProcessor**
    * 빈들의 라이프 사이클을 관리
    * LifeCycle 인터페이스를 구현한 빈들의 start, stop 메서드를 호출
* **ApplicationContextInitializer**
    * ApplicationContext 초기화
* **BeanPostProcessor**
    * 빈 후처리기
    * AOP 프록시 생성, 어노테이션 처리

### Condition

* **ConditionEvaluator**
    * `@ConditionalOnClass`, `@ConditionalOnBean` 등 처리
    * 위의 조건들을 이용하여 빈을 등록할수도, AutoConfiguration 활성화를 할수도있다
