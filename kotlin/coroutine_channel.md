# Channel

코루틴 채널은 코루틴 간 데이터를 주고받기 위한 통신 수단이다. 보통 사용하는 메시지 큐와 비슷하며, Channel Type에 따라 백프레셔도 구현이 가능하며, 무작정 push만 하는 통신 수단은 아니다.

## Channel 타입

```kotlin
public fun <E> Channel(
    capacity: Int = RENDEZVOUS,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    onUndeliveredElement: ((E) -> Unit)? = null
): Channel<E> =
    when (capacity) {
        RENDEZVOUS -> {
            if (onBufferOverflow == BufferOverflow.SUSPEND)
                BufferedChannel(RENDEZVOUS, onUndeliveredElement) // an efficient implementation of rendezvous channel
            else
                ConflatedBufferedChannel(1, onBufferOverflow, onUndeliveredElement) // support buffer overflow with buffered channel
        }
        CONFLATED -> {
            require(onBufferOverflow == BufferOverflow.SUSPEND) {
                "CONFLATED capacity cannot be used with non-default onBufferOverflow"
            }
            ConflatedBufferedChannel(1, BufferOverflow.DROP_OLDEST, onUndeliveredElement)
        }
        UNLIMITED -> BufferedChannel(UNLIMITED, onUndeliveredElement) // ignores onBufferOverflow: it has buffer, but it never overflows
        BUFFERED -> { // uses default capacity with SUSPEND
            if (onBufferOverflow == BufferOverflow.SUSPEND) BufferedChannel(CHANNEL_DEFAULT_CAPACITY, onUndeliveredElement)
            else ConflatedBufferedChannel(1, onBufferOverflow, onUndeliveredElement)
        }
        else -> {
            if (onBufferOverflow === BufferOverflow.SUSPEND) BufferedChannel(capacity, onUndeliveredElement)
            else ConflatedBufferedChannel(capacity, onBufferOverflow, onUndeliveredElement)
        }
    }
```

### 1. capacity - RENDEZVOUS

capacity를 0으로 만들고, BufferOverflow를 처리할 때까지 대기 옵션으로 만든다. 즉, BufferedChannel엔 데이터가 쌓이지 않고, 소비자가 바로바로 처리하지 않는 이상 Channel은 데이터를 받지 않는다.

### 2. capacity - UNLIMITED

capacity는 정수 최대 수이며, BufferOverflow 정책이 존재하지 않는다. Channel에 데이터를 계속 생산할 수 있으나 소비자가 생산량을 따라가지못하면 oom이 발생할 수 있다.

### 3. capacity - CONFLATED

capacity는 1로 만들고, BufferOverflow는 항상 최신의 데이터만 유지하는 정책이다. 아무리 데이터를 생산해도 소비 시점에는 최신의 데이터만 구독할 수 있다.

### 4. capacity - BUFFERED

기본값인 64로 capacity를 구성하는데, 이 때의 BufferOverflow는 처리할 때까지 대기 정책이어야 한다. 그렇지 않다면, 항상 최신이거나 최신의 데이터를 제거하는 정책으로 Channel을 구성한다. 전자라면, capacity값이 전부 찼을 때 소비처에서 데이터를 소비할 수 있다.

## Channel 연산

### 1. send() / receive()

- send(): Channel로 메시지를 보내는 함수이다.
- receive(): Channel로부터 메시지를 읽는 함수이다.
- 이 메서드들이 수행될 때는 suspend 함수이므로 일시 중단된다.

### 2. trySend() / tryReceive()

- trySend(): suspend가 걸려있지 않고, Channel로 메시지를 보내는 함수이다.
- tryReceive(): suspend가 걸려있지 않고, Channel로부터 메시지를 읽는 함수이다.
- 만약 trySend후 반환값인 isSuccess가 false라면 버퍼가 가득찼음을 의미한다.
- 즉, 이 함수는 buffer의 capacity가 1이상이어야 만 동작하는 함수이다.

### 3. close()

- close(): 채널을 닫는 함수이다. 채널을 취소하여 내부에 버퍼링된 요소들도 전부 제거한다. 송신이 전부 완료된 후 채널을 닫을 때 사용한다. (그리고 finally)

---

# 발행 - 구독 패턴

### 1. Produce

