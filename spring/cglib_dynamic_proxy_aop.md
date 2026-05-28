# Filter, Interceptor, Proxy, AOP

> 통합 문서. 원본은 `_archive/spring/filter_interceptor_aop.md`, `_archive/spring/proxy_target_class.md`로 보관.

## 공통 관심사를 관리하는 방법

핵심 로직을 건드리지 않고 로깅, 인증, 트랜잭션 같은 공통 기능을 추가하고 싶다고 하자. 스프링은 요청이 컨트롤러를 거쳐 핵심 로직이 수행되기 전에 부가 기능을 추가할 수 있다.

```
Client Request
  → Filter (doFilter - before)
    → DispatcherServlet
      → Interceptor (preHandle)
        → AOP (@Before)
          → Controller → Service → Repository
        → AOP (@After)
      → Interceptor (postHandle)
    → View Rendering
  → Interceptor (afterCompletion)
→ Filter (doFilter - after)
Client Response
```

* **요청 파이프라인 축** — Filter, Interceptor. 요청이 컨트롤러에 닿기 전후를 가로챈다.
* **메서드 실행 축** — Proxy, AOP. 빈 메서드 호출 전후를 가로챈다.

| 항목 | Filter | Interceptor | AOP |
|------|--------|------------|-----|
| 관리 주체 | 서블릿 컨테이너 (Tomcat) | 스프링 컨테이너 | 스프링 컨테이너 |
| 실행 시점 | DispatcherServlet 이전/이후 | Controller 이전/이후 | 메서드 실행 전후 |
| 사용 객체 | ServletRequest/Response | HttpServletRequest/Response | JoinPoint, ProceedingJoinPoint |
| Spring Bean 접근 | 가능 (DelegatingFilterProxy) | 가능 | 가능 |
| 적합한 용도 | 인코딩, XSS, CORS | 인증, 인가, 로깅 | 트랜잭션, 로깅, 권한 |

---

## Filter

DispatcherServlet 이전에 위치하며, 전역적으로 처리해야 하는 작업(로깅, 감사, 보안, 문자열 인코딩 등)을 처리한다. 컨트롤러로 라우팅되기 전에 body 값을 인코딩해 두면, 컨트롤러는 가공된 값으로 요청을 처리할 수 있다.

`ServletRequest`, `ServletResponse`는 클라이언트의 요청·응답을 서블릿(자바 웹 처리 기술)에서 다룰 수 있도록 변환된 객체다.

## Interceptor

컨트롤러로 라우팅되기 전에 스프링 기능으로 처리할 수 있는 로직(인증, 인가 등)을 담당한다.

`HttpServletRequest`, `HttpServletResponse`는 `ServletRequest`, `ServletResponse`를 상속한 객체로, HTTP 관련 기능(header, session 등)을 쓸 수 있다.

### Filter 로직을 Interceptor에서 구현하면 안 되나

가능은 하지만 분리하는 게 좋다.

* 정적 리소스 요청에는 대응이 안 되어, Interceptor에서 처리하려면 별도 처리(url 등록)가 필요하다 → 비효율적이다.
* 역할과 책임으로도 나누는 게 낫다. Filter는 웹 컨텍스트만으로 처리할 수 있는 전처리(인코딩, XSS 등), Interceptor는 스프링 컨텍스트가 필요한 애플리케이션 로직(인증, 인가)을 맡는다.

### ServletRequest가 상위 타입인데 HttpServletRequest로 형변환되는 이유

톰캣은 요청을 받으면 `HttpServletRequest`로 객체를 만들고, Filter로 넘길 때 상위 타입인 `ServletRequest`로 넘긴다. 실제 객체가 `HttpServletRequest`이므로 다시 형변환이 가능하다.

