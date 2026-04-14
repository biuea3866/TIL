# 스프링 배치

## 스프링 배치

스프링 배치는 큰 단위의 작업을 일괄 처리하는 기술이다

tasklet 기반, chunk 기반의 작업을 통해 작업을 처리한다

## 스프링 배치는 왜 필요한가

대량의 데이터를 안정적이고, 효율적으로 처리한다

* 대량의 데이터를 청크 단위로 나누어 분할 처리할 수 있다
    * 청크 단위의 트랜잭션 처리 -> 실패해도 청크 단위 롤백 가능
    * 청크 단위의 데이터 조회 후 처리 -> 메모리 효율
* 실패 복구 기능
    * 스텝이 실패할 경우 retry, repeat, noRollback 등 다양한 옵션을 이용하여 실패 처리를 할 수 있다
* 메타데이터 관리
    * 메타데이터 테이블을 이용해 배치 성공, 실패 등의 이력을 관리할 수 있다
* 중복 실행 방지
    * 동일한 파라미터로 실행을 할 경우 메타테이블을 참조하여 중복 실행 방지
        * ex) job: evaluationRemoindJob, parameter: 2025-01:26T10:00:00Z
        * 같은 파라미터로 들어오는 요청에 대해선 수행하지 않음
    * 멀티 인스턴스, 단일 스케줄러
        * 현재 그리팅 환경에서는 아래와 같은 환경이므로 중복 처리를 막고 있음
            * k8s cronjob을 이용하여 단일 인스턴스, 단일 스케줄러
            * eventbridge 트리거를 이용하여 단일 pod의 api 호출
        * `@Scheduled`를 활용하는 경우엔 멀티 인스턴스이므로 단일 스케줄러가 사용되어야 한다
            * trm-schedular pod 1개로 운영중
            * shed_lock 테이블
                * shed_lock테이블의 행에 접근하여 락을 획득하면 실행 가능, 획득하지 못하면 대기
            * redis 분산락을 이용하여 해결한다
                * redis에 접근하여 락을 획득하면 실행 가능, 획득하지 못하면 대기

---

## 스프링 배치 테이블

Spring Batch는 모든 실행 정보를 메타데이터를 저장소에 기록하고 관리할 수 있다.

그리고 이를 통해

1. 어떤 작업이 실행되었는지 추적 가능하며
2. 해당 작업이 어디까지 진행되었는지 상태를 알고있으며
3. 실패한 지점부터 재시작할 수 있다.

### 배치 테이블 구조

* **BATCH_JOB_INSTANCE**
    * 배치 작업의 논리적 실행 단위
    * JOB_NAME, JOB_KEY 컬럼에 유니크 제약 조건이 걸려있다.
    * JOB_KEY는 JobParameters의 해시값
* **BATCH_JOB_EXECUTION**
    * JobInstance의 실제 실행 시도를 나타낸다.
* **BATCH_JOB_EXECUTION_PARAMS**
    * JobParameters가 저장되는 테이블이다.
    * JobExecution마다 다른 값을 가질 수 있기 때문에
* **BATCH_JOB_EXECUTION_CONTEXT**
    * Job이 실행되며 공유해야할 데이터를 저장
* **BATCH_STEP_EXECUTION**
    * StepExecution이 저장되는 테이블이다.
    * StepExecution은 JobExecution에 대해 N개로 구성될 수 있으므로 JobExecution의 id를 참조한다.
* **BATCH_STEP_EXECUTION_CONTEXT**
    * Step이 실행되며 공유해야할 데이터를 저장

## 스프링 배치 구조

### JobInstance

Job의 논리적 실행 단위를 나타낸다. Job이 어떤 작업을 할지 정의한 것이라면 JobInstance는 실제 언제, 어떤 데이터로 실행되는지에 대한 실체이다.

JobInstance는 JobBuilder에 의해 정의될 때 이름을 부여받고, JobParameter를 뒤에 붙여 JobInstance를 식별하게 된다.

SpringBatch는 완료된 JobInstance는 재실행할 수 없도록 제외하고, 동일한 작업을 중복 실행하는 부작용을 방지한다.

다만, 그럼에도 멱등성이 보장되어있는 JobInstance라면 JobParametersIncrementer로 여러 번 수행이 가능하다.

