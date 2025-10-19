# [Concurrency] 코루틴(Coroutine)의 원리와 동작 방식

- **Tags:** #Coroutine #Concurrency #Asynchronous #Kotlin #CPS

---

### 무엇을 배웠는가?
코루틴은 함수(서브루틴)의 실행을 `중단(suspend)`했다가 원할 때 `재개(resume)`할 수 있게 해주는 프로그래밍 패턴입니다.   
이는 운영체제가 아닌 프로그래밍 언어 차원에서 **비선점형(Non-preemptive)** 방식으로 동작을 제어하며 '협력형 멀티태스킹'을 가능하게 합니다.

#### 1. 선점형
실행중인 프로세스의 CPU를 강제로 해제하고, 높은 우선순위의 프로세스나 다른 특정 이벤트가 발생했을 때 CPU를 할당합니다.   
가령 프로세스가 CPU를 점유한 상태에서 다른 프로세스가 CPU를 요청하면 현재 프로세스를 중단하고, 새로운 프로세스가 점유할 수 있습니다.

#### 2. 비선점형
CPU를 할당받은 프로세스가 CPU를 스스로 반납할 때까지 해당 프로세스가 계속 CPU를 사용합니다.   
가령 한번 할당된 프로세스는 CPU가 끝날 때까지 다른 프로세스에 의해 방해받지 않습니다.

---

### 왜 중요하고, 어떤 맥락인가?
전통적인 동시성 처리 방식에는 명확한 한계가 존재했습니다.

1.  **동기식 처리 (1-Thread)**: 하나의 스레드가 모든 서브루틴을 순서대로 처리하면, I/O 대기 같은 작업에서 스레드가 멈춰(blocking) 불필요한 대기 시간이 발생하고 비효율적입니다.
2.  **비동기식 처리 (Multi-Thread)**: 여러 스레드를 사용해 대기 시간을 줄일 수 있지만, 두 가지 큰 비용이 발생합니다.
    * **콜백 헬 (Callback Hell)**: `subroutine1`의 결과가 `subroutine2`에 필요한 경우, 콜백을 중첩해서 사용해야 합니다. 이는 코드의 의존성을 복잡하게 만들고 가독성을 심각하게 떨어뜨립니다.
    * **컨텍스트 스위칭 비용**: 스레드는 OS가 관리하는 비싼 자원입니다. CPU가 한 스레드에서 다른 스레드로 작업을 전환할 때마다, 스레드의 상태(레지스터, 스택 등)를 저장하고 복원하는 **컨텍스트 스위칭**이 발생합니다. 이 과정은 CPU 캐시 미스를 유발하여 성능 저하를 일으킵니다.
    
**코루틴은** 스레드를 멈추지 않고(non-blocking) 스레드를 해제(release)함으로써 컨텍스트 스위칭 비용을 없애고, 비동기 코드를 동기식 코드처럼 순차적으로 작성할 수 있게 하여 이 두 가지 문제를 모두 해결합니다.

---

### 상세 내용

#### 1. 핵심 원리: Continuation (CPS)
일반 함수는 `return`을 만나면 호출부로 복귀하며 모든 상태를 잃습니다.    
하지만 코루틴(`suspend` 함수)은 `return` 대신, `Continuation`이라는 콜백 객체에 현재 실행 상태를 저장하고 중단합니다.

* **`Continuation` (지속)**: 중단된 시점의 실행 상태를 캡처하는 객체입니다.
    * **중단 지점(label)**: 함수 내의 어느 지점에서 멈췄는지 저장합니다.
    * **로컬 변수**: 중단 시점의 모든 로컬 변수를 클래스 필드처럼 저장합니다.

#### 2. 컴파일러의 변환: 상태 머신 (State Machine)
컴파일러는 `suspend` 함수를 **상태 머신(State Machine)**으로 변환합니다. 이는 `Continuation`에 저장된 `label` 값을 기반으로 `when` 문을 실행하는 것과 유사합니다.

```kotlin
// 원본 코드
suspend fun example() {
    val a = fetchA() // 중단점 1
    val b = fetchB() // 중단점 2
    return a + b
}

// 컴파일러가 변환한 개념적 코드
fun example(continuation: Continuation): Any? {
    when (continuation.label) {
        0 -> { // 시작
            continuation.label = 1
            return fetchA(continuation) // fetchA 호출 후 중단
        }
        1 -> { // fetchA 완료 후 재개
            val a = continuation.result
            continuation.label = 2
            return fetchB(continuation) // fetchB 호출 후 중단
        }
        2 -> { // fetchB 완료 후 재개
            val b = continuation.result
            return a + b // 최종 결과 반환
        }
    }
}
```

