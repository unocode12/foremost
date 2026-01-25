# WebFlux, MVC, Virtual Thread, Coroutine 동작 원리 비교 분석

## 📌 개요

이 문서는 Spring MVC, Spring WebFlux, Java Virtual Thread, Python Coroutine의 동작 원리를 OS 스레드, 애플리케이션 스레드, CPU, 메모리 관점에서 비교 분석합니다.

---

## 1. 스레드 모델 비교

### 1.1 OS 스레드 vs 애플리케이션 스레드

#### OS 스레드 (Platform Thread)

**특징**:

- 운영체제 커널이 직접 관리
- 1:1 매핑 (애플리케이션 스레드 = OS 스레드)
- 컨텍스트 스위칭 비용 높음
- 스택 크기: 기본 1MB (Linux)

**메모리 구조**:

```
OS 스레드 스택 (1MB)
├── 메서드 호출 프레임
├── 지역 변수
├── 매개 변수
└── 반환 주소
```

**CPU 관점**:

- 컨텍스트 스위칭: CPU 레지스터 저장/복원 필요
- 캐시 미스 가능성 높음
- 스레드 전환 비용: 수 마이크로초

#### 애플리케이션 스레드 (User Thread)

**특징**:

- 애플리케이션 레벨에서 관리
- OS 스레드와의 매핑 방식에 따라 다름
- 경량화 가능

---

## 2. Spring MVC 동작 원리

### 2.1 스레드 모델

**1:1 스레드 모델**:

- 각 요청마다 OS 스레드 할당
- 스레드 풀에서 스레드 가져오기
- 요청 처리 완료까지 스레드 점유

**아키텍처**:

```
[HTTP 요청]
    ↓
[Tomcat Connector]
    ↓
[Thread Pool (예: 200개)]
    ├── Thread-1 → Request-1 처리
    ├── Thread-2 → Request-2 처리
    ├── Thread-3 → Request-3 처리
    └── ...
```

### 2.2 동작 흐름

```
1. HTTP 요청 도착
2. Thread Pool에서 스레드 할당
3. Servlet Filter 체인 실행
4. DispatcherServlet
5. HandlerMapping → Controller
6. Service → Repository
7. DB 쿼리 (블로킹 I/O)
   └─ 스레드가 블로킹되어 대기
8. 응답 반환
9. 스레드 반환
```

### 2.3 CPU 및 메모리 관점

**CPU 사용**:

- **활성 스레드**: CPU 사용 중
- **블로킹 스레드**: CPU 미사용 (대기 중)
- **컨텍스트 스위칭**: 스레드 전환 시 발생

**메모리 사용**:

```
메모리 = 스레드 수 × 스레드 스택 크기
예: 200 스레드 × 1MB = 200MB (최소)
```

**문제점**:

- **스레드 오버헤드**: 많은 스레드 = 많은 메모리
- **컨텍스트 스위칭**: 스레드 전환 비용
- **블로킹**: I/O 대기 중 스레드 낭비

### 2.4 스레드 풀과 OS 스레드 매핑

#### 스레드 풀 크기 vs OS 스레드 수

**핵심 개념**:

- **스레드 풀 크기**: 애플리케이션에서 설정한 최대 스레드 수 (예: 200개)
- **OS 스레드 수**: 운영체제가 실제로 관리하는 스레드 수
- **매핑 관계**: Spring MVC는 **1:1 매핑** (애플리케이션 스레드 = OS 스레드)

#### 실제 동작 방식

**시나리오 1: 스레드 풀 크기 ≤ OS 스레드 한계**

```
설정: 스레드 풀 = 200개
OS 한계: 1000개 (예시)

동작:
├── 200개의 OS 스레드 생성
├── 각 스레드가 요청 처리
└── 모든 스레드가 동시에 실행 가능
```

**시나리오 2: 스레드 풀 크기 > OS 스레드 한계**

```
설정: 스레드 풀 = 500개
OS 한계: 200개 (예시)

동작:
├── 최대 200개의 OS 스레드만 생성 가능
├── 나머지 300개는 대기 큐에서 대기
└── OS 스레드가 해제되면 대기 중인 작업 실행
```

#### 스레드 풀 내부 구조

```java
// Tomcat Thread Pool 내부 구조 (의사 코드)
ThreadPoolExecutor {
    int corePoolSize = 10;        // 기본 스레드 수
    int maximumPoolSize = 200;    // 최대 스레드 수
    BlockingQueue<Runnable> queue; // 대기 큐

    void execute(Runnable task) {
        if (현재 스레드 수 < corePoolSize) {
            // 새 OS 스레드 생성
            createNewThread(task);
        } else if (현재 스레드 수 < maximumPoolSize) {
            // 최대치까지 OS 스레드 생성
            createNewThread(task);
        } else {
            // 대기 큐에 추가
            queue.offer(task);
        }
    }
}
```

#### OS 스레드 생성 과정

```
1. 요청 도착
2. ThreadPoolExecutor.execute() 호출
3. OS 스레드 생성 여부 확인
   ├── 사용 가능한 스레드 있음 → 재사용
   ├── 최대치 미만 → 새 OS 스레드 생성
   │   └─ pthread_create() (Linux) 또는 CreateThread() (Windows)
   └── 최대치 도달 → 대기 큐에 추가
4. OS 스레드가 작업 실행
5. 작업 완료 후 스레드 반환 (재사용)
```

#### 컨텍스트 스위칭 동작

**스레드 풀 크기 > CPU 코어 수인 경우**:

```
CPU 코어: 8개
스레드 풀: 200개

동작:
├── 8개의 스레드만 동시에 CPU 사용
├── 나머지 192개는 대기 상태
│   ├── BLOCKED: I/O 대기 중
│   ├── WAITING: 동기화 대기 중
│   └── TIMED_WAITING: 타임아웃 대기 중
└── OS 스케줄러가 컨텍스트 스위칭으로 전환
```

**컨텍스트 스위칭 과정**:

```
Thread-A 실행 중 (CPU 사용)
    ↓
I/O 블로킹 발생 (DB 쿼리 대기)
    ↓
OS 스케줄러 개입
    ├── Thread-A 상태 저장
    │   ├── CPU 레지스터 저장
    │   ├── 스택 포인터 저장
    │   └── 프로그램 카운터 저장
    ├── Thread-B 상태 복원
    │   ├── CPU 레지스터 복원
    │   ├── 스택 포인터 복원
    │   └── 프로그램 카운터 복원
    └── Thread-B 실행 시작
```

#### 메모리 관점에서의 매핑

**스레드 풀 크기와 메모리 사용**:

```
스레드 풀 크기: 200개
OS 스레드 생성: 200개 (1:1 매핑)

메모리 사용:
├── 스레드 스택: 200 × 1MB = 200MB
├── 메타데이터: 200 × 약 1KB = 200KB
└── 총 메모리: 약 200MB (최소)
```

**스레드 풀 크기 > OS 한계인 경우**:

```
스레드 풀 크기: 500개
OS 스레드 생성: 200개 (한계)

메모리 사용:
├── 실제 생성된 스레드: 200 × 1MB = 200MB
├── 대기 큐: 메모리 사용 최소 (Runnable 객체만)
└── 총 메모리: 약 200MB (대기 큐는 경량)
```

#### 실무 설정 예시

**application.properties**:

```properties
# Tomcat 스레드 풀 설정
server.tomcat.threads.max=200    # 최대 스레드 수
server.tomcat.threads.min-spare=10  # 최소 유지 스레드 수
server.tomcat.max-connections=10000  # 최대 연결 수
server.tomcat.accept-count=100   # 대기 큐 크기
```

**동작 원리**:

```
1. 최소 10개의 OS 스레드 생성 (min-spare)
2. 요청 증가 시 최대 200개까지 OS 스레드 생성
3. 200개 초과 시 대기 큐(accept-count)에 추가
4. 대기 큐도 가득 차면 연결 거부 (503 Service Unavailable)
```

#### 스레드 풀 모니터링

**실제 동작 확인**:

```java
// 스레드 풀 상태 확인
ThreadPoolExecutor executor = (ThreadPoolExecutor)
    ((TomcatWebServer) server).getTomcat().getConnector().getProtocolHandler().getExecutor();

System.out.println("Active Threads: " + executor.getActiveCount());
System.out.println("Pool Size: " + executor.getPoolSize());
System.out.println("Queue Size: " + executor.getQueue().size());
System.out.println("Completed Tasks: " + executor.getCompletedTaskCount());
```

**출력 예시**:

```
Active Threads: 150      // 현재 실행 중인 OS 스레드
Pool Size: 200           // 생성된 OS 스레드 수
Queue Size: 50           // 대기 중인 요청 수
Completed Tasks: 10000   // 완료된 작업 수
```

#### 핵심 정리

**1:1 매핑의 의미**:

- 애플리케이션 스레드 풀의 각 스레드 = OS 스레드
- 스레드 풀 크기 = 최대 생성 가능한 OS 스레드 수
- OS 스레드 한계를 초과하면 대기 큐 사용

**성능 영향**:

- **스레드 풀 크기 < CPU 코어 수**: CPU 활용률 낮음
- **스레드 풀 크기 ≈ CPU 코어 수**: 최적 (CPU 집약적 작업)
- **스레드 풀 크기 > CPU 코어 수**: I/O 대기 작업에 적합
- **스레드 풀 크기 >> OS 한계**: 대기 큐 활용, 응답 지연 가능

**권장 설정**:

```
I/O 집약적 애플리케이션:
├── 스레드 풀 크기: CPU 코어 수 × (1 + I/O 대기 시간 / CPU 처리 시간)
└── 예: 8 코어 × (1 + 10ms / 1ms) = 88 → 약 100~200

CPU 집약적 애플리케이션:
├── 스레드 풀 크기: CPU 코어 수 + 1
└── 예: 8 코어 + 1 = 9 → 약 10~20
```

### 2.5 예시 코드

```java
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        // OS 스레드가 블로킹되어 대기
        return userService.findById(id);
    }
}

@Service
public class UserService {
    @Autowired
    private UserRepository repository;

    public User findById(Long id) {
        // DB 쿼리 실행 (블로킹 I/O)
        // 스레드가 DB 응답을 기다림
        return repository.findById(id).orElse(null);
    }
}
```

**스레드 상태**:

```
Thread-1: RUNNABLE → BLOCKED (DB 대기) → RUNNABLE → TERMINATED
```

---

## 3. Spring WebFlux 동작 원리

### 3.1 스레드 모델

**이벤트 루프 모델**:

- 적은 수의 OS 스레드 (CPU 코어 × 2)
- 논블로킹 I/O
- 이벤트 기반 처리

**아키텍처**:

```
[HTTP 요청들]
    ↓
[Netty EventLoop Group]
    ├── EventLoop-1 (싱글 스레드)
    │   ├── Connection-1
    │   ├── Connection-2
    │   └── Connection-3
    ├── EventLoop-2 (싱글 스레드)
    │   ├── Connection-4
    │   └── Connection-5
    └── ...
```

### 3.2 WebFlux에서 사용되는 스레드 종류

#### 스레드 타입별 역할

**1. EventLoop 스레드 (I/O 스레드)**

**역할**: 네트워크 I/O 처리

```java
// Netty EventLoop 내부 구조
EventLoop {
    Thread thread;              // OS 스레드
    Selector selector;          // Java NIO Selector
    Queue<Runnable> taskQueue;  // 작업 큐

    void run() {
        while (!isShutdown) {
            // 1. I/O 이벤트 처리
            select(selector);   // 준비된 채널 선택

            // 2. 선택된 채널의 I/O 처리
            processSelectedKeys();

            // 3. 작업 큐 처리
            runAllTasks();
        }
    }
}
```

**특징**:

- **스레드 수**: 기본적으로 CPU 코어 수 × 2
- **역할**: 네트워크 읽기/쓰기, 채널 관리
- **블로킹 금지**: 절대 블로킹 작업 수행 불가
- **싱글 스레드**: 각 EventLoop는 하나의 OS 스레드로 동작

**2. Worker 스레드 (블로킹 작업용)**

**역할**: 블로킹 작업 처리 (선택적)

```java
// WebFlux의 스레드 풀 구조
Schedulers {
    // I/O 작업용 (EventLoop)
    Schedulers.immediate()      // 현재 스레드
    Schedulers.parallel()       // CPU 집약적 작업

    // 블로킹 작업용
    Schedulers.boundedElastic() // 블로킹 작업 전용
    // 내부적으로 ThreadPoolExecutor 사용
    // 기본: CPU 코어 수 × 10
}
```

**특징**:

- **스레드 수**: 설정 가능 (기본 CPU 코어 × 10)
- **역할**: 블로킹 I/O, CPU 집약적 작업
- **사용 시점**: `publishOn(Schedulers.boundedElastic())` 명시적 지정 시

**3. 애플리케이션 스레드 (Controller/Service)**

**역할**: 비즈니스 로직 실행

```java
// WebFlux는 기본적으로 EventLoop 스레드에서 실행
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    // 이 코드는 EventLoop 스레드에서 실행됨
    return userService.findById(id);
}
```

**특징**:

- **기본 스레드**: EventLoop 스레드
- **명시적 변경**: `publishOn()` 또는 `subscribeOn()` 사용 시

#### 스레드 사용 흐름

```
요청 처리 과정:

1. HTTP 요청 도착
   └─ EventLoop-1 스레드가 소켓에서 읽기

2. 요청 파싱
   └─ EventLoop-1 스레드에서 HTTP 파싱

3. Controller 실행
   └─ EventLoop-1 스레드에서 실행
   └─ Mono<User> 반환 (아직 실행 안 됨)

4. Service 호출
   └─ subscribe() 시점에 EventLoop-1에서 실행

5. DB 쿼리 (논블로킹)
   └─ EventLoop-1에서 논블로킹 I/O 시작
   └─ I/O 대기 중 EventLoop-1은 다른 작업 처리

6. DB 응답 도착
   └─ EventLoop-1이 콜백 받아서 처리

7. 응답 작성
   └─ EventLoop-1에서 채널에 쓰기

8. 응답 전송
   └─ EventLoop-1이 소켓에 Flush
```

#### 스레드 전환 예시

```java
@GetMapping("/users/{id}")
public Mono<User>  getUser(@PathVariable Long id) {
    // EventLoop 스레드에서 실행
    return userService.findById(id)
        .publishOn(Schedulers.boundedElastic())  // Worker 스레드로 전환
        .map(user -> {
            // 이 부분은 Worker 스레드에서 실행
            // 블로킹 작업 가능
            return heavyComputation(user);
        })
        .publishOn(Schedulers.parallel())  // 다시 다른 스레드로 전환
        .map(user -> {
            // 이 부분은 parallel 스레드에서 실행
            return transform(user);
        });
}
```

