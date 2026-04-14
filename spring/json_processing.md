# 스프링 Json 처리 (요청, 응답)

## 응답

`@RestController`, `@ResponseBody` 어노테이션을 이용하여 Json으로 변환하여 응답할 수 있다

## 요청

`@RequestBody` 어노테이션을 이용하여 객체로 변환하여 요청을 받을 수 있다

## 구조

1. 컴포넌트를 Auto Scan하면서 RequestMappingHandlerAdapter를 스캔한다
2. resolver에 RequestResponseBodyMethodProcessor를 등록하고
    1. RestController, ResponseBody, RequestBody를 감지한다
3. 요청의 경우 HandlerMethodArgumentResolverComposite::resolveArgument를 이용한다
    1. body를 InputStream으로 만들고 HttpMessageConverter를 이용하여 Object로 변환한다
4. 응답의 경우 HandlerMethodReturnValueHandlerComposite::handleReturnValue를 이용한다
    1. Object를 HttpMessageConverter를 이용하여 OutputStream으로 변환한다

---

## 날짜 처리

* DateTimeFormat, JsonFormat을 이용한 날짜 처리

---

## HttpMessageConverter 동작 흐름

```
요청 (JSON → Object):
  Request Body (InputStream)
    → ContentNegotiationManager (Accept/Content-Type 확인)
      → MappingJackson2HttpMessageConverter
        → ObjectMapper.readValue()
          → Java/Kotlin Object

응답 (Object → JSON):
  Return Object
    → MappingJackson2HttpMessageConverter
      → ObjectMapper.writeValue()
        → Response Body (OutputStream)
```

### 주요 HttpMessageConverter 구현체

| 구현체 | Content-Type | 용도 |
|--------|-------------|------|
| `MappingJackson2HttpMessageConverter` | application/json | JSON 직렬화/역직렬화 |
| `MappingJackson2XmlHttpMessageConverter` | application/xml | XML 처리 |
| `StringHttpMessageConverter` | text/plain | 문자열 처리 |
| `ByteArrayHttpMessageConverter` | application/octet-stream | 바이트 배열 |
| `FormHttpMessageConverter` | application/x-www-form-urlencoded | 폼 데이터 |

### 자주 발생하는 문제

```kotlin
// 1. Content-Type 불일치 → 415 Unsupported Media Type
// 요청 시 Content-Type: application/json 헤더를 빠뜨린 경우

// 2. 역직렬화 실패 → 400 Bad Request
// JSON 필드명과 객체 프로퍼티명 불일치, 타입 불일치

// 3. Kotlin data class에서 기본 생성자 없음
// jackson-module-kotlin 의존성 필요
```
