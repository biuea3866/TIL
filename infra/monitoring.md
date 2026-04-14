# 모니터링

## 모니터링의 3가지 축 (Observability)

| 축 | 설명 | 도구 예시 |
|----|------|----------|
| **Metrics** | 시스템의 수치적 상태 (CPU, 메모리, 요청 수, 지연 시간) | Datadog, Prometheus, Grafana |
| **Logs** | 이벤트 기반의 상세 기록 | Datadog Logs, ELK Stack, Loki |
| **Traces** | 요청의 서비스 간 흐름 추적 (분산 추적) | Datadog APM, Jaeger, Zipkin |

이 세 가지를 결합하면 "무슨 문제가 발생했는지(Metrics) → 어디서 발생했는지(Traces) → 왜 발생했는지(Logs)"를 추적할 수 있다.

---

## 로그 포맷

```json
{
  "traceId": "<traceId>",
  "spanId": "<spanId>",
  "msg": "",
  "level": "",
  "timestamp": ""
}
```

### 구조화된 로그의 핵심 필드

| 필드 | 설명 |
|------|------|
| `traceId` | 하나의 요청이 여러 서비스를 거칠 때 동일한 ID를 공유하여 분산 추적 가능 |
| `spanId` | 하나의 트레이스 내에서 개별 작업 단위를 식별 |
| `level` | 로그 심각도 (TRACE, DEBUG, INFO, WARN, ERROR) |
| `timestamp` | 로그 발생 시각 (ISO 8601 형식 권장) |

### 추가 권장 필드

```json
{
  "service": "order-service",
  "env": "production",
  "userId": "12345",
  "method": "POST",
  "path": "/api/orders",
  "duration_ms": 142,
  "status": 200
}
```

---

## 분산 추적 (Distributed Tracing)

```
Client → API Gateway → Order Service → Payment Service → DB
         traceId: abc    traceId: abc    traceId: abc
         spanId: 001     spanId: 002     spanId: 003
                         parentId: 001   parentId: 002
```

* **W3C Trace Context**: 표준화된 트레이스 전파 헤더 (`traceparent`, `tracestate`)
* 서비스 간 HTTP 호출 시 헤더를 통해 traceId/spanId를 전파
* Spring Cloud Sleuth (현재 Micrometer Tracing) 또는 OpenTelemetry로 자동 전파 가능

---

## 로그 수집

* 코틀린 서버
    * Datadog Java 로그 수집: Logback Appender 사용
    * W3C Trace Context로 트레이스-로그 자동 연결

* 노드 서버
    * Datadog Node.js 로그 수집: Winston 3.0+ Logger 사용

* 코틀린 서버 운영
    * Trace Context Propagation 설정으로 서비스 간 추적 연결

---

## 알림 전략 (Alerting)

| 수준 | 조건 예시 | 대응 |
|------|----------|------|
| **P1 (Critical)** | 서비스 다운, 에러율 > 5% | 즉시 대응 (PagerDuty/Slack) |
| **P2 (Warning)** | 지연 시간 > 2초, CPU > 80% | 업무 시간 내 대응 |
| **P3 (Info)** | 디스크 70% 이상 | 다음 스프린트에 처리 |

### 좋은 알림의 조건
* **실행 가능(Actionable)**: 알림을 받았을 때 할 수 있는 액션이 명확해야 한다
* **오탐 최소화**: 불필요한 알림은 알림 피로(Alert Fatigue)를 유발한다
* **컨텍스트 포함**: 대시보드 링크, 관련 로그 쿼리 등을 알림에 포함