```kotlin
JobBuilder().incrementer(RunIdIncrementer())
```

**restartable**

Spring Batch는 JobBuilder에서 실패한 Job에 대한 재실행을 막을 수 있다.

이는 preventRestart라는 기능이고, 같은 JobParameter로 시작되는 JobInstance에 대한 재실행을 막을 수 있다.

다만, RunIdIncrementer와 같이 JobParameter에 run.id를 넣어 조작하는 방식으로 재실행할 경우 막을 수 없다. (매번 새로운 id를 발급받기 때문)

### JobParameter

JobParameter는 Job을 서로 다른 JobInstance로 구분짓게 해준다.

### JobExecution

JobExecution은 JobInstance의 실제 실행 시도(실행 이력)를 나타낸다.

* status: 현재 어떤 상태인지 나타낸다. (COMPLETED, FAILED, STOPPED)
* startTime: Job이 실행한 시점을 나타낸다.
* endTime: Job이 종료한 시점을 나타낸다.
* exitCode: Job 실행의 최종 결과를 나타낸다.
* failureExceptions: Job 실행이 실패한 경우 사유를 나타낸다.

### Step

1. SimpleStepHandler

### StepExecution

단일 Step의 실행 시도를 나타내는 객체이다.

하나의 JobExecution은 N개의 StepExecution을 포함할 수 있다.

* 현재 상태: Step이 현재 어떤 상태인지 나타낸다.
* 읽기 / 쓰기 카운트: 성공적으로 읽거나 쓴 아이템의 수
* 커밋 / 롤백 카운트: 트랜잭션 처리 횟수 (청크, 태스크 단위)
* 스킵 카운트: 청크 처리 중 건너뛴 아이템의 수
* 시작 / 종료 시간: Step 실행의 시간적 정보
* 종료 코드: Step 실행의 최종 결과 코드

### JobLauncher

Job, JobParameter를 받아 실행하고, JobExecution을 반환한다.

1. `jobRepository.getLastJobExecution(String jobName, JobParameters jobParameters)`를 실행한다.
    1. jobInstance를 파라미터로 조회하고, 최근의 jobExecution(실행 기록)을 조회한다.
2. restartable 필드(JobBuilder.preventRestart())를 검사하여 Job의 재실행 여부를 검사한다.
3. `jobRepository.createJobExecution`로 JobExecution을 생성한다.
    1. 이미 완료된 JobInstance의 재실행을 막고, 재시작 시 executionContext를 주입받아 상태 복원을 수행한다.
    2. 이로 인해 새로운 실행이지만 이전 상태를 그대로 유지하는 메커니즘을 가진다.
4. `taskExecutor.execute`를 통해 Job을 실행한다.
    1. BatchStatus.STARTED로 변경 후 BATCH_JOB_EXECUTION 테이블에 반영한다.
    2. 사전에 구성된 Step들을 순차 실행시키고, 하나라도 실패하면 실행을 중단한다.
5. 최종적으로 Job이 끝난 뒤 finally에서는 종료 시간을 기록하고, 후처리 이후 최종 상태를 메타 테이블에 반영한다.

### 예시

일일 매출 집계 Job

1. 매출 집계 Step
    1. 전일 주문 데이터를 읽고(Reader)
    2. 결제 완료된 것만 필터링하여(Processor)
    3. 상품별 / 카테고리별로 집계해서 저장(Writer)
2. 알림 발송 Step
    1. 집계 요약 정보를 생성하여 관리자에게 전달
3. 캐시 갱신 Step
    1. 집계된 데이터로 캐시 정보 업데이트

## SpringBatch의 Step

### Tasklet

Step으로 Tasklet을 등록하여 단순 작업, 일회성으로 데이터를 처리

하나의 Step에서 여러 Tasklet을 등록하여 배치 처리를 할 수 있고, RepeatStatus를 반환하여 Tasklet내에서 작업을 세분화할 수 있다.

**구성방법**

Tasklet 인터페이스를 상속받거나, 람다 표현식으로 바로 구현체를 구성하여 Bean을 만들어 다음과 같은 순으로 구성이 가능하다

