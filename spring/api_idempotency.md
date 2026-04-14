# API 따닥 방지

## 개요

현재 new-back에 구현되어있는 ApiLock(따닥 방지용)은 다음과 같은 특징을 갖습니다

* 파라미터(PathVariable, QueryString, Header)가 달라도 동일 유저가 요청한 api라면 중복으로 간주

```bash
curl --location 'http://localhost:8080/test/not-public/openings/100/applicants/1000' \
--header 'X-Greeting-Workspace-Id: 500' \
--header 'X-Greeting-User-Id: 188' \
--header 'Content-Type: application/json' \
--data '{
    "evaluationId": 2000,
    "moduleId": 3000
}' & curl --location 'http://localhost:8080/test/not-public/openings/99/applicants/1000' \
--header 'X-Greeting-Workspace-Id: 500' \
--header 'X-Greeting-User-Id: 188' \
--header 'Content-Type: application/json' \
--data '{
    "evaluationId": 2000,
    "moduleId": 3000
}'
```

```
# openings의 파라미터를 다르게 했으나 락으로 간주
lockKey: doodlin.greeting.ats.api.opening.OpeningInternalController/testNotPulblic/188
lockKey: doodlin.greeting.ats.api.opening.OpeningInternalController/testNotPulblic/188
thread sleep
```

* 2명의 다른 유저가 같은 요청을 수행할 경우 중복으로 간주하지 않음
* body를 이용한 trigger 조건은 첫번째 인자로 강제되어야 함
* public api가 아니라면 trigger를 사용할 수 없음

### 문제점

1. 동일 유저의 요청이어도 파라미터가 다르다면 다른 요청으로 보아야 하는데 중복으로 간주하여 1초의 지연을 발생시킴
2. trigger를 사용할 경우 무조건 첫번째로 body를 강제해야 활용할 수 있는데 사용성, 실수할 여지가 있어보임

### 고민 사항

복수의 유저가 동일 요청 시 중복으로 간주해야 하는가?

동시에 복수의 유저가 같은 요청으로 평가 내역을 남긴다면 다른 요청이 맞습니다.

다만, userId와 관계없이 동일한 데이터를 만들어내는 로직이 있다면 중복된 생성을 유발하므로 막을 필요가 있습니다.

1. 동시에 다른 유저가 평가 내역을 생성함
2. userId와 관계없는 하나만 존재해야하는 evaluation 데이터를 2개가 생성됨

userId는 옵셔널하게 패턴에 추가해야한다

## 개선해야할 점

* 파라미터가 다르다면 다른 요청으로 보고 지연을 발생시키면 안됨
* 복수의 유저가 동일한 요청을 할 경우 락을 발생시켜야 함
* body의 값을 사용하고 싶으면 인자의 위치가 아니라 RequestBody 어노테이션을 탐색하도록 해야 함

## 개선 방법

### 락 키 패턴 수정

**현재 락키 패턴**

* public: `doodlin.greeting.ats.api.opening.OpeningInternalController/test/2000/3000/null`
* not public: `doodlin.greeting.ats.api.opening.OpeningInternalController/testNotPulblic/188`

**수정 락키 패턴**

`signatureDeclaringType/signatureName/{resourceId}`

userId는 옵셔널하게 적용합니다.

### 다양한 방식의 파라미터를 Key 패턴에 넣기

**SpEL 사용**

SpEL을 사용하여 키 패턴에 포함시킵니다.

```kotlin
@DistributedLock(
    publicApi = false,
    key = "'workspaces/' + #workspaceId + " +
            "'/' + 'openings/' + #openingId + " +
            "'/' + 'applicants/' + #applicantId + " +
            "'/' + 'evaluations/' + #body.evaluationId + " +
            "'/' + 'modules/' + #body.moduleId + " +
            "'/' + 'tests/' + #testId",
)
@PostMapping(value = ["/test/not-public/openings/{openingId}/applicants/{applicantId}"])
fun testNotPulblic(
    @RequestBody body: TestBody,
    @PathVariable openingId: Long,
    @PathVariable applicantId: Long,
    @RequestHeader("X-Greeting-Workspace-Id") workspaceId: Long,
): CommonResponse<Boolean> {
    return CommonResponse.success(true)
}
```

* 장점
    * 간단하게 api 파라미터들을 바인딩하여 파싱이 가능하다
    * header, query string, path variable 등 다양한 파라미터를 타겟으로 키를 삼을 수 있다
* 단점
    * 타입이 명확하지 않고, 오타와 같은 실수의 여지가 있다 (컴파일에서 잡을 수 없으므로)
    * key를 지정하기 위해서 많은 라인의 코드가 생성된다

**어노테이션으로 마킹**

```kotlin
@DistributedLock()
@PostMapping(value = ["/test/not-public/openings/{openingId}/applicants/{applicantId}"])
fun testNotPulblic(
    @RequestBody body: TestBody,
    @LockKey(order = 2) @PathVariable openingId: Long,
    @LockKey(order = 3) @PathVariable applicantId: Long,
    @LockKey(order = 1) @RequestHeader("X-Greeting-Workspace-Id") workspaceId: Long,
    @LockKey(order = 4) @RequestParam testId: Long
): CommonResponse<Boolean> {
    return CommonResponse.success(true)
}

data class TestBody(
    @property:LockKey(order = 6) val evaluationId: Long,
    @property:LockKey(order = 5) val moduleId: Long
)
```

