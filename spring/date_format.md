# [Spring] @JsonFormat vs @DateTimeFormat: 올바른 날짜/시간 처리 방법

- **Tags:** #Spring #SpringBoot #Java #Jackson #Serialization #DateTime

---

### 무엇을 배웠는가?
Spring에서 날짜 및 시간 데이터를 처리할 때 사용하는 `@JsonFormat`과 `@DateTimeFormat`의 명확한 용도 차이를 학습했습니다.   
`@JsonFormat`은 JSON 형식의 요청/응답 Body를 위한 것이고, `@DateTimeFormat`은 `@RequestParam`이나 `@ModelAttribute`와 같이 URL 파라미터나 폼 데이터를 위한 것이라는 점을 이해했습니다.

---

### 왜 중요하고, 어떤 맥락인가?
두 어노테이션의 역할을 혼동하면 날짜/시간 데이터를 파싱하지 못하는 오류가 빈번하게 발생합니다.

* `@JsonFormat`: 요청과 응답 body를 다룰 경우 Jackson 라이브러리를 이용하여 직렬화와 역직렬화를 시도하는데 이 때 `@JsonFormat`을 붙여야 합니다.
* `@DateTimeFormat`: URL, 쿼리 파라미터 등을 다룰 경우 `FormattingConversionService`으로 처리되므로, `@DateTimeFormat`를 붙여야 합니다.

데이터 전송 방식에서는 올바른 어노테이션을 사용하는 것이 매우 중요합니다.

---

### 상세 내용

#### 1. `@JsonFormat`: JSON Body를 위한 날짜 포맷터
* **역할**: Jackson 라이브러리를 통해 JSON 데이터를 Java 객체로 변환(역직렬화)하거나 그 반대(직렬화)를 수행할 때 날짜/시간 필드의 형식을 지정합니다.
* **주요 사용처**: `@RequestBody`가 붙은 DTO 클래스의 `LocalDate`, `LocalDateTime` 필드.
* **핵심**: Spring은 JSON 처리에 Jackson을 기본으로 사용하므로, **JSON 데이터의 날짜 형식은 `@JsonFormat`이 전담합니다.**

#### 2. `@DateTimeFormat`: URL 파라미터 및 폼 데이터를 위한 날짜 포맷터
* **역할**: HTTP 요청 파라미터(Query String)나 폼 데이터 같은 문자열 값을 Java의 날짜/시간 객체로 변환할 때 사용될 형식을 지정합니다.
* **주요 사용처**: `@RequestParam` 또는 `@ModelAttribute`로 받는 `LocalDate`, `LocalDateTime` 파라미터.
* **핵심**: 이 방식은 JSON이 아니므로 Jackson을 사용하지 않습니다. 대신 Spring 내부의 `FormattingConversionService`가 `@DateTimeFormat`을 인지하여 날짜 파싱을 처리합니다.

#### 3. "Pattern"에 대한 흔한 착각
`pattern` 속성은 양방향으로 동작하지만 그 역할이 다릅니다.

* **요청(Request)에서의 Pattern**: 클라이언트가 보내는 날짜 문자열이 **어떤 형식이어야 하는지를 정의하는 유효성 검증 규칙**과 같습니다. 이 규칙에 맞게 요청이 들어오면, 서버의 `LocalDateTime` 객체는 내부적으로 표준 형식(ISO-8601, `yyyy-MM-ddTHH:mm:ss`)으로 값을 저장합니다.
* **응답(Response)에서의 Pattern**: 서버가 클라이언트에게 JSON 응답을 보낼 때, `LocalDateTime` 객체를 **`pattern`에 지정된 문자열 형식으로 변환**하여 전달합니다.

#### 4. 왜 섞어 쓰면 안 될까?
* **`@RequestBody`에 `@DateTimeFormat`을 사용하면?**
    * **동작하지 않습니다.** JSON 데이터는 Jackson 라이브러리가 처리하는데, Jackson은 `@DateTimeFormat` 어노테이션을 알지 못하기 때문에 파싱 에러가 발생합니다.
* **`@RequestParam`에 `@JsonFormat`을 사용하면?**
    * **동작하지 않습니다.** `@RequestParam`은 Spring의 `ConversionService`가 처리하는데, 이 서비스는 `@JsonFormat`을 인지하지 못하므로 타입 변환에 실패합니다.

---

### 한눈에 보는 비교

| 구분 | `@JsonFormat` | `@DateTimeFormat` |
| :--- | :--- | :--- |
| **주요 사용처** | `@RequestBody` (JSON Body) | `@RequestParam`, `@ModelAttribute` |
| **동작 라이브러리** | Jackson | Spring `ConversionService` |
| **데이터 형식** | JSON | Query String, Form-Data |

---

### 핵심
* `@RequestBody`를 사용하는 DTO의 날짜 필드에는 반드시 `@JsonFormat을 사용해야 한다.
* `@RequestParam`이나 `@ModelAttribute`로 날짜 파라미터를 받을 때는 반드시 `@DateTimeFormat`을 사용해야 한다.
* 하나의 필드에 두 어노테이션을 함께 선언해도, 각자의 컨텍스트(JSON 또는 파라미터)에 맞는 어노테이션만 인식되고 나머지는 무시된다. 데이터 전송 방식에 맞는 올바른 어노테이션을 선택하는 것이 중요하다.