1. Tasklet or 람다로 구성된 Tasklet 클래스 구성하고, Bean으로 등록
2. StepBuilder를 이용하여 tasklet을 등록하고, Bean으로 등록
3. JobBuilder를 이용하여 Step을 시작하는 Bean 등록

**상태**

* RepeatStatus
    * CONTINUABLE: 작업을 진행하고, 반환이 될 때마다 트랜잭션 커밋을 한다
    * FINISHED: Step이 끝났음을 의미한다

### Chunk

대량의 데이터를 일정 단위로 쪼갠 청크를 대상으로 데이터를 처리한다.

**특징**

SpringBatch에서의 청크 기반 처리는 다음과 같은 의미를 가진다.

가령 100만 건의 데이터를 한 번에 메모리에 로드하고, 처리하고, 저장한다면 애플리케이션 메모리, 연속적인 쓰기작업으로 인한 DB 부하와 같은 치명적인 문제가 발생한다.

1. **메모리 문제 방지**: 청크 기반 처리는 N개씩 데이터를 나누어 메모리에 로드하고, 이들을 대상으로 가공 및 쓰기 작업을 수행한다
2. **청크 단위 트랜잭션**: 100만건이 한번의 트랜잭션으로 처리된다면, 도중 하나라도 실패하는 순간 전체가 롤백될 수 있다. 하지만 청크 단위로 트랜잭션을 나누어 이전 청크까지는 커밋이 완료되고, 이후 청크부터 재시작할 수 있고 일종의 체크포인트를 만들어준다.

**구성방법**

1. StepBuilder를 구성한다.
2. chunk 메서드를 이용하여 청크의 크기를 지정한다.
3. itemReader, itemProcessor, itemWriter를 StepBuilder에 등록한다.
4. JobBuilder에 Step을 등록한다.

**ItemReader**

```java
public interface ItemReader<T> {
    T read() throws Exception,
        UnexpectedInputException,
        ParseException,
        NonTransientResourceException;
}
```

* ItemReader 인터페이스는 Collection 형태로 데이터를 가져오는 것이 아닌 단건으로 데이터를 읽어오고, 읽을 데이터가 없으면 null을 반환하여 스텝을 종료시킨다
    * 실제 구현체에서는 가령 1000건의 데이터를 읽어와서 메모리에 적재해놓고 read 메서드는 한건씩 읽으면서 null이 있는지 확인한다.
* StepExecution의 read_count컬럼
* 파일, 데이터베이스, 메시지 큐 등 다양한 데이터 소스로부터 읽어올 수 있는 구현체를 제공한다.
    * `JdbcCursorItemReader`
        * native 쿼리 작성 필요
        * 배치 처리가 완료될 때까지 커넥션 유지 (DB 커넥션이 오래 연결됨)
        * order by로 순서를 고정하고, 실패 후 재시작 시 수행되는 `jumpToItem`을 대비해야함
    * `JdbcPagingItemReader`
        * native 쿼리 작성 필요
        * 페이지 단위로 커넥션 유지 (DB 커넥션이 오래 연결됨)
        * offset, keyset 기반으로 페이징이 가능하다
    * `JpaCursorItemReader`
        * JpaEntity로 데이터를 읽는다.
        * 전체 데이터를 읽고, 메모리에서 cursor 처리
    * `JpaPagingItemReader`
        * JpaEntity로 데이터를 읽는다.
        * 데이터베이스로부터 페이지 단위로 읽는다.
        * OneToMany(fetch = Lazy)와 같이 연관관계 설정 시 BatchSize처리가 동작하지 않으므로 Eager로 가져와야 한다
            * doReadPage 메서드에서 transacted의 분기에 따라 다음과 같은 로직이 수행된다.
                * `transacted = true`: entityManager를 flush, clear하여 컨텍스트를 비워버린다. 만약 컨텍스트에 변경 예정인 데이터가 있다면 쓰기 작업까지 발생해버린다
            * 따라서 `transacted = false`옵션으로 수행해야한다.
                * 하지만 이 또한 entity를 준영속화(컨텍스트에서 분리)시켜 연관관계 프록시 객체 기능을 사용할 수 없다.
                * 영속성 컨텍스트 > 커넥션 풀 > DB 조회가 안됨
            * 그렇기 때문에 `transacted = true`, Eager 조합으로 데이터를 조회해야한다.
    * `JsonItemReader`
        * .json파일로부터 데이터를 읽는다.
        * Gson, Jackson를 이용하여 object를 파싱한다.
    * `MongoCursorItemReader`
        * 몽고 컬렉션을 커서 기반으로 읽는다.
    * `MongoPagingItemReader`
        * 몽고 컬렉션을 skip 기반으로 읽는다.
        * 대규모 데이터에서는 성능 저하 문제 발생
    * `StaxEventItemReader`
        * stax방식으로 xml 문서를 읽고, 자바 객체로 매핑하여 데이터를 읽는다.
    * `KafkaItemReader`
        * 카프카 컨슈머로 붙어 n개의 메시지를 읽어온다.
    * `AMQPItemReader`
        * rabbitmq로부터 메시지를 읽어옴
    * `FlatFileItemReader`
        * 텍스트 파일을 읽고, 고정 길이, 구분자, 멀티 라인 등 다양한 형식을 파싱할 수 있다.
        * DefaultLineMapper로 라인마다의 데이터를 객체에 매핑한다.