그러면 처음부터 `HttpServletRequest`로 넘기면 될 텐데 왜 상위 타입으로 넘길까. 서블릿 API는 웹 요청 처리가 목적이지 HTTP 전용이 아니라서, 새로운 웹 기술에도 대응할 수 있도록 설계됐기 때문이다. 지금은 HTTP가 주류라 `HttpServletRequest`로 만들어 두고 `ServletRequest`로 넘기는 형태일 뿐, 다른 프로토콜이 생기면 언제든 바뀔 수 있다.

---

## AOP — 메서드 인터셉트

Filter·Interceptor가 요청 파이프라인을 가로챘다면, AOP는 빈의 메서드 호출 전후에 부가 기능을 끼운다. `@Aspect`로 부가 기능(Advice)과 적용 지점(Pointcut)을 선언하면, 스프링이 빈을 등록하는 시점에 알아서 프록시를 만들어 끼워 넣는다.

```kotlin
@Aspect
@Component
class LoggingAspect {
    @Around("execution(* com.example..*Service.*(..))")
    fun log(joinPoint: ProceedingJoinPoint): Any? {
        println("before ${joinPoint.signature.name}")
        val result = joinPoint.proceed()   // 원본 호출
        println("after ${joinPoint.signature.name}")
        return result
    }
}
```

* `@Transactional`: 트랜잭션 시작/커밋/롤백을 프록시가 감싼다.
* `@Async`: 비동기 실행을 프록시가 별도 스레드에서 수행한다.
* `@Cacheable`: 캐시 조회/저장을 프록시가 가로챈다.
* `@Validated`: 메서드 파라미터 검증을 프록시가 수행한다.
* `@Aspect`: 직접 정의한 횡단 관심사를 프록시로 위빙한다.

그렇다면 이 "프록시"는 무엇이고 어떻게 만들어지는가. 

---

## ㅇ프록시

핵심 메서드 앞뒤로 부가 기능을 원본 메서드마다 코드를 작성하면, 핵심 로직과 부가 로직이 뒤섞이고 같은 코드가 곳곳에 중복된다.

프록시는 원본(target)과 똑같은 모양의 대리 객체를 만들어 호출자가 프록시를 거치게 한다. 프록시가 부가 기능을 처리한 뒤 원본을 호출하므로, 원본 코드는 손대지 않고 부가 기능만 끼워 넣을 수 있다.

```
호출자 → [프록시] → (부가 기능) → [원본 객체] → 핵심 로직 실행
```

이 대리 객체를 런타임에 만들어내는 방식이 두 가지다. JDK Dynamic Proxy(인터페이스 기반)와 CGLib(상속 기반).

```
AOP (무엇을: 횡단 관심사를 분리하는 프로그래밍 패러다임)
  └─ Spring AOP (어떻게: 프록시를 만들어 메서드 호출을 가로챈다)
       ├─ JDK Dynamic Proxy (프록시 생성 방식 1: 인터페이스 기반)
       └─ CGLib            (프록시 생성 방식 2: 상속 기반)
```

### JDK Dynamic Proxy

자바 표준 라이브러리(`java.lang.reflect.Proxy`)가 제공하는 방식이다. 인터페이스를 기준으로, 런타임에 해당 인터페이스를 구현한 프록시 클래스를 동적으로 생성한다.

```kotlin
interface UserService {
    fun getUser(id: Long): String
}

class UserServiceImpl : UserService {
    override fun getUser(id: Long) = "user-$id"
}

// 모든 메서드 호출이 invoke()로 모인다
class LoggingHandler(private val target: Any) : InvocationHandler {
    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
        println("before ${method.name}")
        val result = method.invoke(target, *(args ?: emptyArray()))  // 리플렉션으로 원본 호출
        println("after ${method.name}")
        return result
    }
}

val proxy = Proxy.newProxyInstance(
    UserService::class.java.classLoader,
    arrayOf(UserService::class.java),
    LoggingHandler(UserServiceImpl())
) as UserService
```

* 프록시의 모든 메서드 호출은 `InvocationHandler.invoke()` 한 곳으로 모인다.
* 실제 원본 호출은 `Method.invoke()`, 즉 리플렉션으로 일어난다.
* 인터페이스가 반드시 있어야 한다. 인터페이스가 없는 클래스는 프록시를 만들 수 없다.

