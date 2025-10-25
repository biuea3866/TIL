# Java NIO

## 1. Blocking I/O
I/O는 외부(파일, 네트워크 등)에서 데이터를 읽어오는 것이고, 외부로 데이터를 내보는 것이다.   
Blocking I/O는 I/O를 수행하는 스레드가 읽어오고, 내보내는 동안 멈춰서 기다리는 것을 의미한다.  

```
1. InputStream.read()
2. 데이터가 도착할 때 까지 BLOCKED 상태
3. 데이터 도착
4. read 반환
5. 블로킹 해제 (RUNNABLE)
```
BLOCKED 상황에서 스레드는 CPU를 사용하지는 않지만, 지속적으로 메모리를 차지한다.  
외에도 java.io 패키지에서는 다음과 같이 동기적으로 동작하는 클래스들이 존재한다.

```
InputStream (추상 클래스)
├─ FileInputStream (파일 읽기)
├─ ByteArrayInputStream (메모리에서 읽기)
└─ FilterInputStream
    └─ BufferedInputStream (버퍼링)

OutputStream (추상 클래스)
├─ FileOutputStream (파일 쓰기)
├─ ByteArrayOutputStream (메모리에 쓰기)
└─ FilterOutputStream
    └─ BufferedOutputStream (버퍼링)
```

즉, I/O 과정에서 스레드는 지속적으로 블로킹이 되고, 불필요하게 메모리를 차지하는 등 자원을 효율적으로 사용하지 못한다.

## 2. NIO
Blocking I/O는 하나의 요청에 하나의 스레드를 할당하는 구조이다.   
하나의 요청을 하나의 스레드가 처리함에 따라 불필요한 리소스 낭비가 발생하고, 이것으로 인해 NIO가 등장하게 되었다.   

```
전통적 I/O (Blocking):
클라이언트1 → 스레드1
클라이언트2 → 스레드2
클라이언트3 → 스레드3

NIO (Non-blocking):

         ┌  channel → buffer
Selector ├─ channel → buffer
         └  channel → buffer
```

#### 1. buffer
* 데이터를 임시로 저장하는 메모리 공간
* NIO에서는 모든 데이터가 Buffer를 통해서 이동

#### 2. channel
* 데이터가 흐르는 통로
* Buffer를 통해서만 데이터 읽기 / 쓰기 가능

#### 3. selector
* 여러 Channel을 하나의 스레드로 감시
* 누가 I/O 준비가 되었는지 감시
* Multiplexing

