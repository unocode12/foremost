# Kubernetes PodDisruptionBudget (PDB) — 기술적 상세 정리

> 대상: 쿠버네티스 운영/플랫폼/애플리케이션 엔지니어  
> 목적: PDB의 목적·동작원리·스케줄링 영향·통합 운영 패턴·실전 트러블슈팅을 기술적으로 이해

---

## 1. PDB란 무엇인가? (개념 요약)
PodDisruptionBudget(PDB)은 **자발적(Voluntary) 다운타임** 시에도 애플리케이션의 가용성(최소 가동 파드 수)을 보장하기 위한 쿠버네티스 리소스입니다.  
주로 다음 상황에서 적용됩니다:
- `kubectl drain` / 노드 유지보수(노드 축출, 노드 교체)
- 클러스터 업그레이드(노드 재부팅)
- 클러스터 오토스케일(노드 축소)
- Deployment/DaemonSet/StatefulSet의 Rolling update에 의한 파드 종료(컨트롤러 주도)

**핵심:** PDB는 *자발적(disruption)* 이벤트에 대해서만 작동하며, 노드 장애 같은 비자발적(involuntary) 이벤트는 제한하지 못한다.

---

## 2. PDB의 리소스 정의 (예시)
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: myapp-pdb
  namespace: production
spec:
  minAvailable: 3
  selector:
    matchLabels:
      app: myapp
```
또는 `maxUnavailable` 사용:
```yaml
spec:
  maxUnavailable: 20%  # 또는 정수
```
- `minAvailable`와 `maxUnavailable`은 상호 배타적(oneOf)`로 사용 권장
- `selector`는 PDB가 적용할 파드 집합을 정의(라벨 셀렉터)

---

## 3. 동작 원리 (매우 중요)
1. PDB 컨트롤러는 해당 네임스페이스의 PDB 객체와 파드를 감시(Watch).
2. PDB는 selector로 선택된 파드의 **현재 가용(Ready) 파드 수**를 계산.
   - Ready 조건: 파드가 `Ready` 상태(ready condition true)이거나, PDB가 파드의 상태를 기반으로 판정.
3. `minAvailable` 또는 `maxUnavailable` 기준에 따라 **허용 가능한 최대 자발적 중단 수(allowedDisruptions)** 를 산출.
   - allowedDisruptions = currentReady - minAvailable (if minAvailable used)
   - 또는 total - ceil(total * (1 - maxUnavailable)) 등 계산
4. Eviction 요청(예: `kubectl drain`가 kubelet/kube-controller-manager를 통해 eviction API 호출) 시, Eviction 핸들러는 PDB에서 허용 가능한 disruption이 남아있는지 확인.
   - 남아있으면 Eviction 허용(파드 종료/이동 가능)
   - 남아있지 않으면 Eviction 거부 → `PodDisruptionBudget` 조건 때문에 노드 drain이 대기/실패
5. Eviction 거부는 컨트롤러/유저에게 `TooManyRequests`(HTTP 429) 같거나 `cannot evict because it would violate the poddisruptionbudget` 메시지로 반환될 수 있음.

> **정리:** PDB는 Eviction 시 허용 가능한 disruption 수를 판단하여 파드 강제 종료(축출)를 허용/거부한다.

---

## 4. 자발적(Voluntary) vs 비자발적(Involuntary) 방해
- **자발적(Voluntary)**: 사용자/컨트롤러가 의도적으로 파드를 축출(evict)하려는 경우 (예: drain, kubeadm upgrade, scaling down, rolling update)  
  → PDB 적용(차단/허용)
- **비자발적(Involuntary)**: 노드 장애, kubelet 충돌, 하드웨어 고장, OutOfMemory 등  
  → PDB 적용 불가(파드는 단순히 죽음), PDB는 이러한 경우를 막지 못함

운영 시 오해 주의: PDB는 "절대 파드가 죽지 않게" 보장하는 것이 아니라 "자발적 축출을 조절"한다는 점을 명확히 이해해야 함.

---

## 5. Eviction API와의 관계
- `kubectl drain`은 노드의 파드에 Eviction 요청을 보낸다(`policy/v1` Eviction 객체).
- Eviction 시, kube-controller-manager 또는 API 서버가 각 PDB를 체크하여 허용 여부를 결정.
- Eviction이 거부되면 drain은 “waiting for the pod to be evicted” 상태로 멈추거나 실패한다.

추가 포인트:
- Eviction이 허용되면 kubelet이 파드 삭제를 수행하고 스케줄러/컨트롤러가 새 파드를 다른 노드에 생성할 수 있음.
- Eviction은 `deleteOptions`와 함께 전송되며, grace period 등 삭제 동작에 영향.

---

## 6. PDB와 컨트롤러(Deployment/StatefulSet/DaemonSet)의 상호작용
- **Deployment/ReplicaSet**: RollingUpdate 중 controller는 파드를 순차적으로 종료/생성한다. Eviction 요청은 PDB 차단을 받을 수 있다 → 업데이트 속도 제어를 위해 중요.
- **StatefulSet**: Pod 순서 보장(ordinal 기반) 및 퍼시스턴트 볼륨 때문에 PDB의 영향을 크게 받음. StatefulSet 업그레이드나 스케일 작업 시 PDB로 인해 작업이 블로킹될 수 있음.
- **DaemonSet**: 보통 노드당 1개의 파드, PDB는 DaemonSet과 함께 사용할 때 복잡성 있음. DaemonSet 파드는 특정 노드에 바인딩되므로 Eviction 영향이 다름.

