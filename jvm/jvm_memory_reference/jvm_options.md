# JVM 옵션

## 그리팅에서 사용중인 JVM 옵션

* `-XX:MaxMetaspaceFreeRatio`
    * 메타스페이스(클래스 코드, 바이트 코드, 상수)의 크기를 비율로 제한

* `-XX:MaxMetaspaceSize`
    * 메타스페이스(클래스 코드, 바이트 코드, 상수)의 크기 제한

* `-XX:+HeapDumpOnOutOfMemoryError`
    * oom발생 시 힙 덤프 생성 옵션
        * oom 원인, memory leak이 발생할 때 필요

* `-XX:HeapDumpPath`
    * HeapDumpOnOutOfMemoryError와 함께 쓰이며, 덤프 파일 저장 경로

* `-XX:+UnlockDiagnosticVMOptions`
    * 숨겨진 옵션들을 프린트할 수 있는 옵션

* `-XX:NativeMemoryTracking=detail`
    * 네이티브 메모리 사용량 추적
        * jcmd 명령어를 이용하여 프로세스 정보, 스레드 덤프, 힙 덤프 등 분석 정보를 얻을 수 있음

* `-XX:G1HeapRegionSize`
    * G1 리전 크기 설정
        * 힙 크기 / 2048(기본 리전 갯수) = 리전 크기

* `-XX:+ParallelRefProcEnabled`
    * GC에서 참조 객체 처리를 병렬로 수행할 것인지

* `-XX:-ResizePLAB`
    * Young GC를 돌면서 스레드 로컬에 복사할 객체를 보관하는 곳
    * 켜져있다면 각각의 멀티 스레드가 복사할 객체를 중복으로 보관할 수 있고, 이는 메모리 해제 시 커뮤니케이션 비용으로 발생

* `-XX:UseContainerSupport`
    * 컨테이너 환경에서 메모리 / CPU 제한 인식

* `-XX:MaxGCPauseMillis`
    * G1GC의 목표 정지 시간
        * 작을수록 빠르게 처리해야하기 때문에 gc의 대상이 적어지므로 처리량 감소

* `-Xms`
    * 최소 힙 크기

* `-Xmx`
    * 최대 힙 크기

* `-XX:+UseG1GC`
    * G1 가비지 컬렉터 활성화

* `-XX:+UnlockExperimentalVMOptions`
    * 실험적 vm 옵션 활성화
        * zgc, shenandoah gc 등 최신 기능

* `-XX:MaxRAMPercentage`
    * 애플리케이션 힙 크기의 상한을 결정

* `-XX:InitialRAMPercentage`
    * 애플리케이션의 힙 크기 초기 설정이고, 컨테이너 메모리의 백분율로 설정

* `-XX:ActiveProcessorCount`
    * jvm이 인식할 수 있는 cpu 코어 수를 강제

---

## 적용해볼법한 옵션

### GC 비교

* **Parallel GC**
    * 장점
        * 처리량이 좋고, CPU 사용률이 적음(다른 알고리즘과 달리 애플리케이션 스레드를 멈춰놓고 수행하기 때문)
        * GC 횟수가 G1GC, ZGC에 비해 빈번하지 않다
    * 단점
        * G1GC, ZGC에 비해 STW 시간이 긺

* **G1GC**
    * 장점
        * 리전 단위로 가비지를 수집하고, 목표 stw 시간을 맞추려고 하기 때문에 처리량과 지연이 예측 가능
            * Parallel GC보다 STW 시간이 짧음
    * 단점
        * 애플리케이션과 병행하여 동시 마킹하는 과정이 존재하기 때문에 CPU 사용률이 많음
        * 힙이 커질수록 stw시간이 길어짐
            * 처리해야할 리전, Root GC 스캔 시간, 객체 복사 시간 증가

* **ZGC**
    * 장점
        * 힙이 아무리 커져도 10ms 이내로 GC 완료
    * 단점
        * G1GC와 비교하여 동일한 힙이라고 가정했을 때 ZGC는 colored pointers, load barrier, relocate 등 많은 연산이 수행되므로 비효율적임

