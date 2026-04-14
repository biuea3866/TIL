# 트랜잭션 Isolation, Propagation

## 읽기 이상 현상 (Read Phenomena)

| 현상 | 설명 |
|------|------|
| **Dirty Read** | 다른 트랜잭션이 아직 커밋하지 않은 데이터를 읽음 → 롤백되면 존재하지 않는 데이터를 읽은 것 |
| **Non-Repeatable Read** | 같은 트랜잭션 내에서 같은 행을 두 번 읽었는데 값이 다름 (다른 트랜잭션이 UPDATE + COMMIT) |
| **Phantom Read** | 같은 조건으로 조회했는데 행 수가 달라짐 (다른 트랜잭션이 INSERT/DELETE + COMMIT) |

## Isolation

* **Read Uncommitted**: 커밋되지 않은 데이터를 읽을 수 있어 dirty read 발생
* **Read Committed**: undo log를 사용하여 dirty read는 방지되나 동일 트랜잭션 내에서 커밋된 데이터를 어떻게 읽느냐에 따라서 Non Repeatable Read 문제가 발생
* **Repeatable Read**: mvcc를 이용하여 커밋 되어도 undo log를 활용한 읽기, 갭락을 이용하여 행 잠금 이후의 대상은 읽지않는 읽기로 일관성있는 읽기를 보장
* **Serializable**: 조회 쿼리마저 Lock을 수행하여 Phantom read 방지

### 격리 수준별 허용되는 이상 현상

| 격리 수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|----------|:----------:|:-------------------:|:------------:|
| Read Uncommitted | O | O | O |
| Read Committed | X | O | O |
| Repeatable Read | X | X | O (MySQL InnoDB는 갭락으로 방지) |
| Serializable | X | X | X |

---

## @Transactional에서의 Isolation

* **DEFAULT**
    * 사용하고있는 DB의 설정을 따라감
        * mysql 기본은 Repeatable Read
    * 그리팅 설정
        * primary: Read Committed
        * secondary: Repeatable Read
        * `SELECT @@session.transaction_isolation;`
* **READ_UNCOMMITTED**
* **READ_COMMITTED**
* **REPEATABLE_READ**
* **SERIALIZABLE**

### 이전 격리수준 복구

`DataSourceTransactionManager::doCleanupAfterCompletion`

트랜잭션이 끝나고, 커넥션을 반납하면서 원래의 상태로 되돌려야 한다

그 이유는 커넥션 풀에서 커넥션을 꺼내오고, 트랜잭션 어노테이션에 지정된 커스텀한 옵션들로 잠시 바꾸어 요청을 하기 때문이다

따라서 커넥션을 반납하면서 이전의 상태(기본값)로 돌려야 다음에 커넥션을 사용하는 요청에서 일관된 상태로 요청할 수 있다

## @Transactional에서의 Propagation

* **REQUIRED**
    * 이미 시작된 트랜잭션이 있으면 참여하고, 없으면 새로 시작
* **SUPPORTS**
    * 이미 시작된 트랜잭션이 있으면 참여하고, 없으면 트랜잭션 없이 진행
* **MANDATORY**
    * 이미 시작된 트랜잭션이 있으면 참여하고, 없으면 예외를 발생시킴
    * 혼자서 트랜잭션을 진행하면 안되는 경우에 사용
* **REQUIRES_NEW**
    * 새로운 트랜잭션을 시작
    * 이미 진행중인 트랜잭션이 있으면 보류시키고, 새 트랜잭션을 시작
* **NOT_SUPPORTED**
    * 이미 진행중인 트랜잭션이 있으면 보류
* **NEVER**
    * 이미 진행중인 트랜잭션이 있으면 예외를 발생
* **NESTED**
    * 부모와 중첩되는 자식 트랜잭션을 만들지만 REQUIRES_NEW와 달리 부모의 커밋과 롤백에 영향을 받음
    * 자식 트랜잭션은 부모에 영향을 주지 않음