**스레드 전환 과정**:

```
EventLoop-1: Controller 실행 → Service 호출
    ↓
Worker-Thread-1: heavyComputation() 실행 (블로킹 가능)
    ↓
Parallel-Thread-1: transform() 실행
    ↓
EventLoop-1: 최종 응답 작성 및 전송
```

### 3.3 하나의 EventLoop에서 여러 채널 동시 처리

#### EventLoop의 동작 원리

**핵심 개념**: 하나의 EventLoop 스레드가 여러 채널을 순차적으로 처리

```java
// Netty EventLoop 내부 동작 (의사 코드)
class NioEventLoop {
    private Selector selector;
    private Queue<Runnable> taskQueue;

    void run() {
        while (!isShutdown) {
            // 1. I/O 이벤트 선택 (블로킹, 최대 1초)
            int selectedKeys = selector.select(1000);

            if (selectedKeys > 0) {
                // 2. 준비된 채널들의 이벤트 처리
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iter = keys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    // 3. 각 채널의 이벤트 처리 (순차적)
                    if (key.isReadable()) {
                        processRead(key);  // 채널-1 읽기 처리
                    }
                    if (key.isWritable()) {
                        processWrite(key); // 채널-2 쓰기 처리
                    }
                }
            }

            // 4. 작업 큐 처리 (타임아웃 작업 등)
            runAllTasks();
        }
    }
}
```

#### 동시 요청 처리 시나리오

**시나리오: EventLoop-1에 3개의 채널에서 동시에 요청 도착**

```
시간축: ──────────────────────────────────────→

t0: [Channel-1] HTTP 요청 도착
    [Channel-2] HTTP 요청 도착
    [Channel-3] HTTP 요청 도착
    └─ 모두 EventLoop-1의 Selector에 등록됨

t1: EventLoop-1 스레드가 selector.select() 호출
    └─ 3개 채널 모두 읽기 준비됨
    └─ selectedKeys = {Channel-1, Channel-2, Channel-3}

t2: EventLoop-1이 Channel-1 처리 시작
    ├─ HTTP 요청 파싱
    ├─ Controller 호출
    ├─ Service 호출
    └─ DB 쿼리 시작 (논블로킹, 즉시 반환)
    └─ Channel-1은 I/O 대기 상태로 전환

t3: EventLoop-1이 Channel-2 처리 시작
    ├─ HTTP 요청 파싱
    ├─ Controller 호출
    ├─ Service 호출
    └─ DB 쿼리 시작 (논블로킹, 즉시 반환)
    └─ Channel-2는 I/O 대기 상태로 전환

t4: EventLoop-1이 Channel-3 처리 시작
    ├─ HTTP 요청 파싱
    ├─ Controller 호출
    ├─ Service 호출
    └─ DB 쿼리 시작 (논블로킹, 즉시 반환)
    └─ Channel-3는 I/O 대기 상태로 전환

t5: EventLoop-1이 selector.select() 다시 호출
    └─ 다른 채널들의 이벤트 처리 또는 대기

t6: [Channel-1] DB 응답 도착
    └─ Selector가 Channel-1을 읽기 준비 상태로 표시

t7: EventLoop-1이 selector.select()에서 깨어남
    └─ Channel-1의 응답 처리
    ├─ 데이터 읽기
    ├─ 응답 생성
    └─ Channel-1에 쓰기

t8: [Channel-2] DB 응답 도착
    └─ EventLoop-1이 Channel-2 처리

t9: [Channel-3] DB 응답 도착
    └─ EventLoop-1이 Channel-3 처리
```

#### 핵심 포인트: 순차적 처리

**중요**: 하나의 EventLoop 스레드는 **동시에 여러 작업을 처리하지 않음**

```
❌ 오해: EventLoop가 여러 채널을 동시에 처리
✅ 실제: EventLoop가 여러 채널을 순차적으로 처리

EventLoop-1의 실행 흐름:
├── Channel-1 처리 (완료)
├── Channel-2 처리 (완료)
├── Channel-3 처리 (완료)
└── 다시 selector.select() 호출
```

**왜 효율적인가?**

1. **논블로킹 I/O**: 각 채널 처리 시 블로킹되지 않음

   - DB 쿼리 시작 → 즉시 반환 → 다음 채널 처리
   - I/O 대기는 커널 레벨에서 처리

2. **빠른 전환**: 채널 간 전환이 매우 빠름

   - OS 스레드 컨텍스트 스위칭 불필요
   - 단순히 다음 채널의 이벤트 처리

3. **이벤트 기반**: 준비된 이벤트만 처리
   - `selector.select()`가 준비된 채널만 반환
   - 대기 중인 채널은 건너뜀

### 왜 하나의 EventLoop에 많은 채널이 할당되는가?

#### 핵심 이유

**1. 논블로킹 I/O의 특성**

```java
// 논블로킹 I/O는 즉시 반환
int bytesRead = channel.read(buffer);
// → 데이터가 없으면 0 반환 (블로킹 안 됨)
// → 데이터가 있으면 읽은 바이트 수 반환
// → 스레드는 블로킹되지 않고 다른 작업 처리 가능
```

**핵심**: 논블로킹 I/O는 I/O 대기 중에도 스레드가 다른 작업을 처리할 수 있음

```
블로킹 I/O 모델:
├── Thread-1: [요청 처리] [I/O 대기 중... 블로킹] [응답 처리]
└─ Thread-1이 I/O 대기 중에는 아무것도 할 수 없음

논블로킹 I/O 모델:
├── EventLoop-1: [Channel-1 처리] [Channel-2 처리] [Channel-3 처리] ...
└─ I/O 대기 중에도 다른 채널 처리 가능
```

**2. Selector의 효율성**

```java
// 하나의 Selector가 수천 개의 채널을 감시 가능
Selector selector = Selector.open();

// 수천 개의 채널 등록
for (int i = 0; i < 10000; i++) {
    SocketChannel channel = SocketChannel.open();
    channel.configureBlocking(false);
    channel.register(selector, SelectionKey.OP_READ);
}

// 하나의 select() 호출로 준비된 모든 채널 확인
int readyChannels = selector.select();
// → 커널이 효율적으로 관리
// → 준비된 채널만 반환
```

**커널 레벨 효율성**:

```
epoll (Linux)의 효율성:
├── O(1) 시간 복잡도로 준비된 채널 확인
├── 수천~수만 개의 채널 관리 가능
└─ 하나의 시스템 콜로 모든 채널 상태 확인

전통적인 방식 (poll):
├── O(n) 시간 복잡도 (모든 채널 순회)
└─ 채널 수가 많아질수록 비효율적
```

**3. 스레드 안전성 보장**

```java
// 각 채널은 하나의 EventLoop에만 할당
Channel channel1 = ...;
Channel channel2 = ...;
Channel channel3 = ...;

// 모두 같은 EventLoop에 할당
EventLoop eventLoop = ...;
channel1.register(eventLoop);
channel2.register(eventLoop);
channel3.register(eventLoop);

// 장점: 같은 EventLoop에서만 처리
// → 락 없이 안전하게 처리 가능
// → 동기화 오버헤드 없음
```

**스레드 안전성의 이점**:

```
같은 EventLoop에 할당된 채널들:
├── Channel-1, Channel-2, Channel-3 모두 EventLoop-1에서 처리
├── 동시에 여러 스레드에서 접근하지 않음
└─ 락 없이 안전하게 처리

다른 EventLoop에 할당된 채널:
├── Channel-4는 EventLoop-2에서 처리
└─ EventLoop-1과 EventLoop-2는 독립적으로 동작
```

**4. 메모리 효율성**

```
블로킹 모델 (Spring MVC):
├── 10,000개 연결 = 10,000개 스레드 필요
├── 메모리: 10,000 × 1MB = 10GB
└─ 비현실적

논블로킹 모델 (WebFlux):
├── 10,000개 연결 = 8개 EventLoop 스레드
├── 메모리: 8 × 1MB = 8MB (스레드 스택)
└─ 현실적
```

**5. 컨텍스트 스위칭 최소화**

```
블로킹 모델:
├── 10,000개 스레드가 CPU를 놓고 다툼
├── 컨텍스트 스위칭: 매우 빈번
└─ CPU 캐시 미스 증가

논블로킹 모델:
├── 8개 EventLoop 스레드만 CPU 사용
├── 컨텍스트 스위칭: 최소화
└─ CPU 캐시 효율성 향상
```

**6. 채널 할당 전략**

```java
// Netty의 채널 할당 전략
EventLoopGroup group = new NioEventLoopGroup(8); // 8개 EventLoop

// 새로운 연결이 들어올 때마다
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(group)
    .channel(NioServerSocketChannel.class)
    .childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) {
            // 채널을 EventLoop에 할당
            // 라운드 로빈 방식으로 분산
            // EventLoop-1, EventLoop-2, ..., EventLoop-8 순환
        }
    });
```

**할당 전략**:

```
새로운 연결 도착:
├── Connection-1 → EventLoop-1
├── Connection-2 → EventLoop-2
├── Connection-3 → EventLoop-3
├── ...
├── Connection-8 → EventLoop-8
├── Connection-9 → EventLoop-1 (다시 순환)
└─ 각 EventLoop에 균등하게 분산
```

**7. 실제 처리 능력**

```
시나리오: 10,000개의 동시 연결

EventLoop-1: 1,250개 채널 관리
├── I/O 대기 중인 채널: 1,200개
├── 활성 처리 중인 채널: 50개
└─ 하나의 스레드로 효율적으로 관리

EventLoop-2: 1,250개 채널 관리
├── I/O 대기 중인 채널: 1,200개
├── 활성 처리 중인 채널: 50개
└─ 하나의 스레드로 효율적으로 관리

... (총 8개 EventLoop)

총 처리 능력:
├── 10,000개 연결 동시 처리
├── 8개 스레드만 사용
└─ 메모리: 8MB (스레드 스택)
```

**8. I/O 대기 시간 활용**

```
채널별 I/O 대기 시간:
├── Channel-1: DB 쿼리 대기 (10ms)
├── Channel-2: DB 쿼리 대기 (10ms)
├── Channel-3: DB 쿼리 대기 (10ms)
└─ ...

EventLoop-1의 동작:
├── Channel-1 처리 시작 → I/O 대기 시작
├── Channel-2 처리 시작 → I/O 대기 시작
├── Channel-3 처리 시작 → I/O 대기 시작
├── ... (수백 개의 채널 처리)
└─ I/O 대기 중인 채널들은 커널에서 관리
   → EventLoop는 다른 채널 처리 계속
```

**핵심 정리**:

| 이유                   | 설명                                                     |
| ---------------------- | -------------------------------------------------------- |
| **논블로킹 I/O**       | I/O 대기 중에도 다른 채널 처리 가능                      |
| **Selector 효율성**    | 하나의 Selector가 수천 개 채널 관리 (epoll O(1))         |
| **스레드 안전성**      | 같은 EventLoop에서만 처리 → 락 불필요                    |
| **메모리 효율성**      | 적은 스레드로 많은 연결 처리                             |
| **컨텍스트 스위칭**    | 최소화로 CPU 효율 향상                                   |
| **I/O 대기 시간 활용** | 대기 중인 채널은 커널이 관리, EventLoop는 다른 작업 처리 |

**결론**: 논블로킹 I/O와 Selector의 효율성 덕분에 하나의 EventLoop 스레드가 수천 개의 채널을 효율적으로 관리할 수 있습니다. 이는 블로킹 모델 대비 메모리와 CPU 사용량을 크게 줄이면서도 높은 동시성을 제공합니다.

### 핵심 질문: EventLoop는 순차 처리하는데 왜 여러 채널을 할당하는가?

#### 오해: "I/O는 Worker 스레드를 쓰는데 왜 다중 채널을 쓰는가?"

**중요한 구분**:

```
❌ 오해: 모든 I/O가 Worker 스레드에서 처리됨
✅ 실제: 논블로킹 I/O는 EventLoop에서, 블로킹 I/O만 Worker 스레드 사용
```

#### 논블로킹 I/O vs 블로킹 I/O

**1. 논블로킹 I/O (EventLoop에서 처리)**

```java
// WebFlux의 논블로킹 DB 쿼리
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    // ✅ EventLoop 스레드에서 실행
    // 논블로킹 I/O 사용 (R2DBC, MongoDB Reactive 등)
    return reactiveUserRepository.findById(id);
    // → 즉시 Mono 반환
    // → DB 응답 대기 중에도 EventLoop는 다른 채널 처리
}
```

**내부 동작**:

```java
// 논블로킹 I/O의 동작 (의사 코드)
Mono<User> findById(Long id) {
    // EventLoop 스레드에서 실행
    // 1. DB 쿼리 요청 전송 (논블로킹)
    database.sendQuery("SELECT * FROM users WHERE id = ?", id);
    // → 즉시 반환 (블로킹 안 됨)

    // 2. DB 응답 대기
    // → 커널이 네트워크에서 응답 감지
    // → Selector가 채널을 "읽기 준비" 상태로 표시
    // → EventLoop는 다른 채널 처리 계속

    // 3. DB 응답 도착
    // → Selector가 감지
    // → EventLoop가 채널에서 데이터 읽기
    // → Mono에 결과 전달
}
```

**2. 블로킹 I/O (Worker 스레드 사용)**

```java
// 블로킹 I/O는 Worker 스레드 사용
@GetMapping("/files/{id}")
public Mono<Resource> getFile(@PathVariable String id) {
    // ✅ Worker 스레드로 전환
    return Mono.fromCallable(() -> {
        // Worker 스레드에서 실행
        // 블로킹 I/O (JDBC, Files.readAllBytes 등)
        return Files.readAllBytes(Paths.get(id));
    })
    .subscribeOn(Schedulers.boundedElastic());  // Worker 스레드 풀
}
```

#### 왜 논블로킹 I/O는 EventLoop에서 처리하는가?

**핵심**: 논블로킹 I/O는 즉시 반환되므로, I/O 대기 중에도 다른 채널을 처리할 수 있음

