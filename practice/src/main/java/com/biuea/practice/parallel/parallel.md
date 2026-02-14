# Java Parallel Stream

Java 8에서 도입된 Parallel Stream은 `ForkJoinPool` 프레임워크를 기반으로 데이터 소스를 병렬로 처리합니다.

## 핵심 개념

### 1. ForkJoinPool.commonPool()
* 병렬 스트림은 기본적으로 JVM 전체에서 공유되는 `commonPool`을 사용합니다.
* 별도의 설정이 없다면 `Runtime.getRuntime().availableProcessors() - 1` 만큼의 스레드를 가집니다.

### 2. Work Stealing 알고리즘
* 각 워커 스레드는 자신의 작업을 처리하다가 한가해지면 다른 스레드의 덱(Deque)에서 작업을 훔쳐와서 처리합니다.
* 이를 통해 스레드 가동률을 극대화합니다.

## 주의사항 및 성능 최적화

### Common Pool 공유의 위험성
* `commonPool`은 애플리케이션 내의 모든 병렬 스트림 작업이 공유합니다.
* 만약 특정 작업이 I/O Blocking 등으로 스레드를 점유하면 다른 병렬 작업들도 멈추게 되는 사이드 이펙트가 발생합니다.
* **해결책:** 블로킹 작업은 별도의 `ForkJoinPool`을 생성하여 실행하거나 병렬 스트림 사용을 지양해야 합니다.

### 언제 사용해야 하는가? (NQ 모델)
* `N` (데이터 개수) 가 많고 `Q` (데이터당 처리 비용) 가 높을 때 유리합니다.
* 단순한 연산(예: sum)은 데이터가 수백만 건 이상이어야 병렬화 오버헤드를 극복할 수 있습니다.
* `ArrayList`, `IntStream.range` 처럼 분할이 쉬운 데이터 구조에서 효율적입니다. (`LinkedList` 등은 분할 비용이 커서 효율이 낮음)

## 예시 코드
```java
ForkJoinPool customPool = new ForkJoinPool(4);
try {
    customPool.submit(() -> 
        list.parallelStream().forEach(item -> {
            // 비즈니스 로직
        })
    ).get();
} finally {
    customPool.shutdown();
}
```
