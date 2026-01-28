# Kubernetes NetworkPolicy (기술적으로 자세한 정리)

> 대상 독자: Kubernetes를 실무에서 운영/설계하는 엔지니어  
> 목표: NetworkPolicy의 **동작 원리, 트래픽 흐름, 설계 패턴, 한계와 주의점**까지 이해

---

## 1. NetworkPolicy란?

**NetworkPolicy**는 Kubernetes에서  
👉 **파드 간 / 파드 ↔ 외부 간 네트워크 트래픽을 제어하는 L3/L4 수준의 정책 리소스**입니다.

- “누가(From) → 누구에게(To) → 어떤 트래픽을 허용할지”를 정의
- 기본 철학: **기본 차단(Default Deny) + 명시적 허용(Allow List)**

⚠️ 중요한 전제:
> **NetworkPolicy는 CNI 플러그인이 이를 구현해야만 동작**합니다.

지원 CNI 예:
- Calico (가장 널리 사용)
- Cilium (eBPF 기반, L7 확장 가능)
- Weave Net
- Antrea

미지원(또는 제한):
- Flannel (기본 모드에서는 미지원)

---

## 2. NetworkPolicy가 해결하는 문제

### 2.1 Kubernetes 기본 네트워크의 특징
- 기본적으로 **모든 파드는 서로 통신 가능**
- 네임스페이스 경계는 네트워크 격리가 아님
- 보안 관점에서 “Flat Network”

### 2.2 NetworkPolicy의 역할
- 마이크로서비스 간 **최소 권한 네트워크 접근**
- 내부 침해 시 lateral movement 차단
- 멀티테넌트 환경에서 네임스페이스 간 격리

---

## 3. 기본 동작 모델 (매우 중요 ⭐)

### 3.1 정책이 “선택한 파드”에만 적용됨
NetworkPolicy는 다음 구조를 가짐:

```text
[ policy ]
   └─ podSelector → 보호 대상 파드
        ├─ ingress rules (들어오는 트래픽 허용 조건)
        └─ egress rules  (나가는 트래픽 허용 조건)
```

- `podSelector`에 **선택된 파드만** 정책의 대상
- 선택되지 않은 파드는 **기존처럼 모든 트래픽 허용**

---

### 3.2 Default Allow → Default Deny 전환 조건
#### Ingress
- 어떤 파드에 대해 **Ingress NetworkPolicy가 하나라도 적용되면**
  → 그 파드는 **Ingress Default Deny**
- 이후 허용 규칙에 매칭되는 트래픽만 허용

#### Egress
- Egress 정책도 동일
- Egress 정책이 하나라도 있으면 → **외부로 나가는 트래픽 Default Deny**

👉 “정책이 존재하는 순간, 그 방향은 기본 차단”

---

## 4. NetworkPolicy 리소스 구조

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: example-policy
  namespace: app
spec:
  podSelector:
    matchLabels:
      app: backend
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: frontend
    ports:
    - protocol: TCP
      port: 8080
```

### 주요 필드 설명
- `podSelector` : 보호 대상 파드
- `policyTypes` : Ingress / Egress
- `ingress.from` : 허용되는 출발지
- `egress.to` : 허용되는 목적지
- `ports` : 허용 포트/프로토콜

---

## 5. Selector 동작 방식 (핵심 개념)

### 5.1 podSelector
- **같은 네임스페이스** 내 파드만 선택
- label 기반

```yaml
podSelector:
  matchLabels:
    role: db
```

### 5.2 namespaceSelector
- 네임스페이스 라벨 기준으로 선택
- podSelector와 조합 가능

```yaml
from:
- namespaceSelector:
    matchLabels:
      team: payment
```

### 5.3 podSelector + namespaceSelector 조합
```yaml
from:
- namespaceSelector:
    matchLabels:
      team: payment
  podSelector:
    matchLabels:
      app: api
