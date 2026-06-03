# Spring AutoConfiguration

## AutoConfiguration이 풀려는 문제

스프링 부트 이전에는 DataSource, EntityManagerFactory, DispatcherServlet, JacksonHttpMessageConverter 같은 빈을 사용자가 직접 `@Bean`으로 등록해야 했다. AutoConfiguration은 "클래스패스에 무엇이 있는지, 사용자가 어떤 빈을 이미 만들었는지"를 보고 기본 인프라 빈을 알아서 등록해주는 메커니즘이다.

무조건 등록하는 게 아니라 `@ConditionalOn*` 으로 특정 상황일 때만 등록한다

## 어디서 시작되는가

```
@SpringBootApplication
  └─ @EnableAutoConfiguration
       └─ @Import(AutoConfigurationImportSelector.class)
            └─ ImportCandidates.load() → META-INF/spring/...AutoConfiguration.imports 읽기
                 └─ ConfigurationClassFilter (조건 평가)
                      └─ 살아남은 @AutoConfiguration 클래스들을 Configuration처럼 등록
```

`@EnableAutoConfiguration` 정의

```java
// org/springframework/boot/autoconfigure/EnableAutoConfiguration.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@AutoConfigurationPackage
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration { ... }
```

`@Import(AutoConfigurationImportSelector.class)`로 시작한다. `AutoConfigurationImportSelector`는 `DeferredImportSelector`를 구현해서 사용자가 작성한 `@Configuration` 클래스가 모두 처리된 후 후보 자동설정 클래스를 import한다.

## 자동설정 후보 클래스를 찾는 방법

`AutoConfigurationImportSelector::getCandidateConfigurations`

```java
// org/springframework/boot/autoconfigure/AutoConfigurationImportSelector.java
protected List<String> getCandidateConfigurations(AnnotationMetadata metadata,
        AnnotationAttributes attributes) {
    ImportCandidates importCandidates = ImportCandidates.load(
        this.autoConfigurationAnnotation, getBeanClassLoader());
    List<String> configurations = importCandidates.getCandidates();
    Assert.notEmpty(configurations,
        "No auto configuration classes found in META-INF/spring/"
            + this.autoConfigurationAnnotation.getName() + ".imports. ...");
    return configurations;
}
```

`ImportCandidates`는 클래스패스의 모든 JAR을 탐색하고, 다음 파일을 모은다.

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
```
org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
...
```

* 각 스타터(spring-boot-starter-data-jpa, spring-boot-starter-kafka 등)가 자기 JAR에 `.imports`를 포함시켜 후보를 등록한다.

## 등록 흐름

`AutoConfigurationImportSelector::getAutoConfigurationEntry`

```java
protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata metadata) {
    if (!isEnabled(metadata)) return EMPTY_ENTRY;
    AnnotationAttributes attributes = getAttributes(metadata);
    List<String> configurations = getCandidateConfigurations(metadata, attributes); // 1) .imports 로드
    configurations = removeDuplicates(configurations);
    Set<String> exclusions = getExclusions(metadata, attributes);
    checkExcludedClasses(configurations, exclusions);
    configurations.removeAll(exclusions);                                          // 2) exclude 처리
    configurations = getConfigurationClassFilter().filter(configurations);         // 3) 조건 평가
    fireAutoConfigurationImportEvents(configurations, exclusions);
    return new AutoConfigurationEntry(configurations, exclusions);
}
```

1. `.imports`에서 모든 후보 클래스를 로드
2. `@SpringBootApplication(exclude = ...)` 또는 `spring.autoconfigure.exclude` 로 제외
3. `ConfigurationClassFilter`가 `@ConditionalOnClass` 같은 조건을 파악 후 클래스 로딩 전에 제외하고, 통과한 후보만 실제 BeanDefinition으로 등록

## @AutoConfiguration vs @Configuration

```java
// org/springframework/boot/autoconfigure/AutoConfiguration.java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore
@AutoConfigureAfter
public @interface AutoConfiguration { ... }
```

> proxyBeanMethods란

`@Configuration` 클래스의 bean 어노테이션이 붙은 메서드들을 CGLib 프록시로 변환하여 싱글톤 객체로 반환할지에 대한 설정값이다.