```kotlin
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true) {
        send(x++)
        delay(100)
    }
}

fun CoroutineScope.square(numbers: ReceiveChannel<Int>) = produce<Int> {
    for (x in numbers) {
        send(x * x)
    }
}

fun main() = runBlocking {
    val numbers = produceNumbers()  // 1, 2, 3, 4, 5, ...
    val squares = square(numbers)   // 1, 4, 9, 16, 25, ...

    repeat(5) {
        println(squares.receive())
    }

    coroutineContext.cancelChildren()  // 모든 자식 취소
}
```

```
1
4
9
16
25
```

### Fan-out

하나의 producer가 메시지를 발행하면, 여러 consumer가 메시지를 구독하고 처리하는 방식이다.

```kotlin
fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true) {
        send(x++)
        delay(100)
    }
}

fun CoroutineScope.launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
    for (msg in channel) {
        println("Processor #$id received $msg")
    }
}

fun main() = runBlocking {
    val producer = produceNumbers()

    repeat(5) { launchProcessor(it, producer) }

    delay(1000)
    producer.cancel() // 취소안하면 무한루프로 계속 돎
}
```

```
Processor #0 received 1
Processor #1 received 2
Processor #2 received 3
Processor #3 received 4
Processor #4 received 5
Processor #0 received 6
```

### Fan-in

여러 Producer가 발행한 메시지를 하나의 Consumer가 메시지를 수신하는 방식이다.

```kotlin
suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
        delay(time)
        channel.send(s)
    }
}

fun main() = runBlocking {
    val channel = Channel<String>()

    launch { sendString(channel, "foo", 200L) }
    launch { sendString(channel, "BAR!", 500L) }

    repeat(6) {
        println(channel.receive())
    }

    coroutineContext.cancelChildren()
}
```

```
foo
foo
BAR!
foo
foo
BAR!
```

코드를 보면 알 수 있듯이, 기본 동작은 channel을 만들어 놓고 send(element)를 넣어서 Channel 버퍼에 데이터를 쌓아놓고, receive 함수를 호출하여 원하는 타이밍에 fan-out 형태로 수신자를 만들어 병렬 처리할 수 있다. 다만, 이 케이스에는 순서 보장이 안되므로 염두에 두어야 한다.

---

# Flow vs Channel

### Flow?

Flow는 코루틴에서 비동기 데이터 스트림을 처리하기 위한 **Cold Stream**이다. collect()가 호출될 때마다 새롭게 데이터를 방출한다.

```kotlin
fun numberFlow(): Flow<Int> = flow {
    for (i in 1..5) {
        delay(100)
        emit(i)
    }
}

fun main() = runBlocking {
    numberFlow().collect { println(it) }
}
```

### Flow vs Channel 비교

| 항목 | Flow | Channel |
|------|------|---------|
| 스트림 타입 | **Cold** (collect 시 시작) | **Hot** (생성 시 시작) |
| 생산자-소비자 | 1:1 (단일 수집자) | N:M (다중 생산자/소비자) |
| 백프레셔 | 자동 (suspend 기반) | capacity/BufferOverflow로 제어 |
| 생명주기 | collect 범위 내에서만 동작 | 명시적으로 close() 필요 |
| 상태 공유 | 불가 (매번 새로운 스트림) | 가능 (공유 버퍼) |
| 사용 시나리오 | DB 조회, API 호출 등 순차 데이터 | 코루틴 간 실시간 통신 |

### 언제 무엇을 쓸까?

* **Flow**: 데이터를 순차적으로 변환/처리할 때 (map, filter, flatMap 등 연산자 체이닝). 단일 소비자가 데이터를 수집하는 경우.
* **Channel**: 여러 코루틴이 동시에 데이터를 주고받아야 할 때. Fan-out/Fan-in 패턴이 필요한 경우. 이벤트 기반 통신이 필요한 경우.

```kotlin
// Flow: 데이터 변환 파이프라인에 적합
suspend fun processUsers() {
    userRepository.findAll()       // Flow<User>
        .filter { it.isActive }
        .map { it.toDto() }
        .collect { sendResponse(it) }
}

// Channel: 코루틴 간 통신에 적합
suspend fun workerPool() = coroutineScope {
    val tasks = Channel<Task>(capacity = 64)

    // 여러 워커가 하나의 채널에서 태스크를 가져감 (Fan-out)
    repeat(4) { workerId ->
        launch { for (task in tasks) process(workerId, task) }
    }

    // 태스크 발행
    taskSource.forEach { tasks.send(it) }
    tasks.close()
}
