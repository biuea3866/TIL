# Netty

## 등장 배경

### 전통적인 Java I/O

* java.io 패키지는 블로킹 I/O 방식이고, 클라이언트마다 스레드를 할당하는 방식
* 대량의 스레드 생성으로 인해 메모리 부족 및 컨텍스트 스위칭 오버헤드가 발생

### Java NIO(New I/O) 등장

* Non-blocking I/O와 Selector를 통한 효율적인 처리
* 하지만 저수준의 API이기 때문에 버퍼 관리, 채널 설정, Selector 처리등 보일러 플레이트 코드 과다

### Netty

* 비동기 이벤트 기반 네트워크 애플리케이션 프레임워크 등장
* 수만 ~ 수십만 동시 연결 처리 가능

## 정의

Netty는 비동기 이벤트 기반의 고성능 네트워크 애플리케이션 프레임워크이다.

* Java NIO를 기반으로 한 추상화 레이어 제공
* 이벤트 루프 기반의 아키텍처
* 채널 파이프라인을 통한 데이터 처리
* 다양한 프로토콜 지원 (HTTP, WebSocket, TCP, UDP 등)

## 구성 요소

* Channel: 네트워크 소켓 추상화
* EventLoop & EventLoopGroup: 이벤트 처리 스레드 풀
* ChannelHandler: 비즈니스 로직 처리
* ChannelPipeline: 핸들러 체인
* ByteBuf: 고성능 바이트 버퍼
* Bootstrap & ServerBootstrap: 서버 / 클라이언트 설정
* ChannelFuture: 비동기 작업 결과
* Codec: 인코더 / 디코더

## 구성 요소별 설명

### EventLoopGroup

EventLoop들을 관리하는 컨테이너 역할을 한다. 일종의 스레드 풀이고, EventLoop는 스레드로 생각할 수 있다.

* BossEventLoopGroup: 새 연결(accept)을 처리하는 역할을 하며, 보통 1개이다.
* WorkerEventLoopGroup: 연결된 채널들에 대해 read / write를 수행한다.

### EventLoop

1 Selector, 1 Thread, 1 TaskQueue로 구성된다. 하나의 스레드가 EventLoop를 실행시킨다. while문을 돌면서 계속해서 I/O 이벤트를 감지하고, 이 이벤트를 TaskQueue로 넘긴다. 그리고 Channel은 이 Event들을 할당받아 네트워크 처리를 한다.

### Channel

네트워크 소켓에 대한 추상화 인터페이스이다.

* ServerSocketChannel: 연결 수락을 담당하는 ChannelSocket
* SocketChannel: 실제 통신을 담당

#### 주요 구현체

* NioSocketChannel: 클라이언트 TCP 소켓
* NioServerSocketChannel: 서버 TCP 소켓
* NioDatagramChannel: UDP 소켓
* EpollSocketChannel: Linux epoll 기반

#### 주요 메서드

* `channel.writeAndFlush(message)`: 데이터 전송
* `channel.close()`: 연결 종료
* `channel.pipeline()`: 파이프라인 접근
* `channel.isActive()`: 연결 상태 확인

#### 특징

* 읽기 / 쓰기 작업은 항상 비동기 처리
* 자동으로 이벤트를 발생시켜 핸들러에 전달

### ChannelPipeline

ChannelHandler들의 연결리스트

Inbound: Socket → Handler1 → Handler2 → Handler3 → Business Logic

Outbound: Business Logic → Handler3 → Handler2 → Handler1 → Socket

```kotlin
channel.pipeline().apply {
   it.addLast("decoder", StringDecoder())
   it.addLast("encoder", StringEncoder())
   it.addLast("logger", LoggingHandler())
   it.addLast("handler", MyBusinessHandler())
}
```

* 동적으로 핸들러 추가 / 제거 가능
* 각 핸들러는 독립적으로 동작
* 인터셉터와 유사

### ChannelHandler

실제 비즈니스 로직을 처리하는 컴포넌트

```kotlin
// 인바운드 핸들러 (데이터 수신)
class InboundHandler: ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        // 데이터 처리
        ctx.fireChannelRead(msg) // 다음 핸들러로 전달
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        // 에러 처리
        cause.printStackTrace()
        ctx.close()
    }
}

// 아웃바운드 핸들러 (데이터 전송)
class OutboundHandler: ChannelOutboundHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        // 데이터 전송 전 처리
        ctx.write(msg, promise)
    }
}
```

### ByteBuf

Netty의 고성능 바이트 버퍼

Java ByteBuffer에선 1) 단일 인덱스로 읽기 / 쓰기로 위치를 관리하고, 2) 크기 조정이 불가능하며, 3) 사용하기 어렵다는 단점이 존재하여 ByteBuf는 이를 대체한다.

```kotlin
val buffer = Unpooled.buffer(256)
// write, read로 분리된 인덱스
buffer.writeInt(42) // writerIndex 증가
val value = buffer.readInt() // readerIndex 증가

// 크기 조정
buffer.capacity(512)

// 참조 카운팅
buffer.retain() // 참조 증가
buffer.release() // 참조 감소, 0이 되면 메모리 해제
```

