# [Spring] @Controller, @RestController

- **Tag**: #Spring #SpringBoot #Controller #RestController

---
### 무엇을 배웠는가
* Spring Framework에서 컨트롤러를 정의할 때 사용하는 `@Controller`와 `@RestController`의 차이점을 학습합니다.
* `@Controller`는 웹 페이지(View), 데이터(Json)를 모두 반환하는 반면, `@RestController`는 데이터(Json) 반환에 특화된 어노테이션이라는 것을 이해합니다.

---
### 왜 중요하고, 어떤 맥락인가
현대 웹 개발은 서버에서 View를 그려주는 **서버 사이드 렌더링** 방식과, 데이터만 제공하고 클라이언트에서 뷰를 그리는 **클라이언트 사이드 렌더링**방식으로 나뉩니다.   
이 두 가지 시나리오를 이해하여 어떤 컨트롤러를 사용할지 결정할 수 있습니다.

---

### 상세 내용
#### 1. `@Controller`: 뷰(View)와 데이터(Data)를 모두 다루는 컨트롤러
`@Controller`는 클라이언트의 요청을 받아 **두 가지 형태**로 응답할 수 있습니다.

#### **1.1 View 반환**
* 주로 서버 사이드 렌더링 환경에서 사용됩니다.
* 요청 처리 후, 서비스 계층을 거친 결과물을 `ModelAndView` 객체에 담아 반환합니다.
* `ViewResolver`는 반환된 뷰 이름 (e.g., "home.html")을 해석하여 사용자에게 렌더링된 HTML 페이지를 보여줍니다.

```kotlin
@Controller
class HomeController {
    @GetMapping("/home")
    fun home(): String {
        return "home" // "home.html" 같은 뷰 파일을 찾아 렌더링
    }
}
```

#### 1.2 Data 반환 (`@ResponseBody`)
* 메서드에 `@ResponseBody` 어노테이션을 추가하면 뷰가 아닌 데이터를 반환할 수 있습니다.
* 반환된 객체는 `HttpMessageConverter`에 의해 Json 형태로 반환되어 클라이언트에게 전달됩니다.

```kotlin
@Controller
class UserController {
    @GetMapping("/{id}")
    @ResponseBody
    fun getUserById(@PathVariable id: Long): UserDto {
        return UserDto(...) // 객체가 Json으로 변환됨
    }
}
```

#### 2. `@RestController`: 데이터에 특화된 컨트롤러
`@RestController`는 RESTful API 구축을 위해 등장한 데이터 반환에 특화된 컨트롤러입니다.
* `@RestController` 어노테이션 자체에는 `@Controller`, `@ResponseBody`가 포함되어 있습니다.
* 따라서 해당 어노테이션이 붙읕 클래스는 `@ResponseBody`를 메서드마다 붙이지 않아도 객체를 Json으로 변환할 수 있습니다.

```kotlin
@RestController
class UserController {
    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): UserDto {
        return UserDto(...) // 객체가 Json으로 변환됨
    }
}
```

---
### 비교
|구분|`@Controller`|`@RestController`
|---|-------------|----------------|
|주요 목적| UI(View) 또는 Data 반환| Data 반환|
|반환 타입| `String`(View 경로), `ModelAndView`| DTO, `ResponseEntity` 등 객체|
|`@ResponseBody`| Data 반환 시 메서드별로 명시| 클래스 레벨에 자동 적용|
|주요 사용처| Thymeleaf, JSP를 사용한 SSR | RESTful API (SPA, 모바일 앱 연동)|

---
### 요약
* Thymeleaf나 JSP를 사용하여 서버에서 웹 페이지를 구성할 때는 `@Controller`를 사용합니다.
* React, Vue 같은 프론트엔트 프레임워크와 연동하기 위한 순수한 데이터 API를 만들 때는 `@RestController`를 사용합니다.