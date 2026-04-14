# HTTP (HyperText Transfer Protocol)

## HTTP

클라이언트 - 서버의 관계에서 웹을 이용하여 데이터를 주고 받기 위한 통신 프로토콜이다

클라이언트가 요청을 보내고, 서버는 응답을 반환하고 이 과정은 stateless하다

* stateless?
    * 서버는 클라이언트 요청을 저장하거나 상태를 보존하지 않음을 의미한다
    * 보존을 하게 되면?
        * 서버는 클라이언트의 모든 요청들에 대해서 상태를 관리해야 한다
        * 서버 재시작을 할 경우 데이터를 어떻게 관리할 것인지 고민해야 한다
        * 스케일 아웃된 서버라면 클라이언트의 상태를 어떻게 공유할 것인지 고민해야 한다

## HTTP 요청 구조

```
[요청 라인]
GET /api/users/1 HTTP/1.1
[헤더]
Host: www.example.com
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
Accept: application/json
Content-Type: application/json
Authorization: Bearer asdasda231312
Content-Length: 45
Empty Line
[바디]
{
    "username": "kim",
    "email": "kim@google.com"
}
```

### 요청 라인

* HTTP 메서드: GET, POST, PUT, DELETE, PATCH 등
* 요청 경로: `/api/users/123` 어떤 위치로 요청을 보낼 것인지
* HTTP 버전: `HTTP/1.1` 어떤 버전의 HTTP 요청을 보낼 것인지

### 헤더

* Host: 서버의 도메인명 (HTTP/1.1에서 필수)
* User-Agent: 클라이언트 정보 (브라우저, 앱 등)
* Content-Type: 클라이언트에서 보내는 데이터 형식
* Content-Length: 바디 크기 (바이트)
* Accept: 클라이언트가 받을 데이터 형식
* Authorization: 인증 토큰
* Cookie: 쿠키 정보

### 바디

요청할 데이터이며, json, xml, form-data 등 다양한 형식으로 데이터를 보낼 수 있다

헤더의 Content-Type과 바디의 데이터 구조가 일치해야하며, 다를 경우 파싱 에러가 발생한다

```
POST /api/users HTTP/1.1
Content-Type: application/json // json으로 요청

username=hong&age=25           // form-data로 body에 담김
```

## HTTP 응답 구조

```
[상태 라인]
HTTP/1.1 200 OK
[헤더]
Content-Type: application/json
Content-Length: 156
Server: nginx/1.18.0
Date: Mon, 03 Aug 2025 10:30:00 GMT
Set-Cookie: sessionId=abc123; HttpOnly; Secure
Access-Control-Allow-Origin: *
Empty Line
{
    "username": "kim"
}
```

### 상태 라인

* HTTP 버전: `HTTP/1.1`
* 상태 코드: 200, 204, 400, 404 등 요청에 대한 응답 코드
* 상태 메시지: OK, CREATED, NOT_FOUND 등 상태 코드에 대한 설명

### 헤더

* Content-Type: 응답 데이터 형식
* Content-Length: 응답 본문 크기
* Server: 서버 정보
* Date: 응답 시각
* Set-Cookie: 서버와 클라이언트 간 연결 유지가 필요할 경우 사용되는 작은 데이터 조각
    * HttpOnly: 서버와 통신할 때만 정보를 확인할 수 있고, 개발자 도구와 같은 탭에서도 확인이 불가하다
* Access-Control-Allow-Origin: 접근 허용 도메인

### 바디

응답받을 데이터이며, json, xml, form-data 등 다양한 형식으로 데이터를 보낼 수 있다

요청과 마찬가지로 헤더의 Content-Type과 바디의 데이터 구조가 일치해야하며, 다를 경우 파싱 에러가 발생한다

---

## HTTP 상태 코드

| 범위 | 분류 | 주요 코드 |
|------|------|----------|
| 1xx | 정보 (Informational) | 100 Continue, 101 Switching Protocols |
| 2xx | 성공 (Success) | 200 OK, 201 Created, 204 No Content |
| 3xx | 리다이렉션 (Redirection) | 301 Moved Permanently, 302 Found, 304 Not Modified |
| 4xx | 클라이언트 오류 | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 429 Too Many Requests |
| 5xx | 서버 오류 | 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout |

### 자주 혼동되는 상태 코드

* **401 vs 403**: 401은 인증 안 됨 (로그인 필요), 403은 인가 안 됨 (권한 없음)
* **301 vs 302**: 301은 영구 이동 (SEO 반영), 302는 임시 이동
* **PUT vs PATCH 응답**: PUT은 전체 리소스 교체 후 200, PATCH는 부분 수정 후 200

---

## HTTP 버전 비교

| 항목 | HTTP/1.0 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|------|----------|----------|--------|--------|
| 연결 방식 | 요청마다 새 연결 | Keep-Alive (연결 재사용) | 멀티플렉싱 | QUIC (UDP 기반) |
| HOL Blocking | 있음 | 파이프라이닝으로 완화 | 스트림 단위로 해결 | 완전 해결 |
| 헤더 압축 | 없음 | 없음 | HPACK 압축 | QPACK 압축 |
| 서버 푸시 | 없음 | 없음 | 지원 | 지원 |
| 전송 프로토콜 | TCP | TCP | TCP + TLS | UDP (QUIC) |
| 연결 수립 시간 | 매번 3-way handshake | 최초 1회 | TLS 포함 1-RTT | 0-RTT 가능 |

### HTTP/1.1의 Keep-Alive

```
HTTP/1.0: 요청1 → 연결 → 응답 → 종료 → 요청2 → 연결 → 응답 → 종료
HTTP/1.1: 요청1 → 연결 → 응답 → 요청2 → 응답 → 요청3 → 응답 → 종료
```

하나의 TCP 연결에서 여러 요청/응답을 처리하여 연결 오버헤드를 줄인다.

### HTTP/2의 멀티플렉싱

```
HTTP/1.1: 요청1 → 응답1 → 요청2 → 응답2 (순차 처리, HOL Blocking)
HTTP/2:   요청1 ──┐
          요청2 ──┼── 동시 전송 ──┬── 응답2 (먼저 완료된 것부터)
          요청3 ──┘              ├── 응답1
                                └── 응답3
```

하나의 TCP 연결 내에서 여러 스트림을 동시에 주고받아 HOL Blocking을 해결한다.

### HTTP/3의 QUIC

* UDP 기반이므로 TCP의 3-way handshake 없이 빠른 연결 수립
* 스트림 독립성: 한 스트림의 패킷 손실이 다른 스트림에 영향을 주지 않음
* 연결 마이그레이션: 네트워크 변경(Wi-Fi → LTE) 시에도 연결 유지