```
시나리오: EventLoop-1에 3개 채널이 할당됨

t0: Channel-1 HTTP 요청 도착
    └─ EventLoop-1이 Channel-1 처리 시작
    ├─ HTTP 파싱 (0.1ms)
    ├─ Controller 실행 (0.05ms)
    └─ DB 쿼리 시작 (논블로킹, 0.01ms)
    └─ 즉시 반환! (블로킹 안 됨)
    └─ 총 점유 시간: 0.16ms

t1: Channel-2 HTTP 요청 도착
    └─ EventLoop-1이 Channel-2 처리 시작
    ├─ HTTP 파싱 (0.1ms)
    ├─ Controller 실행 (0.05ms)
    └─ DB 쿼리 시작 (논블로킹, 0.01ms)
    └─ 즉시 반환!
    └─ 총 점유 시간: 0.16ms

t2: Channel-3 HTTP 요청 도착
    └─ EventLoop-1이 Channel-3 처리 시작
    └─ 총 점유 시간: 0.16ms

t3: EventLoop-1이 selector.select() 호출
    └─ DB 응답 대기 중 (커널이 관리)
    └─ 다른 채널의 이벤트 처리 또는 대기

t4: Channel-1의 DB 응답 도착 (10ms 후)
    └─ Selector가 감지
    └─ EventLoop-1이 Channel-1의 응답 처리 (0.1ms)

t5: Channel-2의 DB 응답 도착
    └─ EventLoop-1이 Channel-2의 응답 처리 (0.1ms)
```

**핵심 포인트**:

1. **논블로킹 I/O는 즉시 반환**: DB 쿼리 시작 후 즉시 반환 (0.01ms)
2. **I/O 대기는 커널이 관리**: DB 응답 대기 중 EventLoop는 다른 채널 처리
3. **순차 처리지만 매우 빠름**: 각 채널 처리 시간이 매우 짧음 (0.16ms)
4. **대기 시간 활용**: I/O 대기 중 다른 채널 처리 가능

#### 블로킹 I/O를 사용하면?

```java
// ❌ 블로킹 I/O를 EventLoop에서 사용하면?
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    // EventLoop 스레드에서 실행
    // ❌ 블로킹 I/O 사용
    User user = jdbcTemplate.queryForObject(...);  // 10ms 블로킹!
    // → EventLoop가 10ms 동안 블로킹
    // → 다른 채널 처리 불가
    // → 성능 저하
    return Mono.just(user);
}
```

**문제점**:

```
블로킹 I/O를 EventLoop에서 사용:
├── Channel-1 처리 시작
├── DB 쿼리 실행 (10ms 블로킹)
│   └─ EventLoop가 10ms 동안 대기
│   └─ 다른 채널 처리 불가
└─ Channel-2, Channel-3는 10ms 동안 대기

결과:
├── 동시성 저하
├── 응답 지연
└─ EventLoop의 장점 상실
```

**해결책**: 블로킹 I/O는 Worker 스레드 사용

```java
// ✅ 블로킹 I/O는 Worker 스레드 사용
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return Mono.fromCallable(() -> {
        // Worker 스레드에서 블로킹 I/O
        return jdbcTemplate.queryForObject(...);
    })
    .subscribeOn(Schedulers.boundedElastic());  // Worker 스레드 풀
    // → EventLoop는 블로킹되지 않음
    // → 다른 채널 처리 계속 가능
}
```

#### 왜 여러 채널을 하나의 EventLoop에 할당하는가?

**핵심 이유**: 논블로킹 I/O의 대기 시간을 활용하기 위해

```
시나리오: EventLoop-1에 1,000개 채널 할당

각 채널의 처리 패턴:
├── HTTP 요청 처리: 0.1ms (EventLoop 점유)
├── DB 쿼리 시작: 0.01ms (논블로킹, 즉시 반환)
├── DB 응답 대기: 10ms (커널이 관리, EventLoop는 다른 채널 처리)
└─ 응답 처리: 0.1ms (EventLoop 점유)

EventLoop-1의 시간 활용:
├── 0.11ms: Channel-1 처리
├── 0.11ms: Channel-2 처리
├── 0.11ms: Channel-3 처리
├── ... (1,000개 채널 처리)
├── 총 점유 시간: 110ms (1,000 × 0.11ms)
├── DB 응답 대기: 10ms (커널이 관리)
└─ 이 10ms 동안 다른 채널 처리 또는 대기

효율성:
├── 1,000개 채널을 110ms에 처리 시작
├── DB 응답 대기 중에도 다른 작업 처리 가능
└─ 하나의 스레드로 1,000개 연결 효율적으로 관리
```

**만약 채널을 하나씩만 할당하면?**

```
시나리오: EventLoop-1에 Channel-1만 할당

Channel-1 처리:
├── HTTP 요청 처리: 0.1ms
├── DB 쿼리 시작: 0.01ms
├── DB 응답 대기: 10ms (커널이 관리)
└─ 응답 처리: 0.1ms

EventLoop-1의 시간:
├── 0.11ms: Channel-1 처리
├── 10ms: 대기 (다른 채널 없음, 낭비!)
└─ 0.1ms: 응답 처리

문제점:
├── 10ms 동안 EventLoop가 거의 유휴 상태
├── 스레드 낭비
└─ 비효율적
```

#### 실제 처리 흐름

```java
// EventLoop의 실제 동작
void run() {
    while (!isShutdown) {
        // 1. I/O 이벤트 선택
        int selectedKeys = selector.select(1000);
        // → 준비된 채널들 확인
        // → 예: Channel-1, Channel-5, Channel-10이 준비됨

        // 2. 준비된 채널들 순차 처리
        if (selectedKeys > 0) {
            processSelectedKeys();
            // → Channel-1 처리 (0.11ms)
            // → Channel-5 처리 (0.11ms)
            // → Channel-10 처리 (0.11ms)
            // → 총 0.33ms
        }

        // 3. 작업 큐 처리
        runAllTasks();

        // 4. 다시 select() 호출
        // → 다른 채널의 이벤트 대기 또는 처리
    }
}
```

**핵심 정리**:

| 질문                                          | 답변                                                               |
| --------------------------------------------- | ------------------------------------------------------------------ |
| **EventLoop는 순차 처리하는데 왜 여러 채널?** | 논블로킹 I/O는 즉시 반환되므로, I/O 대기 중 다른 채널 처리 가능    |
| **I/O는 Worker 스레드를 쓰는데?**             | 블로킹 I/O만 Worker 스레드 사용. 논블로킹 I/O는 EventLoop에서 처리 |
| **여러 채널의 이점은?**                       | I/O 대기 시간을 활용하여 스레드 효율성 극대화                      |
| **순차 처리인데 빠른 이유?**                  | 각 채널 처리 시간이 매우 짧고(0.1ms), I/O 대기는 커널이 관리       |

**결론**: 논블로킹 I/O의 "즉시 반환" 특성 덕분에, EventLoop는 순차적으로 처리하지만 I/O 대기 시간 동안 다른 채널을 처리할 수 있어 효율적입니다. 여러 채널을 할당하는 이유는 이 대기 시간을 활용하기 위함입니다.

### WebFlux가 블로킹 I/O 기반 서비스를 호출하면?

#### 핵심 문제: 전체 스택이 논블로킹이어야 함

**시나리오: WebFlux → MVC (블로킹) 서비스 호출**

```java
// WebFlux 서비스
@RestController
public class UserController {
    @Autowired
    private RestTemplate restTemplate;  // 블로킹 HTTP 클라이언트

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        // ❌ 문제: 블로킹 HTTP 클라이언트 사용
        return Mono.fromCallable(() -> {
            // Worker 스레드에서 실행
            return restTemplate.getForObject(
                "http://user-service/users/" + id,
                User.class
            );
        })
        .subscribeOn(Schedulers.boundedElastic());  // Worker 스레드 풀
    }
}
```

**문제점**:

```
WebFlux (논블로킹) → MVC 서비스 (블로킹) 호출:

1. EventLoop 스레드에서 요청 처리 시작
2. Worker 스레드로 전환 (블로킹 호출)
3. Worker 스레드가 MVC 서비스 호출 (블로킹)
   └─ HTTP 연결 대기 (10ms)
   └─ 스레드가 블로킹됨
4. 응답 받아서 EventLoop로 전달

결과:
├── EventLoop의 장점 상실
├── Worker 스레드 풀에 의존
├── 스레드 풀 고갈 가능성
└─ WebFlux의 이점이 거의 없음
```

#### 성능 비교

**시나리오 1: 전체 스택 논블로킹 (WebFlux → WebFlux)**

```java
// WebFlux 서비스
@RestController
public class UserController {
    @Autowired
    private WebClient webClient;  // 논블로킹 HTTP 클라이언트

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        // ✅ 논블로킹 HTTP 클라이언트 사용
        return webClient.get()
            .uri("http://user-service/users/" + id)
            .retrieve()
            .bodyToMono(User.class);
        // → EventLoop 스레드에서 실행
        // → 즉시 Mono 반환
        // → I/O 대기 중 다른 채널 처리 가능
    }
}
```

**성능**:

```
논블로킹 스택:
├── EventLoop-1: Channel-1 처리 (0.1ms)
├── 논블로킹 HTTP 요청 시작 (0.01ms)
├── 즉시 반환, 다른 채널 처리 계속
├── I/O 대기: 커널이 관리
└─ 응답 도착 시 처리 (0.1ms)

동시 처리: 수천 개의 채널 동시 처리 가능
메모리: 8MB (EventLoop 스레드만)
```

**시나리오 2: 블로킹 서비스 호출 (WebFlux → MVC)**

```java
// WebFlux 서비스
@RestController
public class UserController {
    @Autowired
    private RestTemplate restTemplate;  // 블로킹

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            // Worker 스레드에서 블로킹 호출
            return restTemplate.getForObject(...);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}
```

**성능**:

```
블로킹 스택:
├── EventLoop-1: Channel-1 처리 (0.1ms)
├── Worker 스레드로 전환
├── Worker-1: HTTP 요청 (10ms 블로킹)
│   └─ 스레드가 10ms 동안 블로킹
│   └─ 다른 작업 처리 불가
└─ 응답 받아서 EventLoop로 전달

동시 처리: Worker 스레드 풀 크기에 제한 (예: 200개)
메모리: 8MB (EventLoop) + 200MB (Worker 스레드) = 208MB
```

**비교 결과**:

| 항목             | 전체 논블로킹             | 블로킹 서비스 호출                |
| ---------------- | ------------------------- | --------------------------------- |
| **동시 처리**    | 수천 개                   | Worker 스레드 풀 크기 (예: 200개) |
| **메모리**       | 8MB                       | 208MB                             |
| **스레드 효율**  | 높음 (I/O 대기 시간 활용) | 낮음 (스레드 블로킹)              |
| **WebFlux 이점** | 최대화                    | 거의 없음                         |

#### 실제 시나리오 분석

**시나리오: 1,000개의 동시 요청**

```
전체 논블로킹 스택:
├── EventLoop-1~8: 1,000개 요청 처리
├── 논블로킹 HTTP 요청 시작 (각 0.01ms)
├── I/O 대기: 커널이 관리
└─ 응답 도착 시 처리

처리 시간:
├── 요청 처리 시작: 1,000 × 0.11ms = 110ms
├── I/O 대기: 10ms (커널이 관리)
└─ 응답 처리: 1,000 × 0.1ms = 100ms

총 스레드 점유 시간: 210ms
동시 처리: 1,000개 모두 처리 가능
```

```
블로킹 서비스 호출:
├── EventLoop-1~8: 1,000개 요청 처리 시작
├── Worker 스레드 풀 (200개)에 작업 전달
├── Worker-1~200: 각각 블로킹 HTTP 호출 (10ms)
│   └─ 200개만 동시 처리
│   └─ 나머지 800개는 대기 큐에서 대기
└─ 첫 번째 200개 완료 후 다음 200개 처리

처리 시간:
├── 첫 번째 배치: 10ms (200개)
├── 두 번째 배치: 10ms (200개)
├── 세 번째 배치: 10ms (200개)
├── 네 번째 배치: 10ms (200개)
└─ 다섯 번째 배치: 10ms (200개)

총 처리 시간: 50ms (순차 처리)
동시 처리: 200개만 (Worker 스레드 풀 크기)
```

#### 해결 방법

**1. 전체 스택을 논블로킹으로 전환**

```java
// ✅ 권장: 논블로킹 HTTP 클라이언트 사용
@RestController
public class UserController {
    @Autowired
    private WebClient webClient;  // 논블로킹

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return webClient.get()
            .uri("http://user-service/users/" + id)
            .retrieve()
            .bodyToMono(User.class);
    }
}
```

**2. 블로킹 서비스가 불가피한 경우**

```java
// ⚠️ 주의: Worker 스레드 풀 크기 조정 필요
@Configuration
public class WebFluxConfig {
    @Bean
    public Scheduler boundedElastic() {
        // 블로킹 호출이 많으면 스레드 풀 크기 증가
        return Schedulers.newBoundedElastic(
            200,  // 스레드 풀 크기
            10000, // 대기 큐 크기
            "blocking-pool"
        );
    }
}

@RestController
public class UserController {
    @Autowired
    private RestTemplate restTemplate;

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            return restTemplate.getForObject(...);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}
```

**3. 하이브리드 접근**

```java
// 일부는 논블로킹, 일부는 블로킹
@RestController
public class UserController {
    @Autowired
    private WebClient webClient;  // 논블로킹 (우선 사용)

    @Autowired
    private RestTemplate restTemplate;  // 블로킹 (fallback)

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return webClient.get()
            .uri("http://user-service/users/" + id)
            .retrieve()
            .bodyToMono(User.class)
            .onErrorResume(e -> {
                // 실패 시 블로킹 클라이언트로 fallback
                return Mono.fromCallable(() -> {
                    return restTemplate.getForObject(...);
                })
                .subscribeOn(Schedulers.boundedElastic());
            });
    }
}
```

#### 핵심 원칙

**"WebFlux의 이점을 얻으려면 전체 스택이 논블로킹이어야 함"**

```
논블로킹 스택:
WebFlux (논블로킹)
  → WebClient (논블로킹)
    → R2DBC / MongoDB Reactive (논블로킹)
      → 논블로킹 DB 드라이버

✅ EventLoop의 장점 최대화
✅ 높은 동시성
✅ 낮은 메모리 사용
```

```
블로킹 스택:
WebFlux (논블로킹)
  → RestTemplate (블로킹)
    → JDBC (블로킹)
      → 블로킹 DB 드라이버

❌ Worker 스레드 풀에 의존
❌ 동시성 제한 (스레드 풀 크기)
❌ 높은 메모리 사용
❌ WebFlux의 이점 거의 없음
```

#### 실무 권장사항

**1. 마이그레이션 전략**