### API 서버

현재 에러 메시지가 가장 많이 발생하는 opening, offercent-bff, greeting-aggregator 비교

|  | **opening** | **offercent-bff** | **greeting-aggregator** | **greeting-ats-server** |
| --- | --- | --- | --- | --- |
| gc | G1GC | 모름(g1gc 추정 근데 cpu가 1이하라 serial gc일수도 있음) | G1GC | G1GC |
| 요청 memory | 1gb | 1gb | 2gb | 2gb |
| 요청 cpu | 0.25 코어 | 0.25 코어 | 1코어 | 1코어 |
| 제한 memory | 2gb | 1gb | 2gb | 4gb |
| 제한 cpu | 없음 | 없음 | 없음 | 없음 |
| max jvm heap | 512mb | 256mb | 512mb | 1gb |
| 설정된 jvm 옵션 | -XX:+UseContainerSupport | 없음 | -XX:InitialMetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:MaxMetaspaceFreeRatio=60 -XX:ActiveProcessorCount=2 | -XshowSettings:all -XX:MaxMetaspaceFreeRatio=60 -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail |
| 추가하거나 수정하면 좋을 선택지 | 힙사이즈 늘리기. 계속 임계치를 왔다갔다하는 형태. 리전 사이즈 낮추기. 가비지 크기를 낮추어 잦지만 빠르게 gc를 돌려 메모리 해제하기. 메모리에 문제가 되는 코드 수정하기 (오류를 발생시키는 api) |  | 힙 사용량 여유 o | 힙 사용량 여유 o |

* 응답이 늦어진다는 오류는 GC로 인해 지연이 발생하는 것인지 확인할 필요 있음
* 다만, jvm heap의 임계치를 넘어서는 로그들은 좀 더 확인해볼 필요 있음

---

# Backend 서비스 JVM 옵션

