## 1. DaemonSet

### 1.1 DaemonSet이란?
**DaemonSet**은 클러스터의 **(선택된) 모든 노드마다 파드를 1개씩** 실행하도록 보장하는 워크로드 리소스입니다.  
즉, “노드 단위로 상주해야 하는 에이전트/데몬”을 배포하는 목적에 적합합니다.

- Deployment/ReplicaSet: *파드 개수(N)* 중심
- DaemonSet: *노드 수(또는 노드 셀렉터 결과)* 중심

대표 사용 사례:
- 로그 수집 에이전트: Fluent Bit / Fluentd / Vector
- 노드 모니터링: node-exporter, Datadog agent
- 네트워킹/CNI 에이전트: calico-node 등
- 스토리지 플러그인/CSI 노드 컴포넌트
- 보안/감사 에이전트(노드 레벨)

---

### 1.2 동작 원리: DaemonSet 컨트롤러와 스케줄링
DaemonSet은 control plane의 **DaemonSet 컨트롤러**가 관리합니다.

1) 컨트롤러가 노드 목록/상태를 관찰(Watch)  
2) DaemonSet의 `nodeSelector`/`nodeAffinity`/`tolerations`/`taints` 조건에 맞는 노드를 계산  
3) 각 대상 노드에 **파드가 존재하도록** 생성/삭제를 조정

#### 스케줄러와의 관계(핵심 이해 포인트)
- 일반 파드는 kube-scheduler가 노드를 “선택”합니다.
- DaemonSet 파드는 컨트롤러가 **대상 노드를 결정**하고 파드에 `spec.nodeName`을 지정하는 방식으로 특정 노드에 “바인딩”되는 것이 일반적입니다.
  - 즉 “노드당 1개 보장”이라는 요구사항 때문에 스케줄러의 탐색/균형 로직보다 **노드 고정 배치** 성격이 강합니다.

---

### 1.3 “노드당 1개”의 의미와 예외
기본적으로 **각 노드마다 1개의 파드**가 실행됩니다.  
다만 다음 조건을 만족하지 못하면 그 노드에는 파드가 생기지 않거나, 생성돼도 구동 실패할 수 있습니다.

- 노드가 `NotReady`
- taint를 toleration으로 허용하지 않음
- 리소스/제약 충돌
  - `hostPort` 충돌
  - `hostNetwork` 사용 시 포트 충돌
  - 노드 로컬 디스크/메모리 부족 등
- 이미지 풀 실패, 런타임 오류 등

---

### 1.4 노드 선택/제외: nodeSelector, affinity, tolerations
DaemonSet은 보통 “모든 노드”가 아니라 **조건에 맞는 노드 집합**에 배포합니다.

- `nodeSelector` / `nodeAffinity` : 특정 라벨이 있는 노드에만 배포
- `tolerations` : taint 노드(예: control-plane)에도 배포할지 결정

예: control-plane 노드에 배포 허용(운영 정책에 따라 제한적으로 사용)
```yaml
spec:
  template:
    spec:
      tolerations:
      - key: "node-role.kubernetes.io/control-plane"
        operator: "Exists"
        effect: "NoSchedule"
```

---

### 1.5 업데이트 전략: RollingUpdate vs OnDelete
DaemonSet의 `.spec.updateStrategy.type`

- **RollingUpdate (기본)**  
  노드별로 파드를 순차적으로 교체합니다.  
  - `.spec.updateStrategy.rollingUpdate.maxUnavailable`로 동시에 내려갈 수 있는 파드 수 제한 가능
- **OnDelete**  
  사용자가 기존 파드를 직접 삭제해야 새 파드가 생성됩니다.  
  - “업데이트를 매우 통제”하고 싶을 때 사용

운영 팁:
- 로그/모니터링 에이전트는 RollingUpdate가 일반적
- 커널 모듈/드라이버 등 민감 작업은 OnDelete로 통제하기도 함

---

### 1.6 스케일 개념(중요)
DaemonSet에는 `replicas`가 없습니다.  
“대상 노드 수 = 파드 수”로 자동 결정됩니다.

대상 노드를 조절하려면:
- 노드 라벨링(추가/제거)
- nodeAffinity 조건 변경
- taint/toleration 정책 변경

---

### 1.7 DaemonSet에서 자주 쓰는 노드 레벨 옵션
#### 1) hostNetwork / hostPID / hostIPC
노드 네임스페이스를 공유해야 할 때 사용

```yaml
spec:
  template:
    spec:
      hostNetwork: true
      hostPID: true
```

#### 2) hostPath 볼륨
노드 파일시스템 접근(로그 디렉터리, 런타임 소켓 등)

```yaml
volumes:
- name: varlog
  hostPath:
    path: /var/log
```

#### 3) privileged / capabilities
노드 네트워크/커널 조작이 필요하면 `privileged` 또는 capability 사용  
> 보안상 강력한 권한이므로 최소 권한 원칙 + 정책(Pod Security/OPA 등) 고려가 중요합니다.

---

### 1.8 트러블슈팅 포인트(현업 체크)
- **control-plane 노드에 안 뜬다** → taint/toleration 확인
- **특정 노드에만 안 뜬다** → nodeSelector/affinity 조건, 라벨 확인
- **파드 Pending** → hostPort 충돌, 리소스 부족, 바인딩(nodeName) 여부 확인
- **노드 로그를 못 읽는다** → hostPath 경로/권한 확인
- **업데이트 느림** → `maxUnavailable` 및 이미지 pull 시간/노드 수 확인

---

## 2. Init Container (Init Container Function)

### 2.1 Init Container란?
**Init Container**는 “앱 컨테이너(main containers)가 시작되기 전에” 실행되는 **초기화 전용 컨테이너**입니다.