### CGLib (Code Generation Library)

클래스를 상속해서 프록시를 만든다. 런타임에 원본 클래스를 상속한 자식 클래스를 생성하고, 메서드를 오버라이드해 가로챈다.

```kotlin
class UserService {
    fun getUser(id: Long) = "user-$id"
}

val enhancer = Enhancer()
enhancer.setSuperclass(UserService::class.java)   // 원본을 상속
enhancer.setCallback(MethodInterceptor { obj, method, args, proxy ->
    println("before ${method.name}")
    val result = proxy.invokeSuper(obj, args)       // 부모(원본) 메서드 호출
    println("after ${method.name}")
    result
})
val proxy = enhancer.create() as UserService
```

* 상속 기반이기에 `final` 클래스는 프록시를 만들 수 없고, `final` 메서드는 가로챌 수 없다. 오버라이드가 막혀 있기 때문이다.
* `private` 메서드도 오버라이드 대상이 아니라 가로채지 못한다.

### 두 방식 비교

| 항목 | JDK Dynamic Proxy | CGLib |
|------|:-----------------:|:-----:|
| 기준 | 인터페이스 | 클래스 |
| 생성 원리 | 인터페이스 구현 (형제) | 클래스 상속 (자식) |
| 인터페이스 필수 | O | X |
| `final` 클래스 | 가능 (인터페이스만 있으면) | 불가 (상속 불가) |
| `final`/`private` 메서드 | 가능 | 불가 (오버라이드 불가) |
| 호출 방식 | 리플렉션 (`Method.invoke`) | 인덱스 디스패치 (FastClass) |
| 구체 클래스로 캐스팅 | 불가 (인터페이스로만) | 가능 |
| 의존성 | 자바 표준 | spring-core에 포함 |

---

## proxy-target-class: 어떤 프록시를 쓸지

```yaml
spring:
  aop:
    proxy-target-class: true   # Spring Boot 기본값
```

* `true`: 항상 CGLib을 쓴다. (Spring Boot 2.0+ 기본값)
* `false`: 인터페이스가 있으면 JDK Dynamic Proxy, 없으면 CGLib.

Spring Boot가 CGLib을 기본으로 택한 이유는 주입 타입 때문이다. JDK Dynamic Proxy로 만든 프록시는 인터페이스만 구현한 객체라서, 구체 클래스 타입(`UserServiceImpl`)으로 주입받으려 하면 타입이 맞지 않아 `BeanNotOfRequiredTypeException`이 발생한다. 반면 CGLib 프록시는 구체 클래스를 상속한 자식이라, 인터페이스로도 구체 클래스로도 주입할 수 있다. 

```kotlin
// JDK Dynamic Proxy일 때
@Autowired lateinit var service: UserService      // 인터페이스 → OK
@Autowired lateinit var service: UserServiceImpl   // 구체 클래스 → 예외!

// CGLib일 때
@Autowired lateinit var service: UserServiceImpl   // 자식이므로 OK
```

---

## 프록시 기반의 한계: 자기 호출(Self-invocation)

호출이 프록시를 거쳐야 부가 기능이 동작하는데, 같은 클래스 안에서 메서드를 직접 호출하면 해당 메서드는 프록시 객체가 아닌 원본 메소드이기 때문에 부가 기능이 동작하지 않는다.

스프링은 원본 객체(target)를 만든 뒤, 그것을 참조로 들고 있는 별도의 프록시 객체를 만들어 빈으로 등록한다. 그래서 다른 빈에 주입되는 건 원본이 아니라 프록시다.

```
컨트롤러 → [OrderService 프록시] → (트랜잭션 시작) → [원본 OrderService] → order() 실제 실행
              ↑ 주입받은 건 이것                       ↑ 진짜 로직은 여기
```

### 외부 호출 vs 내부 호출