```

→ “payment 네임스페이스의 api 파드만 허용”

---

## 6. Ingress 정책 (들어오는 트래픽)

### 6.1 개념
- 대상 파드로 **들어오는 트래픽**을 제어
- 클라이언트 파드 기준으로 `from` 설정

### 6.2 예: frontend → backend만 허용
```yaml
ingress:
- from:
  - podSelector:
      matchLabels:
        app: frontend
  ports:
  - protocol: TCP
    port: 8080
```

### 6.3 주의점
- kube-proxy / NodePort / LoadBalancer 경로도 영향을 받을 수 있음
- readiness/liveness probe가 막히는 경우 자주 발생

---

## 7. Egress 정책 (나가는 트래픽)

### 7.1 왜 중요한가
- 내부 침해 시 **외부로의 데이터 유출 차단**
- DB, 외부 API 접근 범위 제한

### 7.2 예: DB + DNS만 허용
```yaml
egress:
- to:
  - podSelector:
      matchLabels:
        role: db
  ports:
  - protocol: TCP
    port: 5432

- to:
  - namespaceSelector:
      matchLabels:
        kubernetes.io/metadata.name: kube-system
  ports:
  - protocol: UDP
    port: 53
```

⚠️ DNS 허용을 빼먹으면:
- 외부 도메인 접근 실패
- 이미지 pull / API 호출 문제 발생

---

## 8. IPBlock (CIDR 기반 제어)

### 8.1 개념
- 파드/네임스페이스가 아닌 **IP 대역** 기준 제어
- 주로 외부 네트워크 접근 허용 시 사용

```yaml
ipBlock:
  cidr: 10.0.0.0/8
  except:
  - 10.1.0.0/16
```

### 8.2 제한 사항
- `ipBlock`은 **podSelector/namespaceSelector와 동시에 사용 불가**
- NAT 환경에서는 실제 IP와 다를 수 있음

---

## 9. NetworkPolicy의 한계 (중요 ⭐)

### 9.1 L3/L4까지만 제어
- IP / Port / Protocol 기반
- HTTP path, method, header 기반 제어 불가 (표준 NetworkPolicy)

👉 L7 제어 필요 시:
- Cilium L7 NetworkPolicy
- Service Mesh (Istio, Linkerd)

### 9.2 “거부(Deny)” 규칙 없음
- NetworkPolicy는 **Allow List 모델**
- 명시적 deny 규칙 불가
- “허용하지 않으면 차단” 구조

### 9.3 CNI별 구현 차이
- 정책 처리 순서/성능/로그 가시성 차이
- 동일 YAML이라도 동작 차이가 날 수 있음

---

## 10. 실무 설계 패턴

### 10.1 네임스페이스 Default Deny
```yaml
spec:
  podSelector: {}
  policyTypes:
  - Ingress
```
→ 네임스페이스 전체 Ingress 차단

### 10.2 계층형 접근
- frontend → api → db
- 각 계층 간 최소 허용

### 10.3 공통 인프라 예외
- DNS (53)
- Metrics / Monitoring
- Service Mesh control plane

---

## 11. 트러블슈팅 체크리스트

- CNI가 NetworkPolicy를 지원하는가?
- 정책이 적용된 파드가 맞는가? (`podSelector`)
- DNS egress 허용했는가?
- kube-system 네임스페이스 접근 필요 여부
- readiness/liveness probe 차단 여부
- NodePort / LoadBalancer 경로 영향

---

## 12. NetworkPolicy vs 보안 계층 비교

| 계층 | 기술 | 역할 |
|---|---|---|
| L3/L4 | NetworkPolicy | 기본 네트워크 격리 |
| L7 | Service Mesh | HTTP/GRPC 레벨 제어 |
| Identity | mTLS | 파드 간 인증 |
| Runtime | Pod Security / SELinux | 실행 권한 제한 |

---

## 13. 한 줄 요약
- **NetworkPolicy는 Kubernetes 네트워크 보안의 최소 단위**
- 기본은 “모두 허용”, 정책이 생기는 순간 “기본 차단 + 명시적 허용”
- 설계 시 **DNS, 운영 트래픽, CNI 구현 차이**를 반드시 고려해야 함

