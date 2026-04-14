# Java I/O Stream

## Stream

스트림은 외부 -> 내부, 내부 -> 외부로 데이터가 이동하게 되는데, 이 때 이동 통로이다.
단방향으로 데이터가 흐르게 되며, 데이터를 입력받을 때는 InputStream, 데이터를 출력할 때는 OutputStream이라 한다.

Stream은 일종의 버퍼로 특정 길이 만큼의 데이터를 읽고, 처리하고 등의 과정을 거치도록 해준다.
만약 Stream 없이 파일 데이터를 한번에 메모리에 로드할 경우 OOM을 유발할 수 있지만 Stream은 이 파일 데이터를 가령 한줄씩 읽는다던지의 과정을 통해 OOM을 방지할 수 있다.

자바에서는 InputStream, OutputStream 인터페이스를 제공하고, 다양한 곳에서 구현체를 만들고 제공하고있다.

## InputStream, OutputStream

### InputStream

바이트 기반 입력 스트림이다.
파일, 네트워크 등 외부 소스로부터 데이터를 순차적으로 읽으며, 동기적으로 동작한다.

* `abstract int read(byte b[])`: 특정 바이트만큼 데이터를 읽어온다.
* `public byte[] readNBytes(int len)`: len만큼의 바이트를 InputStream으로부터 읽어온다. 잘못 사용하면 모든 바이트를 읽어오므로 OOM을 유발할 수 있다.
* `public long skip(long n)`: n만큼의 바이트를 버리고, 바이트를 읽어온다.
* `public void close`: Stream을 닫는다.
* `public synchronized void mark(int readlimit)`: 현재 어디까지 읽었는지 마킹하고, 다음번에 어디서부터 읽을지를 참조한다.
* `public long transferTo(OutputStream out)`: OutputStream을 전달받아 InputStream으로 파일을 읽고, 바로 OutputStream에 버퍼 만큼씩 데이터 쓰기작업을 한다.

### OutputStream

바이트 기반 출력 스트림이다.
특정 바이트만큼씩 메모리에 올리고, 파일 등을 생성한다.

* `public abstract void write(int b)`: b만큼씩 OutputStream에 바이트를 담아 클라이언트에게 응답한다.
* `public void flush()`: 메모리에 적재된 바이트를 플러시한다.
* `public void close()`: 스트림을 닫는다.

## 서버가 API로 Stream을 응답받는 것이란?

api에 Stream 형태로 요청을 받아 처리하는 방식이 있다.
이는 클라이언트로부터 n byte 씩 계속해서 데이터를 전달받는 것이며, 해당 작업이 끝나야 api가 마무리된다.

서버는 Stream을 받고, 내부적으로 데이터를 전부 풀고 가공을 하고 등의 방식을 진행할 수 있다.
또는 외부 S3와 같은 저장소에 Stream을 그대로 던져서 클라이언트 -> 서버 -> S3와 같이 데이터를 안정적으로 처리하는 방법 또한 존재한다.

## 클라이언트가 API로 Stream을 응답받는 것이란?

파일을 클라이언트에게 응답할 경우 Stream형태로 데이터를 응답한다.
Stream은 일정 크기만큼씩 읽어오게 하는 장치로 마지막까지 바이트 데이터를 응답을 해야 완벽한 byte array를 클라이언트에게 제공할 수 있다.

클라이언트에게 응답값으로 Stream을 제공한다는 것은 클라이언트 <> 서버 간 마지막까지 바이트 데이터를 메모리 효율적으로 응답받을 수 있게 하는 장치이지만 중간에 요청이 끊기면 미완의 byte array를 응답받음을 의미한다.
실제 예시로 브라우저에서 파일 다운로드 api를 호출하게 되면, 로딩바가 돌아가면서 완료가 되어야 최종적인 파일을 받을 수 있고, 중간에 끊기면 깨진 파일이라는 경고를 받게 된다.
이것이 Stream 응답에 대한 예시이다.

그래서 간혹가다 `OutputStream.toByteArray()`를 하고, 클라이언트에게 응답하는 코드들이 존재하는데 이는 결국 메모리에 모든 byte array를 올리는 방식이므로 주의해야한다.

---

## Java I/O vs NIO

| 항목 | java.io (Stream) | java.nio (Channel + Buffer) |
|------|:-----------------:|:---------------------------:|
| 방향 | 단방향 (Input/Output 분리) | 양방향 (Channel 하나로) |
| 동작 방식 | 블로킹 | 논블로킹 가능 (Selector) |
| 데이터 단위 | 바이트/문자 스트림 | Buffer 기반 |
| 동시 연결 | 연결당 스레드 필요 | Selector로 다중 채널 관리 |
| 적합한 경우 | 파일 I/O, 간단한 네트워크 | 대량 동시 연결 (서버) |

```java
// java.io - 블로킹, 스레드당 연결
InputStream in = socket.getInputStream();
int data = in.read();  // 데이터 올 때까지 스레드 블로킹

// java.nio - 논블로킹, Selector로 다중 관리
SocketChannel channel = SocketChannel.open();
channel.configureBlocking(false);
Selector selector = Selector.open();
channel.register(selector, SelectionKey.OP_READ);
// Selector가 이벤트 감지 → 데이터 준비된 채널만 처리
```

Netty, Spring WebFlux 등의 고성능 네트워크 프레임워크는 내부적으로 java.nio를 기반으로 동작한다.
