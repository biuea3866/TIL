# Graceful Shutdown

graceful shutdown은 더이상 요청을 받지않고, 내부적으로 아직 동작하는 스레드들의 작업까지 마무리하고 프로세스를 종료시키는 방식이다.

해당 옵션이 없다면, 동작하고 있는 작업의 유무에 관계없이 프로세스를 종료시켜버리게 된다.

```java
private void doShutdown(GracefulShutdownCallback callback, CountDownLatch shutdownUnderway) {
    try {
        List<Connector> connectors = getConnectors();
        connectors.forEach(this::close);
        shutdownUnderway.countDown();
        awaitInactiveOrAborted();
        if (this.aborted) {
            logger.info("Graceful shutdown aborted with one or more requests still active");
            callback.shutdownComplete(GracefulShutdownResult.REQUESTS_ACTIVE);
        }
        else {
            logger.info("Graceful shutdown complete");
            callback.shutdownComplete(GracefulShutdownResult.IDLE);
        }
    }
    finally {
        shutdownUnderway.countDown();
    }
}

private void awaitInactiveOrAborted() {
    try {
        for (Container host : this.tomcat.getEngine().findChildren()) {
            for (Container context : host.findChildren()) {
                while (!this.aborted && isActive(context)) {
                    Thread.sleep(50);
                }
            }
        }
    }
    catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
    }
}
```

톰캣에서는 해당 옵션이 켜져있다면 다음과 같은 순서로 프로세스를 종료시킨다.

1. 연결된 커넥터(TCP 연결을 허락하는)들을 전부 종료시킨다.
2. 50ms씩 정지시키면서, 현재 처리중인 작업이 있는지 계속해서 확인한다.
3. aborted(중단) 상태에 따라 callback객체에 특정 상태값을 남긴다.

---

## 설정 방법

```yaml
# application.yml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s  # 종료 대기 최대 시간 (기본 30초)
```

## 종료 과정

```
SIGTERM 수신
  → 새로운 요청 거부 (커넥터 종료)
  → 현재 진행 중인 요청 처리 대기
  → timeout-per-shutdown-phase 초과 시 강제 종료
  → 프로세스 종료
```

## Kubernetes에서의 Graceful Shutdown

```yaml
# Pod spec
spec:
  terminationGracePeriodSeconds: 60  # k8s 대기 시간 (Spring 타임아웃보다 커야 함)
```

```
k8s가 Pod 종료 결정
  → PreStop Hook 실행 (있다면)
  → SIGTERM 전송 → Spring Graceful Shutdown 시작
  → terminationGracePeriodSeconds 초과 시 SIGKILL 강제 종료
```

* `terminationGracePeriodSeconds` > `timeout-per-shutdown-phase` 로 설정해야 Spring이 정상 종료할 시간이 확보된다
* PreStop Hook에 `sleep 5`를 넣어 로드밸런서가 Pod를 제외할 시간을 확보하는 패턴이 일반적