| **배포명(서비스명)** | **JVM metric** (avg, max, 설정된 max) | **사용중인 Jvm Option** | **Pod 메모리 사용량** (avg, max) | **Pod 메모리 설정** | **비고** |
| --- | --- | --- | --- | --- | --- |
| greeting-ats-server | avg: 795MiB max: 906MiB max_heap: 1000MiB | -XshowSettings:all -XX:MaxMetaspaceFreeRatio=60 -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail (힙 크기 조절 관련 옵션 없음) | avg: 1.9GiB max: 3.84GiB cpu avg: 1 미만 cpu max: 6.72 | limits: memory 4000Mi / requests: cpu 1, memory 2000Mi | 배포일자에 매번 메모리가 4GiB 가까이 튀는 현상. 배포시 cpu 최대 6.72코어 사용. jib를 활용한 빌드 수행중 |
| greeting-recruitment-api-server | nestjs | nestjs | avg: 200MiB max: 520MiB | limits: memory 500Mi / requests: cpu 250m, memory 500Mi | |
| doodlin-communication | jvm metric 수집x | -XshowSettings:all -XX:MaxMetaspaceFreeRatio=60 -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail (힙 크기 조절 관련 옵션 없음) | avg: 1.4GiB max: 2.77GiB | limits: memory 3000Mi / requests: cpu 250m, memory 2000Mi | jib를 활용한 빌드 수행중 |
| doodlin-communication-batch (evaluationRemindJob) | jvm metric 수집 x | -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=11619 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.rmi.port=11619 -Dcom.amazonaws.sdk.enableDefaultMetrics -Djava.net.preferIPv4Stack=true -Dlog4j2.formatMsgNoLookups=true -XshowSettings:all -XX:MaxMetaspaceFreeRatio=60 -XX:MaxMetaspaceSize=512m -XX:G1HeapRegionSize=8m -XX:+ParallelRefProcEnabled -XX:-ResizePLAB -XX:NativeMemoryTracking=detail (힙 크기 조절 관련 옵션 없음) | avg: 560MiB | limits: memory 2500Mi / requests: cpu 250m, memory 2500Mi | jib를 활용한 빌드 수행중 |
| doodlin-communication-batch (sendReservedMailJob) | jvm metric 수집 x | (evaluationRemindJob과 동일) | avg: 688MiB | limits: memory 2500Mi / requests: cpu 250m, memory 2500Mi | jib를 활용한 빌드 수행중 |
| greeting-analytics-server | jvm metric 수집 x | -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -XX:+UseContainerSupport (파드 메모리중 최소 50%, 최대 80% 사용 가능) | avg: 1.2GiB max: 2GiB | limits: memory 1500Mi / requests: cpu 250m, memory 1500Mi | 6월 9일 2GiB 사용. limits 수정 필요 |
| greeting-ats2-recruit-applicant-api | avg: 370MiB max: 390MiB max_heap: 500MiB | 관련 설정 없음 | avg: 1.1GiB max: 2GiB | limits: memory 2000Mi / requests: cpu 250m, memory 2000Mi | jib를 활용한 빌드 수행중 |
| greeting-ats2-wno | avg: 730MiB max: 730MiB max_heap: 2GiB | -Xms$memory -Xmx$memory -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:-ResizePLAB (힙 최소, 최대 메모리 2g 설정됨. 조정 필요) | avg: 1.8GiB max: 3.8GiB | limits: memory 2000Mi / requests: cpu 250m, memory 2000Mi | jib를 활용한 빌드 수행중 |
| greeting-authn-server | avg: 350MiB max: 400MiB max_heap: 1GiB | -Xms512m -Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail -XX:+UseContainerSupport (힙 최소: 512mb, 최대: 1024mb) | avg: 1GiB max: 2GiB | limits: memory 3000Mi / requests: cpu 250m, memory 3000Mi | |
| greeting-integration-worker | jvm 메트릭 수집 x | -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -XX:+UseContainerSupport -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XX:+UnlockDiagnosticVMOptions -XX:NativeMemoryTracking=detail (파드 메모리중 최소 50%, 최대 80% 사용 가능) | avg: 600MiB max: 1.14GiB | limits: memory 750Mi / requests: cpu 250m, memory 750Mi | |
| greeting-kotlin-mail-worker | jvm 메트릭 수집 x | -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -XX:+UseContainerSupport (파드 메모리중 최소 50%, 최대 80% 사용 가능) | | limits: memory 750Mi / requests: cpu 250m, memory 500Mi | 사용안하는 것으로 알고있는데, 6월 29일부터 메모리가 증가하는 추세를 보임 |
| greeting-payment-server | avg: 194MiB max: 208MiB max_heap: 580MiB | -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=60.0 -XX:+UseContainerSupport -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=512m (파드 메모리중 최소 50%, 최대 60% 사용 가능) | avg: 700MiB max: 1.35GiB | requests: memory 1000Mi, cpu 10m / limits: memory 1000Mi | |
| greeting-trm-server | avg: 667MiB max: 736MiB max_heap: 1.17GiB | -server -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=60.0 -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:G1HeapRegionSize=8m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -Xshare:off (파드 메모리중 최소 50%, 최대 60% 사용 가능) | avg: 1.8GiB max: 3.57GiB | limits: memory 2000Mi / requests: cpu 400m, memory 2000Mi | jib를 활용한 빌드 수행중 |
| greeting-aggregator | jvm 메트릭 수집 x | -XX:InitialMetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:MaxMetaspaceFreeRatio=60 -XX:ActiveProcessorCount=2 (힙메모리 설정 없음. 최대 500MiB까지 사용 가능할 것으로 예상) | avg: 830MiB max: 3.93GiB | limits: memory 2000Mi / requests: cpu 1000m, memory 2000Mi | jib를 활용한 빌드 수행중. 힙 메모리 설정 필요 |
| greeting-api-gateway | avg: 295MiB max: 394MiB max_heap: 500MiB -> 1GiB(7/2 이후) | -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:NativeMemoryTracking=detail -XX:+ParallelRefProcEnabled -XX:-ResizePLAB -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XshowSettings:all (힙메모리 설정 없음) | avg: 1000MiB max: 4.12GiB | limits: memory 2000Mi / requests: cpu 250m, memory 2000Mi | jib를 활용한 빌드 수행중. 힙 메모리 설정 필요. 7/2일에 heap memory size를 1GiB로 증가시킴 |
| greeting-ats2-api | avg: 759MiB max: 879MiB max_heap: 2GiB | -server -Xms2g -Xmx2g -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:MaxGCPauseMillis=200 -XX:+ParallelRefProcEnabled -XX:-ResizePLAB -XX:NativeMemoryTracking=detail | avg: 1.92GiB max: 3.79GiB | limits: memory 2000Mi / requests: cpu 250m, memory 2000Mi | jib를 활용한 빌드 수행중. 최소, 최대치 조정 필요 |
| greeting-authz-server | avg: 244MiB max: 297MiB max_heap: 365MiB -> 750MiB(7/2 이후) | -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:NativeMemoryTracking=detail -XX:+ParallelRefProcEnabled -XX:-ResizePLAB -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XshowSettings:all (힙메모리 설정 없음) | avg: 950MiB max: 2.65GiB | limits: memory 1500Mi / requests: cpu 250m, memory 1500Mi | jib를 활용한 빌드 수행중. 힙 메모리 설정 필요 |
| greeting-communication-api | avg: 266MiB max: 345MiB max_heap: 500MiB | -XX:InitialMetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:MaxMetaspaceFreeRatio=60 -XX:ActiveProcessorCount=2 (힙메모리 설정 없음) | avg: 885MiB max: 1.94GiB | limits: memory 2000Mi / requests: cpu 300m, memory 2000Mi | jib를 활용한 빌드 수행중. 힙 메모리 설정 필요 |
| greeting-communication-offer | | -XX:InitialMetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:MaxMetaspaceFreeRatio=60 -XX:ActiveProcessorCount=2 (힙메모리 설정 없음) | avg: 498MiB max: 983MiB | limits: memory 2000Mi / requests: cpu 300m, memory 2000Mi | jib를 활용한 빌드 수행중. 힙 메모리 설정 필요. 임시 서버이며 추후 통합 예정 |
| greeting-trm-authz-server | jvm 메트릭 수집 x | -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -XX:+UseContainerSupport (파드 메모리중 최소 50%, 최대 80% 사용 가능) | avg: 1.2GiB max: 2.4GiB | limits: memory 2000Mi / requests: cpu 250m, memory 1000Mi | |
| greeting-workspace-server | avg: 250MiB max: 250MiB max_heap: 1GiB | -Xms512m -Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseContainerSupport -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dump -XX:NativeMemoryTracking=detail (힙 최소: 512mb, 최대: 1024mb) | avg: 800MiB max: 1.6GiB | limits: memory 3000Mi / requests: cpu 250m, memory 3000Mi | 타 서비스 대비 메모리 과도하게 설정되어있음 |
| greeting-expired-applicant-processor | jvm 메트릭 수집 x | -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=80.0 -XX:+UseContainerSupport (파드 메모리중 최소 50%, 최대 80% 사용 가능) | | limits: memory 1000Mi / requests: cpu 250m, memory 1000Mi | |
| doodlin-internal-notification-consumer | - | -Xmx2g -XX:MaxMetaspaceSize=512m | - | limits: memory 2000Mi / requests: cpu 250m, memory 2000Mi | jib를 활용한 빌드 수행중 |
| offercent-bff | avg: 196MiB max: 239MiB max_heap: 242MiB | 관련 설정 없음 | avg: 698MiB max: 2.19GiB | limits: memory 1000Mi / requests: cpu 250m, memory 1000Mi | JVM 관련 설정 추가 필요 |
| offercent-user-api | avg: 164MiB max: 197MiB max_heap: 242MiB | 관련 설정 없음 | avg: 748MiB max: 1.43GiB | limits: memory 1000Mi / requests: cpu 250m, memory 1000Mi | JVM 관련 설정 추가 필요 |
| offercent-auth-api | avg: 169MiB max: 239MiB max_heap: 242MiB | 관련 설정 없음 | avg: 656MiB max: 1.17GiB | limits: memory 1000Mi / requests: cpu 250m, memory 1000Mi | JVM 관련 설정 추가 필요 |