운영 팁:
- 롤링 업데이트 전략(Burstiness)과 PDB 설정을 함께 고려해 업데이트 시간이 크게 늘어나지 않도록 조정
- StatefulSet의 경우 `maxUnavailable` 사용 불가(제한), 주의 필요

---

## 7. minAvailable vs maxUnavailable (세부 비교)
- `minAvailable` (정수 또는 %): 유지해야 하는 최소 Ready 파드 수. 예: `minAvailable: 3` → 최소 3개 Ready를 유지해야 한다.
- `maxUnavailable` (정수 또는 %): 허용 가능한 최대 비가용 파드 수. 예: `maxUnavailable: 1` or `20%`

동작 예시:
- 총 5개 파드, `minAvailable: 3` → allowedDisruptions = currentReady - 3
- 총 5개 파드, `maxUnavailable: 40%` → 최대 2개 비가용 허용

권장:
- 서비스의 SLA에 맞춰 계산. `minAvailable`는 가독성 있음(예: "항상 3대 이상"), `maxUnavailable`는 비율 기반 스케일에 유리

---

## 8. PDB의 한계와 오해 포인트
- PDB는 **가용성 목표(availability budget)**를 나타내지만 **복구 시간(RTO/RPO)**이나 **리소스 부족 자체**는 해결하지 않음.
- PDB는 개별 파드 `Ready` 상태를 기준으로 판단. 파드가 CrashLoop 상태여도 Ready가 false이면 PDB 계산에 영향을 줌.
- PDB는 StatefulSet의 `partition` 전략 등과 조합하면 롤링 업그레이드 제어가 쉬움.
- DaemonSet은 노드당 1개로 동작하므로 PDB의 효과가 제한적일 수 있음(대상 파드 수가 노드 수와 같아 계산이 복잡함).

---

## 9. 모니터링·가시성(권장 지표)
- **kube_poddisruptionbudget_status_current_healthy**: 현재 healthy(Ready) 파드 수
- **kube_poddisruptionbudget_status_desired_healthy**: 목표 healthy 파드 수
- **kube_poddisruptionbudget_status_disruptions_allowed**: 현재 허용 가능한 추가 자발적 중단 수
- **kube_poddisruptionbudget_status_expected_pods**: selector로 선택된 예상 파드 수

운영 대시보드 예시:
- PDB별 disruptions_allowed가 0에 근접하면 drain/업데이트 시 대기 또는 실패 가능
- PDB의 desired_healthy와 current_healthy 차이를 알람으로 설정

---

## 10. 실전 운영 패턴 및 권장 사항
1. **서비스 단위 PDB 설정**: 모든 서비스에 무분별한 PDB 생성은 오히려 운영 복잡도를 높인다. 핵심/상태 유지가 필요한 서비스 위주로 설정.
2. **네임스페이스/팀별 가이드라인**: 표준화된 PDB 템플릿(예: stateless: minAvailable 1, stateful: minAvailable N) 제공.
3. **Drain 자동화와 PDB 체크**: 자동화 스크립트에서 `kubectl drain` 실패 시 PDB 원인 진단, 혹은 `kubectl get pdb` 사전 확인 로직 추가.
4. **업데이트 전략 튜닝**: Deployment의 `maxUnavailable`와 PDB 충돌 가능성 점검. 예: Deployment maxUnavailable=1인데 PDB가 더 엄격하면 업데이트가 멈춤.
5. **긴급 예외 절차**: 긴급 유지보수 시 PDB를 일시적으로 제거하거나 수정하는 권한/프로세스 정의.
6. **파드 준비(Ready) 상태 관리**: ReadinessProbe를 통해 실제 서비스 가능 상태를 정확히 판단하도록 구성(잘못된 readiness는 PDB 계산을 망가뜨림).

---

## 11. 트러블슈팅 체크리스트
- `kubectl get pdb -n <ns>`로 disruptionsAllowed, currentHealthy, desiredHealthy 확인
- `kubectl describe pdb <name>`로 상세 메시지 확인(무슨 파드가 blocking인지)
- Eviction 요청 로그(예: `kubectl drain`)에서 `cannot evict` 메시지 확인
- 해당 파드의 Ready 상태 확인 (`kubectl get pod -o wide`)
- Deployment/StatefulSet/ReplicaSet의 desired/available 상태 확인
- 노드 레이블/taint/affinity가 drain 시 파드 이동을 방해하는지 확인
- 만약 PDB 때문에 자동화가 멈추면, 안전한 시간 동안 PDB를 수정(예: 임시로 maxUnavailable 증가)하거나 노드별 수동 조치

---

## 12. 예제: 실무에서 자주 쓰는 PDB 템플릿
### 12.1 상태 비저장(Stateless) 서비스(허용 가용성 1)
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: stateless-pdb
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: stateless-app
```
### 12.2 상태 저장(Stateful) 중요 서비스(항상 3대 유지)
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: stateful-db-pdb
spec:
  minAvailable: 3
  selector:
    matchLabels:
      app: important-db
```
### 12.3 비율 기반(maxUnavailable 사용)
```yaml
spec:
  maxUnavailable: 25%
  selector:
    matchLabels:
      app: web
```

---

## 13. 요약(실무 관점)
- PDB는 **자발적 축출(Eviction)** 에 대해 애플리케이션의 최소 가용성을 보장하는 메커니즘이다.  
- PDB는 비자발적 장애를 막지 못하므로, 장애 대응은 별도 DR/오토스케일/복제 전략으로 설계해야 한다.  
- PDB 설정은 서비스 특성(SLA, 복제 수, 상태 저장 여부)에 맞게 신중히 설계하며, 모니터링과 자동화(Drains, Upgrades) 통합을 반드시 병행해야 한다.

---

