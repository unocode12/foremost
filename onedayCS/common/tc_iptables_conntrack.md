# Linux 네트워킹 핵심 도구: tc, iptables, conntrack

## 📋 목차

1. [tc (Traffic Control)](#1-tc-traffic-control)
2. [iptables](#2-iptables)
3. [conntrack Table](#3-conntrack-table)
4. [통합 사용 예시](#4-통합-사용-예시)

---

## 1. tc (Traffic Control)

### 1.1 개요

**tc (Traffic Control)**는 Linux 커널의 트래픽 제어 시스템으로, 네트워크 인터페이스의 대역폭, 지연, 패킷 손실 등을 제어하는 도구입니다.

**주요 용도**:
- 대역폭 제한 (Bandwidth Limiting)
- 트래픽 셰이핑 (Traffic Shaping)
- QoS (Quality of Service) 구현
- 네트워크 에뮬레이션 (지연, 패킷 손실 시뮬레이션)

---

### 1.2 핵심 개념

**Qdisc (Queueing Discipline)**:
- 패킷을 큐에 넣고 스케줄링하는 알고리즘
- 예: `pfifo`, `bfifo`, `htb`, `netem`

**Class**:
- 트래픽을 분류하는 단위
- 각 클래스에 다른 정책 적용 가능

**Filter**:
- 패킷을 특정 클래스로 분류하는 규칙

---

### 1.3 기본 구조

```
[Network Interface]
    ↓
[Root Qdisc]
    ↓
[Class 1] [Class 2] [Class 3]
    ↓
[Leaf Qdisc]
```

---

### 1.4 주요 명령어

**Qdisc 추가**:
```bash
# HTB (Hierarchical Token Bucket) Qdisc 추가
tc qdisc add dev eth0 root handle 1: htb default 30

# Netem Qdisc 추가 (지연, 손실 시뮬레이션)
tc qdisc add dev eth0 root netem delay 100ms
```

**Class 추가**:
```bash
# HTB 클래스 추가 (대역폭 제한)
tc class add dev eth0 parent 1: classid 1:1 htb rate 100mbit
tc class add dev eth0 parent 1:1 classid 1:10 htb rate 50mbit ceil 100mbit
tc class add dev eth0 parent 1:1 classid 1:20 htb rate 30mbit ceil 50mbit
```

**Filter 추가**:
```bash
# IP 주소 기반 필터링
tc filter add dev eth0 protocol ip parent 1:0 prio 1 u32 \
    match ip src 192.168.1.0/24 flowid 1:10

# 포트 기반 필터링
tc filter add dev eth0 protocol ip parent 1:0 prio 2 u32 \
    match ip dport 80 0xffff flowid 1:20
```

**현재 설정 확인**:
```bash
# Qdisc 확인
tc qdisc show dev eth0

# Class 확인
tc class show dev eth0

# Filter 확인
tc filter show dev eth0

# 통계 확인
tc -s qdisc show dev eth0
tc -s class show dev eth0
```

**설정 삭제**:
```bash
# Qdisc 삭제 (모든 하위 설정도 함께 삭제)
tc qdisc del dev eth0 root
```

---

### 1.5 주요 Qdisc 타입

| Qdisc | 설명 | 용도 |
|-------|------|------|
| **pfifo** | 단순 FIFO 큐 | 기본 큐잉 |
| **bfifo** | 바이트 기반 FIFO | 바이트 제한 |
| **htb** | 계층적 토큰 버킷 | 대역폭 제한, 우선순위 |
| **netem** | 네트워크 에뮬레이션 | 지연, 손실, 재정렬 시뮬레이션 |
| **sfq** | Stochastic Fair Queueing | 공정한 대역폭 분배 |
| **tbf** | Token Bucket Filter | 단순 대역폭 제한 |

---

### 1.6 실전 예시

**예시 1: 대역폭 제한**
```bash
# eth0 인터페이스에 100Mbps 제한
tc qdisc add dev eth0 root handle 1: htb default 30
tc class add dev eth0 parent 1: classid 1:1 htb rate 100mbit
tc class add dev eth0 parent 1:1 classid 1:10 htb rate 50mbit ceil 100mbit
tc class add dev eth0 parent 1:1 classid 1:20 htb rate 30mbit ceil 50mbit
```

**예시 2: 네트워크 지연 시뮬레이션**
```bash
# 100ms 지연 추가
tc qdisc add dev eth0 root netem delay 100ms

# 지연 + 변동성 (100ms ± 10ms)
tc qdisc add dev eth0 root netem delay 100ms 10ms

# 패킷 손실 1%
tc qdisc add dev eth0 root netem loss 1%

# 패킷 재정렬 5%
tc qdisc add dev eth0 root netem reorder 5%
```

**예시 3: 우선순위 기반 트래픽 제어**
```bash
# 루트 Qdisc 설정
tc qdisc add dev eth0 root handle 1: htb default 30

# 루트 클래스
tc class add dev eth0 parent 1: classid 1:1 htb rate 100mbit

# 우선순위 높은 트래픽 (SSH, 50Mbps)
tc class add dev eth0 parent 1:1 classid 1:10 htb rate 50mbit ceil 100mbit prio 1

# 일반 트래픽 (30Mbps)
tc class add dev eth0 parent 1:1 classid 1:20 htb rate 30mbit ceil 50mbit prio 2

# 낮은 우선순위 트래픽 (20Mbps)
tc class add dev eth0 parent 1:1 classid 1:30 htb rate 20mbit ceil 30mbit prio 3

# SSH 트래픽을 우선순위 높은 클래스로
tc filter add dev eth0 protocol ip parent 1:0 prio 1 u32 \
    match ip dport 22 0xffff flowid 1:10
```

---

## 2. iptables

### 2.1 개요

**iptables**는 Linux 커널의 Netfilter 프레임워크를 사용하여 패킷 필터링, NAT(Network Address Translation), 패킷 변환 등을 수행하는 도구입니다.

**주요 용도**:
- 방화벽 (Firewall)
- NAT (Network Address Translation)
- 패킷 필터링
- 로드 밸런싱 (DNAT)
- 포트 포워딩

---

### 2.2 핵심 개념

**Table (테이블)**:
- `filter`: 기본 패킷 필터링
- `nat`: NAT 규칙
- `mangle`: 패킷 헤더 수정
- `raw`: 연결 추적 전 처리

**Chain (체인)**:
- `INPUT`: 들어오는 패킷
- `OUTPUT`: 나가는 패킷
- `FORWARD`: 포워딩되는 패킷
- `PREROUTING`: 라우팅 전 (nat, mangle)
- `POSTROUTING`: 라우팅 후 (nat, mangle)

**Target (타겟)**:
- `ACCEPT`: 패킷 허용
- `DROP`: 패킷 버림
- `REJECT`: 패킷 거부 (응답 전송)
- `DNAT`: 목적지 NAT
- `SNAT`: 출발지 NAT
- `MASQUERADE`: 동적 SNAT
- `LOG`: 로깅

---

### 2.3 패킷 흐름

```
[Incoming Packet]
    ↓
[PREROUTING] (raw, mangle, nat)
    ↓
[Routing Decision]
    ↓
    ├─→ [INPUT] (mangle, filter)
    │       ↓
    │   [Local Process]
    │       ↓
    │   [OUTPUT] (raw, mangle, nat, filter)
    │       ↓
    └─→ [FORWARD] (mangle, filter)
            ↓
[POSTROUTING] (mangle, nat)
    ↓
[Outgoing Packet]
```

---

### 2.4 주요 명령어

**규칙 추가**:
```bash
# 기본 규칙 추가
iptables -A INPUT -p tcp --dport 22 -j ACCEPT
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
iptables -A INPUT -j DROP

# 특정 위치에 규칙 삽입
iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT

# 체인별 규칙 추가
iptables -A INPUT -s 192.168.1.0/24 -j ACCEPT
iptables -A OUTPUT -d 192.168.1.0/24 -j ACCEPT
iptables -A FORWARD -i eth0 -o eth1 -j ACCEPT
```

**NAT 규칙**:
```bash
# SNAT (Source NAT)
iptables -t nat -A POSTROUTING -o eth0 -j SNAT --to-source 1.2.3.4

# MASQUERADE (동적 IP용)
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE

# DNAT (Destination NAT)
iptables -t nat -A PREROUTING -p tcp --dport 80 -j DNAT --to-destination 192.168.1.100:8080

# 포트 포워딩
iptables -t nat -A PREROUTING -p tcp --dport 8080 -j DNAT --to-destination 192.168.1.100:80
iptables -t nat -A POSTROUTING -p tcp -d 192.168.1.100 --dport 80 -j SNAT --to-source 192.168.1.1
```

**규칙 확인**:
```bash
# 모든 테이블 규칙 확인
iptables -L -n -v

# 특정 테이블 확인
iptables -t nat -L -n -v
iptables -t mangle -L -n -v

# 규칙 번호와 함께 확인
iptables -L -n -v --line-numbers

# 통계 확인
iptables -L -n -v -x
```

**규칙 삭제**:
```bash
# 규칙 번호로 삭제
iptables -D INPUT 1

# 규칙 내용으로 삭제
iptables -D INPUT -p tcp --dport 22 -j ACCEPT

# 체인의 모든 규칙 삭제
iptables -F INPUT

# 모든 체인 규칙 삭제
iptables -F

# NAT 테이블 규칙 삭제
iptables -t nat -F
```

**정책 설정**:
```bash
# 기본 정책 설정 (DROP)
iptables -P INPUT DROP
iptables -P OUTPUT DROP
iptables -P FORWARD DROP

# 기본 정책 설정 (ACCEPT)
iptables -P INPUT ACCEPT
iptables -P OUTPUT ACCEPT
iptables -P FORWARD ACCEPT
```

---

### 2.5 실전 예시

**예시 1: 기본 방화벽 설정**
```bash
# 기본 정책 설정
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# Loopback 허용
iptables -A INPUT -i lo -j ACCEPT

# ESTABLISHED, RELATED 연결 허용
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# SSH 허용
iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# HTTP, HTTPS 허용
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# 특정 IP 대역 허용
iptables -A INPUT -s 192.168.1.0/24 -j ACCEPT
```

**예시 2: NAT 설정 (게이트웨이)**
```bash
# IP 포워딩 활성화
echo 1 > /proc/sys/net/ipv4/ip_forward

# 내부 네트워크를 외부로 나가는 트래픽 MASQUERADE
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE

# FORWARD 체인 허용
iptables -A FORWARD -i eth1 -o eth0 -j ACCEPT
iptables -A FORWARD -i eth0 -o eth1 -m state --state ESTABLISHED,RELATED -j ACCEPT
```

**예시 3: 포트 포워딩**
```bash
# 외부 8080 포트를 내부 192.168.1.100:80으로 포워딩
iptables -t nat -A PREROUTING -p tcp --dport 8080 -j DNAT --to-destination 192.168.1.100:80
iptables -t nat -A POSTROUTING -p tcp -d 192.168.1.100 --dport 80 -j SNAT --to-source 192.168.1.1
iptables -A FORWARD -p tcp -d 192.168.1.100 --dport 80 -j ACCEPT
```

**예시 4: 로깅**
```bash
# 거부된 패킷 로깅
iptables -A INPUT -j LOG --log-prefix "INPUT DROP: " --log-level 4
iptables -A INPUT -j DROP

# 특정 포트 접근 로깅
iptables -A INPUT -p tcp --dport 22 -j LOG --log-prefix "SSH: "
iptables -A INPUT -p tcp --dport 22 -j ACCEPT
```

---

## 3. conntrack Table

### 3.1 개요

**conntrack (Connection Tracking)**은 Linux 커널의 Netfilter 프레임워크에서 네트워크 연결 상태를 추적하는 메커니즘입니다.

**주요 용도**:
- 상태 기반 방화벽 (Stateful Firewall)
- NAT 연결 추적
- 연결 상태 모니터링
- 세션 관리

---

### 3.2 연결 상태

**주요 상태**:
- `NEW`: 새로운 연결 시작
- `ESTABLISHED`: 연결이 설정됨
- `RELATED`: 기존 연결과 관련된 연결 (예: FTP 데이터 연결)
- `INVALID`: 유효하지 않은 패킷
- `UNTRACKED`: 추적되지 않는 연결
- `SNAT`: SNAT된 연결
- `DNAT`: DNAT된 연결

---

### 3.3 conntrack-tools 사용

**설치**:
```bash
# Ubuntu/Debian
apt-get install conntrack

# CentOS/RHEL
yum install conntrack-tools
```

**연결 확인**:
```bash
# 모든 연결 확인
conntrack -L

# 특정 프로토콜만 확인
conntrack -L -p tcp
conntrack -L -p udp

# 특정 IP 확인
conntrack -L -s 192.168.1.100
conntrack -L -d 192.168.1.100

# 특정 포트 확인
conntrack -L -p tcp --dport 80

# 실시간 모니터링
conntrack -E

# 통계 확인
conntrack -S
```

**연결 삭제**:
```bash
# 특정 연결 삭제
conntrack -D -s 192.168.1.100 -d 192.168.1.200

# 모든 연결 삭제
conntrack -F

# 특정 프로토콜 연결 삭제
conntrack -D -p tcp
```

**연결 정보 확인**:
```bash
# 상세 정보 확인
conntrack -L -o extended

# 특정 연결 상세 정보
conntrack -L -s 192.168.1.100 -o extended
```

---

### 3.4 /proc/net/ip_conntrack 확인

**직접 확인**:
```bash
# 연결 추적 테이블 확인 (구버전)
cat /proc/net/ip_conntrack

# 연결 추적 테이블 확인 (신버전)
cat /proc/net/nf_conntrack

# 연결 수 확인
cat /proc/net/nf_conntrack | wc -l

# 최대 연결 수 확인
cat /proc/sys/net/netfilter/nf_conntrack_max
```

**설정 조정**:
```bash
# 최대 연결 수 증가
echo 65536 > /proc/sys/net/netfilter/nf_conntrack_max

# 타임아웃 설정
echo 120 > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_established
echo 60 > /proc/sys/net/netfilter/nf_conntrack_tcp_timeout_time_wait
```

---

### 3.5 iptables와의 통합

**상태 기반 필터링**:
```bash
# ESTABLISHED, RELATED 연결만 허용
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# NEW 연결 허용 (특정 포트만)
iptables -A INPUT -m state --state NEW -p tcp --dport 22 -j ACCEPT
iptables -A INPUT -m state --state NEW -p tcp --dport 80 -j ACCEPT

# conntrack 모듈 사용
iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
```

**연결 추적 예외**:
```bash
# 특정 IP는 추적하지 않음
iptables -t raw -A PREROUTING -s 192.168.1.100 -j NOTRACK
iptables -t raw -A OUTPUT -d 192.168.1.100 -j NOTRACK
```

---

### 3.6 성능 최적화

**연결 추적 테이블 크기 조정**:
```bash
# 최대 연결 수 설정
sysctl -w net.netfilter.nf_conntrack_max=1000000

# 타임아웃 최적화
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_established=3600
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_time_wait=60
sysctl -w net.netfilter.nf_conntrack_tcp_timeout_close_wait=60
```

**영구 설정**:
```bash
# /etc/sysctl.conf에 추가
net.netfilter.nf_conntrack_max = 1000000
net.netfilter.nf_conntrack_tcp_timeout_established = 3600
net.netfilter.nf_conntrack_tcp_timeout_time_wait = 60
```

---

## 4. 통합 사용 예시

### 4.1 게이트웨이 서버 설정

**iptables + NAT + conntrack**:
```bash
#!/bin/bash

# IP 포워딩 활성화
echo 1 > /proc/sys/net/ipv4/ip_forward

# 기본 정책
iptables -P INPUT ACCEPT
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# Loopback 허용
iptables -A INPUT -i lo -j ACCEPT

# ESTABLISHED, RELATED 연결 허용
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
iptables -A FORWARD -m state --state ESTABLISHED,RELATED -j ACCEPT

# SSH 허용
iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# NAT 설정
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE

# 내부에서 외부로 포워딩 허용
iptables -A FORWARD -i eth1 -o eth0 -j ACCEPT
```

### 4.2 트래픽 제어 + 방화벽

**tc + iptables 통합**:
```bash
#!/bin/bash

# tc: 대역폭 제한
tc qdisc add dev eth0 root handle 1: htb default 30
tc class add dev eth0 parent 1: classid 1:1 htb rate 100mbit
tc class add dev eth0 parent 1:1 classid 1:10 htb rate 50mbit ceil 100mbit prio 1
tc class add dev eth0 parent 1:1 classid 1:20 htb rate 30mbit ceil 50mbit prio 2

# iptables: 트래픽 분류
# SSH 트래픽을 우선순위 높은 클래스로
tc filter add dev eth0 protocol ip parent 1:0 prio 1 u32 \
    match ip dport 22 0xffff flowid 1:10

# HTTP 트래픽을 일반 클래스로
tc filter add dev eth0 protocol ip parent 1:0 prio 2 u32 \
    match ip dport 80 0xffff flowid 1:20

# iptables: 방화벽 규칙
iptables -A INPUT -p tcp --dport 22 -j ACCEPT
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
```

### 4.3 모니터링 스크립트

**통합 모니터링**:
```bash
#!/bin/bash

echo "=== iptables 통계 ==="
iptables -L -n -v -x

echo -e "\n=== tc 통계 ==="
tc -s qdisc show dev eth0
tc -s class show dev eth0

echo -e "\n=== conntrack 통계 ==="
conntrack -S

echo -e "\n=== 활성 연결 수 ==="
conntrack -L | wc -l

echo -e "\n=== 최대 연결 수 ==="
cat /proc/sys/net/netfilter/nf_conntrack_max
```

---

## 5. 핵심 정리

### 5.1 각 도구의 역할

| 도구 | 계층 | 주요 역할 |
|------|------|----------|
| **tc** | L2/L3 | 트래픽 제어, QoS, 대역폭 관리 |
| **iptables** | L3/L4 | 방화벽, NAT, 패킷 필터링 |
| **conntrack** | L4 | 연결 상태 추적, 상태 기반 필터링 |

### 5.2 사용 시나리오

**tc 사용 시기**:
- 대역폭 제한이 필요한 경우
- QoS 구현이 필요한 경우
- 네트워크 조건 시뮬레이션이 필요한 경우

**iptables 사용 시기**:
- 방화벽 설정이 필요한 경우
- NAT 설정이 필요한 경우
- 포트 포워딩이 필요한 경우

**conntrack 사용 시기**:
- 상태 기반 방화벽이 필요한 경우
- 연결 모니터링이 필요한 경우
- NAT 연결 추적이 필요한 경우

### 5.3 성능 고려사항

**tc**:
- CPU 사용량 증가 가능
- 복잡한 규칙은 성능 저하
- 적절한 큐 크기 설정 중요

**iptables**:
- 규칙 순서가 성능에 영향
- 자주 매칭되는 규칙을 앞에 배치
- 불필요한 규칙 제거

**conntrack**:
- 메모리 사용량 증가
- 최대 연결 수 제한 설정 중요
- 타임아웃 값 최적화 필요

---

## 📚 참고 자료

- [Linux Traffic Control HOWTO](https://tldp.org/HOWTO/Traffic-Control-HOWTO/)
- [iptables Tutorial](https://www.netfilter.org/documentation/)
- [conntrack-tools Documentation](https://conntrack-tools.netfilter.org/)
- [Netfilter/iptables Project](https://www.netfilter.org/)

---

**작성일**: 2024  
**버전**: 1.0