```
단계별 마이그레이션:
1. WebFlux 도입 (논블로킹 서버)
2. WebClient로 HTTP 클라이언트 전환
3. R2DBC / MongoDB Reactive로 DB 전환
4. 전체 스택 논블로킹 완성
```

**2. 성능 측정**

```java
// 블로킹 호출이 많은 경우 모니터링
@RestController
public class UserController {
    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            long start = System.currentTimeMillis();
            User user = restTemplate.getForObject(...);
            long elapsed = System.currentTimeMillis() - start;

            // 블로킹 시간 로깅
            log.warn("Blocking call took {}ms", elapsed);

            return user;
        })
        .subscribeOn(Schedulers.boundedElastic());
    }
}
```

**3. 선택 기준**

| 상황                        | 권장 방법                             |
| --------------------------- | ------------------------------------- |
| **전체 스택 논블로킹 가능** | WebFlux + WebClient + R2DBC           |
| **일부만 블로킹**           | WebFlux + Worker 스레드 (제한적 사용) |
| **대부분 블로킹**           | Spring MVC (WebFlux 도입 불필요)      |

**결론**: WebFlux가 블로킹 I/O 기반 서비스를 호출하면 Worker 스레드 풀에 의존하게 되어 WebFlux의 이점이 거의 사라집니다. WebFlux의 장점을 최대화하려면 전체 스택(HTTP 클라이언트, DB 드라이버 등)이 논블로킹이어야 합니다.

#### Selector의 동작 원리

```java
// Java NIO Selector 내부 동작 (개념적)
Selector {
    // 등록된 모든 채널
    Set<SelectionKey> keys;

    // I/O 준비된 채널 (커널이 설정)
    Set<SelectionKey> selectedKeys;

    int select() {
        // 1. 커널에 I/O 준비 상태 확인 요청
        // 2. 준비된 채널이 있으면 selectedKeys에 추가
        // 3. 준비된 채널 수 반환
        return epoll_wait(...);  // Linux
        // 또는 kqueue()  // macOS/BSD
        // 또는 select()  // Windows
    }
}
```

**커널 레벨 동작**:

```
애플리케이션 레벨:
├── EventLoop-1: selector.select() 호출
└── 대기 중...

커널 레벨:
├── 네트워크 스택에서 패킷 수신 감지
├── 해당 소켓을 "읽기 준비" 상태로 표시
└── EventLoop-1에 깨우기 신호 전송

애플리케이션 레벨:
├── EventLoop-1 깨어남
├── selectedKeys에서 준비된 채널 확인
└── 각 채널 처리
```

#### 실제 처리 시간 분석

**예시: 3개 채널 동시 요청 처리**

```
채널별 처리 시간:
├── Channel-1: HTTP 파싱 (0.1ms) + DB 쿼리 시작 (0.01ms) = 0.11ms
├── Channel-2: HTTP 파싱 (0.1ms) + DB 쿼리 시작 (0.01ms) = 0.11ms
└── Channel-3: HTTP 파싱 (0.1ms) + DB 쿼리 시작 (0.01ms) = 0.11ms

총 EventLoop 점유 시간: 0.33ms
DB 응답 대기 시간: 10ms (커널에서 처리, EventLoop는 다른 작업 처리)

EventLoop 관점:
├── 0.33ms: 3개 채널 처리
├── 10ms: 다른 채널 처리 또는 대기
└── DB 응답 도착 시: 각 채널 응답 처리 (각 0.1ms)
```

**효율성**:

- **블로킹 모델**: 3개 스레드 × 10ms = 30ms 스레드 점유
- **논블로킹 모델**: 1개 스레드 × 0.33ms = 0.33ms 스레드 점유
- **효율 향상**: 약 90배

#### 작업 큐 (Task Queue) 처리

**작업 큐가 필요한 이유**:

1. **스레드 안전성**: 다른 스레드에서 EventLoop의 채널에 접근할 때
2. **순서 보장**: 채널 작업이 순서대로 실행되도록 보장
3. **논블로킹 보장**: EventLoop 스레드가 블로킹되지 않도록 보장

**EventLoop의 작업 큐 구조**:

```java
// Netty EventLoop 내부 구조 (의사 코드)
class NioEventLoop {
    // 작업 큐 (스레드 안전한 큐)
    private final Queue<Runnable> taskQueue;

    // 현재 실행 중인 스레드
    private Thread thread;

    // Selector
    private Selector selector;

    // 작업 큐 초기화 (MPSC - Multi Producer Single Consumer)
    // 여러 스레드가 작업을 추가할 수 있지만, EventLoop 스레드만 소비
    taskQueue = new MpscQueue<>();  // Lock-free 큐

    /**
     * 작업을 EventLoop에 추가
     *
     * @param task 실행할 작업
     */
    void execute(Runnable task) {
        // 1. 현재 스레드가 EventLoop 스레드인지 확인
        if (isInEventLoop()) {
            // 현재 EventLoop 스레드면 즉시 실행
            // 이유: 이미 올바른 스레드에 있으므로 큐를 거칠 필요 없음
            task.run();
        } else {
            // 다른 스레드에서 호출된 경우

            // 2. 작업 큐에 추가 (스레드 안전)
            taskQueue.offer(task);

            // 3. EventLoop가 select()에서 대기 중이면 깨우기
            if (wakesUp.getAndIncrement() == 0) {
                // selector.wakeup() 호출하여 select() 블로킹 해제
                selector.wakeup();
            }
        }
    }

    /**
     * EventLoop의 메인 루프
     */
    void run() {
        while (!isShutdown) {
            try {
                // 1. I/O 이벤트 선택 (타임아웃 설정)
                int selectedKeys = selector.select(1000);

                // 2. I/O 이벤트 처리
                if (selectedKeys > 0) {
                    processSelectedKeys();
                }

                // 3. 작업 큐 처리 (중요!)
                // I/O 이벤트 처리 후 작업 큐의 모든 작업 실행
                runAllTasks();

            } catch (Exception e) {
                // 예외 처리
            }
        }
    }

    /**
     * 작업 큐의 모든 작업 실행
     */
    void runAllTasks() {
        // 타임아웃 설정 (너무 오래 실행되지 않도록)
        long deadline = System.nanoTime() + 1000000; // 1ms

        int executed = 0;
        Runnable task;

        // 큐에서 작업을 하나씩 꺼내서 실행
        while ((task = taskQueue.poll()) != null) {
            try {
                // 작업 실행
                task.run();
                executed++;

                // 타임아웃 체크 (너무 많은 작업이 있으면 중단)
                if (executed >= 1000 || System.nanoTime() > deadline) {
                    break;
                }
            } catch (Throwable t) {
                // 예외 처리
            }
        }
    }

    /**
     * 현재 스레드가 EventLoop 스레드인지 확인
     */
    boolean isInEventLoop() {
        return Thread.currentThread() == this.thread;
    }
}
```

**작업 큐 사용 시나리오 (실제 소스 코드 예시)**:

#### 시나리오 1: 다른 스레드에서 채널에 쓰기

**문제 상황**: Worker 스레드에서 비즈니스 로직을 처리한 후, 결과를 채널에 써야 하는 경우

```java
// ❌ 잘못된 예: 다른 스레드에서 직접 채널에 쓰기
CompletableFuture.supplyAsync(() -> {
    // Worker 스레드에서 실행
    return processData();
}).thenAccept(data -> {
    // ❌ 위험: 다른 스레드에서 채널에 직접 쓰기
    // 채널은 EventLoop 스레드에서만 접근해야 함
    channel.writeAndFlush(data);  // 스레드 안전하지 않음!
});

// ✅ 올바른 예: 작업 큐를 통해 EventLoop에 작업 추가
CompletableFuture.supplyAsync(() -> {
    // Worker 스레드에서 실행
    return processData();
}).thenAccept(data -> {
    // ✅ 안전: 작업 큐를 통해 EventLoop에 작업 추가
    channel.eventLoop().execute(() -> {
        // 이 코드는 EventLoop 스레드에서 실행됨
        // 스레드 안전하게 채널에 쓰기 가능
        channel.writeAndFlush(data);
    });
});
```

**내부 동작**:

```java
// Netty 내부 코드 (의사 코드)
public void writeAndFlush(Object msg) {
    Channel channel = this;

    // 현재 스레드가 EventLoop 스레드인지 확인
    EventLoop eventLoop = channel.eventLoop();

    if (eventLoop.inEventLoop()) {
        // EventLoop 스레드면 직접 쓰기
        doWrite(msg);
    } else {
        // 다른 스레드면 작업 큐에 추가
        eventLoop.execute(() -> {
            doWrite(msg);
        });
    }
}
```

#### 시나리오 2: WebFlux에서 publishOn() 사용 시

**실제 WebFlux 코드에서의 작업 큐 사용**:

```java
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return userService.findById(id)
            // Worker 스레드로 전환
            .publishOn(Schedulers.boundedElastic())
            .map(user -> {
                // Worker 스레드에서 실행
                // 블로킹 작업 가능
                return heavyComputation(user);
            })
            .publishOn(Schedulers.immediate())  // EventLoop로 돌아가기
            .doOnNext(user -> {
                // EventLoop 스레드에서 실행
                // 작업 큐를 통해 안전하게 채널에 쓰기
            });
    }
}
```

**내부 동작 (Reactor Core)**:

```java
// Reactor 내부 코드 (의사 코드)
public final Mono<T> publishOn(Scheduler scheduler) {
    return new MonoPublishOn<>(this, scheduler);
}

class MonoPublishOn<T> extends Mono<T> {
    public void subscribe(CoreSubscriber<? super T> actual) {
        // 구독 시점에 스레드 전환
        Scheduler.Worker worker = scheduler.createWorker();

        source.subscribe(new PublishOnSubscriber<>(actual, worker));
    }

    static final class PublishOnSubscriber<T> implements Subscriber<T> {
        public void onNext(T value) {
            // Worker 스레드에서 실행
            worker.schedule(() -> {
                // 작업 큐에 추가되어 실행됨
                actual.onNext(value);
            });
        }
    }
}
```

#### 시나리오 3: 타이머 작업 (Netty 내부)

**실제 Netty 코드에서의 사용**:

```java
// Netty ChannelHandler에서 타이머 사용
public class MyChannelHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // EventLoop에 지연 작업 추가
        ctx.channel().eventLoop().schedule(() -> {
            // 5초 후 실행
            ctx.writeAndFlush("Timeout");
        }, 5, TimeUnit.SECONDS);
    }
}
```

**내부 동작 (Netty ScheduledTaskQueue)**:

```java
// Netty 내부 코드 (의사 코드)
class NioEventLoop {
    // 지연 작업 큐 (우선순위 큐)
    private final PriorityQueue<ScheduledTask> scheduledTaskQueue;

    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        // 지연 시간 계산
        long delayNanos = unit.toNanos(delay);
        long deadline = System.nanoTime() + delayNanos;

        // ScheduledTask 생성
        ScheduledTask scheduledTask = new ScheduledTask(task, deadline);

        // 작업 큐에 추가
        scheduledTaskQueue.offer(scheduledTask);

        // EventLoop가 select()에서 대기 중이면 깨우기
        if (wakesUp.getAndIncrement() == 0) {
            selector.wakeup();
        }

        return scheduledTask;
    }

    void runAllTasks() {
        long currentTime = System.nanoTime();

        // 1. 지연 작업 큐에서 시간이 된 작업들을 일반 작업 큐로 이동
        while (!scheduledTaskQueue.isEmpty()) {
            ScheduledTask task = scheduledTaskQueue.peek();
            if (task.deadline <= currentTime) {
                scheduledTaskQueue.poll();
                taskQueue.offer(task);  // 일반 작업 큐로 이동
            } else {
                break;
            }
        }

        // 2. 일반 작업 큐 처리
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            task.run();
        }
    }
}
```

#### 시나리오 4: 주기적 작업 (Heartbeat)

```java
// WebSocket Heartbeat 예시
public class WebSocketHandler extends ChannelInboundHandlerAdapter {
    private ScheduledFuture<?> heartbeatTask;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // EventLoop에 주기적 작업 추가
        heartbeatTask = ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            // 1초마다 실행
            ctx.writeAndFlush(new TextWebSocketFrame("Heartbeat"));
        }, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 채널 종료 시 작업 취소
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }
    }
}
```

#### 시나리오 5: WebFlux에서 블로킹 작업 처리

**실제 WebFlux 코드**:

```java
@RestController
public class FileController {
    @GetMapping("/files/{id}")
    public Mono<Resource> getFile(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            // 블로킹 I/O 작업
            return Files.readAllBytes(Paths.get(id));
        })
        .subscribeOn(Schedulers.boundedElastic())  // Worker 스레드에서 실행
        .map(bytes -> new ByteArrayResource(bytes))
        .doOnNext(resource -> {
            // EventLoop 스레드로 돌아와서 응답 작성
            // 작업 큐를 통해 안전하게 처리
        });
    }
}
```

**내부 동작**:

```java
// Reactor 내부 코드 (의사 코드)
public static <T> Mono<T> fromCallable(Callable<T> callable) {
    return new MonoCallable<>(callable);
}

class MonoCallable<T> extends Mono<T> {
    public void subscribe(CoreSubscriber<? super T> actual) {
        // 구독 시점에 스레드 전환
        Scheduler scheduler = Schedulers.boundedElastic();
        Scheduler.Worker worker = scheduler.createWorker();

        worker.schedule(() -> {
            try {
                // Worker 스레드에서 블로킹 작업 실행
                T value = callable.call();

                // 결과를 EventLoop로 전달 (작업 큐 사용)
                actual.onNext(value);
                actual.onComplete();
            } catch (Exception e) {
                actual.onError(e);
            }
        });
    }
}
```

#### 시나리오 6: Netty Channel Pipeline에서의 작업 큐 사용

```java
// Netty ChannelHandler에서 다른 스레드로 전환 후 다시 돌아오기
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // EventLoop 스레드에서 실행 중

        // 다른 스레드에서 비동기 작업 수행
        CompletableFuture.supplyAsync(() -> {
            // Worker 스레드에서 실행
            return processAsync(msg);
        }).thenAccept(result -> {
            // Worker 스레드에서 실행
            // 작업 큐를 통해 EventLoop로 돌아가기
            ctx.channel().eventLoop().execute(() -> {
                // EventLoop 스레드에서 실행
                ctx.fireChannelRead(result);  // 다음 핸들러로 전달
            });
        });
    }
}
```

#### 시나리오 7: WebFlux의 doOnNext에서 작업 큐 사용