* `true` : Configuration 클래스를 CGLib으로 감싼다. `@Bean` 메서드를 프록시 객체로 만들고, 싱글톤 객체로 생성한다.
* `false`: 프록시를 만들지 않는다. `@Bean` 메서드 호출 시 매번 새 객체로 생성하여 반환한다.

```java
@Configuration(proxyBeanMethods = false)
public class AppConfig {
    @Bean Repository repository() { return new Repository(); }

    // 내부에서 repository()를 직접 호출하지 말고, 파라미터로 받는다.
    @Bean ServiceA serviceA(Repository repo) { return new ServiceA(repo); }
    @Bean ServiceB serviceB(Repository repo) { return new ServiceB(repo); }
}
```

## 조건부 어노테이션 정리

| 어노테이션 | 의미 |
|---|---|
| `@ConditionalOnClass` | 해당 클래스가 클래스패스에 있을 때 |
| `@ConditionalOnMissingClass` | 없을 때 |
| `@ConditionalOnBean` | 컨테이너에 해당 빈이 이미 있을 때 |
| `@ConditionalOnMissingBean` | 해당 빈이 아직 없을 때 (= 사용자 빈 우선) |
| `@ConditionalOnProperty` | 프로퍼티 값이 일치할 때 |
| `@ConditionalOnWebApplication(type = SERVLET/REACTIVE)` | 웹 애플리케이션 타입 일치 |
| `@ConditionalOnSingleCandidate` | 해당 타입 빈이 정확히 1개 또는 `@Primary` 1개일 때 |
| `@Conditional(MyCondition.class)` | 임의의 `Condition` 구현체로 평가 |

## 사례 1: DataSourceAutoConfiguration

```java
// org/springframework/boot/autoconfigure/jdbc/DataSourceAutoConfiguration.java
@AutoConfiguration(before = SqlInitializationAutoConfiguration.class)
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })
@ConditionalOnMissingBean(type = "io.r2dbc.spi.ConnectionFactory")
@EnableConfigurationProperties(DataSourceProperties.class)
@Import({ DataSourcePoolMetadataProvidersConfiguration.class,
          DataSourceCheckpointRestoreConfiguration.class })
public class DataSourceAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @Conditional(EmbeddedDatabaseCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import(EmbeddedDataSourceConfiguration.class)
    protected static class EmbeddedDatabaseConfiguration { }

    @Configuration(proxyBeanMethods = false)
    @Conditional(PooledDataSourceCondition.class)
    @ConditionalOnMissingBean({ DataSource.class, XADataSource.class })
    @Import({ DataSourceConfiguration.Hikari.class,
              DataSourceConfiguration.Tomcat.class,
              DataSourceConfiguration.Dbcp2.class,
              DataSourceConfiguration.OracleUcp.class,
              DataSourceConfiguration.Generic.class,
              DataSourceJmxConfiguration.class })
    protected static class PooledDataSourceConfiguration { ... }
}
```

1. `DataSource` 클래스가 클래스패스에 없으면(=JDBC 안 씀) 사용 안함
2. R2DBC `ConnectionFactory` 빈이 이미 있으면(=Reactive 스택 사용) 사용 안함
3. 사용자가 `DataSource` 빈을 직접 등록했으면 두 내부 Configuration 모두 비활성
4. 사용자가 등록 안 했고 풀(Hikari 등)이 잡히면 `PooledDataSourceConfiguration` 활성 → Hikari가 클래스패스에 있으면 `DataSourceConfiguration.Hikari` 안의 `@Bean DataSource`가 등록됨
5. 풀이 없고 H2/HSQL/Derby 같은 임베디드 DB가 있으면 `EmbeddedDataSourceConfiguration`이 활성

`application.yml`의 `spring.datasource.url`을 적으면 동작하는 이유, Hikari를 빼고 다른 풀로 바꾸면 자동으로 그 풀이 잡히는 이유, 직접 `@Bean DataSource`를 등록 시 설정이 바뀌는 이유이다.

## 사례 2: KafkaAutoConfiguration

