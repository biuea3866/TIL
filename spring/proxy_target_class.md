# proxy-target-class

## 설정

```yaml
spring:
  aop:
    proxy-target-class: false
```

proxy-target-class는 프록시 객체를 어떤 방식으로 만들 것인가에 대한 옵션이다

* **true** (기본값, Spring Boot 2.0+): CGLIB로 동작하며, final 클래스라면 동작하지 않는다
* **false**: JDK Dynamic Proxy로 동작하며, 인터페이스가 없다면 생성되지 않는다

---

## JDK Dynamic Proxy vs CGLIB

| 항목 | JDK Dynamic Proxy | CGLIB |
|------|:-----------------:|:-----:|
| 프록시 방식 | 인터페이스 기반 | 클래스 상속 기반 (바이트코드 조작) |
| 인터페이스 필수 | O | X |
| final 클래스 | 프록시 가능 (인터페이스만 있으면) | 불가 (상속 불가) |
| final 메서드 | 프록시 가능 | 불가 (오버라이드 불가) |
| 성능 | 리플렉션 기반으로 상대적으로 느림 | 바이트코드 생성으로 빠름 |
| 타입 캐스팅 | 인터페이스로만 캐스팅 가능 | 구체 클래스로도 캐스팅 가능 |

### Spring Boot가 CGLIB를 기본으로 선택한 이유

```kotlin
// JDK Dynamic Proxy: 인터페이스로만 주입 가능
@Autowired
lateinit var userService: UserService  // 인터페이스 OK
// lateinit var userService: UserServiceImpl  // 구체 클래스 주입 시 에러!

// CGLIB: 구체 클래스로도 주입 가능
@Autowired
lateinit var userService: UserServiceImpl  // OK
```

JDK Dynamic Proxy를 사용하면 구체 클래스 타입으로 주입받을 수 없어서 `BeanNotOfRequiredTypeException`이 발생하는 경우가 빈번했다. Spring Boot 2.0부터 이런 혼란을 방지하기 위해 CGLIB를 기본값으로 변경했다.

## 프록시가 사용되는 곳

* `@Transactional`: 트랜잭션 시작/커밋/롤백을 프록시가 감싸서 처리
* `@Async`: 비동기 실행을 프록시가 별도 스레드에서 수행
* `@Cacheable`: 캐시 조회/저장을 프록시가 가로채서 처리
* `@Validated`: 메서드 파라미터 검증을 프록시가 수행
* AOP (`@Aspect`): 횡단 관심사를 프록시로 위빙

### 자기 호출(Self-invocation) 문제

```kotlin
@Service
class OrderService {
    @Transactional
    fun order() { ... }

    fun process() {
        order()  // 프록시를 거치지 않는 내부 호출 → @Transactional 미동작!
    }
}
```

같은 클래스 내에서 메서드를 호출하면 프록시를 거치지 않아 AOP가 적용되지 않는다. 해결 방법:
* 별도 클래스로 분리
* `ApplicationContext.getBean()`으로 프록시 객체를 직접 가져와 호출
* `@Lazy`로 자기 자신을 주입
