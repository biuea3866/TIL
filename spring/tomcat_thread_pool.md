# 톰캣 스레드 풀

## 스레드 풀

스레드 풀은 프로세스에서 미리 일정량의 스레드를 생성해두고, 필요 시 사용할 수 있게 존재하는 객체의 집합이다

미리 생성해두기 때문에 실시간으로 스레드를 생성하는 비용을 줄일 수 있으며, 할당되고 난 후 다시 스레드 풀로 돌아와 재사용 가능하게 관리된다

## 톰캣 스레드 풀

톰캣은 web application server(process)이고, thread를 이용하여 클라이언트로부터의 요청을 처리할 수 있다

## 톰캣 스레드 풀 설정

* **maxThreads**: 톰캣이 동시에 처리할 수 있는 최대 스레드 수
    * greeting 운영 기준 20
    * 기본값 200
    * 너무 낮으면?
        * 처리 속도 저하, 요청 대기 증가
    * 너무 높으면?
        * cpu, 메모리 과부하 발생

* **minSpareThreads**: 유지할 최소 유휴(idle) 스레드 수
    * 기본값 10
    * 너무 적으면?
        * 초기 응답 속도 저하 → 가용 가능한 스레드가 적으므로
    * 너무 높으면?
        * 메모리 낭비

* **acceptCount**: maxThreads 도달 후 요청을 대기할 큐 사이즈
    * 기본값 100
    * 너무 낮으면?
        * 과부하 시 timeout되는 요청이 증가
    * 너무 높으면?
        * 대기열이 커지면서 메모리 문제 발생

* **executor**: 여러 커넥터가 공유할 중앙 관리 스레드 풀 설정

> **커넥터**: 클라이언트의 요청을 받고, 처리하는 역할을 담당하여 다양한 프로토콜 (http, https, ajp)을 처리

---

## 요청 처리 흐름

```
Client Request
  → Connector (TCP 연결 수락)
    → acceptCount 큐에 대기
      → Thread Pool에서 유휴 스레드 할당
        → Request 처리 (Filter → Servlet → Controller → Service → ...)
          → Response 반환
        → 스레드 반환 (Thread Pool로 복귀)
```

## Spring Boot 기본 설정

```yaml
server:
  tomcat:
    threads:
      max: 200          # maxThreads
      min-spare: 10     # minSpareThreads
    accept-count: 100   # acceptCount
    max-connections: 8192  # 동시에 유지할 수 있는 최대 커넥션 수
    connection-timeout: 20000  # 커넥션 타임아웃 (ms)
```

## 스레드 풀 크기 산정 기준

* **CPU Bound 작업**: 스레드 수 ≈ CPU 코어 수 (컨텍스트 스위칭 최소화)
* **I/O Bound 작업**: 스레드 수 = CPU 코어 수 × (1 + 대기시간/처리시간)
  * 대부분의 웹 애플리케이션은 I/O Bound (DB 조회, 외부 API 호출)
* 너무 많은 스레드: 메모리 소비 증가 (스레드당 약 512KB~1MB 스택 메모리), 컨텍스트 스위칭 오버헤드
* 너무 적은 스레드: 요청 대기, acceptCount 큐 가득 참 → 요청 거부 (Connection Refused)
