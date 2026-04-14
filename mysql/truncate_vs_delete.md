# Truncate vs Delete

## 비교 요약

| 항목 | DELETE | TRUNCATE | DROP |
|------|--------|----------|------|
| 분류 | DML | DDL (내부적으로) | DDL |
| 조건 삭제 | WHERE 절 사용 가능 | 불가 (전체 삭제만) | 테이블 자체 삭제 |
| 롤백 | 가능 (트랜잭션 내) | 불가 (암묵적 COMMIT) | 불가 (암묵적 COMMIT) |
| Auto Increment | 유지 | 초기화 (1부터) | 테이블 제거 |
| 트리거 | 실행됨 | 실행 안 됨 | 실행 안 됨 |
| 속도 | 느림 (행 단위) | 빠름 (페이지 단위) | 가장 빠름 |
| 로그 | Row별 로그 기록 | 최소한의 로그만 기록 | 최소한의 로그만 기록 |
| 외래키 제약 | 제약 조건 체크 | 외래키 있으면 실패 | CASCADE 옵션 필요 |

---

## Delete

```sql
delete from User where id < 200;
```

* 조건에 해당하는 행들을 삭제
* 마지막 채번의 데이터(n)가 삭제되어도 다음 데이터는 n + 1 번의 값으로 채번되어 생성
* Row별로 트랜잭션 로그(Undo Log)를 보관 → 롤백 가능
* 데이터 삭제 시 index scan, full scan 등을 이용하여 데이터 삭제
* 트리거 실행 가능
* InnoDB에서 DELETE는 실제로 행을 즉시 물리 삭제하지 않고, **delete mark**를 설정한 후 Purge Thread가 나중에 정리한다

### DELETE의 내부 동작 (InnoDB)

1. WHERE 조건에 맞는 행을 인덱스를 통해 탐색
2. 해당 행에 X Lock (배타적 잠금) 획득
3. Undo Log에 변경 전 데이터 기록 (롤백 대비)
4. 행에 delete mark 플래그 설정
5. Redo Log에 변경 사항 기록
6. COMMIT 후 Purge Thread가 실제 물리 삭제 수행

## Truncate

```sql
truncate table User;
```

* 특정 조건에 해당하는 행들을 삭제할 수 없고, 빠르게 전체 데이터를 삭제 가능
* 채번값이 초기화되어 다음 데이터 insert 시 1번부터 채번
* 외래키와 같은 제약사항이 존재한다면 실행이 불가능하지만 `set foreign_key_checks = 0` 로 비활성화 시킨 후 사용 가능
* 트랜잭션 로그를 보관하지 않아 롤백 불가능
* 행 수와 관계없이 테이블의 모든 데이터 페이지를 한번에 할당 해제하므로 빠름
* 트리거 실행 불가능
    * 행 단위 데이터 접근 없이 바로 데이터 페이지를 해제하므로 트리거 실행 불가능

### TRUNCATE의 내부 동작 (InnoDB)

1. 기존 테이블의 데이터 파일(.ibd)을 통째로 삭제
2. 테이블 구조(스키마)를 기반으로 새로운 빈 데이터 파일 생성
3. Auto Increment 값 초기화
4. 행 단위 처리가 아니므로 데이터 양과 무관하게 일정한 속도

---

## 사용 시나리오

* **DELETE**: 특정 조건의 데이터만 삭제할 때, 트랜잭션 롤백이 필요할 때, 트리거 실행이 필요할 때
* **TRUNCATE**: 테이블 전체 데이터를 빠르게 비울 때 (테스트 데이터 초기화, 임시 테이블 정리 등)
* **DROP**: 테이블 자체가 더 이상 필요 없을 때

### 주의사항

```sql
-- TRUNCATE는 DDL이므로 트랜잭션 안에서 사용해도 롤백되지 않는다
START TRANSACTION;
TRUNCATE TABLE User;  -- 즉시 암묵적 COMMIT 발생
ROLLBACK;             -- 이미 COMMIT되어 롤백 불가
```

```sql
-- 외래키 제약이 있을 때 TRUNCATE 사용법
SET foreign_key_checks = 0;
TRUNCATE TABLE User;
SET foreign_key_checks = 1;  -- 반드시 다시 활성화
```

---

### 트리거 동작 방식

트리거는 테이블에서 이벤트(UPDATE, DELETE, INSERT)가 발생했을 때 실행되는 것을 의미한다.

영향을 받은 행에 대해 실행되며, 변경 전은 OLD, 변경 후는 NEW라는 변수로 사용 가능하다.

```sql
-- 예시: User 삭제 시 삭제 이력을 남기는 트리거
CREATE TRIGGER before_user_delete
BEFORE DELETE ON User
FOR EACH ROW
BEGIN
    INSERT INTO User_Delete_Log (user_id, deleted_at)
    VALUES (OLD.id, NOW());
END;
```

* `BEFORE` 트리거: 행 변경 전에 실행 (검증, 로깅 등)
* `AFTER` 트리거: 행 변경 후에 실행 (후처리, 알림 등)
* TRUNCATE는 행 단위 접근이 없으므로 `FOR EACH ROW` 트리거가 동작할 수 없다
