# RESTful

## REST (REpresentational State Transfer)

자원을 표현으로 만들어 이 자원의 상태를 주고 받는 것을 의미

HTTP URL(Uniform Resource Identifier)를 통해 자원을 명시하고, HTTP Method(POST, GET, PUT, DELETE)로 자원에 대한 CRUD를 적용

* 자원: 소프트웨어가 관리하는 것
    * 문서, 그림, 데이터 등

* 자원의 표현: 자원을 표현하기 위한 이름
    * 지원서 → Applicants로 자원을 표현

* 상태 전달: 자원의 정보를 전달
    * json, xml, form-data 등의 방식으로 전달

## REST 특징

### client - server

* 클라이어트(자원을 요청)와 서버(자원을 관리)를 나누어 관심사를 분리
    * 클라이언트: ui/ux, 사용자 인증 정보
    * 서버: 비즈니스 로직, 외부 저장소와 연결
* 상호 의존성 제거

### stateless

* 서버는 클라이언트의 상태를 저장하지 않음
    * 복잡한 상태 관리가 불필요
* 어느 인스턴스에서도 동일하게 클라이언트에게 응답을 반환

### cache

* 헤더에 캐싱 표현

    ```
    HTTP/1.1 200 OK
    Cache-Control: max-age=3600, public     // 1시간 캐시 가능
    ETag: "abc123def456"                    // 버전 식별자
    Last-Modified: Mon, 01 Jan 2025 10:00:00 GMT

    {
      "id": 123,
      "name": "홍길동",
      "email": "hong@example.com"
    }
    ```

* 중복 처리를 방지하고, 대량의 요청에 대해 빠른 응답 가능

### uniform interface

* uri로 표현한 자원을 조작 및 식별
* RestAPI(uri, method)만으로 어떤 요청인지 식별이 가능 (자기 서술)
* 응답에 대한 데이터 뿐아니라 관련된 요청에 필요한 URI를 응답에 포함한다(Hypermedia As The Engine Of Application State)

    * 가령 지원자를 조회 후 `GET https://api.greetinghr.com/applicants/100` 이 다음에 행동할만 한 것(상태 전이)을 응답(하이퍼링크)으로 내려준다

        ```json
        {
            "id": 100,
            "name": "kim",
            "resume": "https://api.greetinghr.com/applicants/100/resume"
        }
        ```

    * 다만 위와 같은 응답은 실제 응답 파라미터인지, 하이퍼링크인지 분간이 어렵기 때문에 HAL (Hypertext, Application Language)형태로 응답이 가능하다

        ```json
        {
            "id": 100,
            "name": "kim",
            "_links": {
                "self": "https://api.greetinghr.com/applicants/100",
                "next": "https://api.greetinghr.com/applicants/101",
                "resume": "https://api.greetinghr.com/applicants/100"
            }
        }
        ```

    * 궁금증
        * 서버 관점
            * 기능을 추가할 때마다 uri 추가해주어야 함
            * 모든 요청마다 관리를 해주어야 하는데 현실적으로 가능한가?
        * 클라이언트 관점
            * 사내 서버야 그렇다치겠는데, 외부에서 제공하는 하이퍼링크면 신뢰가 되는 것인가?
            * 서버가 링크를 동적으로 바꿀 수 있기 때문에 서버 uri 변경이 영향을 주지 않는다고 함 이것도 마찬가지로 신뢰가 되는 것인가? 서버 응답에 의존성이 생기는 것 아닌가?

### layered system

* 클라이언트는 서버만 호출한다
* 서버는 로드밸런싱, 게이트웨이 > 보안 > 인증 > 비즈니스 등의 계층화를 할 수 있다

### code-on-demand

* 서버로부터 스크립트를 받아서 클라이언트에서 실행한다

## REST API

rest를 기반으로 기능을 사용할 수 있는 인터페이스

* HTTP 표준을 기반으로 구현되었으므로 HTTP를 지원하는 다양한 클라이언트, 서버로 구현할 수 있다

### 구성 요소

* 자원(URI)
    * 자원, 자원이 가진 고유한 id를 이용하여 표현하고, 정보를 조작한다

* 행위(HTTP Method)
    * CRUD(POST, GET, PUT, DELETE)를 메서드로 표현하여 어떻게 처리할 것인지 명시한다

* 표현(Representation)
    * 정보 조작에 대한 응답을 어떻게 표현(xml, json 등)할 것인지 정의한다

### 설계 규칙

#### 1. PathVariable, QueryString, Header

* PathVariable
    * 리소스를 식별할 때 사용한다
    * 리소스 간의 계층을 표현할 수 있다
    * `/Workspaces/1/Openings/1`

* QueryString
    * 옵션 사항에 사용한다
    * 필터, 정렬 등 부가적인 매개변수에 사용된다
    * `/Workspaces/1/Openings?title=공고제목&page=0&pageSize50`

* Header
    * 메타데이터를 표현할 때 사용한다
    * 인증 토큰, API 버전, 멱등성 키 등
    * `Authorization: Bearer ....`
    * `X-Api-Version: v1`

#### 2. uri에서 품사

* 명사 사용
    * 복수형으로 자원을 표기(Collection)하고 id를 특정해서 사용한다
    * 예시: `/openings`, `/applications`

* 동사 사용
    * HTTP 메서드로 동작을 표기하기에 uri에서는 명사를 표기하는 것이 좋으나
    * 가령 로그인과 같은 것은 HTTP 메서드로 표기하기 어려우니 `[POST] /login`과 같이 동사를 사용할 수 있다

* 형용사 사용
    * 상태나 속성을 나타낼 때 제한적 사용
    * `/applicants/1/submit`, `/openings/1/active`
    * 하지만 QueryString으로 처리하는 것이 더 좋음
        * 형용사는 보통 선택적이고, 명사를 수식하기 때문에 query string으로 표현해도 좋음
        * `/applicants/1?status=SUBMIT`

#### 3. 하이픈을 사용하는 경우

* 리소스가 길어지는 가령 띄어쓰기가 포함된 리소스에 사용
    * `/processEvaluators/1` → `/process-evaluators/1`
    * 언더바는?
        * url로 보여질 때 잘 안보임..
