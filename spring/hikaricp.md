# [DB] HikariCP: 고성능 커넥션 풀의 동작 원리와 최적화

- **Tags:** #Database #ConnectionPool #HikariCP #JDBC #Performance

---

### 무엇을 배웠는가?
Java가 DB에 접근하는 표준 API인 **JDBC**의 동작 과정과 매번 커넥션을 맺고 끊는 비용 문제를 해결하기 위한 **커넥션 풀**의 필요성에 대해 학습했습니다.   
특히, Spring Boot의 기본 CP 구현체인 **HikariCP**가 어떻게 다른 풀보다 빠른 성능을 내는지, 그 비결이 되는 **`FastList`**, **`CAS` 연산**, **다층적 커넥션 획득 방식**에 대해 이해했습니다.

---

### 왜 중요하고, 어떤 맥락인가?
일반적인 JDBC 동작 방식은 매번 DB 질의 시마다 네트워크 통신(e.g., 3-way handshake)을 통해 커넥션을 맺고, 질의가 끝나면 커넥션을 반납합니다.    
실시간 사용자 요청을 처리하는 **OLTP 환경**에서는 이 과정이 수시로 반복되며, 이는 애플리케이션에 **심각한 성능 병목**을 유발합니다.

**커넥션 풀**은 애플리케이션 시작 시 DB와 일정량의 커넥션을 미리 맺어두고, 요청이 올 때마다 이 커넥션을 빌려주고 반납받아 재사용하는 방식입니다.   
HikariCP는 이 커넥션 풀을 구현한 라이브러리로, 바이트코드 레벨의 마이크로 최적화와 락(Lock)을 최소화하는 설계로 가장 빠른 성능을 제공합니다.

---

### 상세 내용

#### 1. HikariCP의 고성능 비결
HikariCP는 다른 CP 구현체 대비 사소한 부분까지 최적화하여 성능을 향상시켰습니다.

* **`FastList`**: `ArrayList`와 유사하지만, `ThreadLocal`에 저장되어 사용됩니다. 커넥션을 가져올 때 배열의 인덱스 범위 체크 등을 제거하여 미세한 성능 향상을 꾀합니다.
* **`CAS` (Compare-and-Set)**: 커넥션 획득 시 전통적인 락(Lock) 대신, CPU 레벨의 락-프리(Lock-Free) 연산인 CAS를 사용하여 스레드 간 경합을 최소화합니다.
* **다층적 커넥션 획득**: 커넥션을 찾기 위해 여러 레이어를 순차적으로 탐색하여 가장 빠른 경로로 커넥션을 제공합니다.

#### 2. 커넥션 획득 및 반납 과정
HikariCP가 `getConnection()`을 호출했을 때의 내부 동작은 다음과 같습니다.

* **커넥션 획득 (Get)**
    1.  우선, `threadLocal`에 저장된 `FastList`를 확인합니다. (해당 스레드가 직전에 사용했던 커넥션이 있는지 확인. 가장 빠름)
    2.  없다면, `sharedList` (전역 커넥션 풀)에서 커넥션을 조회합니다.
    3.  그래도 없다면, **`handoff Queue`** (다른 스레드가 방금 반납한 커넥션이 대기 중인 큐)를 확인하여 획득을 시도합니다.
* **커넥션 반납 (Return)**
    1.  사용이 끝난 커넥션을 `handoff Queue`에 반납합니다. (커넥션을 반납하는 스레드는 큐에 넣고 즉시 복귀)
    2.  이후 `ConcurrentBag` (HikariCP의 풀 관리 객체)이 `handoff Queue`의 커넥션을 가져가 `sharedList`에 추가합니다.


#### 3. 주요 설정 (HikariCP & MySQL)
HikariCP의 성능을 최대로 활용하려면 JDBC 드라이버 옵션도 함께 튜닝해야 합니다.

* **HikariCP 자체 옵션**
  * auto-commit(default: true): 커넥션이 종료되거나 반환될 때 트랜잭션 커밋 반영 여부 
  * connection-timeout(default: 30000): 커넥션 풀에서 커넥션을 얻어오기까지의 최대 시간 
  * idle-timeout(default: 60000): 유휴 커넥션이 풀에서 존재할 수 있는 최대 시간, 시간이 넘는다면 커넥션을 제거하여 서버 자원을 확보한다. 
  * keepalive-time: 유휴 커넥션이 살아있는지 확인하는 주기, max-lifetime보다 작아야 함 
  * max-lifetime(default: 1800000): 커넥션 풀에서 살아있을 수 있는 최대 시간 
  * minimum-idle(default: maximum-pool-size): 최소한 유지되어야 하는 유휴 커넥션 갯수 
  * maximum-pool-size(default: 10): 최대 커넥션 갯수 
  * read-only(default: false): 커넥션을 조회할 때 readOnly에 해당하는 DB서버로부터 커넥션을 가져옴 
  * transaction-isolation: 트랜잭션 격리 수준 
  * driver-class-name: JDBC 드라이버 구현체

* **MySQL Connector/J 최적화 옵션** (`data-source-properties`)
  * socketTimeout: 소켓 읽기 타임아웃, 해당 시간 내에 DB로부터 응답을 받지 못하면 연결 종료 
  * cachePrepStmts: PreparedStatement 캐싱 활성화, 동일 쿼리 반복에 대한 캐싱 활성화 
  * prepStmtCacheSize: 커넥션당 캐시할 PreparedStatement 갯수 
  * prepStmtCacheSqlLimit: 캐시할 최대 쿼리의 길이, 초과한다면 캐싱하지 않음 
  * useServerPrepStmts: Mysql 서버에서 쿼리를 파싱하고 최적화 
  * useLocalSessionState: 격리 수준, autoCommit등과 같은 정보를 클라이언트에서 캐싱하여 불필요하게 DB 서버로 쿼리하는 것을 방지 
  * rewriteBatchedStatements: 배치 insert / update를 단일 쿼리로 작성하여 한번의 네트워크 통신으로 쿼리를 수행 
  * cacheResultSetMetadata: PreparedStatement의 결과(ResultSet)의 메타데이터 (컬럼명, 타입 등)를 캐싱하여 DB 서버에 메타데이터 질의하는 요청을 줄임 
  * cacheServerConfiguration: MySQL 서버 설정 정보를 캐싱하여 서버 변수와 설정을 매번 조회하지 않음 
  * elideSetAutoCommits: useLocalSessionState가 true일 때 동작하고, 드라이버가 이미 autoCommit 모드라면 코드에서의 autoCommit 요청을 무시 
  * maintainTimeStats: 드라이버의 시계열 통계 수집 비활성화, true일 경우 디버깅에 유용하나 운영 환경에서는 보통 false

---

### 핵심 요약
* JDBC의 비효율적인 커넥션 획득/반납 과정을 해결하기 위해 **커넥션 풀**을 사용합니다.
* **HikariCP**는 `FastList`, `CAS` 등 락을 최소화하고 마이크로 최적화를 통해 최고 수준의 성능을 제공하는 커넥션 풀입니다.
* 커넥션 획득 시 **`ThreadLocal` -> `SharedList` -> `HandoffQueue`** 순으로 가장 빠른 경로를 탐색합니다.
* HikariCP의 성능을 100% 활용하려면, `cachePrepStmts`, `useLocalSessionState` 등 **JDBC 드라이버 속성 최적화**가 반드시 병행되어야 합니다.