#### 버퍼 타입

* Heap Buffer: JVM 힙 메모리 (GC 대상)
* Direct Buffer: JVM 외부 메모리 (Zero-Copy 가능)
* Composite Buffer: 여러 버퍼를 논리적으로 혼합

### Bootstrap & ServerBootstrap

서버 / 클라이언트 애플리케이션 설정 및 시작

#### ServerBootstrap (서버)

```kotlin
val bootstrap = ServerBootstrap()
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel::class.java)
    .childHandler(object: ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel) {
            ch.pipeline().apply {
                it.addLast(MyDecoder())
                it.addLast(MyEncoder())
                it.addLast(MyHandler())
            }
        }
    })
    .option(ChannelOption.SO_BACKLOG, 128)
    .childOption(ChannelOption.SO_KEEPALIVE, true)

val future = bootstrap.bind(8080).sync()
```

#### Bootstrap (클라이언트)

```kotlin
val bootstrap = Bootstrap()
bootstrap.group(workerGroup)
    .channel(NioSocketChannel::class.java)
    .handler(object: ChannelInitializer<SocketChannel>() {
        override fun initChannel(ch: SocketChannel) {
            ch.pipeline().apply {
                it.addLast(MyClientHandler())
            }
        }
    })

val future = bootstrap.connect("localhost", 8080).sync()
```

### ChannelFuture

비동기 작업 결과를 나타내는 future

```kotlin
val future = channel.writeAndFlush(message)

future.addListener(object: ChannelFutureListener() {
    override fun operationComplete(f: ChannelFuture) {
        if (f.isSuccess()) println("전송 성공")
        else {
            println("전송 실패")
            f.cause().printStackTrace()
        }
    }
})
```

### Codec

메시지 인코딩 / 디코딩을 처리하는 핸들러

* StringEncoder / StringDecoder: 문자열 처리
* LineBasedFrameDecoder: 줄 단위로 메시지 분리
* LengthFieldBasedFrameDecoder: 길이 필드 기반 프레임 분리
* HttpServerCodec: HTTP 프로토콜
* WebSocketServerProtocolHandler: WebSocket

## 동작 원리

### 전체 구조

```
클라이언트 (Client 1, 2, 3, N...)
        │
        ▼ TCP 연결
Boss EventLoopGroup (1 thread)
  └─ Boss EventLoop (연결 수락 전담)
        │ 채널 할당
        ▼
Worker EventLoopGroup (N threads)
  ├─ Worker EventLoop 1 ──▶ Channel 1 [NioSocketChannel → Pipeline → Decoder → Encoder → Logger → BusinessHandler]
  ├─ Worker EventLoop 2 ──▶ Channel 2 [NioSocketChannel → Pipeline → Decoder → Encoder → BusinessHandler]
  └─ Worker EventLoop 3
                                    │
                                    ▼
                            ByteBuf Pool (메모리 관리)
```

### 동작 흐름

1. Client가 TCP 연결 요청
2. Boss EventLoop가 accept() 이벤트 감지
3. Boss가 Worker EventLoop에 새 채널 등록 (채널은 평생 동일한 스레드에 바인딩)
4. Client가 데이터 전송
5. Worker EventLoop가 read() 이벤트 감지, 데이터를 ByteBuf로 읽기
6. ChannelPipeline에 channelRead() 이벤트 발생
7. Decoder 호출 (ByteBuf → 객체 변환)
8. Business Handler 호출 (비즈니스 로직 처리)
9. 응답 데이터 생성 후 ctx.writeAndFlush()
10. Encoder 호출 (객체 → ByteBuf 변환)
11. Worker EventLoop에 write 이벤트 전달, Selector에 write() 이벤트 등록
12. Client로 데이터 전송
13. ByteBuf 참조 카운트 감소, 필요시 메모리 해제
14. 연결 종료 시 channelInactive() 이벤트 → 리소스 정리 → 남은 버퍼 해제

---

## Netty를 사용하는 프레임워크

| 프레임워크 | 설명 |
|-----------|------|
| **Spring WebFlux (Reactor Netty)** | Spring의 리액티브 웹 프레임워크, 내부적으로 Netty 사용 |
| **gRPC** | Google의 RPC 프레임워크, Java 구현체가 Netty 기반 |
| **Apache Kafka** | 브로커 간 통신에 Netty 사용 |
| **Elasticsearch** | Transport Layer에서 Netty 사용 |

### Spring MVC (Tomcat) vs Spring WebFlux (Netty)

| 항목 | Spring MVC + Tomcat | Spring WebFlux + Netty |
|------|--------------------|-----------------------|
| I/O 모델 | 블로킹 (요청당 스레드) | 논블로킹 (이벤트 루프) |
| 스레드 수 | 200~500 (기본) | CPU 코어 수 × 2 |
| 동시 연결 | 스레드 수에 비례 | 수만~수십만 |
| 적합한 경우 | CRUD 중심, JDBC/JPA | I/O 바운드, 높은 동시성 |