**ItemProcessor**

```java
public interface ItemProcessor<I, O> {
    O process(I item) throws Exception;
}
```

ItemReader로부터 데이터를 받아 가공을 한다.

* 입력 데이터 (I)를 원하는 형태(O)로 변환한다.
* null을 반환하면 처리 흐름에서 제외되고, ItemWriter로 전달되지 않는다.
* 조건에 부합하지 않는 데이터를 만났을 때 예외를 발생시킬 수 있으며, Skip가 사용한다면 발생 데이터만 건너뛰고 배치를 진행할 수 있다.

**ItemWriter**

```java
public interface ItemWriter<T> {
    void write(Chunk<? extends T> chunk) throws Exception;
}
```

ItemProcessor로부터 결과물을 받아 원하는 방식으로 저장 / 출력한다.

* Chunk 단위로 묶어서 데이터를 한번에 쓰기 작업을 한다.
* 파일, 데이터베이스, 외부 시스템 전송 등 다양한 구현체를 제공한다.
    * `JdbcBatchItemWriter`: chunksize만큼 데이터를 모아 jdbcBatchTemplate을 이용하여 벌크로 쿼리를 수행한다.
    * `JpaItemWriter`: EntityManager를 이용하여 item을 쓰기 처리한다.
    * `FlatFileItemWriter`: 파일에 구분자, 라인 등의 옵션을 이용하여 쓰기 처리한다.
    * `StaxEventItemWriter`: xml파일에 데이터를 쓰기 처리한다.
    * `JsonFileItemWriter`: 객체를 json으로 역직렬화하여 .json 파일에 쓰기 처리한다.
    * `MongoItemWriter`: chunk 기반으로 데이터를 벌크로 쓰기 처리한다.
    * `AmqpItemWriter`: rabbitmq queue로 메시지를 퍼블리싱한다.
    * `KafkaItemWriter`: kafka 파티션으로 메시지를 퍼블리싱한다.

## JobParameter

jobParameters는 배치 작업에 전달되는 값이고, 이 값으로 어떤 조건 혹은 어떤 데이터를 처리할지 결정할 수 있다.

```bash
./gradlew bootRun --args='--spring.batch.job.name=batchJob id=10,java.lang.String targetCount=5,java.lang.Integer'
```

1. --spring.batch.job.name으로 job을 찾는다.
2. 이후 key=value 형식의 인자들을 JobParameters로 변환(DefaultJobParameterConverter)
3. JobLauncher가 Job, JobParameters로 실행한다.

### JobParameter 표기법

```
parameterName=parameterValue,parameterType,identificationFlag
```

* parameterName: 배치 Job에서 파라미터를 찾을 때 사용하는 key이다. 이 이름으로 Job 내에서 파라미터에 접근할 수 있다.
* parameterValue: 파라미터의 실제 값
* parameterType: 파라미터의 타입(java.lang.String, java.lang.Integer와 같은 FQCN 사용)
* identificationFlag: 파라미터가 JobInstance 식별에 사용될 파라미터인지 여부를 전달하는 값

### JobParameter 전달 방법

**기본 파라미터 전달 방법**