```java
@RestController
public class LoggingController {
    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return userService.findById(id)
            .doOnNext(user -> {
                // EventLoop 스레드에서 실행
                // 로깅 작업 (논블로킹)
                log.info("User found: {}", user);
            })
            .publishOn(Schedulers.boundedElastic())
            .doOnNext(user -> {
                // Worker 스레드에서 실행
                // 블로킹 로깅 작업
                writeToFile(user);
            })
            .publishOn(Schedulers.immediate())
            .doOnNext(user -> {
                // 다시 EventLoop 스레드로 돌아옴
                // 작업 큐를 통해 안전하게 처리
            });
    }
}
```

**작업 큐 사용 시점 요약**:

| 상황                      | 사용 방법                         | 이유             |
| ------------------------- | --------------------------------- | ---------------- |
| 다른 스레드에서 채널 쓰기 | `eventLoop.execute()`             | 스레드 안전성    |
| 타이머 작업               | `eventLoop.schedule()`            | 지연 실행        |
| 주기적 작업               | `eventLoop.scheduleAtFixedRate()` | 주기적 실행      |
| WebFlux 스레드 전환       | `publishOn()` / `subscribeOn()`   | 스레드 전환      |
| 블로킹 작업 후 채널 접근  | 작업 큐 사용                      | 스레드 안전성    |
| Pipeline에서 스레드 전환  | `eventLoop.execute()`             | EventLoop로 복귀 |

**작업 큐 처리 순서**:

```
EventLoop 실행 흐름:

1. selector.select() 호출
   └─ I/O 이벤트 대기 (최대 1초)

2. processSelectedKeys() 호출
   └─ 준비된 채널의 I/O 이벤트 처리
   └─ 예: Channel-1 읽기, Channel-2 쓰기

3. runAllTasks() 호출
   └─ 작업 큐의 모든 작업 실행
   └─ 예: 다른 스레드에서 추가한 쓰기 작업

4. 다시 1번으로 돌아감
```

**작업 큐와 I/O 이벤트의 우선순위**:

```
우선순위:
1. I/O 이벤트 처리 (높음)
   └─ 네트워크 I/O는 지연되면 안 됨

2. 작업 큐 처리 (중간)
   └─ I/O 이벤트 처리 후 실행
   └─ 타임아웃 설정으로 무한 실행 방지
```

### I/O 이벤트 처리 vs 작업 큐 처리: 소스 코드로 이해하기

#### 1. I/O 이벤트 처리란?

**정의**: 네트워크에서 실제 데이터가 도착하거나 전송 준비가 되었을 때 처리하는 작업

**소스 코드로 보는 I/O 이벤트 처리**:

```java
// Netty EventLoop 내부 코드 (의사 코드)
class NioEventLoop {
    private Selector selector;
    private Queue<Runnable> taskQueue;

    void run() {
        while (!isShutdown) {
            try {
                // 1. I/O 이벤트 선택 (커널에서 준비된 채널 확인)
                int selectedKeys = selector.select(1000);

                // 2. I/O 이벤트 처리 (우선순위 높음)
                if (selectedKeys > 0) {
                    processSelectedKeys();  // ← I/O 이벤트 처리
                }

                // 3. 작업 큐 처리 (I/O 이벤트 처리 후)
                runAllTasks();  // ← 작업 큐 처리

            } catch (Exception e) {
                // 예외 처리
            }
        }
    }

    /**
     * I/O 이벤트 처리 (네트워크에서 데이터 읽기/쓰기)
     */
    void processSelectedKeys() {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iter = selectedKeys.iterator();

        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();

            // I/O 이벤트 타입에 따라 처리
            if (key.isReadable()) {
                // 읽기 이벤트: 네트워크에서 데이터가 도착함
                processRead(key);
            }
            if (key.isWritable()) {
                // 쓰기 이벤트: 네트워크로 데이터를 보낼 수 있음
                processWrite(key);
            }
            if (key.isAcceptable()) {
                // 연결 수락 이벤트: 새로운 클라이언트 연결
                processAccept(key);
            }
            if (key.isConnectable()) {
                // 연결 완료 이벤트: 서버 연결 완료
                processConnect(key);
            }
        }
    }

    /**
     * 읽기 이벤트 처리
     */
    void processRead(SelectionKey key) {
        // 채널에서 데이터 읽기
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // 실제 네트워크에서 데이터 읽기 (논블로킹)
        int bytesRead = channel.read(buffer);

        if (bytesRead > 0) {
            // 데이터를 읽었으면 ChannelPipeline으로 전달
            ChannelHandlerContext ctx = getContext(channel);
            ctx.fireChannelRead(buffer);
        }
    }

    /**
     * 쓰기 이벤트 처리
     */
    void processWrite(SelectionKey key) {
        // 채널에 데이터 쓰기
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelHandlerContext ctx = getContext(channel);

        // 쓰기 버퍼에서 데이터를 네트워크로 전송
        ctx.flush();
    }
}
```

**I/O 이벤트의 특징**:

- **커널에서 발생**: 네트워크 스택에서 실제 데이터 도착/전송 가능 시점
- **즉시 처리 필요**: 지연되면 네트워크 버퍼 오버플로우 가능
- **Selector가 감지**: `selector.select()`가 준비된 채널 반환

#### 2. 작업 큐 처리란?

**정의**: 다른 스레드에서 EventLoop에 추가한 일반 작업들을 처리

**소스 코드로 보는 작업 큐 처리**:

```java
// Netty EventLoop 내부 코드 (의사 코드)
class NioEventLoop {
    private Queue<Runnable> taskQueue;

    /**
     * 작업 큐 처리
     */
    void runAllTasks() {
        long deadline = System.nanoTime() + 1000000; // 1ms 타임아웃
        int executed = 0;

        Runnable task;
        // 작업 큐에서 작업을 하나씩 꺼내서 실행
        while ((task = taskQueue.poll()) != null) {
            try {
                // 작업 실행
                task.run();
                executed++;

                // 타임아웃 체크 (너무 오래 실행되지 않도록)
                if (executed >= 1000 || System.nanoTime() > deadline) {
                    break;  // 다음 select() 사이클에서 다시 처리
                }
            } catch (Throwable t) {
                // 예외 처리
            }
        }
    }

    /**
     * 작업을 큐에 추가
     */
    void execute(Runnable task) {
        if (isInEventLoop()) {
            // 현재 EventLoop 스레드면 즉시 실행
            task.run();
        } else {
            // 다른 스레드면 큐에 추가
            taskQueue.offer(task);

            // EventLoop가 select()에서 대기 중이면 깨우기
            if (wakesUp.getAndIncrement() == 0) {
                selector.wakeup();
            }
        }
    }
}
```

**작업 큐의 특징**:

- **애플리케이션에서 발생**: 다른 스레드가 `eventLoop.execute()` 호출
- **지연 가능**: I/O 이벤트 처리 후 실행
- **타임아웃 제한**: 무한 실행 방지

#### 3. I/O 이벤트 vs 작업 큐: 실제 동작 비교

**시나리오: 동시에 I/O 이벤트와 작업 큐 작업이 있는 경우**

```java
// 실제 Netty EventLoop 실행 흐름
void run() {
    while (!isShutdown) {
        // 상황:
        // - Channel-1에서 HTTP 요청 데이터 도착 (I/O 이벤트)
        // - Worker 스레드에서 작업 큐에 쓰기 작업 추가

        // 1. I/O 이벤트 선택
        int selectedKeys = selector.select(1000);
        // → Channel-1이 읽기 준비 상태로 감지됨

        // 2. I/O 이벤트 처리 (우선순위 높음)
        if (selectedKeys > 0) {
            processSelectedKeys();
            // → Channel-1에서 HTTP 요청 데이터 읽기
            // → ChannelPipeline으로 전달
            // → Controller 실행
        }

        // 3. 작업 큐 처리 (I/O 이벤트 처리 후)
        runAllTasks();
        // → Worker 스레드가 추가한 쓰기 작업 실행
        // → Channel-1에 응답 데이터 쓰기
    }
}
```

**왜 I/O 이벤트가 우선순위가 높은가?**

```java
// 문제 상황: 작업 큐를 먼저 처리하면?
void run() {
    // ❌ 잘못된 순서
    runAllTasks();  // 작업 큐 먼저 처리
    processSelectedKeys();  // I/O 이벤트 나중에 처리

    // 문제점:
    // 1. 네트워크 버퍼가 가득 차면 데이터 손실 가능
    // 2. 클라이언트 타임아웃 발생 가능
    // 3. 네트워크 성능 저하
}

// ✅ 올바른 순서
void run() {
    processSelectedKeys();  // I/O 이벤트 먼저 처리
    runAllTasks();  // 작업 큐 나중에 처리

    // 장점:
    // 1. 네트워크 데이터 즉시 처리
    // 2. 버퍼 오버플로우 방지
    // 3. 낮은 지연 시간
}
```

#### 4. Worker 스레드 vs 작업 큐: 언제 무엇을 사용하는가?

**핵심 차이점**:

| 구분          | Worker 스레드          | 작업 큐                                 |
| ------------- | ---------------------- | --------------------------------------- |
| **용도**      | 블로킹 작업 실행       | EventLoop 스레드에서 실행해야 하는 작업 |
| **스레드**    | 별도의 스레드 풀       | EventLoop 스레드                        |
| **블로킹**    | 블로킹 가능            | 블로킹 금지                             |
| **사용 시점** | CPU 집약적, 블로킹 I/O | 채널 접근, 타이머, 순서 보장            |

**소스 코드로 보는 차이**:

```java
// 시나리오 1: 블로킹 작업 → Worker 스레드 사용
@GetMapping("/files/{id}")
public Mono<Resource> getFile(@PathVariable String id) {
    return Mono.fromCallable(() -> {
        // ✅ Worker 스레드에서 실행
        // 블로킹 I/O 작업
        return Files.readAllBytes(Paths.get(id));  // 블로킹!
    })
    .subscribeOn(Schedulers.boundedElastic())  // Worker 스레드 풀 사용
    .map(bytes -> new ByteArrayResource(bytes));
}

// 내부 동작:
// 1. boundedElastic 스레드 풀에서 실행
// 2. 블로킹 I/O 수행 (파일 읽기)
// 3. 완료 후 결과 반환
```

```java
// 시나리오 2: 채널 접근 → 작업 큐 사용
CompletableFuture.supplyAsync(() -> {
    // Worker 스레드에서 비즈니스 로직 처리
    return processData();
}).thenAccept(data -> {
    // ✅ 작업 큐를 통해 EventLoop에 작업 추가
    // 이유: 채널은 EventLoop 스레드에서만 접근 가능
    channel.eventLoop().execute(() -> {
        // EventLoop 스레드에서 실행
        channel.writeAndFlush(data);  // 채널 접근
    });
});
```

**왜 Worker 스레드가 있는데 작업 큐를 사용하는가?**

**이유 1: 스레드 안전성**

```java
// ❌ 잘못된 예: Worker 스레드에서 직접 채널 접근
CompletableFuture.supplyAsync(() -> {
    return processData();
}).thenAccept(data -> {
    // Worker 스레드에서 실행 중
    // ❌ 위험: 다른 스레드에서 채널 접근
    channel.writeAndFlush(data);  // 스레드 안전하지 않음!
    // Netty의 채널은 하나의 EventLoop에만 할당됨
    // 다른 스레드에서 접근하면 동시성 문제 발생
});

// ✅ 올바른 예: 작업 큐 사용
CompletableFuture.supplyAsync(() -> {
    return processData();
}).thenAccept(data -> {
    // Worker 스레드에서 실행 중
    // ✅ 안전: 작업 큐를 통해 EventLoop에 작업 추가
    channel.eventLoop().execute(() -> {
        // EventLoop 스레드에서 실행
        channel.writeAndFlush(data);  // 스레드 안전
    });
});
```

**이유 2: 순서 보장**

```java
// 채널의 이벤트는 순서대로 처리되어야 함
// Worker 스레드를 사용하면 순서 보장 불가

// ❌ Worker 스레드 사용 시 순서 보장 불가
CompletableFuture.supplyAsync(() -> {
    return "Task-1";
}).thenAccept(data -> {
    // Worker-Thread-1에서 실행
    channel.write(data);  // 언제 실행될지 모름
});

CompletableFuture.supplyAsync(() -> {
    return "Task-2";
}).thenAccept(data -> {
    // Worker-Thread-2에서 실행
    channel.write(data);  // Task-1보다 먼저 실행될 수 있음!
});

// ✅ 작업 큐 사용 시 순서 보장
channel.eventLoop().execute(() -> {
    channel.write("Task-1");  // 순서대로 실행
});
channel.eventLoop().execute(() -> {
    channel.write("Task-2");  // Task-1 다음에 실행
});
```

**이유 3: 성능**

```java
// Worker 스레드는 블로킹 작업용
// 작업 큐는 경량 작업용

// ✅ 블로킹 작업: Worker 스레드
Mono.fromCallable(() -> {
    // 블로킹 I/O
    return database.query();  // 10ms 소요
}).subscribeOn(Schedulers.boundedElastic());

// ✅ 경량 작업: 작업 큐
channel.eventLoop().execute(() -> {
    // 논블로킹 작업
    channel.write(data);  // 0.01ms 소요
    // EventLoop 스레드에서 즉시 실행
});
```

**실제 사용 예시 비교**:

```java
// 예시 1: 블로킹 DB 쿼리 → Worker 스레드
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return Mono.fromCallable(() -> {
        // ✅ Worker 스레드에서 실행
        // 블로킹 DB 쿼리
        return jdbcTemplate.queryForObject(
            "SELECT * FROM users WHERE id = ?",
            User.class,
            id
        );
    })
    .subscribeOn(Schedulers.boundedElastic());  // Worker 스레드 풀
}

// 예시 2: 논블로킹 DB 쿼리 후 채널 접근 → 작업 큐
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return reactiveUserRepository.findById(id)
        .doOnNext(user -> {
            // 논블로킹 쿼리 완료 후
            // ✅ 작업 큐를 통해 EventLoop에 작업 추가
            channel.eventLoop().execute(() -> {
                // EventLoop 스레드에서 채널 접근
                logChannelAccess(channel, user);
            });
        });
}
```

**핵심 정리**:

1. **I/O 이벤트**: 네트워크에서 실제 데이터 도착/전송 시 처리 (우선순위 높음)
2. **작업 큐**: 다른 스레드에서 EventLoop에 추가한 작업 처리 (우선순위 낮음)
3. **Worker 스레드**: 블로킹 작업 실행용 (별도 스레드 풀)
4. **작업 큐**: EventLoop 스레드에서 실행해야 하는 작업용 (스레드 안전성, 순서 보장)