```
LockKey version: workspaceId/500/openingId/100/applicantId/1000/testId/900/moduleId/3000/evaluationId/2000
```

* 장점
    * 어노테이션 마킹만으로 파싱이 가능하다
    * header, query string, path variable 등 다양한 파라미터를 타겟으로 키를 삼을 수 있다
    * order와 같은 명시적인 값을 이용하여 패턴의 순서를 지정할 수 있다
* 단점
    * 테스트는 되었겠지만 의도치 않은 파라미터가 들어올 경우 런타임에서 오류를 발생시킬 수 있다
    * 리플렉션을 이용하기 때문에 코드가 복잡하고, 유지보수가 어려울 수 있다

---

## 그외 따닥을 방지할 수 있는 방법

### 멱등키 사용

1. 클라이언트는 요청 파라미터(header, querystring, api path 등)을 이용하여 멱등키를 생성합니다.
2. 생성된 멱등키를 헤더에 실어 api를 요청합니다.
3. 서버는 멱등키를 저장소로부터 조회하여 중복 요청인지 확인합니다.
    1. 존재한다면 Lock
    2. 아니라면 저장소에 멱등키 저장
4. 두번째 따닥 요청을 실패 혹은 대기 처리합니다.

서비스 입장에서는 키 패턴(SpEL, 어노테이션 마킹 등)을 유지보수하지않아도 된다는 점 그리고 단순 저장소로부터 데이터를 조회하고, 저장만 하면 된다는 점에서 편리한 이점이 있는 것 같습니다

또한 멱등키 : 응답값이 저장되어있는 상황이라면 빠른 응답을 통해 api 레이턴시, 멱등성 면에서 이점을 가질 수 있을 것 같습니다 (혹은 에러, 대기로 처리해도 될듯)

다만 두번째 요청이 멱등키 저장 이전에 들어온다면 중복 요청이 허용되는 것이 아닌가라는 의문이 남습니다.

참고: https://docs.tosspayments.com/blog/what-is-idempotency

### MySQL 네임드 락

1. 클라이언트는 서비스에 API 요청을 시도합니다.
2. API를 기반으로 락 키를 생성하고, MySQL에 `GET_LOCK` 요청을 수행합니다.
3. 두번째 요청은 실패 혹은 대기 처리가 됩니다.
4. 첫번째 요청이 `RELEASE_LOCK`이 되고, 두번째 요청은 처리됩니다.

다른 저장소 사용 없이 MySQL만으로 처리 가능하다는 면에서 이점이 있습니다.

그리고 트랜잭션에 함께 묶어 사용할 수 있어 원자적 측면에서 유리합니다.

다만, 트랜잭션을 어떻게 관리하느냐에 따라서 락의 생명주기가 달리 될 수 있다는 위험성이 존재합니다.

그리고 메모리 저장소(redis)에 비해 처리 속도가 상대적으로 느림에 따라 중복 처리가 원활하지 않을 수 있습니다.

참고: https://techblog.woowahan.com/2631/

**우아한 형제들 네임드 락 요약**

* 비즈니스와 독립적으로 수행하기 위해 트랜잭션을 사용하지 않고 락 획득 로직 구성
    * 트랜잭션을 사용하지 않기 때문에 get lock 후 커넥션을 바로 반납하게 됨
    * release lock 수행 시 get lock과는 다른 커넥션을 획득하여 수행할 여지가 있음
        * 네임드 락은 세션(connection)단위이며, 획득과 해제 커넥션이 다르면 올바르게 동작을 수행하지 못함
        * 더군다나 다른 스레드가 커넥션을 획득하여 락을 풀어버릴 수도 있음
* 비즈니스와 동일한 트랜잭션으로 락 획득 로직 구성
    * 트랜잭션을 사용하기 때문에 get lock과 release lock이 동일한 커넥션임

### Redis 분산 락

멱등키 사용과 같은 플로우

MySQL과 비교해서 상대적으로 빠른 처리 속도로 락 기능을 수행할 수 있습니다.

그리고 TTL이 있어 자동적으로 시간이 지나면 락을 해제할 수 있습니다.

다만, 단순 하나의 클러스터에 멀티 인스턴스(write - read 분리)를 구성할 경우 복제 지연으로 인해 문제가 생길 수 있으니 복수의 독립적인 인스턴스 구성을 통한 Redlock 알고리즘을 수행할 경우 효과적이지만 비용 문제가 생길 수 있습니다.

### 서버 메모리 기반 Debounce

1. API 요청을 수행합니다.
2. 서버(프로세스)의 메모리로부터 락 데이터를 조회합니다.
3. 두번째 요청에서 락 데이터가 존재할 경우 별도 처리를 수행합니다.
4. 첫번째 요청의 락을 해제하고, 두번째 요청을 수행합니다.

서버의 리소스만으로 락 기능을 수행할 수 있습니다.

저장소와 통신을 위한 네트워크 통신도 존재하지 않으며, 별도의 저장소도 필요없기 때문에 손쉽게 구현이 가능합니다.

다만, scale out이 되는 경우 복수의 서버가 구성되므로 글로벌한 락을 수행할 수 없습니다.

그리고 서버의 메모리를 이용하므로 서버에서 처리할 수 있는 쓰레드가 많고, 요청이 많아질수록 oom에 대한 위험성도 존재합니다.