```kotlin
@Bean
@StepScope // @Value로 잡 파라미터를 받기 위해 사용
fun terminatorTasklet(
    @Value("#{jobParameters['id']}") id: String,
    @Value("#{jobParameters['targetCount']}") targetCount: Int
): Tasklet {
    return Tasklet { contribution, chunkContext ->
        println("jobParameterId: $id")
        println("targetCount: $targetCount")

        for (i in 1..targetCount) {
            println("$i")
        }

        return@Tasklet RepeatStatus.FINISHED
    }
}
```

**POJO를 활용한 전달 방법**

```kotlin
@StepScope
@Component
class Pojo(
    @Value("#{jobParameters['id']}") val id: String,
    @Value("#{jobParameters['targetCount']}") val targetCount: Int
)

@Bean
fun terminatorTasklet(pojo: Pojo): Tasklet {
    return Tasklet { contribution, chunkContext ->
        println("jobParameterId: ${pojo.id}")
        println("targetCount: ${pojo.targetCount}")

        for (i in 1..targetCount) {
            println("$i")
        }

        return@Tasklet RepeatStatus.FINISHED
    }
}
```

### JobParameter 유효성 검사

```java
public interface JobParametersValidator {
    void validate(@Nullable JobParameters parameters) throws JobParametersInvalidException;
}
```

JobParametersValidator 인터페이스를 구현하여 JobBuilder에 등록하여 커스텀한 유효성 검사를 수행할 수 있다.

```kotlin
@Component
class CustomJobParameterValidator: JobParameterValidator {
    override fun validate(parameters: JobParameters) {
        val id = parameters.getLong("id")

        if (id > 10) throw JobParametersInvalidException("id는 10을 넘을 수 없습니다.")
    }
}
```

## Job, Step의 Scope

JobScope, StepScope로 선언된 빈은 애플리케이션 구동 시점에는 프록시 객체로 존재하다 실제 수행을 위해 접근 시 빈이 생성된다.

Lazy로 생성되기 때문에 런타임에 JobParameter를 받아도 주입받고 구동이 가능하며, 여러 Job이 동시에 실행되어도 독립적인 빈으로 사용되어 동시성 문제도 해결할 수 있다.

### JobScope

Job이 실행되고, 종료될 때 제거되며 JobExecution과 생명주기가 같다.

* @JobScope가 등록된 빈은 프록시 객체로 생성되어 Job이 수행될 때 빈이 생성되고, 종료된다.
* 빈이 지연 생성되기 때문에 런타임의 JobParameter를 정확하게 주입할 수 있다.
* 기본 scope은 싱글톤이나 JobScope으로 인해 지연되어 Bean이 생성되므로 여러 스레드가 같은 Job을 실행하더라도 다른 Bean으로 수행되어 동시성 문제를 해소할 수 있다.

### StepScope

JobScope와 마찬가지로 Step 수준에서 격리된 빈을 생성한다.

### 유의 사항

* CGLIB를 이용하여 프록시 객체로 생성되기 때문에 상속 가능한 클래스여야 한다.
* StepBuilder가 존재하는 빈에 StepScope, JobScope를 선언하면 안된다.
    * StepBuilder는 Step을 정의하는 곳이므로 StepScope가 붙어버리면 Job, Step이 활성화되기 전에 프록시 객체에 접근하므로 에러가 발생한다.

## Spring Batch Listener

Listener 인터페이스로 원하는 시점을 제어할 수 있고, 인터페이스 구현이 아닌 어노테이션으로도 제어가 가능하다

### JobExecutionListener

```java
public interface JobExecutionListener {
    default void beforeJob(JobExecution jobExecution) { }
    default void afterJob(JobExecution jobExecution) { }
}
```

Job 실행의 시작과 종료 시점에 호출되는 인터페이스이다.

afterJob은 아직 메타데이터 저장소에 저장되기 전 시점이므로 이를 활용하여 Job의 실행 결과를 제어할 수 있다.

### StepExecutionListener

```java
public interface StepExecutionListener extends StepListener {
    default void beforeStep(StepExecution stepExecution) { }

    @Nullable
    default ExitStatus afterStep(StepExecution stepExecution) {
        return null;
    }
}
```

Step 실행의 시작과 종료 시점에 호출되는 인터페이스이다.

### ChunkListener