### I/O 이벤트 처리 상세 설명

#### I/O 이벤트 처리가 정확히 하는 일

**정의**: 커널에서 네트워크 소켓에 실제 데이터가 도착하거나 전송 준비가 되었을 때, Selector가 감지하여 EventLoop가 처리하는 작업

**구체적인 처리 내용**:

```java
// Netty EventLoop의 I/O 이벤트 처리 (의사 코드)
void processSelectedKeys() {
    Set<SelectionKey> selectedKeys = selector.selectedKeys();

    for (SelectionKey key : selectedKeys) {
        // 1. 읽기 이벤트 처리
        if (key.isReadable()) {
            // 네트워크에서 데이터가 도착함
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(8192);

            // 실제 네트워크 소켓에서 데이터 읽기
            int bytesRead = channel.read(buffer);

            if (bytesRead > 0) {
                // 읽은 데이터를 ChannelPipeline으로 전달
                // → HTTP 요청 파싱
                // → Controller 호출
                // → Service 실행
                pipeline.fireChannelRead(buffer);
            } else if (bytesRead < 0) {
                // 연결 종료
                pipeline.fireChannelInactive();
            }
        }

        // 2. 쓰기 이벤트 처리
        if (key.isWritable()) {
            // 네트워크로 데이터를 보낼 수 있음
            SocketChannel channel = (SocketChannel) key.channel();

            // 쓰기 버퍼에서 데이터를 실제 소켓으로 전송
            ChannelOutboundBuffer outboundBuffer = channel.unsafe().outboundBuffer();
            int written = channel.write(outboundBuffer.getBytes());

            if (written > 0) {
                // 전송 완료된 데이터 제거
                outboundBuffer.removeBytes(written);
            }
        }

        // 3. 연결 수락 이벤트 처리
        if (key.isAcceptable()) {
            // 새로운 클라이언트 연결 요청
            ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            SocketChannel clientChannel = serverChannel.accept();

            // 논블로킹 모드 설정
            clientChannel.configureBlocking(false);

            // 새로운 채널을 EventLoop에 등록
            clientChannel.register(selector, SelectionKey.OP_READ);

            // 연결 이벤트를 Pipeline으로 전달
            pipeline.fireChannelActive();
        }

        // 4. 연결 완료 이벤트 처리
        if (key.isConnectable()) {
            // 서버 연결 완료
            SocketChannel channel = (SocketChannel) key.channel();

            if (channel.finishConnect()) {
                // 연결 완료 이벤트 전달
                pipeline.fireChannelActive();
            } else {
                // 연결 실패
                pipeline.fireExceptionCaught(new ConnectException());
            }
        }
    }
}
```

**I/O 이벤트 처리의 특징**:

1. **커널에서 발생**: 네트워크 스택에서 실제 패킷 수신/전송 가능 시점
2. **즉시 처리 필요**: 지연되면 네트워크 버퍼 오버플로우 가능
3. **Selector가 감지**: `selector.select()`가 준비된 채널 반환
4. **논블로킹 I/O**: `channel.read()`, `channel.write()`는 즉시 반환

**실제 처리 예시**:

```
시나리오: HTTP 요청 처리

1. 클라이언트가 HTTP 요청 전송
   └─ 네트워크 스택에 패킷 도착

2. 커널이 소켓을 "읽기 준비" 상태로 표시
   └─ Selector가 감지

3. EventLoop가 selector.select()에서 깨어남
   └─ Channel-1이 읽기 준비 상태

4. processSelectedKeys() 호출
   ├─ Channel-1에서 HTTP 요청 데이터 읽기
   ├─ ByteBuffer에 데이터 저장
   └─ ChannelPipeline으로 전달

5. Pipeline 처리
   ├─ HTTP 디코더가 요청 파싱
   ├─ DispatcherServlet이 라우팅
   ├─ Controller 실행
   └─ Service → Repository 실행

6. 응답 생성 후 쓰기 버퍼에 추가
   └─ 네트워크로 전송 준비

7. 커널이 소켓을 "쓰기 준비" 상태로 표시
   └─ Selector가 감지

8. processSelectedKeys()에서 쓰기 이벤트 처리
   └─ 실제 네트워크로 응답 전송
```

### 다른 스레드에서 EventLoop에 추가한 작업 처리: 요약

#### 작업 큐에 추가되는 경우들

**1. 다른 스레드에서 채널에 쓰기**

```java
// Worker 스레드에서 실행
CompletableFuture.supplyAsync(() -> {
    // 비즈니스 로직 처리
    return processData();
}).thenAccept(data -> {
    // ❌ 위험: Worker 스레드에서 직접 채널 접근
    // channel.writeAndFlush(data);

    // ✅ 안전: 작업 큐를 통해 EventLoop에 작업 추가
    channel.eventLoop().execute(() -> {
        // EventLoop 스레드에서 실행
        channel.writeAndFlush(data);
    });
});
```

**이유**: 채널은 하나의 EventLoop에만 할당되어야 하므로, 다른 스레드에서 직접 접근하면 스레드 안전성 문제 발생

**2. 타이머 작업 (지연 실행)**

```java
// EventLoop에 지연 작업 추가
channel.eventLoop().schedule(() -> {
    // 5초 후 실행
    channel.writeAndFlush("Timeout");
}, 5, TimeUnit.SECONDS);
```

**내부 동작**: ScheduledTaskQueue에 추가 → 시간이 되면 일반 작업 큐로 이동 → 실행

**3. 주기적 작업 (Heartbeat)**

```java
// EventLoop에 주기적 작업 추가
ScheduledFuture<?> heartbeat = channel.eventLoop().scheduleAtFixedRate(() -> {
    // 1초마다 실행
    channel.writeAndFlush("Heartbeat");
}, 0, 1, TimeUnit.SECONDS);
```

**내부 동작**: 매 주기마다 작업 큐에 추가 → 실행

**4. WebFlux의 publishOn() 사용 시**

```java
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable Long id) {
    return userService.findById(id)
        .publishOn(Schedulers.boundedElastic())  // Worker 스레드로 전환
        .map(user -> {
            // Worker 스레드에서 실행
            return heavyComputation(user);
        })
        .publishOn(Schedulers.immediate())  // EventLoop로 돌아가기
        .doOnNext(user -> {
            // 작업 큐를 통해 EventLoop에 작업 추가
            // EventLoop 스레드에서 실행
        });
}
```

**내부 동작**: Reactor가 내부적으로 `eventLoop.execute()` 호출하여 작업 큐에 추가

**5. 블로킹 작업 완료 후 채널 접근**

```java
// 블로킹 작업을 Worker 스레드에서 실행
Mono.fromCallable(() -> {
    // Worker 스레드에서 블로킹 I/O
    return Files.readAllBytes(path);
})
.subscribeOn(Schedulers.boundedElastic())
.doOnNext(bytes -> {
    // Worker 스레드에서 실행
    // 작업 큐를 통해 EventLoop로 돌아가기
    channel.eventLoop().execute(() -> {
        // EventLoop 스레드에서 채널 접근
        channel.writeAndFlush(bytes);
    });
});
```

**이유**: 블로킹 작업은 Worker 스레드에서 실행하되, 채널 접근은 EventLoop 스레드에서 해야 함

**6. Netty ChannelHandler에서 스레드 전환**

```java
public class MyHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // EventLoop 스레드에서 실행 중

        // 다른 스레드에서 비동기 작업 수행
        CompletableFuture.supplyAsync(() -> {
            // Worker 스레드에서 실행
            return processAsync(msg);
        }).thenAccept(result -> {
            // Worker 스레드에서 실행
            // 작업 큐를 통해 EventLoop로 돌아가기
            ctx.channel().eventLoop().execute(() -> {
                // EventLoop 스레드에서 실행
                ctx.fireChannelRead(result);  // 다음 핸들러로 전달
            });
        });
    }
}
```

**이유**: Pipeline의 다음 핸들러는 EventLoop 스레드에서 실행되어야 순서 보장

**7. 외부 이벤트 리스너에서 채널 접근**

```java
// 외부 시스템의 이벤트 리스너
externalSystem.addListener(event -> {
    // 외부 스레드에서 실행
    // 작업 큐를 통해 EventLoop에 작업 추가
    channel.eventLoop().execute(() -> {
        // EventLoop 스레드에서 채널 접근
        channel.writeAndFlush(event);
    });
});
```

**이유**: 외부 시스템의 스레드에서 직접 채널 접근하면 스레드 안전성 문제

#### 작업 큐 사용 패턴 요약

| 상황                          | 사용 방법                         | 이유                                     |
| ----------------------------- | --------------------------------- | ---------------------------------------- |
| **다른 스레드에서 채널 쓰기** | `eventLoop.execute()`             | 스레드 안전성 (채널은 EventLoop 전용)    |
| **타이머 작업**               | `eventLoop.schedule()`            | 지연 실행 (ScheduledTaskQueue → 작업 큐) |
| **주기적 작업**               | `eventLoop.scheduleAtFixedRate()` | 주기적 실행 (작업 큐에 반복 추가)        |
| **WebFlux 스레드 전환**       | `publishOn()` / `subscribeOn()`   | Reactor가 내부적으로 작업 큐 사용        |
| **블로킹 작업 후 채널 접근**  | `eventLoop.execute()`             | Worker 스레드 → EventLoop 스레드 전환    |
| **Pipeline 스레드 전환**      | `eventLoop.execute()`             | Pipeline 순서 보장                       |
| **외부 이벤트 리스너**        | `eventLoop.execute()`             | 외부 스레드 → EventLoop 스레드 전환      |

**공통 원칙**:

- **채널 접근**: 항상 EventLoop 스레드에서만
- **스레드 전환**: 다른 스레드에서 작업 완료 후 EventLoop로 복귀
- **순서 보장**: 같은 채널의 작업은 순서대로 실행
- **스레드 안전성**: 락 없이 안전하게 처리

**스레드 안전성 보장**:

```java
// 작업 큐는 MPSC (Multi Producer Single Consumer) 큐 사용
// 여러 스레드가 동시에 작업을 추가할 수 있음

// 스레드-1
eventLoop.execute(() -> channel.write("Task-1"));

// 스레드-2
eventLoop.execute(() -> channel.write("Task-2"));

// 스레드-3
eventLoop.execute(() -> channel.write("Task-3"));

// EventLoop 스레드만 작업을 소비
// → Lock-free 알고리즘으로 성능 최적화
```

**작업 큐의 타임아웃 처리**:

```java
void runAllTasks() {
    long deadline = System.nanoTime() + 1000000; // 1ms

    int executed = 0;
    Runnable task;

    while ((task = taskQueue.poll()) != null) {
        task.run();
        executed++;

        // 타임아웃 체크
        if (executed >= 1000 || System.nanoTime() > deadline) {
            // 너무 많은 작업이 있으면 중단
            // 다음 select() 사이클에서 다시 처리
            break;
        }
    }
}
```

**왜 타임아웃이 필요한가?**

```
문제 상황:
├── 작업 큐에 수만 개의 작업이 쌓임
├── runAllTasks()가 모든 작업을 처리하려고 시도
└─ I/O 이벤트 처리가 지연됨

해결:
├── 타임아웃 설정 (예: 1ms)
├── 최대 실행 개수 제한 (예: 1000개)
└─ 나머지 작업은 다음 사이클에서 처리
```

**실제 사용 예시: WebFlux에서의 활용**:

```java
// WebFlux 내부에서 작업 큐 사용
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        return userService.findById(id)
            .publishOn(Schedulers.boundedElastic())  // Worker 스레드로 전환
            .map(user -> {
                // Worker 스레드에서 실행
                return heavyComputation(user);
            })
            .publishOn(Schedulers.immediate())  // EventLoop로 돌아가기
            .doOnNext(user -> {
                // EventLoop 스레드에서 실행
                // 작업 큐를 통해 안전하게 채널에 쓰기
                channel.write(user);
            });
    }
}
```

**작업 큐의 성능 특성**:

```
작업 큐 타입: MPSC (Multi Producer Single Consumer)
├── Lock-free 알고리즘
├── 여러 스레드가 동시에 추가 가능 (Producer)
├── EventLoop 스레드만 소비 (Consumer)
└─ 높은 성능 (락 없음)

성능 비교:
├── BlockingQueue: 락 사용 → 느림
├── MPSC Queue: Lock-free → 빠름
└─ 성능 차이: 약 10배 이상
```

**작업 큐 모니터링**:

```java
// 작업 큐 상태 확인
NioEventLoop eventLoop = (NioEventLoop) channel.eventLoop();

// 큐 크기 확인 (Netty 내부 API)
int queueSize = eventLoop.pendingTasks();

// 큐가 너무 크면 경고
if (queueSize > 1000) {
    logger.warn("Task queue is too large: {}", queueSize);
}
```

**핵심 정리**:

1. **작업 큐의 역할**:

   - 다른 스레드에서 EventLoop에 작업을 안전하게 추가
   - 채널 작업의 순서 보장
   - 스레드 안전성 보장

2. **execute() 메서드**:

   - 현재 스레드가 EventLoop면 즉시 실행
   - 다른 스레드면 큐에 추가하고 selector.wakeup() 호출

3. **runAllTasks() 메서드**:

   - I/O 이벤트 처리 후 호출
   - 타임아웃과 최대 실행 개수로 제한
   - 무한 실행 방지

4. **성능 최적화**:
   - MPSC Lock-free 큐 사용
   - 락 없이 높은 성능 보장

#### 스레드 안전성

**핵심 원칙**: 각 채널은 하나의 EventLoop에만 할당됨

```
채널 할당 규칙:
├── Channel-1 → EventLoop-1 (고정)
├── Channel-2 → EventLoop-1 (고정)
├── Channel-3 → EventLoop-2 (고정)
└── Channel-4 → EventLoop-2 (고정)
```

**이유**:

- **스레드 안전성**: 같은 EventLoop에서만 처리 → 동기화 불필요
- **순서 보장**: 같은 채널의 이벤트는 순서대로 처리
- **성능**: 락 없이 처리 가능

### 3.4 Channel과 Select 개념

#### Channel (채널)

**역할**: 논블로킹 I/O의 핵심

```java
// Netty의 Channel 개념
Channel channel = ...;
channel.read();  // 논블로킹: 즉시 반환
channel.write(); // 논블로킹: 즉시 반환
```

**특징**:

- 논블로킹 I/O 지원
- 이벤트 기반 콜백
- Selector에 등록 가능

#### Select (셀렉터)

**역할**: 여러 채널의 I/O 이벤트를 감시