코루틴이 재개될 때마다 label을 확인하여 정확히 중단된 지점부터 실행을 이어갑니다. 

#### 3. 코루틴의 주요 구성 요소
**CoroutineScope (범위)**

코루틴의 생명주기를 관리합니다.    
모든 코루틴은 특정 스코프 내에서 실행되어야 하며, 스코프가 취소(cancel)되면 그 안의 모든 코루틴도 함께 취소됩니다.  (e.g., 안드로이드의 viewModelScope)

**CoroutineContext (문맥)**

코루틴의 실행 환경을 정의하는 요소들의 집합이고, 모든 코루틴은 이 컨텍스트 안에서 실행도비니다.
* Job: 코루틴의 생명주기(상태)를 나타내는 객체입니다. cancel, join 등이 가능하며 , 부모-자식 간의 계층 구조를 가집니다.
* Dispatcher: 코루틴을 어떤 스레드에서 실행할지 결정합니다.
  * Dispatchers.IO: 네트워크 통신, 파일 읽기/쓰기 등 I/O 작업에 최적화된 스레드 풀. 기본 64개
    ```kotlin
    @Service
    class ExternalApiService {
        // 외부 API 호출 - I/O 작업
        suspend fun fetchUserData(userId: String): UserData = 
            withContext(Dispatchers.IO) {
                httpClient.get("https://api.example.com/users/$userId")
            }
    }
    ```
  * Dispatchers.Default: 대용량 데이터 정렬, JSON 파싱 등 CPU 집약적인 작업에 최적화된 스레드 풀 (CPU 코어 수만큼).
    ```kotlin
        @Service
        class DataProcessingService {
            // 대용량 데이터 정렬
            suspend fun sortLargeDataset(data: List<Record>): List<Record> = 
                withContext(Dispatchers.Default) {
                    data.sortedWith(compareBy({ it.timestamp }, { it.id }))
                }
            
            // 암호화 작업
            suspend fun encryptData(data: ByteArray): ByteArray = 
                withContext(Dispatchers.Default) {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                    cipher.doFinal(data)
                }
            
            // JSON 파싱 (대용량)
            suspend fun parseJson(json: String): List<Item> = 
                withContext(Dispatchers.Default) {
                    Json.decodeFromString(json)
                }
            
            // 이미지 처리
            suspend fun resizeImage(image: BufferedImage, width: Int, height: Int): BufferedImage = 
                withContext(Dispatchers.Default) {
                    val resized = BufferedImage(width, height, image.type)
                    val graphics = resized.createGraphics()
                    graphics.drawImage(image, 0, 0, width, height, null)
                    graphics.dispose()
                    resized
                }
        }
    ```  
  * Dispatchers.Unconfined: 특정 스레드에 종속되지 않고, 현재 실행 중인 스레드에서 즉시 실행합니다.
  * limitedParallelism: 병렬로 실행할 코루틴의 최대 개수를 제한합니다.
    * 스레드 사용을 제한하는 이유는 오래 걸리는 연산을 끊임없이 처리한다면 비교적 빠른 연산이라던지, 다른 연산들의 실행이 안되는 상황이 올 수 있기 때문에 이를 방지하기 위한 효과가 있다.
    * 다만, Default의 경우 공유 스레드 풀의 Default로 사용가능한 스레드 중 일부를 제한하지만 IO의 경우 공유 스레드 풀에서 스레드를 가져와 새로운 풀을 만들어내고, 부족할 경우 새로운 스레드를 생성한다.
* CoroutineExceptionHandler: 코루틴 내에서 처리되지 않은 예외를 다룹니다.
  * SupervisorScope: SupervisorJob을 이용하여 Scope를 만든 코루틴 스코프 빌더입니다. 
  * SupervisorJob: 자식 코루틴의 실패(예외)가 부모나 다른 형제 코루틴으로 전파되는 것을 막는 특별한 Job입니다.
* CoroutineName: 디버깅을 위한 코루틴의 이름입니다. 

### 핵심
코루틴은 스레드를 `블로킹(Blocking)`하는 대신 `중단(Suspending)`시킴으로써, 해당 스레드를 즉시 다른 작업에 사용할 수 있도록 해제합니다.  
suspend 함수는 컴파일러가 `상태 머신`과 `Continuation(CPS)`을 이용해 비동기 코드를 순차적인 코드로 변환해준 결과입니다.

구조화된 동시성(Structured Concurrency) (CoroutineScope + Job)을 통해 코루틴의 생명주기를 명확하게 관리하고, 예외 전파를 제어하여 메모리 누수나 좀비 프로세스를 방지할 수 있습니다.