```java
public interface ChunkListener extends StepListener {
    default void beforeChunk(ChunkContext context) { }
    default void afterChunk(ChunkContext context) { }
    default void afterChunkError(ChunkContext context) { }
}
```

청크 처리 시작전, 커밋 후, 롤백이 되었을 때 호출되는 리스너 인터페이스이다.

### ItemListener

```java
// ItemReadListener.java
public interface ItemReadListener<T> extends StepListener {
    default void beforeRead() { }
    default void afterRead(T item) { }
    default void onReadError(Exception ex) { }
}

// ItemProcessListener.java
public interface ItemProcessListener<T, S> extends StepListener {
    default void beforeProcess(T item) { }
    default void afterProcess(T item, @Nullable S result) { }
    default void onProcessError(T item, Exception e) { }
}

// ItemWriteListener.java
public interface ItemWriteListener<S> extends StepListener {
    default void beforeWrite(Chunk<? extends S> items) { }
    default void afterWrite(Chunk<? extends S> items) { }
    default void onWriteError(Exception exception, Chunk<? extends S> items) { }
}
```

Reader, Processor, Writer의 수행 시점 호출되는 리스너이다.

## Retry

스프링 배치는 대량의 데이터를 처리하는만큼 재시도, 건너뛰기를 이용하여 내결함성 기능을 제공한다.

### 재시도

내결함성 기능을 활성화하면 RetryTemplate사용하여 재시도를 수행한다.

* canRetry: 사전에 정의된 재시도 정책을 기반으로 결정
    * retryCallback: 최초 실행 부터 재시도까지 모든 시도를 retryCallback을 통해 수행된다.
    * recoveryCallback: 더 이상의 재시도가 불가능할 경우 호출되며, 예외를 던지거나 fallback 로직을 수행한다.

읽어둔 청크를 별도로 저장하고, ItemProcessor, ItemWriter로직이 실패할 경우 retryCallback으로 청크 처리가 수행된다.

1. ItemProcessor, ItemWriter 예외 발생
2. chunk 재개
3. canRetry?
4. retryCallback or recoveryCallback

단, 롤백이 되더라도 processor, writer에서 외부 api 콜이 엮여있는 상황이라면 전체를 재처리할 것인지 고민을 해야한다

### RetryPolicy

* 발생한 예외가 사전에 지정된 예외 유형인가
* 현재 재시도 횟수가 임계치를 초과하였는가

## Skip

건너뛰기(recoveryCallback)는 예외가 발생한 레코드를 건너뛰어 중단없이 계속 진행하게 한다.

* skip: 건너뛸 예외를 지정
* skipLimit: 스텝에서 허용할 건너뛰기 임계치를 지정한다.
    * stepLimit = 2라면 step retry가 1번까지 수행하고, 2번째부터 Exception을 발생시킨다.

Processor에서는 null을 반환하여 필터를 하고, Writer에서는 스캔 모드로 변경되어 개별 아이템을 커밋하고, 예외가 발생한 아이템은 건너뛴다.

=> Writer의 경우 1000건의 chunk에 오류가 발생하면 크나큰 성능 저하가 발생하므로 미리 전처리를 하는 것이 중요하다.

### SkipListener

Spring batch는 SkipListener 인터페이스를 통해 각 단계별로 스킵된 아이템을 추적 관찰할 수 있다.

## 스프링 배치 격리 수준

기본적으로는 application.yml에서 `spring.batch.jdbc.isolation-level-for-create`로 커스텀하게 설정이 가능하다

그리고 이 설정이 적용되는 곳은 `BatchAutoConfiguration`이다

만약 설정값이 Null이라면 다음과 같이 설정된다

```java
// BatchAutoConfiguration.java
@Override
protected Isolation getIsolationLevelForCreate() {
    Isolation isolation = this.properties.getJdbc().getIsolationLevelForCreate();
    return (isolation != null) ? isolation : super.getIsolationLevelForCreate();
}

// super.getIsolationLevelForCreate()
// DefaultBatchConfiguration.java
protected Isolation getIsolationLevelForCreate() {
    return Isolation.SERIALIZABLE;
}
```

이렇게 설정되는 이유는 배치 테이블들 간의 물리적인 외래키, 상태 관리를 위해 엄격한 격리 수준을 사용하기 때문이다