```java
// Java NIO Selector 개념
Selector selector = Selector.open();
channel.register(selector, SelectionKey.OP_READ);

while (true) {
    // 준비된 이벤트가 있을 때까지 대기 (블로킹)
    selector.select();

    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    for (SelectionKey key : selectedKeys) {
        if (key.isReadable()) {
            // 읽기 가능한 채널 처리
        }
    }
}
```

**WebFlux에서의 활용**:

- Netty가 내부적으로 Selector 사용
- EventLoop가 Selector를 통해 여러 채널 관리
- 준비된 이벤트만 처리

### 3.5 동작 흐름

```
1. HTTP 요청 도착 (Netty Channel)
2. EventLoop 스레드에서 요청 파싱
3. Reactive Pipeline 생성 (아직 실행 안 됨)
   └─ Mono<Void> chain = filter1 → filter2 → controller → service
4. subscribe() 호출
5. Subscriber 체인 실행
   └─ 각 Subscriber가 다음 Subscriber를 감쌈
6. DB 쿼리 (논블로킹)
   └─ I/O 준비되면 EventLoop에 콜백
   └─ 스레드는 다른 작업 처리
7. 응답 작성
8. Channel Flush
```

### 3.5 Subscriber 체인 구조

```java
// 실제 WebFlux 내부 구조 (의사 코드)
NettySubscriber
  └─ ExceptionHandlingSubscriber
      └─ FilterSubscriber
          └─ ControllerSubscriber
              └─ ServiceSubscriber
                  └─ RepositorySubscriber
```

**시그널 전달**:

```
onSubscribe() → onNext() → onNext() → ... → onComplete()
```

### 3.6 CPU 및 메모리 관점

**CPU 사용**:

- **활성 스레드**: CPU 사용 중
- **대기 중**: Selector.select()에서 대기 (커널 레벨)
- **컨텍스트 스위칭**: 최소화 (적은 스레드)

**메모리 사용**:

```
메모리 = EventLoop 수 × 스레드 스택 크기 + Subscriber 체인
예: 8 EventLoop × 1MB = 8MB (최소)
+ Subscriber 객체들 (경량)
```

**장점**:

- **적은 스레드**: 메모리 절약
- **높은 동시성**: 수천 개의 연결 처리 가능
- **효율적인 CPU 사용**: 블로킹 최소화

### 3.7 예시 코드

```java
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/users/{id}")
    public Mono<User> getUser(@PathVariable Long id) {
        // 논블로킹: 즉시 Mono 반환
        // 실제 실행은 subscribe() 시점
        return userService.findById(id);
    }
}

@Service
public class UserService {
    @Autowired
    private ReactiveUserRepository repository;

    public Mono<User> findById(Long id) {
        // 논블로킹 DB 쿼리
        // 데이터 준비되면 onNext() 호출
        return repository.findById(id);
    }
}
```

**스레드 상태**:

```
EventLoop-1: RUNNABLE (여러 요청 처리) → RUNNABLE (계속 처리)
```

---

## 4. Java Virtual Thread 동작 원리

### 4.1 스레드 모델

**M:N 스레드 모델**:

- 많은 Virtual Thread (수천~수만 개)
- 적은 수의 Carrier Thread (OS 스레드)
- JVM이 스케줄링 관리

**아키텍처**:

```
[Virtual Thread들]
    ├── VirtualThread-1
    ├── VirtualThread-2
    ├── VirtualThread-3
    └── ... (수천 개)
        ↓
[Carrier Thread Pool]
    ├── Carrier-1 (OS 스레드)
    ├── Carrier-2 (OS 스레드)
    └── ... (CPU 코어 수만큼)
```

### 4.2 Continuation 개념

#### Continuation이란?

**정의**: 실행 중단 지점과 재개 지점을 저장하는 구조

**동작 원리**:

```java
// Virtual Thread 내부 동작 (의사 코드)
VirtualThread {
    Continuation continuation;

    void run() {
        continuation = new Continuation(scope, () -> {
            // 사용자 코드 실행
            userCode();
        });

        while (!continuation.isDone()) {
            continuation.run();
            if (blocking) {
                // 블로킹 발생 시
                // 1. 스택 상태 저장
                // 2. Carrier Thread 반환
                // 3. 대기
                park();
            }
        }
    }
}
```

#### Continuation의 구조

**스택 저장**:

```
Continuation {
    스택 프레임 스냅샷
    ├── 메서드 호출 체인
    ├── 지역 변수 값
    ├── 매개 변수 값
    └── 실행 위치 (PC - Program Counter)
}
```

**재개 과정**:

```
1. 블로킹 해제 이벤트 발생
2. Carrier Thread 할당
3. 스택 복원
4. 실행 재개
```

### 4.3 동작 흐름

```
1. Virtual Thread 생성 (경량)
2. 작업 시작
3. 블로킹 I/O 발생
   └─ Continuation에 상태 저장
   └─ Carrier Thread 반환
   └─ 다른 Virtual Thread 실행
4. I/O 완료
   └─ Continuation 재개
   └─ Carrier Thread 할당
5. 작업 완료
```

### 4.4 CPU 및 메모리 관점

**CPU 사용**:

- **Carrier Thread**: 실제 CPU 사용
- **Virtual Thread**: 경량 객체 (CPU 직접 사용 안 함)
- **컨텍스트 스위칭**: Continuation 전환 (경량)

**메모리 사용**:

```
메모리 = Carrier Thread 수 × 스택 크기 + Virtual Thread 수 × Continuation 크기
예: 8 Carrier × 1MB + 10,000 Virtual × 1KB = 8MB + 10MB = 18MB
```

**장점**:

- **경량**: Virtual Thread는 Continuation만 저장
- **확장성**: 수만 개의 Virtual Thread 가능
- **블로킹 허용**: 블로킹해도 문제없음

### 4.5 예시 코드

```java
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        // Virtual Thread에서 실행
        // 블로킹 발생 시 Continuation에 상태 저장
        return userService.findById(id);
    }
}

@Service
public class UserService {
    @Autowired
    private UserRepository repository;

    public User findById(Long id) {
        // 블로킹 I/O
        // Virtual Thread가 블로킹되면:
        // 1. Continuation에 상태 저장
        // 2. Carrier Thread 반환
        // 3. I/O 완료 시 재개
        return repository.findById(id).orElse(null);
    }
}
```

**스레드 상태**:

```
VirtualThread-1: RUNNABLE → PARKED (Continuation 저장) → RUNNABLE
Carrier-1: VirtualThread-1 실행 → VirtualThread-2 실행 → ...
```

---

## 5. Python Coroutine 동작 원리

### 5.1 스레드 모델

**이벤트 루프 기반 (asyncio)**:

- 단일 스레드 이벤트 루프 (기본)
- Generator 기반 코루틴
- async/await 문법
- GIL (Global Interpreter Lock)의 영향

**아키텍처**:

```
[Coroutine들]
    ├── Coroutine-1
    ├── Coroutine-2
    ├── Coroutine-3
    └── ... (수천 개)
        ↓
[Event Loop (단일 스레드)]
    └── Thread-1 (OS 스레드)
        └── asyncio 이벤트 루프
```

**GIL의 영향**:

- Python은 GIL로 인해 한 번에 하나의 스레드만 Python 바이트코드 실행
- I/O 작업은 GIL을 해제하므로 비동기 I/O에 적합
- CPU 집약적 작업은 멀티프로세싱 필요

### 5.2 Generator 기반 코루틴

#### Python Coroutine의 본질

**정의**: Generator를 기반으로 한 코루틴

```python
# Generator 기반 코루틴 (Python 3.5 이전)
def old_style_coroutine():
    yield from asyncio.sleep(1)
    return "done"

# async/await 문법 (Python 3.5+)
async def modern_coroutine():
    await asyncio.sleep(1)
    return "done"
```

**내부 동작 원리**:

```python
# async 함수는 Generator로 변환됨
async def fetch_user(user_id: int):
    # await는 yield from과 유사
    data = await database.fetch(user_id)
    return data

# 내부적으로는 다음과 같이 동작 (의사 코드)
def fetch_user(user_id: int):
    # Generator 객체 생성
    gen = _fetch_user_impl(user_id)

    # 코루틴 실행
    try:
        # 첫 번째 yield까지 실행
        result = next(gen)

        # await 중이면 Task로 등록
        if isinstance(result, asyncio.Future):
            result.add_done_callback(lambda f: gen.send(f.result()))
    except StopIteration as e:
        return e.value
```

#### Generator의 구조

**상태 저장**:

```python
# Generator 객체는 실행 상태를 저장
class Generator:
    gi_frame: Frame  # 실행 프레임
    gi_code: Code    # 바이트코드
    gi_running: bool # 실행 중 여부

    # 프레임에 저장되는 정보
    Frame {
        f_locals: dict      # 지역 변수
        f_globals: dict     # 전역 변수
        f_code: Code        # 바이트코드
        f_lasti: int        # 마지막 명령어 인덱스
        f_back: Frame       # 이전 프레임 (호출 스택)
    }
```

**코루틴의 구조**:

```python
# 코루틴 객체
class Coroutine:
    cr_code: Code           # 바이트코드
    cr_frame: Frame         # 실행 프레임
    cr_await: object        # 대기 중인 Future/Task
    cr_running: bool        # 실행 중 여부

    # 상태 머신 정보
    Frame {
        f_locals: {
            'user_id': 123,
            'data': None,
            # ... 지역 변수들
        }
        f_lasti: 42  # await 위치
    }
```

### 5.3 이벤트 루프 동작 원리

#### asyncio Event Loop

**핵심 구조**:

```python
# asyncio 이벤트 루프 내부 (의사 코드)
class EventLoop:
    def __init__(self):
        self._ready = deque()      # 실행 준비된 Task 큐
        self._scheduled = []       # 지연 실행 Task 큐
        self._selector = Selector() # I/O 이벤트 선택자

    def run_forever(self):
        while True:
            # 1. 준비된 Task 실행
            self._run_ready_tasks()

            # 2. 지연 실행 Task 처리
            self._run_scheduled_tasks()

            # 3. I/O 이벤트 대기
            timeout = self._get_next_timeout()
            events = self._selector.select(timeout)

            # 4. I/O 이벤트 처리
            for key, mask in events:
                callback = key.data
                callback()

            # 5. 준비된 Task 다시 실행
            self._run_ready_tasks()
```

#### Task와 Future

**Task 생성 및 실행**:

```python
# 코루틴을 Task로 변환
async def fetch_user(user_id: int):
    await asyncio.sleep(0.1)  # 비동기 I/O 시뮬레이션
    return {"id": user_id, "name": "Alice"}

# Task 생성
task = asyncio.create_task(fetch_user(123))

# 내부 동작 (의사 코드)
class Task:
    def __init__(self, coro):
        self._coro = coro
        self._state = 'PENDING'
        self._result = None

    def _step(self):
        try:
            # 코루틴 실행 (Generator의 next()와 유사)
            result = self._coro.send(None)

            if isinstance(result, Future):
                # Future 대기 중
                result.add_done_callback(self._wakeup)
                self._state = 'PENDING'
            else:
                # 완료
                self._result = result
                self._state = 'DONE'
        except StopIteration as e:
            # 코루틴 완료
            self._result = e.value
            self._state = 'DONE'
        except Exception as e:
            # 예외 발생
            self._exception = e
            self._state = 'DONE'

    def _wakeup(self, future):
        # Future 완료 시 코루틴 재개
        self._step()
```

### 5.4 동작 흐름

```
1. 코루틴 생성 (async 함수 호출)
   └─ Generator 객체 생성 (아직 실행 안 됨)

2. Task 생성 (create_task 또는 await)
   └─ 이벤트 루프에 Task 등록

3. 이벤트 루프가 Task 실행
   └─ 코루틴의 첫 번째 await까지 실행

4. await에서 Future 반환
   └─ Task를 대기 상태로 전환
   └─ 이벤트 루프는 다른 Task 실행

5. I/O 완료
   └─ Future.set_result() 호출
   └─ Task를 ready 큐에 추가

6. 이벤트 루프가 Task 재개
   └─ 코루틴의 다음 부분 실행
```

### 5.5 CPU 및 메모리 관점

**CPU 사용**:

- **이벤트 루프 스레드**: 실제 CPU 사용 (단일 스레드)
- **Coroutine**: Generator 객체 (경량)
- **컨텍스트 스위칭**: Generator 전환 (매우 빠름)

**메모리 사용**:

```
메모리 = 이벤트 루프 스레드 스택 + Coroutine 수 × Generator 크기
예: 1 스레드 × 1MB + 10,000 Coroutine × 1KB = 1MB + 10MB = 11MB
```

**GIL의 영향**:

- **I/O 작업**: GIL 해제 → 비동기 I/O 효율적
- **CPU 작업**: GIL 유지 → 멀티스레딩 비효율적
- **해결책**: CPU 집약적 작업은 `ProcessPoolExecutor` 사용

**장점**:

- **경량**: Generator는 프레임만 저장
- **단순함**: 단일 스레드 이벤트 루프
- **I/O 효율**: GIL 해제로 I/O 병렬 처리

**단점**:

- **CPU 제한**: GIL로 인해 CPU 집약적 작업에 부적합
- **단일 스레드**: 기본적으로 하나의 이벤트 루프만 실행

### 5.6 예시 코드

```python
# FastAPI 예시
from fastapi import FastAPI
import asyncio

app = FastAPI()

@app.get("/users/{user_id}")
async def get_user(user_id: int):
    # async 함수: 코루틴 생성
    # await에서 이벤트 루프에 제어권 반환
    user = await user_service.find_by_id(user_id)
    return user

class UserService:
    async def find_by_id(self, user_id: int):
        # 비동기 DB 쿼리
        # await는 Generator의 yield와 유사
        # 이벤트 루프에 제어권 반환
        result = await database.fetch_one(
            "SELECT * FROM users WHERE id = ?",
            user_id
        )
        return result
```

**내부 동작**:

```python
# 실제 실행 흐름
async def get_user(user_id: int):
    # 1. 코루틴 객체 생성 (아직 실행 안 됨)
    coro = user_service.find_by_id(user_id)

    # 2. Task 생성 및 이벤트 루프에 등록
    task = asyncio.create_task(coro)

    # 3. await: 이벤트 루프에 제어권 반환
    user = await task
    # → Generator의 yield와 유사
    # → 이벤트 루프가 다른 Task 실행 가능

    return user
```

**스레드 상태**:

```
EventLoop-Thread: RUNNABLE (Task-1 실행) → RUNNABLE (Task-2 실행) → ...
Coroutine-1: PENDING → RUNNING → SUSPENDED (await) → RUNNING → DONE
```

### 5.7 asyncio와 WebFlux 비교

**유사점**:

- 이벤트 루프 기반
- 단일 스레드에서 여러 작업 처리
- 논블로킹 I/O

**차이점**:

| 항목         | Python asyncio           | WebFlux                        |
| ------------ | ------------------------ | ------------------------------ |
| **기반**     | Generator                | Reactor (Publisher/Subscriber) |
| **스레드**   | 단일 스레드 (기본)       | CPU 코어 × 2                   |
| **GIL**      | 있음 (제약)              | 없음                           |
| **CPU 작업** | ProcessPoolExecutor 필요 | Worker 스레드 사용             |
| **문법**     | async/await              | Mono/Flux 체이닝               |

---

## 6. 상세 비교 분석

### 6.1 스레드 모델 비교

| 항목            | Spring MVC      | WebFlux      | Virtual Thread | Python Coroutine          |
| --------------- | --------------- | ------------ | -------------- | ------------------------- |
| **스레드 모델** | 1:1 (OS 스레드) | 이벤트 루프  | M:N (Carrier)  | 이벤트 루프 (단일 스레드) |
| **스레드 수**   | 수백 개         | CPU 코어 × 2 | 수만 개 가능   | 1개 (기본)                |
| **블로킹**      | 허용            | 비허용       | 허용           | 허용 (I/O는 논블로킹)     |
| **I/O 모델**    | 블로킹 I/O      | 논블로킹 I/O | 블로킹 I/O     | 논블로킹 I/O              |

### 6.2 메모리 사용 비교

#### Spring MVC

```
메모리 = 스레드 수 × 스택 크기
예: 200 스레드 × 1MB = 200MB
```

**특징**:

- 각 스레드마다 1MB 스택
- 많은 스레드 = 많은 메모리

#### WebFlux

```
메모리 = EventLoop 수 × 스택 크기 + Subscriber 객체들
예: 8 EventLoop × 1MB + 경량 객체들 = 약 10MB
```

**특징**:

- 적은 스레드로 많은 연결 처리
- Subscriber는 경량 객체

#### Virtual Thread

```
메모리 = Carrier Thread 수 × 스택 크기 + Virtual Thread 수 × Continuation 크기
예: 8 Carrier × 1MB + 10,000 Virtual × 1KB = 18MB
```

**특징**:

- Continuation은 스택 일부만 저장
- 경량 Virtual Thread

#### Python Coroutine

```
메모리 = 이벤트 루프 스레드 스택 + Coroutine 수 × Generator 크기
예: 1 스레드 × 1MB + 10,000 Coroutine × 1KB = 11MB
```

**특징**:

- 단일 스레드 이벤트 루프
- Generator 프레임으로 상태 관리
- GIL의 영향으로 CPU 집약적 작업은 제한적

### 6.3 CPU 사용 비교

#### Spring MVC

**CPU 사용 패턴**:

```
Thread-1: [실행] [블로킹 대기] [실행] [종료]
Thread-2: [실행] [블로킹 대기] [실행] [종료]
...
```

**문제점**:

- 블로킹 중 CPU 미사용
- 많은 스레드 = 많은 컨텍스트 스위칭

#### WebFlux

**CPU 사용 패턴**:

```
EventLoop-1: [실행] [실행] [실행] [실행] ... (계속 실행)
EventLoop-2: [실행] [실행] [실행] [실행] ... (계속 실행)
```

**장점**:

- 블로킹 최소화
- 적은 컨텍스트 스위칭
- 높은 CPU 활용률

#### Virtual Thread

**CPU 사용 패턴**:

```
Carrier-1: [VT-1 실행] [VT-2 실행] [VT-3 실행] ...
Carrier-2: [VT-4 실행] [VT-5 실행] [VT-6 실행] ...
```

**특징**:

- Carrier Thread만 실제 CPU 사용
- Virtual Thread는 경량 전환

#### Python Coroutine

**CPU 사용 패턴**:

```
EventLoop-Thread: [Task-1] [Task-2] [Task-3] ... (단일 스레드)
```

**특징**:

- 단일 이벤트 루프 스레드만 실제 CPU 사용
- Generator 전환으로 경량 처리
- GIL로 인해 CPU 집약적 작업은 제한적

### 6.4 Continuation 비교

#### Virtual Thread Continuation

**구조**:

```java
Continuation {
    스택 프레임 스냅샷
    ├── 메서드 호출 체인
    ├── 지역 변수
    └── 실행 위치
}
```

**특징**:

- JVM이 자동 관리
- 스택 일부만 저장 (경량)
- 블로킹 시 자동 저장/복원

#### Python Coroutine Generator

**구조**:

```python
Generator {
    gi_frame: Frame {
        f_locals: {
            'user_id': 123,
            'data': None,
            # ... 지역 변수들
        }
        f_code: Code        # 바이트코드
        f_lasti: int        # 마지막 명령어 인덱스 (await 위치)
        f_back: Frame       # 이전 프레임 (호출 스택)
    }
    gi_running: bool        # 실행 중 여부
    cr_await: Future        # 대기 중인 Future/Task
}
```

**특징**:

- Generator 기반으로 구현
- 프레임에 실행 상태 저장
- await에서 Generator의 yield와 유사하게 동작
- 이벤트 루프가 Task 재개

### 6.5 Channel과 Select 비교

#### WebFlux의 Channel

**Netty Channel**:

```java
// Netty Channel은 Java NIO Channel 기반
Channel channel = ...;
channel.read();  // 논블로킹
channel.write(); // 논블로킹
```

**특징**:

- 논블로킹 I/O
- 이벤트 기반
- Selector에 등록

#### WebFlux의 Select

**Netty EventLoop 내부**:

```java
// Netty가 내부적으로 사용 (의사 코드)
Selector selector = Selector.open();
channel.register(selector, SelectionKey.OP_READ);

while (true) {
    selector.select(); // 준비된 이벤트 대기
    Set<SelectionKey> keys = selector.selectedKeys();
    for (SelectionKey key : keys) {
        if (key.isReadable()) {
            // 읽기 처리
        }
    }
}
```

**특징**:

- EventLoop가 Selector 사용
- 여러 채널을 하나의 스레드에서 관리
- 준비된 이벤트만 처리

#### Python Coroutine의 Queue

**asyncio.Queue**:

```python
import asyncio

queue = asyncio.Queue()

# 송신
async def producer():
    await queue.put(1)

# 수신
async def consumer():
    value = await queue.get()
```

**특징**:

- 코루틴 간 통신
- 백프레셔 지원 (maxsize 파라미터)
- 이벤트 루프 기반

#### Python Coroutine의 Select (asyncio.wait)

**asyncio.wait / asyncio.gather**:

```python
import asyncio

# 여러 코루틴 중 완료된 것 선택
async def main():
    # 여러 Task 중 완료된 것 선택
    done, pending = await asyncio.wait([
        task1,
        task2,
        task3
    ], return_when=asyncio.FIRST_COMPLETED)

    # 또는 모든 Task 완료 대기
    results = await asyncio.gather(
        task1,
        task2,
        task3
    )

    # 타임아웃 지원
    try:
        result = await asyncio.wait_for(task, timeout=1.0)
    except asyncio.TimeoutError:
        pass
```

**특징**:

- 여러 Task 중 완료된 것 선택
- 타임아웃 지원
- 논블로킹
- 이벤트 루프 기반

---

## 7. 성능 비교

### 7.1 동시 처리 능력

| 항목                 | 동시 연결 수          | 메모리 사용   | CPU 효율        |
| -------------------- | --------------------- | ------------- | --------------- |
| **Spring MVC**       | ~200 (스레드 풀 크기) | 높음 (200MB+) | 낮음 (블로킹)   |
| **WebFlux**          | 수천~수만 개          | 낮음 (~10MB)  | 높음 (논블로킹) |
| **Virtual Thread**   | 수만 개               | 중간 (~20MB)  | 중간            |
| **Python Coroutine** | 수천~수만 개          | 낮음 (~11MB)  | 중간 (GIL 제약) |

### 7.2 응답 시간

**동시 요청 1000개 처리 시**:

- **Spring MVC**: 스레드 풀 고갈 → 대기 → 느림
- **WebFlux**: 논블로킹 → 빠름
- **Virtual Thread**: 경량 전환 → 빠름
- **Python Coroutine**: Generator 전환 → 빠름 (I/O 작업)

### 7.3 리소스 사용

**CPU 사용률**:

- **MVC**: 낮음 (블로킹 중 미사용)
- **WebFlux**: 높음 (계속 실행)
- **Virtual Thread**: 중간 (Carrier만 사용)
- **Python Coroutine**: 중간 (단일 이벤트 루프, GIL 제약)

**메모리 사용**:

- **MVC**: 높음 (스레드 스택)
- **WebFlux**: 낮음 (적은 스레드)
- **Virtual Thread**: 중간 (Continuation)
- **Python Coroutine**: 낮음 (Generator 프레임)

---

## 8. 사용 시나리오

### 8.1 Spring MVC

**적합한 경우**:

- ✅ 전통적인 블로킹 I/O
- ✅ 간단한 CRUD 애플리케이션
- ✅ 낮은 동시성 요구사항
- ✅ 기존 코드베이스 유지

**부적합한 경우**:

- ❌ 높은 동시성 요구
- ❌ 스트리밍 데이터
- ❌ 논블로킹 I/O 필요

### 8.2 WebFlux

**적합한 경우**:

- ✅ 높은 동시성 요구
- ✅ 논블로킹 I/O
- ✅ 스트리밍 데이터
- ✅ 마이크로서비스 간 비동기 통신

**부적합한 경우**:

- ❌ 블로킹 I/O가 많은 경우
- ❌ 전통적인 서블릿 스택 필요
- ❌ 학습 곡선 고려

### 8.3 Virtual Thread

**적합한 경우**:

- ✅ 기존 블로킹 코드 활용
- ✅ 높은 동시성 요구
- ✅ Java 21+ 사용 가능
- ✅ 블로킹 I/O 허용

**부적합한 경우**:

- ❌ Java 21 미만
- ❌ CPU 집약적 작업
- ❌ 논블로킹 I/O 필수

### 8.4 Python Coroutine

**적합한 경우**:

- ✅ Python 사용
- ✅ I/O 집약적 애플리케이션
- ✅ 높은 동시성 요구 (I/O 작업)
- ✅ FastAPI, aiohttp 등 비동기 프레임워크 사용

**부적합한 경우**:

- ❌ CPU 집약적 작업 (GIL 제약)
- ❌ Java/Kotlin 전용 프로젝트
- ❌ 멀티스레딩 필요 (ProcessPoolExecutor 필요)

---

## 9. 핵심 개념 정리

### 9.1 Continuation

**공통점**:

- 실행 상태 저장
- 재개 가능
- 경량

**차이점**:

- **Virtual Thread**: JVM이 자동 관리, 스택 스냅샷
- **Python Coroutine**: Generator 기반, 프레임에 상태 저장

### 9.2 Channel과 Select

**WebFlux**:

- Netty Channel (Java NIO 기반)
- Selector로 이벤트 감시
- 논블로킹 I/O

**Python Coroutine**:

- asyncio.Queue (코루틴 간 통신)
- asyncio.wait / asyncio.gather
- 백프레셔 지원 (maxsize)

### 9.3 스레드 전환 비용

| 항목                 | 전환 비용 | 이유                       |
| -------------------- | --------- | -------------------------- |
| **OS 스레드**        | 높음      | 컨텍스트 스위칭, 캐시 미스 |
| **Virtual Thread**   | 낮음      | Continuation 전환만        |
| **Python Coroutine** | 낮음      | Generator 전환만           |
| **WebFlux**          | 최소      | 같은 EventLoop 유지        |

---

## 10. 실무 적용 가이드

### 10.1 선택 기준

**동시성 요구사항**:

- 낮음 (< 100): Spring MVC
- 중간 (100~1000): Virtual Thread 또는 Python Coroutine
- 높음 (> 1000): WebFlux 또는 Python Coroutine (I/O 집약적)

**I/O 특성**:

- 블로킹 I/O: Virtual Thread
- 논블로킹 I/O: WebFlux 또는 Python Coroutine

**기술 스택**:

- Java 전용: Spring MVC 또는 Virtual Thread
- Python 사용: Python Coroutine (asyncio)
- 리액티브 스택: WebFlux

### 10.2 마이그레이션 전략

**MVC → WebFlux**:

- 점진적 마이그레이션
- 블로킹 코드는 boundedElastic 사용
- 테스트 강화

**MVC → Virtual Thread**:

- 코드 변경 최소화
- Java 21+ 필요
- 점진적 적용

**Java → Python Coroutine**:

- 언어 변경 필요
- asyncio 기반으로 재작성
- CPU 집약적 작업은 ProcessPoolExecutor 사용

---

## 📊 종합 비교표

| 항목               | Spring MVC    | WebFlux      | Virtual Thread | Python Coroutine          |
| ------------------ | ------------- | ------------ | -------------- | ------------------------- |
| **스레드 모델**    | 1:1 OS 스레드 | 이벤트 루프  | M:N Carrier    | 이벤트 루프 (단일 스레드) |
| **동시성**         | 낮음          | 매우 높음    | 높음           | 높음 (I/O 집약적)         |
| **메모리**         | 높음          | 낮음         | 중간           | 낮음                      |
| **CPU 효율**       | 낮음          | 높음         | 중간           | 중간 (GIL 제약)           |
| **블로킹**         | 허용          | 비허용       | 허용           | 허용 (I/O는 논블로킹)     |
| **학습 곡선**      | 낮음          | 높음         | 낮음           | 중간                      |
| **코드 복잡도**    | 낮음          | 높음         | 낮음           | 중간                      |
| **Continuation**   | 없음          | 없음         | 있음 (자동)    | 있음 (Generator)          |
| **Channel/Select** | 없음          | 있음 (Netty) | 없음           | 있음 (asyncio)            |

---

## 📝 결론

각 기술은 서로 다른 접근 방식으로 동시성 문제를 해결합니다:

1. **Spring MVC**: 전통적인 스레드 풀 모델
2. **WebFlux**: 이벤트 루프 + 논블로킹 I/O
3. **Virtual Thread**: 경량 스레드 + Continuation
4. **Python Coroutine**: 이벤트 루프 + Generator 기반 코루틴

프로젝트의 요구사항, 기술 스택, 팀의 역량을 고려하여 적절한 기술을 선택하는 것이 중요합니다.