- 파드의 `spec.initContainers`에 정의
- **순차 실행**: 리스트 순서대로 1개씩 실행
- 각 init container는 **성공(Exit Code 0)** 해야 다음 init container 또는 main container가 실행됨
- 실패하면 재시도되며, 성공할 때까지 main container는 시작하지 않음

즉, init container는 **파드 부팅 전 게이트(관문)** 역할을 합니다.

---

### 2.2 실행/라이프사이클 상세
파드 상태 흐름(개념):
1) 파드 생성 → 노드 배치  
2) init containers 실행(순차)  
3) 모두 성공 → main containers 실행  
4) readiness 조건 만족 시 서비스 트래픽 처리

핵심 포인트:
- init container는 **항상 main보다 먼저**
- init container는 “초기화 단계”이므로 보통 main 재시작과 무관하게 **재실행되지 않음**
  - 단, init 단계에서 실패하여 재시도 중이라면 반복 실행
  - 파드가 새로 만들어지면(init부터) 다시 실행

---

### 2.3 init container를 쓰는 기술적 이유
#### 1) 의존성 준비(Dependency readiness)
- 외부 서비스(DB, MQ)가 준비될 때까지 대기
- 네트워크 경로/인증서/시크릿 준비 검증

예: DB 포트가 열릴 때까지 대기
```yaml
initContainers:
- name: wait-for-db
  image: busybox:1.36
  command: ['sh', '-c', 'until nc -z db 5432; do echo waiting db; sleep 2; done;']
```

#### 2) 초기 데이터/구성 생성
- 설정 템플릿 렌더링
- 설정/키 생성 후 shared volume에 기록
- (주의) 마이그레이션 같은 변경 작업은 락/리더 선출 등 통제가 필요

#### 3) 권한/파일시스템 준비
- 볼륨 디렉터리 생성, chown/chmod 수행
- main은 non-root로 두고 init만 root로 하는 패턴

#### 4) 사이드카/메시 흐름 제어
- 사이드카가 사용해야 할 파일/룰/설정을 먼저 준비

---

### 2.4 init container와 볼륨 공유(가장 흔한 패턴)
init 단계에서 만든 결과물을 main 컨테이너가 사용하도록 **같은 볼륨을 마운트**합니다.

패턴: init이 설정 생성 → emptyDir에 기록 → 앱이 읽기
```yaml
volumes:
- name: config
  emptyDir: {}

initContainers:
- name: render-config
  image: alpine:3.20
  command: ['sh', '-c', 'echo "key=value" > /config/app.conf']
  volumeMounts:
  - name: config
    mountPath: /config

containers:
- name: app
  image: myapp:1.0
  volumeMounts:
  - name: config
    mountPath: /app/config
```

---

### 2.5 실패 처리와 관측(Observability)
- init 실패 시 파드는 `Initialized=False` 상태로 멈추며 재시도
- 흔히 `CrashLoopBackOff`처럼 보일 수 있음
- 진단은 **해당 init container 로그**가 핵심

운영 팁:
- 무한 대기 루프를 쓸 때는 “무엇을 기다리는지”를 로그로 남기고,
  필요하면 타임아웃을 둬 장애 감지를 빠르게 하세요.

---

### 2.6 init container vs postStart hook vs sidecar 비교
| 구분 | 실행 시점 | 목적 | 특징 |
|---|---|---|---|
| init container | main 컨테이너 **이전** | 필수 준비/검증/생성 | 순차/성공 보장, 실패 시 앱 시작 차단 |
| postStart hook | main 시작 직후 | 가벼운 초기화 | 컨테이너 시작과 동시, 복잡 작업엔 부적합 |
| sidecar | main과 **동시에/상시** | 보조 기능(프록시/로깅 등) | 지속 실행, 종료/순서 제어는 별도 설계 필요 |

---

### 2.7 보안/운영 베스트 프랙티스
- init는 필요할 때만 권한 상승(root/privileged) 사용
- init은 “시작 전 필수 조건”, readiness는 “트래픽 수신 준비”로 역할 분리
- 마이그레이션/스키마 변경은 **단 1개 파드만 수행**하도록(락/Job/리더 선출)
- 이미지 최소화로 기동 시간 단축 + 명확한 로그

---

## 3. 실무 예시 템플릿

### 3.1 DaemonSet 예시(노드 에이전트)
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: node-agent
spec:
  selector:
    matchLabels:
      app: node-agent
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 10%
  template:
    metadata:
      labels:
        app: node-agent
    spec:
      tolerations:
      - operator: "Exists"
      containers:
      - name: agent
        image: example/agent:1.0
        volumeMounts:
        - name: varlog
          mountPath: /var/log
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
```

### 3.2 Init Container 예시(의존성 대기 + 설정 생성)
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: app-with-init
spec:
  volumes:
  - name: config
    emptyDir: {}

  initContainers:
  - name: wait-for-db
    image: busybox:1.36
    command: ['sh', '-c', 'until nc -z db 5432; do echo waiting db; sleep 2; done;']

  - name: render-config
    image: alpine:3.20
    command: ['sh', '-c', 'echo "DB_HOST=db" > /config/app.conf']
    volumeMounts:
    - name: config
      mountPath: /config

  containers:
  - name: app
    image: example/app:1.0
    volumeMounts:
    - name: config
      mountPath: /app/config
```

---

## 4. 한 줄 요약
- **DaemonSet**: “대상 노드마다 1개” 파드를 유지하는 노드 상주형 워크로드
- **Init Container**: main 시작 전, 필수 준비 작업을 **순차/성공 보장**으로 수행하는 초기화 컨테이너

