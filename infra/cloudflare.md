# Cloudflare

## 역할

* 방화벽 기능 (WAF)
* CDN (네트워크 엣지 서버)
    * 프록시 서버 기능
* DNS 기능
* DDoS 방어
* SSL/TLS 인증서 관리

## 요청 흐름

```
Client → Cloudflare Edge (가장 가까운 PoP)
           ├── DNS 해석
           ├── DDoS 방어
           ├── WAF 규칙 검사
           ├── SSL/TLS 종료
           ├── 캐시 히트 → 즉시 응답
           └── 캐시 미스 → Origin Server로 프록시
```

---

## 사용

### WAF (Web Application Firewall)

웹 애플리케이션 앞단에서 SQL Injection, XSS, 콘텐츠 차단 등과 같은 기능을 수행하여 웹앱을 보호한다.

| 규칙 유형 | 설명 |
|----------|------|
| Managed Rules | Cloudflare가 관리하는 OWASP 기반 규칙셋 |
| Custom Rules | 직접 정의하는 방화벽 규칙 (IP, 경로, 헤더 기반) |
| Rate Limiting | 특정 경로/IP에 대한 요청 속도 제한 |

### DDoS 방어

* L3/L4 공격 (SYN Flood, UDP Flood): 네트워크 레벨에서 자동 차단
* L7 공격 (HTTP Flood): WAF + Rate Limiting + Bot Management로 방어
* Always Online: 원본 서버 다운 시에도 캐시된 페이지 제공

### CDN 서버

* 캐싱 서버를 두어 원본 서버까지의 요청 시간을 줄일 수 있다
    * 원본 서버로의 부하를 줄일 수 있다
    * 부하를 줄이는만큼 원본 서버의 비용을 절감시킬 수 있다
* 원본 서버가 장애가 생겨 백업 서버가 구동될 때까지 캐싱 서버가 대체해줄 수 있다
* 압축(Brotli, Gzip)을 이용하여 페이지 로드 시간을 개선할 수 있다

#### 캐시 제어

| 설정 | 설명 |
|------|------|
| Cache-Control 헤더 | Origin에서 캐시 정책 지정 (`max-age`, `s-maxage`) |
| Page Rules | 경로별 캐시 정책 커스텀 (예: `/api/*`는 캐시 안 함) |
| Purge Cache | 캐시 무효화 (전체 또는 URL 단위) |

### 프록시 서버

리버스 프록시 역할을 수행하여 원본 서버의 실제 IP를 숨긴다.

* 클라이언트는 Cloudflare IP만 볼 수 있어 Origin IP 노출 방지
* 원본 서버에서 실제 클라이언트 IP를 알기 위해 `CF-Connecting-IP` 또는 `X-Forwarded-For` 헤더 사용

### SSL/TLS

| 모드 | 설명 |
|------|------|
| Off | 암호화 없음 |
| Flexible | Client ↔ Cloudflare만 HTTPS (Origin은 HTTP) |
| Full | 양쪽 모두 HTTPS (Origin 인증서 검증 안 함) |
| Full (Strict) | 양쪽 모두 HTTPS + Origin 인증서 검증 (**권장**) |

* 무료 SSL 인증서 자동 발급/갱신
* Origin Certificate: Cloudflare가 발급하는 Origin 전용 인증서 (15년 유효)

### DNS

* 빠른 DNS 해석 속도 (Anycast 네트워크 활용)
* 프록시 모드(주황색 구름): 트래픽이 Cloudflare를 경유 → WAF, CDN, DDoS 방어 적용
* DNS Only 모드(회색 구름): 순수 DNS 해석만 수행, Cloudflare 기능 미적용