```kotlin
@Service
class OrderService {
    @Transactional
    fun order() { ... }

    fun process() {
        order()   // = this.order() → 프록시를 거치지 않음 → @Transactional 미적용!
    }
}
```

* **외부 호출** — 컨트롤러가 `orderService.order()`를 부르면, 손에 쥔 게 프록시라서 실제론 `proxy.order()`다. 프록시가 트랜잭션을 열고 원본을 호출하므로 정상 동작한다.
* **내부 호출** — `process()` 안의 `order()`는 사실 `this.order()`인데, 이때 `this`는 프록시가 아니라 원본 객체다. 그래서 프록시를 건너뛰고 원본 안에서 곧장 호출되어 트랜잭션이 시작되지 않는다.

---

## Spring AOP vs AspectJ

Spring AOP는 "프록시 기반 AOP"다. AOP를 구현하는 또 다른 방식으로 AspectJ가 있는데, 둘의 차이는 부가 기능을 끼워 넣는(위빙, weaving) 시점이다.

| 항목 | Spring AOP | AspectJ |
|------|-----------|---------|
| 위빙 시점 | 런타임 (프록시 생성) | 컴파일 / 클래스 로드 시 (바이트코드 직접 조작) |
| 적용 대상 | 스프링 빈의 메서드만 | 모든 객체, 생성자, 필드까지 |
| 자기 호출 문제 | 있음 | 없음 |
| 설정 부담 | 가볍다 | 위빙 설정 필요 (무겁다) |

---

## @Aspect vs BeanPostProcessor

지금까지 본 AOP는 "메서드 호출을 프록시로 가로채는" 방식이다. 그런데 스프링에는 더 낮은 수준에서 빈을 가로채 부가 기능을 더 할 수 있다. 이를 BeanPostProcessor라 한다.

하고 싶은 일이 "메서드 호출을 가로채는 것"이냐 "빈 자체를 다루는 것"이냐로 갈린다.

| | `@Aspect` (Spring AOP) | BeanPostProcessor |
|--|--|--|
| 언제 동작 | 메서드가 호출될 때마다 (런타임, 반복) | 빈이 생성·초기화될 때 한 번 (기동 시점) |
| 무엇을 다루나 | 메서드 호출(JoinPoint) | 빈 인스턴스 그 자체 |
| 추상화 수준 | 고수준·선언적 (포인트컷 매칭) | 저수준·프로그래밍적 (모든 빈을 직접 훑음) |
| 전형적 용도 | 로깅·트랜잭션·캐시·재시도·권한 | 빈 검사·검증·등록·교체·커스텀 주입 |
| 관계 | BPP 위에 만들어진 추상화 | 컨테이너 확장점 그 자체 |

### `@Aspect`를 사용하는 경우
런타임에 메소드 호출 전후로 부가 기능을 추가할 때 사용된다. 모든 `*Service` 메서드 실행 시간 측정, `@AuditLog` 붙은 메서드 감사 로깅, 특정 패키지에 재시도 적용 등이 해당한다. 전부 메서드가 불릴 때마다 일어나는 일이라 프록시가 필요하고, `@Aspect`가 그 프록시를 알아서 만들어 준다.

### BeanPostProcessor를 사용하는 경우
빈을 만들 때 한 번 부가 기능을 추가할 때 사용된다.

* 특정 어노테이션을 가진 빈/메서드를 전부 찾아 레지스트리에 **등록**한다. `@KafkaListener`, `@EventListener`, `@Scheduled`가 이 방식이다. 이들은 빈을 프록시로 감싸는 게 아니라, 메서드를 엔드포인트로 등록해 두고 외부 트리거(메시지·스케줄)가 오면 직접 호출한다.
* 기동 시 빈 설정을 검증해, 잘못되면 애플리케이션을 아예 못 뜨게 막는다.
* 커스텀 어노테이션을 보고 빈 필드에 값을 주입한다. (스프링의 `@Autowired` 처리기 `AutowiredAnnotationBeanPostProcessor`)