```java
// org/springframework/boot/autoconfigure/kafka/KafkaAutoConfiguration.java
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(KafkaProperties.class)
@Import({ KafkaAnnotationDrivenConfiguration.class,
          KafkaStreamsAnnotationDrivenConfiguration.class })
public class KafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(KafkaConnectionDetails.class)
    PropertiesKafkaConnectionDetails kafkaConnectionDetails(KafkaProperties properties) { ... }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<?, ?> kafkaTemplate(...) { ... }

    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public DefaultKafkaProducerFactory<?, ?> kafkaProducerFactory(...) { ... }

    @Bean
    @ConditionalOnMissingBean(ConsumerFactory.class)
    public DefaultKafkaConsumerFactory<?, ?> kafkaConsumerFactory(...) { ... }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.producer.transaction-id-prefix")
    @ConditionalOnMissingBean
    public KafkaTransactionManager<?, ?> kafkaTransactionManager(...) { ... }
}
```

* `KafkaTemplate` 클래스가 없으면(spring-kafka 의존성 미포함) 사용 안함
* `spring.kafka.*` 프로퍼티는 `KafkaProperties`로 바인딩
* `ProducerFactory`, `ConsumerFactory`, `KafkaTemplate`은 사용자가 커스텀하게 등록하지 않는다면 기본으로 생성해준다.
* 트랜잭션 매니저는 `spring.kafka.producer.transaction-id-prefix`가 설정된 경우에만 등록

## 사례 3: WebMvcAutoConfiguration

```java
// org/springframework/boot/autoconfigure/web/servlet/WebMvcAutoConfiguration.java
@AutoConfiguration(after = { DispatcherServletAutoConfiguration.class,
                             TaskExecutionAutoConfiguration.class, ... })
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ Servlet.class, DispatcherServlet.class, WebMvcConfigurer.class })
@ConditionalOnMissingBean(WebMvcConfigurationSupport.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
public class WebMvcAutoConfiguration { ... }
```

사용자가 `@EnableWebMvc`를 붙이면 자동 설정이 통째로 꺼진다. `@EnableWebMvc`가 `WebMvcConfigurationSupport`를 import하기 때문이다. 그래서 부트에서 `@EnableWebMvc`를 붙이면 기본 메시지 컨버터가 사라졌다는 현상이 발생한다.

`after = DispatcherServletAutoConfiguration.class`는 DispatcherServlet이 먼저 등록된 다음 그에 붙는 컨버터/리졸버를 채워 넣는다는 순서 의존성 명시이다.

## 자동설정 간 순서

자동설정 클래스는 서로 의존할 수 있다. 예: 트랜잭션 매니저 자동설정은 DataSource 자동설정 다음에 와야 한다.

* `@AutoConfiguration(before = X.class, after = Y.class)` — 직접 지정
* `@AutoConfigureBefore` / `@AutoConfigureAfter` — 별도 어노테이션 형태(레거시 호환)
* `@AutoConfigureOrder` — 같은 그룹 내 우선순위

## 빈을 만드는 두 가지 방식

자동설정 내부에서 빈을 만드는 방식은 두 가지다.

**(a) `@Bean` 메서드로 직접 생성**

```java
@Bean
@ConditionalOnMissingBean(KafkaTemplate.class)
public KafkaTemplate<?, ?> kafkaTemplate(...) { ... }
```

**(b) `@Import`로 다른 Configuration을 끌어오기**

```java
@AutoConfiguration
@Import({ DataSourcePoolMetadataProvidersConfiguration.class, ... })
public class DataSourceAutoConfiguration { ... }
```

## 자동설정 비활성화

특정 자동설정만 끄고 싶을 때

```kotlin
@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
class App
```

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

## 커스텀 자동설정

**1) `@AutoConfiguration` 클래스 작성**

```kotlin
@AutoConfiguration
@ConditionalOnClass(MyClient::class)
@ConditionalOnProperty(prefix = "my.client", name = ["enabled"], matchIfMissing = true)
@EnableConfigurationProperties(MyClientProperties::class)
class MyClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun myClient(props: MyClientProperties): MyClient = MyClient(props.url, props.timeout)
}
```

**2) `.imports` 파일 등록**

```
my-starter/
└── src/
    └── main/
        └── resources/
            └── META-INF/
                └── spring/
                    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

파일 내용

```
com.biuea.myclient.MyClientAutoConfiguration
```