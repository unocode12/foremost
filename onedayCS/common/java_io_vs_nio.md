# Java NIO의 동작원리 및 IO 모델

## 📌 개요

기존 Java I/O의 성능 문제를 해결하기 위해 등장한 NIO(New I/O)는 운영체제 수준의 네이티브 I/O 기술을 활용하여 성능을 향상시켰습니다.

### 기존 Java I/O의 문제점

- **이중 복사 문제**: 커널 버퍼 → JVM 버퍼로 데이터 복사가 두 번 발생
- **블로킹 I/O**: 스레드가 I/O 작업 완료까지 대기
- **스레드 오버헤드**: 클라이언트마다 스레드 생성 필요

---

## STEP 1. 바이트 버퍼(ByteBuffer)

### 핵심 개념

**ByteBuffer만 다이렉트 버퍼를 만들 수 있는 이유**:

- 운영체제의 기본 데이터 단위가 바이트
- 시스템 메모리도 순차적인 바이트들의 집합

### 다이렉트 버퍼 vs 일반 버퍼

```java
// 일반 버퍼 (JVM 힙 메모리)
ByteBuffer buffer = ByteBuffer.allocate(1024);

// 다이렉트 버퍼 (시스템 메모리 직접 사용)
ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
```

### 데이터 플로우 비교

**기존 I/O (Blocking I/O)**:

```
JVM → 시스템 콜 → 커널 → 디스크 컨트롤러
→ 커널 버퍼 복사 → JVM 버퍼 복사 (이중 복사!)
```

**NIO (Direct Buffer 사용 시)**:

```
JVM → 시스템 콜 → JNI → 디스크 컨트롤러
→ DMA → 직접 복사 (단일 복사!)
```

#### 🔍 용어 설명

**JNI (Java Native Interface)**:

- Java와 네이티브 코드(C/C++) 간의 인터페이스
- Java에서 C/C++ 함수를 호출하거나 반대로 호출할 수 있게 해주는 브릿지
- NIO에서 시스템 메모리 할당 시 JNI를 통해 네이티브 코드 호출
- **역할**: Java 코드와 운영체제 레벨 코드를 연결

**디스크 컨트롤러 (Disk Controller)**:

- 하드디스크나 SSD 같은 저장 장치를 제어하는 하드웨어
- CPU와 저장 장치 사이의 중간 역할
- 데이터 읽기/쓰기 명령을 받아 저장 장치에 전달
- **역할**: 저장 장치와의 물리적 통신 담당

**DMA (Direct Memory Access)**:

- CPU를 거치지 않고 메모리와 I/O 장치 간 직접 데이터 전송
- CPU 개입 없이 디스크 컨트롤러가 직접 메모리에 데이터를 읽고 쓸 수 있음
- CPU는 다른 작업을 수행할 수 있어 전체 시스템 성능 향상
- **역할**: CPU 부하 없이 고속 데이터 전송

#### 📊 데이터 플로우 상세 설명

**NIO 다이렉트 버퍼의 전체 과정**:

1. **JVM**: Java 애플리케이션에서 파일 읽기 요청
2. **시스템 콜**: 운영체제 커널에 파일 읽기 요청
3. **JNI**: Java에서 네이티브 코드로 전환 (시스템 메모리 할당)
4. **디스크 컨트롤러**: 저장 장치에서 데이터 읽기
5. **DMA**: 디스크 컨트롤러가 CPU 없이 직접 시스템 메모리로 데이터 복사
6. **직접 복사**: 커널 버퍼를 거치지 않고 바로 시스템 메모리로 복사

**왜 빠른가?**

- CPU 개입 최소화 (DMA 사용)
- 이중 복사 제거 (커널 버퍼 → JVM 버퍼 과정 생략)
- 시스템 메모리 직접 사용

### 다이렉트 버퍼의 동작 원리

1. **초기 할당**: JNI를 통해 시스템 메모리 할당
2. **래핑**: 할당된 메모리를 Java 객체로 래핑
3. **직접 제어**: 래핑된 객체로 시스템 메모리 직접 제어

**장점**:

- 이중 복사 제거로 성능 향상
- 시스템 메모리 직접 사용

**단점**:

- 할당/해제 비용이 높음
- GC 관리 밖에 있어 수동 관리 필요

---

## STEP 2. 채널(Channel)

채널은 데이터를 읽고 쓰는 통로입니다. 스트림과 달리 양방향 통신이 가능합니다.

### 2.1 ScatteringByteChannel, GatheringByteChannel

**Scattering (분산 읽기)**:

- 하나의 채널에서 여러 버퍼로 데이터를 분산하여 읽기

**Gathering (집합 쓰기)**:

- 여러 버퍼의 데이터를 하나의 채널로 집합하여 쓰기

**사용 예시**:

```java
// 여러 버퍼에 분산하여 읽기
ByteBuffer header = ByteBuffer.allocate(128);
ByteBuffer body = ByteBuffer.allocate(1024);
ByteBuffer[] buffers = {header, body};
channel.read(buffers);

// 여러 버퍼의 데이터를 한 번에 쓰기
channel.write(buffers);
```

### 2.2 파일 채널(FileChannel)

#### 파일 채널의 특징

- **메모리 맵 파일**: `map()` 메서드로 파일을 메모리에 매핑
- **파일 락**: `lock()` 메서드로 파일 잠금 지원
- **비동기 I/O**: `transferTo()`, `transferFrom()` 메서드로 효율적인 파일 복사

#### 파일 채널의 속성

- **위치(Position)**: 읽기/쓰기 위치 추적
- **크기(Size)**: 파일 크기 관리
- **트렁케이션(Truncation)**: 파일 크기 조절

**예시**:

```java
FileChannel channel = FileChannel.open(path,
    StandardOpenOption.READ,
    StandardOpenOption.WRITE);

// 메모리 맵 파일
MappedByteBuffer mappedBuffer = channel.map(
    FileChannel.MapMode.READ_WRITE, 0, channel.size());

// 파일 락
FileLock lock = channel.lock();
```

### 2.3 소켓 채널(SocketChannel)

네트워크 통신을 위한 채널입니다.

**특징**:

- **논블로킹 모드**: `configureBlocking(false)` 설정 가능
- **셀렉터와 연동**: Selector에 등록하여 이벤트 기반 처리

---

## STEP 3. 셀렉터(Selector)

### 3.1 기존 네트워크 프로그래밍 모델의 단점

**블로킹 I/O 모델**:

- 클라이언트마다 스레드 생성 필요
- 스레드가 I/O 완료까지 블로킹
- 많은 클라이언트 = 많은 스레드 = 높은 메모리 사용량

**문제점**:

- 스레드 생성/관리 오버헤드
- 컨텍스트 스위칭 비용
- 메모리 낭비

### 3.2 비블로킹 모델과 셀렉터 동작원리

셀렉터는 **I/O 멀티플렉싱**을 구현하는 핵심 컴포넌트입니다.

#### 3.2.1 SelectableChannel (셀렉터블 채널)

셀렉터에 등록 가능한 채널입니다.

**주요 채널**:

- `SocketChannel`: TCP 소켓 채널
- `ServerSocketChannel`: 서버 소켓 채널
- `DatagramChannel`: UDP 채널

**설정**:

```java
// 논블로킹 모드 설정
channel.configureBlocking(false);

// 셀렉터에 등록
SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
```

#### 3.2.2 SelectionKey (셀렉션 키)

채널과 셀렉터의 연결을 나타내는 객체입니다.

**이벤트 타입**:

- `OP_READ`: 읽기 준비됨
- `OP_WRITE`: 쓰기 준비됨
- `OP_CONNECT`: 연결 완료
- `OP_ACCEPT`: 연결 수락 준비

**사용 예시**:

```java
if (key.isReadable()) {
    // 읽기 가능
}
if (key.isWritable()) {
    // 쓰기 가능
}
```

#### 3.2.3 Selector (셀렉터)

**역할**: 여러 채널의 I/O 이벤트를 감시하고 준비된 채널만 선택

**동작 원리**:

1. 채널들을 셀렉터에 등록
2. `select()` 메서드로 준비된 이벤트 확인
3. `selectedKeys()`로 준비된 채널들 반환
4. 각 채널의 이벤트 처리

**예시**:

```java
Selector selector = Selector.open();
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    // 준비된 이벤트가 있을 때까지 블로킹
    selector.select();

    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    Iterator<SelectionKey> iterator = selectedKeys.iterator();

    while (iterator.hasNext()) {
        SelectionKey key = iterator.next();

        if (key.isAcceptable()) {
            // 연결 수락
        } else if (key.isReadable()) {
            // 읽기 처리
        }

        iterator.remove();
    }
}
```

**장점**:

- 단일 스레드로 여러 채널 관리 가능
- 스레드 오버헤드 감소
- 높은 동시성 처리 가능

---

## STEP 4. I/O 모델 및 간단한 채팅 어플리케이션 예제

### 4.1 I/O 모델

#### 1. 블로킹 I/O (Blocking I/O)

**특징**:

- I/O 작업 완료까지 스레드가 대기
- 클라이언트마다 스레드 필요

**단점**:

- 스레드 오버헤드
- 많은 클라이언트 처리 어려움

#### 2. 논블로킹 I/O (Non-blocking I/O)

**특징**:

- I/O 작업이 즉시 반환 (준비되지 않으면)
- 폴링(Polling) 방식으로 준비 상태 확인

**단점**:

- CPU 낭비 (폴링 오버헤드)

#### 3. I/O 멀티플렉싱 (I/O Multiplexing)

**특징**:

- 셀렉터가 여러 채널을 감시
- 준비된 채널만 선택하여 처리
- 단일 스레드로 여러 클라이언트 처리

**장점**:

- 효율적인 이벤트 처리
- 스레드 오버헤드 최소화

### 4.2 블로킹 vs 논블로킹 비교

#### 블로킹 I/O 예제 (기존 방식)

```java
// 클라이언트마다 스레드 생성
public class InputThread extends Thread {
    private Socket socket;
    private BufferedReader reader;

    public void run() {
        try {
            String line;
            // 블로킹: 데이터가 올 때까지 대기
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (Exception e) {
            // 처리
        }
    }
}
```

**문제점**:

- 각 클라이언트마다 스레드 필요
- 스레드가 블로킹되어 대기

#### 논블로킹 I/O 예제 (NIO 방식)

```java
// 셀렉터로 여러 클라이언트 처리
private void startReader() {
    while (true) {
        // 준비된 이벤트만 선택 (블로킹 최소화)
        selector.select();
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();

            if (key.isReadable()) {
                // 논블로킹 읽기
                read(key);
            }
            iterator.remove();
        }
    }
}
```

**장점**:

- 단일 스레드로 여러 클라이언트 처리
- 블로킹 최소화
- 높은 동시성

---

## 📊 핵심 정리

### NIO의 주요 구성 요소

| 구성 요소      | 역할        | 특징                                    |
| -------------- | ----------- | --------------------------------------- |
| **ByteBuffer** | 데이터 저장 | 다이렉트 버퍼로 시스템 메모리 직접 사용 |
| **Channel**    | 데이터 통로 | 양방향 통신, 스트림보다 효율적          |
| **Selector**   | 이벤트 감시 | I/O 멀티플렉싱, 논블로킹 처리           |

### 성능 향상 포인트

1. **다이렉트 버퍼**: 이중 복사 제거
2. **채널**: 양방향 통신, 메모리 맵 파일
3. **셀렉터**: 단일 스레드로 다중 클라이언트 처리

### 사용 시나리오

- **대용량 파일 처리**: FileChannel + 메모리 맵 파일
- **고성능 네트워크 서버**: SocketChannel + Selector
- **동시성 높은 애플리케이션**: 논블로킹 I/O + 멀티플렉싱

---

## 💡 실무 적용

### 언제 NIO를 사용해야 할까?

**NIO 사용 권장**:

- 고성능 네트워크 서버 (채팅, 게임 서버 등)
- 대용량 파일 처리
- 높은 동시성 요구사항

**기존 I/O 사용 권장**:

- 간단한 파일 읽기/쓰기
- 낮은 동시성 요구사항
- 개발 편의성 우선

### 현대적 대안

- **Spring WebFlux**: 리액티브 스택, NIO 기반
- **Netty**: 고성능 네트워크 프레임워크
- **Project Reactor**: 리액티브 프로그래밍
