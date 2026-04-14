# Filter, Interceptor, AOP

클라이언트의 요청에 대해 각기 다른 계층에서 공통적인 로직을 처리해줄 수 있는 인터페이스

## 실행 순서

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

## 비교 요약

| 항목 | Filter | Interceptor | AOP |
|------|--------|------------|-----|
| 관리 주체 | 서블릿 컨테이너 (Tomcat) | 스프링 컨테이너 | 스프링 컨테이너 |
| 실행 시점 | DispatcherServlet 이전/이후 | Controller 이전/이후 | 메서드 실행 전후 |
| 사용 객체 | ServletRequest/Response | HttpServletRequest/Response | JoinPoint, ProceedingJoinPoint |
| Spring Bean 접근 | 가능 (DelegatingFilterProxy) | 가능 | 가능 |
| 적합한 용도 | 인코딩, XSS, CORS | 인증, 인가, 로깅 | 트랜잭션, 로깅, 권한 |

## Filter

DispatcherServlet 이전에 위치하며 전역적으로 처리해야하는 작업(로깅, 감사, 보안, 문자열 인코딩 등)들을 처리한다

가령 컨트롤러로 라우팅되기 전에 body의 값들을 인코딩을 해주고, 컨트롤러에서는 가공된 값들로 http 요청을 처리할 수 있다

### ServletRequest, ServletResponse

클라이언트의 요청, 응답을 서블릿(자바를 이용한 웹 처리 기술)에서 사용할 수 있도록 변환된 객체

---

## Interceptor

컨트롤러로 라우팅되기 전에 스프링의 기능으로 처리할 수 있는 로직(인증, 인가 등)들을 처리할 수 있다

### HttpServletRequest, HttpServletResponse

ServletRequest, ServletResponse를 상속받은 객체로 HTTP와 관련된 기능(header, session 등)들을 사용할 수 있음

---

## AOP (Aspect Oriented Programming)

메소드 동작 시점에 비즈니스 로직들을 위한 부가 기능(트랜잭션)들을 처리한다

패키지, 파일 단위로 공통 로직을 처리할 수도 있고, 어노테이션을 이용하여 마킹이 된 메서드, 클래스 단위로 공통 로직을 처리할 수 도 있다

---

## 궁금한 사항

### Filter로직을 Interceptor에서 구현하면 안되는건가?

가능은 하지만 가급적이면 분리해서 사용하는 것이 좋다.

가령 정적 리소스 요청에 대응이 안되며, interceptor에서 대응하고 싶을 경우 별도의 처리(url 등록)를 해주어야 함

-> 비효율적인 작업

그리고 역할과 책임의 관점에서도 나누는 것이 좋다.

* **Filter**: 웹 컨텍스트만으로 처리할 수 있는 요청 담당 (인코딩, XSS 보안 처리 등) -> 전처리
* **Interceptor**: 스프링 컨텍스트를 이용하여 처리할 수 있는 요청 담당 (인증, 인가) -> 애플리케이션 로직

### 어째서 ServletRequest가 상위 타입인데 HttpServletRequest로 형변환이 가능한 것인가?

톰캣은 요청을 받으면 HttpServletRequest로 객체를 생성하고, Filter로 넘겨줄 때는 상위 타입인 ServletRequest로 넘겨주어 ServletRequest가 HttpServletRequest로 형변환이 가능 (결과적으로는 ServletRequest = HttpServletRequest)

-> 근데 그러면 확장된 HttpServletRequest를 그대로 Filter에 쓰면 되는데 왜 ServletRequest로 변환해서 넘기는가?

서블릿 API는 웹 요청 처리가 목적이나 HTTP에 특화는 아니고, 언제든 새로운 웹 기술에 대응할 수 있도록 설계됨

현재는 HTTP가 웹 요청에서 주류이기 때문에 HttpServletRequest로 만들어놓고, ServletRequest로 변환하는 형태이나 다른 프로토콜이 생긴다면 언제든 변경될 수 있음
