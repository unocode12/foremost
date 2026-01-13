## 📌 개요

이 문서는 Linux 커널 레벨에서 소켓과 TCP가 어떻게 동작하는지, 그리고 실제 서비스에서 겪을 수 있는 TCP 관련 문제들을 다룹니다.

---

## STEP 1. 소켓이란?

### 소켓의 정의

> **표준 유닉스 파일 디스크립터(File Descriptors)를 통해서 다른 프로그램과 정보를 교환하는 방법**

### 왜 파일 디스크립터인가?

**"Everything is a file"** 개념:

- UNIX 진영의 철학
- 다양한 리소스의 입/출력을 파일과 같은 바이트 스트림으로 처리
- 소켓도 파일처럼 취급됨
- 네트워크 데이터를 바이트로 직렬화/역직렬화하는 이유

### 소켓 생성 과정

```c
// 시스템 콜: socket()
SYSCALL_DEFINE3(socket, int, family, int, type, int, protocol)
    → __sys_socket()
        → __sys_socket_create()  // 소켓 생성
        → sock_map_fd()          // 파일 디스크립터에 매핑
```

**흐름**:

1. 유저 공간에서 `socket()` 함수 호출
2. 커널에서 시스템 콜 인터페이스를 통해 소켓 생성
3. 생성된 소켓을 파일 디스크립터에 매핑
4. 파일 디스크립터 반환

### 1.1 소켓의 종류

#### 1.1.1 스트림 소켓 (Stream Socket)

**특징**:

- **TCP** 사용
- 연결 지향형 (Connection-oriented)
- 신뢰성 보장 (순서 보장, 재전송)
- 양방향 통신

**사용 예시**:

- 웹 브라우저 (HTTP/HTTPS)
- 이메일 (SMTP)
- 파일 전송 (FTP)

#### 1.1.2 데이터그램 소켓 (Datagram Socket)

**특징**:

- **UDP** 사용
- 비연결형 (Connectionless)
- 빠른 전송 (신뢰성 보장 안 함)
- 일방향 통신 가능

**사용 예시**:

- DNS 조회
- 비디오 스트리밍
- 게임 서버

---

## STEP 2. 커널 코드로 알아보는 TCP와 소켓

### 2.1 TCP Handshake 과정

#### 2.1.1 3-Way Handshake (연결 수립)

**과정**:

1. **클라이언트 → 서버: SYN**

   - 클라이언트가 연결 요청
   - 상태: `CLOSED` → `SYN_SENT`

2. **서버 → 클라이언트: SYN-ACK**

   - 서버가 연결 수락
   - 상태: `LISTEN` → `SYN_RCVD`

3. **클라이언트 → 서버: ACK**
   - 클라이언트가 확인 응답
   - 상태: `SYN_SENT` → `ESTABLISHED`
   - 서버도 `SYN_RCVD` → `ESTABLISHED`

**커널 코드 흐름**:

```
tcp_v4_connect()           // 클라이언트 측
    → tcp_connect()        // SYN 전송
    → tcp_transmit_skb()   // 패킷 전송

tcp_v4_do_rcv()            // 서버 측
    → tcp_rcv_state_process()
    → tcp_v4_conn_request() // SYN 수신 처리
    → tcp_v4_send_synack()  // SYN-ACK 전송
```

**핵심**:

- 각 단계마다 커널이 소켓 상태를 변경
- 타임아웃 설정 (`TCP_SYN_RETRIES`)
- 재전송 메커니즘

#### 2.1.2 4-Way Handshake (연결 해제)

**과정**:

1. **클라이언트 → 서버: FIN**

   - 클라이언트가 연결 종료 요청
   - 상태: `ESTABLISHED` → `FIN_WAIT_1`

2. **서버 → 클라이언트: ACK**

   - 서버가 종료 요청 확인
   - 상태: `ESTABLISHED` → `CLOSE_WAIT`

3. **서버 → 클라이언트: FIN**

   - 서버가 종료 요청
   - 상태: `CLOSE_WAIT` → `LAST_ACK`

4. **클라이언트 → 서버: ACK**
   - 클라이언트가 확인 응답
   - 상태: `FIN_WAIT_1` → `FIN_WAIT_2` → `TIME_WAIT` → `CLOSED`

**커널 코드 흐름**:

```
tcp_close()                // 연결 종료 시작
    → tcp_send_fin()       // FIN 전송
    → tcp_fin()            // FIN 수신 처리
```

### 2.2 TCP 메시지 송/수신 과정

#### 2.2.1 TCP 송신 과정

**흐름**:

1. **애플리케이션**: `send()` 또는 `write()` 호출
2. **소켓 버퍼**: 데이터를 소켓 송신 버퍼에 저장
3. **TCP 계층**: 데이터를 세그먼트로 분할
4. **IP 계층**: IP 패킷으로 캡슐화
5. **네트워크 인터페이스**: 물리적 전송

**커널 코드**:

```
tcp_sendmsg()              // 애플리케이션 데이터 수신
    → tcp_push()           // 데이터 전송
    → tcp_transmit_skb()   // 패킷 전송
```

**특징**:

- **흐름 제어**: 수신 측 버퍼 크기 고려
- **혼잡 제어**: 네트워크 상태에 따른 전송 속도 조절
- **재전송**: ACK 미수신 시 재전송

#### 2.2.2 TCP 수신 과정

**흐름**:

1. **네트워크 인터페이스**: 패킷 수신
2. **IP 계층**: IP 헤더 처리
3. **TCP 계층**: 세그먼트 재조립
4. **소켓 버퍼**: 수신 버퍼에 저장
5. **애플리케이션**: `recv()` 또는 `read()`로 데이터 읽기

**커널 코드**:

```
tcp_v4_rcv()               // 패킷 수신
    → tcp_v4_do_rcv()      // TCP 처리
    → tcp_rcv_established() // ESTABLISHED 상태 처리
    → tcp_data_queue()     // 데이터 버퍼에 저장
```

**특징**:

- **순서 보장**: 시퀀스 번호로 순서 확인
- **중복 제거**: 중복 패킷 제거
- **ACK 전송**: 수신 확인 응답

---

## STEP 3. TCP 사용 시 서비스에서 겪을 수 있는 문제들

### 3.1 TIME_WAIT 소켓

#### 3.1.1 클라이언트 측면

**문제**:

- 연결 종료 후 `TIME_WAIT` 상태로 2MSL (Maximum Segment Lifetime) 대기
- 같은 포트로 즉시 재연결 불가능
- 포트 고갈 가능성

**해결책**:

```bash
# SO_REUSEADDR 옵션 사용
setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
```

#### 3.1.2 서버 측면

**문제**:

- 서버가 먼저 종료하면 `TIME_WAIT` 상태 발생
- 포트 고갈로 인한 새로운 연결 거부

**해결책**:

- 클라이언트가 먼저 종료하도록 설계
- 또는 `SO_REUSEADDR` 옵션 사용

#### 3.1.3 TIME_WAIT 소켓의 존재 의의

**왜 필요한가?**:

1. **지연된 패킷 처리**: 네트워크에 남아있을 수 있는 패킷 처리
2. **연결 정리**: 양쪽 모두 연결 종료 확인
3. **데이터 무결성**: 이전 연결의 패킷이 새 연결에 섞이지 않도록 방지

**커널 파라미터**:

```bash
# TIME_WAIT 소켓 최대 개수
net.ipv4.tcp_max_tw_buckets = 32768

# TIME_WAIT 소켓 재사용 허용
net.ipv4.tcp_tw_reuse = 1
```

### 3.2 TCP Keep-Alive

#### 3.2.1 TCP Keep-Alive 파라미터

**목적**: 유휴 연결이 살아있는지 확인

**커널 파라미터**:

```bash
# Keep-Alive 시작 시간 (초)
net.ipv4.tcp_keepalive_time = 7200

# Keep-Alive 간격 (초)
net.ipv4.tcp_keepalive_intvl = 75

# Keep-Alive 재시도 횟수
net.ipv4.tcp_keepalive_probes = 9
```

**동작**:

1. 7200초(2시간) 동안 데이터 없음
2. Keep-Alive 패킷 전송 시작
3. 75초마다 재시도
4. 9번 실패 시 연결 종료

#### 3.2.2 좀비 커넥션 (Zombie Connection)

**문제**:

- 클라이언트가 비정상 종료 (네트워크 끊김, 강제 종료 등)
- 서버는 연결이 살아있다고 생각
- 리소스 낭비

**해결책**:

- TCP Keep-Alive 활성화
- 애플리케이션 레벨 타임아웃 설정

#### 3.2.3 HTTP 지속 커넥션 vs TCP Keep-Alive

**HTTP Keep-Alive**:

- 애플리케이션 레벨
- HTTP 헤더로 관리
- 여러 요청/응답을 하나의 TCP 연결로 처리

**TCP Keep-Alive**:

- 커널 레벨
- 네트워크 계층에서 관리
- 연결이 살아있는지 확인

**관계**:

- HTTP Keep-Alive는 TCP 연결을 재사용
- TCP Keep-Alive는 연결이 끊어졌는지 확인

#### 3.2.4 로드 밸런서와 TCP Keep-Alive

**문제**:

- 로드 밸런서가 Keep-Alive 연결을 관리
- 백엔드 서버와의 연결도 Keep-Alive 필요
- 연결 풀 관리 중요

**해결책**:

- 로드 밸런서의 Keep-Alive 설정 조정
- 백엔드 서버의 Keep-Alive 설정 조정
- 연결 풀 크기 최적화

### 3.3 TCP 재전송과 타임아웃

#### 3.3.1 TCP 재전송 커널 파라미터

**재전송 타임아웃 (RTO - Retransmission Timeout)**:

```bash
# 초기 RTO (밀리초)
net.ipv4.tcp_syn_retries = 6        # SYN 재전송 횟수
net.ipv4.tcp_synack_retries = 5    # SYN-ACK 재전송 횟수

# 최소 RTO (밀리초)
TCP_RTO_MIN = 200ms

# 최대 RTO (밀리초)
TCP_RTO_MAX = 120초
```

**재전송 전략**:

- **Exponential Backoff**: 재전송 간격을 지수적으로 증가
- **Jitter**: 랜덤 지연 추가 (Thundering Herd 방지)

**커널 코드**:

```c
// 재전송 횟수 계산
tcp_orphan_retries()
    - retries == 0 && alive → 8번 재시도
    - TCP_RTO_MAX (120초) 보다 작으면 alive = true
```

#### 애플리케이션 타임아웃 설정 가이드

**주의사항**:

1. **초기 연결 수립**: `TCP_RTO_MIN` (200ms) 이상 설정
2. **일반 요청**: 평균 응답 시간 + 여유 시간 고려
3. **재전송 고려**: 네트워크 불안정 시 재전송 시간 포함

**권장 설정**:

- 초기 연결: 최소 1초 이상
- 일반 요청: 평균 응답 시간의 2~3배
- 장시간 작업: 작업 시간 + 재전송 시간 고려

---

## 📊 핵심 정리

### 소켓의 본질

- **파일 디스크립터**: 소켓도 파일처럼 취급
- **"Everything is a file"**: UNIX 철학
- **시스템 콜**: `socket()`으로 생성

### TCP 핵심 메커니즘

| 메커니즘            | 목적        | 특징                  |
| ------------------- | ----------- | --------------------- |
| **3-Way Handshake** | 연결 수립   | SYN → SYN-ACK → ACK   |
| **4-Way Handshake** | 연결 해제   | FIN → ACK → FIN → ACK |
| **재전송**          | 신뢰성 보장 | Exponential Backoff   |
| **Keep-Alive**      | 연결 유지   | 유휴 연결 감지        |

### 실무에서 주의할 점

1. **TIME_WAIT**: 포트 고갈 방지
2. **Keep-Alive**: 좀비 커넥션 방지
3. **타임아웃**: 재전송 메커니즘 고려
4. **로드 밸런서**: Keep-Alive 설정 조정

---

## 🔧 커널 파라미터 요약

### TIME_WAIT 관련

```bash
# TIME_WAIT 소켓 최대 개수
net.ipv4.tcp_max_tw_buckets = 32768

# TIME_WAIT 소켓 재사용
net.ipv4.tcp_tw_reuse = 1
```

### Keep-Alive 관련

```bash
# Keep-Alive 시작 시간 (초)
net.ipv4.tcp_keepalive_time = 7200

# Keep-Alive 간격 (초)
net.ipv4.tcp_keepalive_intvl = 75

# Keep-Alive 재시도 횟수
net.ipv4.tcp_keepalive_probes = 9
```

### 재전송 관련

```bash
# SYN 재전송 횟수
net.ipv4.tcp_syn_retries = 6

# SYN-ACK 재전송 횟수
net.ipv4.tcp_synack_retries = 5

# 최소 RTO: 200ms
# 최대 RTO: 120초
```

---

## 💡 실무 적용 가이드

### 문제 해결 체크리스트

**포트 고갈 문제**:

- [ ] TIME_WAIT 소켓 확인: `ss -tan | grep TIME-WAIT`
- [ ] SO_REUSEADDR 옵션 사용
- [ ] 클라이언트가 먼저 종료하도록 설계

**좀비 커넥션 문제**:

- [ ] TCP Keep-Alive 활성화
- [ ] 애플리케이션 타임아웃 설정
- [ ] 연결 모니터링

**타임아웃 문제**:

- [ ] 커널 파라미터 확인
- [ ] 평균 응답 시간 측정
- [ ] 재전송 시간 고려한 타임아웃 설정

### 모니터링 명령어

```bash
# 소켓 상태 확인
ss -tan | grep ESTABLISHED
ss -tan | grep TIME-WAIT

# Keep-Alive 설정 확인
sysctl net.ipv4.tcp_keepalive_time

# 재전송 통계 확인
cat /proc/net/sockstat
```

---

## 📝 결론

이 문서는 Linux 커널 레벨에서 소켓과 TCP의 동작 원리를 설명하고, 실제 서비스에서 겪을 수 있는 문제들과 해결 방법을 제시합니다.

**핵심 포인트**:

1. 소켓은 파일 디스크립터로 관리됨
2. TCP는 신뢰성을 위한 복잡한 메커니즘을 가짐
3. 실무에서는 TIME_WAIT, Keep-Alive, 재전송을 고려해야 함
4. 커널 파라미터 튜닝이 성능에 큰 영향

이러한 지식을 바탕으로 네트워크 관련 문제를 진단하고 해결할 수 있습니다.
