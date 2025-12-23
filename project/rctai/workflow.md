# Workflow 시스템 기술 분석

## 📋 목차

1. [Workflow 시스템 개요](#1-workflow-시스템-개요)
2. [아키텍처 구조](#2-아키텍처-구조)
3. [DAG (Directed Acyclic Graph) 구조](#3-dag-directed-acyclic-graph-구조)
4. [노드 상태 관리](#4-노드-상태-관리)
5. [워크플로우 실행 흐름](#5-워크플로우-실행-흐름)
6. [특수 노드 처리](#6-특수-노드-처리)
7. [Rule Engine](#7-rule-engine)
8. [병렬 실행 메커니즘](#8-병렬-실행-메커니즘)
9. [실제 사용 예시](#9-실제-사용-예시)

---

## 1. Workflow 시스템 개요

### 1.1 Workflow란?

**Workflow**는 여러 Agent나 Service를 순차적/병렬적으로 실행하여 복잡한 작업을 수행하는 시스템입니다.

**핵심 개념:**
- **DAG 기반 실행**: Directed Acyclic Graph로 워크플로우 구조 표현
- **상태 기반 스케줄링**: 노드 상태에 따라 실행 순서 결정
- **병렬 실행**: 의존성이 없는 노드는 동시 실행
- **Loop/Selector 지원**: 반복 실행 및 조건부 분기

### 1.2 Workflow 시스템의 역할

```
┌─────────────────────────────────────────────────────────────┐
│                    사용자 요청                                │
│    ExecutionInput (query, parameters)                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                  WorkflowEngine                             │
│  - 서비스 워크플로우 조회                                      │
│  - WorkflowNode 변환                                         │
│  - WorkflowExecutor 생성 및 실행                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                WorkflowExecutor                             │
│  - DAG 구조 생성                                             │
│  - 노드 상태 관리                                             │
│  - 병렬 실행 (TaskGroup)                                      │
│  - Loop/Selector 처리                                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│              각 노드 실행 (Agent/Service)                     │
│  - Agent 실행                                                │
│  - Service 실행 (중첩 워크플로우)                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 아키텍처 구조

### 2.1 전체 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    WorkflowEngine                           │
│  - execute_service()                                        │
│  - ServiceWorkflow → WorkflowNode 변환                       │
│  - WorkflowExecutor 생성 및 실행                              │
└─────────────────────────────────────────────────────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                WorkflowExecutor                             │
│  - DAG 구조 관리                                             │
│  - 노드 상태 관리                                             │
│  - 실행 스케줄링                                              │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ 일반 노드     │  │ Loop 노드     │  │ Selector 노드 │
│ (Agent/      │  │ (반복 실행)   │  │ (조건 분기)    │
│ Service)     │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
                          │
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                    Rule Engine                              │
│  - 조건 평가                                                 │
│  - Selector 규칙 처리                                        │
│  - Loop 종료 조건 처리                                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 핵심 컴포넌트

**WorkflowEngine**
- 서비스 워크플로우 실행 진입점
- ServiceWorkflow를 WorkflowNode로 변환
- WorkflowExecutor 생성 및 실행

**WorkflowExecutor**
- DAG 구조 생성 및 관리
- 노드 상태 기반 스케줄링
- 병렬 실행 관리 (asyncio.TaskGroup)
- Loop/Selector 처리

**RuleEngine**
- 조건 평가 엔진
- Selector 규칙 처리
- Loop 종료 조건 처리

---

## 3. DAG (Directed Acyclic Graph) 구조

### 3.1 DAGNode 구조

```python
@dataclass
class DAGNode:
    id: str                          # 노드 ID
    flow_node: WorkflowNode          # 원본 워크플로우 노드
    children: Set[str] = field(default_factory=set)  # 자식 노드 ID들
    parents: Set[str] = field(default_factory=set)  # 부모 노드 ID들
    status: NodeStatus = NodeStatus.PENDING  # 노드 상태
    is_loop_start: bool = False      # Loop 시작 노드 여부
    is_loop_end: bool = False        # Loop 종료 노드 여부
    loop_count: int = 0              # Loop 반복 횟수
    loop_results: List[Any] = field(default_factory=list)  # Loop 결과들
    execution_count: int = 0         # 실행 횟수 (loop에서 사용)
```

### 3.2 DAG 생성 과정

```python
def _build_dag(self, flow_nodes: List[WorkflowNode]):
    """DAG 구조 생성"""
    # 1. 노드 생성
    for node in flow_nodes:
        self.dag[str(node.id)] = DAGNode(id=str(node.id), flow_node=node)
    
    # 2. 노드간 관계 설정
    for node in flow_nodes:
        if node.next_flow_node_id:
            node_id = str(node.id)
            next_ids = [nid.strip() for nid in str(node.next_flow_node_id).split(",")]
            
            for next_id in next_ids:
                self.dag[node_id].children.add(next_id)
                self.dag[next_id].parents.add(node_id)
```

**DAG 구조 예시:**

```
        Node 1 (시작)
         /    \
        ↓      ↓
    Node 2  Node 3 (병렬 실행)
        \    /
         ↓  ↓
      Node 4 (병합)
         ↓
      Node 5 (종료)
```

### 3.3 시작 노드 찾기

```python
def get_start_nodes(self) -> list[str]:
    """시작 노드 찾기 (부모가 없는 노드)"""
    start_node = []
    for node_id, node in self.dag.items():
        # 부모가 없는 노드
        if not node.parents:
            start_node.append(node_id)
        
        # 루프 시작 노드인 경우 (루프 종료 노드만 부모로 가진 경우)
        if (
            self.dag[node_id].is_loop_start
            and len(node.parents) == 1
            and self.dag[next(iter(node.parents))].is_loop_end
        ):
            start_node.append(node_id)
    
    return start_node
```

---

## 4. 노드 상태 관리

### 4.1 NodeStatus Enum

```python
class NodeStatus(Enum):
    PENDING = "pending"      # 아직 실행되지 않음
    READY = "ready"          # 실행 준비 완료 (모든 부모 노드 실행 완료)
    RUNNING = "running"      # 실행 중
    COMPLETED = "completed"  # 실행 완료
    SKIPPED = "skipped"      # 실행에서 제외됨 (선택되지 않은 브랜치)
    FAILED = "failed"        # 실행 중 에러 발생
```

### 4.2 상태 전이 흐름

```
PENDING
  ↓ (모든 부모 노드 완료)
READY
  ↓ (실행 시작)
RUNNING
  ↓ (실행 완료)
COMPLETED
  ↓ (자식 노드들을 READY로 변경)
```

**예외 케이스:**
- **SKIPPED**: Selector에서 선택되지 않은 브랜치
- **FAILED**: 실행 중 에러 발생

### 4.3 상태 기반 스케줄링

```python
# 실행 가능한 노드 찾기
for node_id in list(ready_nodes):
    node = self.dag[node_id]
    
    # 이미 완료되거나 스킵된 노드는 건너뛰기
    if node.status == NodeStatus.COMPLETED or node.status == NodeStatus.SKIPPED:
        continue
    
    # 노드 실행
    node.status = NodeStatus.RUNNING
    task = tg.create_task(self._process_node(node.flow_node))
    running_tasks[node_id] = task
```

---

## 5. 워크플로우 실행 흐름

### 5.1 전체 실행 흐름

```
┌─────────────────────────────────────────────────────────────┐
│ 1. WorkflowEngine.execute_service()                         │
│    - 서비스 워크플로우 조회                                    │
│    - ServiceWorkflow → WorkflowNode 변환                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. WorkflowExecutor 생성                                     │
│    - DAG 구조 생성 (_build_dag)                              │
│    - Loop 노드 설정 (_setup_loop_nodes)                      │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 시작 노드 찾기                                             │
│    start_nodes = get_start_nodes()                          │
│    - 부모가 없는 노드들                                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 실행 루프 시작 (asyncio.TaskGroup)                         │
│    while ready_nodes or running_tasks:                      │
│        - 실행 가능한 노드 처리                                 │
│        - 태스크 완료 모니터링                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 노드 실행                                                 │
│    - 일반 노드: _process_node()                              │
│    - Selector 노드: _process_selector_node()                 │
│    - Loop 시작: _handle_loop_start_node()                    │
│    - Loop 종료: _handle_loop_end_node()                      │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. 자식 노드 활성화                                           │
│    - 부모 노드 완료 시 자식 노드 상태를 READY로 변경             │
│    - 모든 부모가 완료되어야 자식 노드 실행 가능                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. 리프 노드 결과 수집 및 반환                                  │
│    - 자식이 없는 완료된 노드들의 결과 수집                       │
│    - List[PrevResult] 형태로 반환                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 실행 루프 상세

```python
async def execute(self, input_data: Any = None, test_mode: bool = False):
    """워크플로우 실행"""
    # 1. 초기화
    ready_nodes = start_id_list.copy()
    running_tasks = {}
    active_loops = {}
    
    try:
        async with asyncio.TaskGroup() as tg:
            while ready_nodes or running_tasks:
                # 2. 실행 가능한 노드 처리
                for node_id in list(ready_nodes):
                    ready_nodes.remove(node_id)
                    node = self.dag[node_id]
                    
                    # 3. 노드 타입별 처리
                    if node.is_loop_start:
                        await self._handle_loop_start_node(...)
                    elif node.is_loop_end:
                        await self._handle_loop_end_node(...)
                    elif has_selector_rules:
                        selected_nodes = await self._process_selector_node(...)
                    else:
                        # 4. 일반 노드 실행
                        node.status = NodeStatus.RUNNING
                        task = tg.create_task(self._process_node(node.flow_node))
                        running_tasks[node_id] = task
                        
                        # 5. 태스크 모니터링
                        tg.create_task(self._monitor_task(...))
                
                # 6. 태스크 완료 대기
                if running_tasks and not ready_nodes:
                    done, _ = await asyncio.wait(
                        running_tasks.values(),
                        return_when=asyncio.FIRST_COMPLETED
                    )
                
                # 7. 종료 조건
                if not running_tasks and not ready_nodes:
                    break
    
    # 8. 리프 노드 결과 수집
    leaf_nodes = self._get_leaf_nodes()
    return self._collect_leaf_results(leaf_nodes)
```

### 5.3 노드 실행 상세

```python
async def _process_node(self, node: WorkflowNode, input_data: Any = None) -> Any:
    """노드 실행"""
    # 1. 입력 데이터 준비
    if not input_data:
        input_data = self._get_node_input(str(node.id))
    
    # 2. 노드 타입별 실행
    if node.agent_instance:
        # Agent 실행
        agent = get_agent_registry().get_agent(agent_sub_type)
        result = await agent.invoke(
            agent_instance_info=agent_setting_info,
            user_input=self.user_input,
            prev_results=input_data,
        )
        return result
    
    elif node.service_id:
        # Service 실행 (중첩 워크플로우)
        result = await self.workflow_engine.execute_service(
            node.service_id,
            execution_input,
            input_data
        )
        return result
```

---

## 6. 특수 노드 처리

### 6.1 Selector 노드

**역할:** 조건에 따라 다음 노드를 선택하는 분기 노드

**처리 흐름:**

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Selector 노드 실행                                        │
│    result = await _process_node(selector_node)              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Selector 결과 파싱                                        │
│    selector_result = result.selector_result                 │
│    # 예: ["node_2", "node_3"]                               │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 자식 노드 분류                                             │
│    for child_id in node.children:                           │
│        if child_id in selector_result:                      │
│            selected_nodes.append(child_id)  # 선택됨         │
│        else:                                                │
│            skipped_nodes.append(child_id)  # 스킵됨          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 선택된 노드만 READY로 설정                                  │
│    for selected_node in selected_nodes:                     │
│        self.dag[selected_node].status = NodeStatus.READY    │
│        ready_nodes.append(selected_node)                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 스킵된 브랜치 전파                                          │
│    for skipped_node in skipped_nodes:                       │
│        self.dag[skipped_node].status = NodeStatus.SKIPPED   │
│        self._skip_branch(skipped_node)  # 하위 노드도 스킵    │
└─────────────────────────────────────────────────────────────┘
```

**코드 예시:**

```python
async def _process_selector_node(self, node_id: str, input_data: Any = None):
    """Selector 노드 처리"""
    node = self.dag[node_id]
    
    # Selector 노드 실행
    selector_execute_result = await self._process_node(node.flow_node)
    selector_result = selector_execute_result.selector_result
    
    # 결과를 리스트로 변환
    selector_result_list = selector_result if isinstance(selector_result, list) else [selector_result]
    
    # 자식 노드 분류
    selected_child_id_list = []
    non_selected_children = []
    
    for child_id in node.children:
        if child_id in selector_result_list:
            self.dag[child_id].status = NodeStatus.READY
            selected_child_id_list.append(child_id)
        else:
            # 스킵 처리
            self.dag[child_id].status = NodeStatus.SKIPPED
            self._skipped_accum.add(child_id)
            non_selected_children.append(child_id)
    
    # 스킵 브랜치 전파
    for child_id in non_selected_children:
        self._skip_branch(child_id, child_id, self._skipped_accum)
    
    return selected_child_id_list
```

### 6.2 Loop 노드

**역할:** 조건에 따라 반복 실행하는 노드

**Loop 구조:**

```
Loop Start Node
    ↓
Loop Body Nodes
    ↓
Loop End Node
    ↓ (조건 확인)
    ├─ 조건 만족 → Loop Start로 돌아감
    └─ 조건 불만족 → 다음 노드로 진행
```

**Loop 시작 노드 처리:**

```python
async def _handle_loop_start_node(
    self, node_id: str, input_data: Any, ready_nodes: List[str], active_loops: Dict[str, Dict]
):
    """루프 시작 노드 처리"""
    node = self.dag[node_id]
    
    # 1. 루프 종료 노드 찾기
    end_node_id = self._find_loop_end_node(node_id)
    
    # 2. 최대 반복 횟수 확인
    max_iterations = node.flow_node.agent_instance.custom_parameters.get("max_iterations", 10)
    
    # 3. 루프 정보 설정
    active_loops[node_id] = {
        "iteration": 0,
        "nodes": set(),
        "end_node_id": end_node_id,
        "parent_loop_id": self._find_parent_loop(node_id, active_loops)
    }
    
    # 4. 루프 시작 노드 완료 처리
    result = self._get_node_input(node_id)
    self.node_results[node_id] = result
    node.status = NodeStatus.COMPLETED
    
    # 5. 루프 내 첫 번째 노드들을 READY로 설정
    for child_id in node.children:
        if child_id != end_node_id:
            self.dag[child_id].status = NodeStatus.READY
            ready_nodes.append(child_id)
```

**Loop 종료 노드 처리:**

```python
async def _handle_loop_end_node(
    self, node_id: str, input_data: Any, ready_nodes: List[str], active_loops: Dict[str, Dict]
):
    """루프 종료 노드 처리"""
    node = self.dag[node_id]
    
    # 1. 루프 시작 노드 찾기
    loop_start_id = self._find_loop_start_node(node_id)
    loop_info = active_loops.get(loop_start_id)
    
    # 2. 루프 반복 횟수 증가
    loop_info["iteration"] += 1
    node.loop_count = loop_info["iteration"]
    
    # 3. 루프 결과 저장
    loop_result = self._get_node_input(node_id)
    loop_info["nodes"].add(node_id)
    
    # 4. 종료 조건 평가
    exit_conditions = node.flow_node.agent_instance.custom_parameters.get("loop_exit_conditions", [])
    should_exit = rule_engine.evaluate_ruleset_with_literals(exit_conditions)
    
    # 5. 최대 반복 횟수 확인
    max_iterations = loop_info.get("max_iterations", 10)
    if loop_info["iteration"] >= max_iterations:
        should_exit = True
    
    if should_exit:
        # 6. 루프 종료 - 다음 노드로 진행
        node.status = NodeStatus.COMPLETED
        loop_results = [self.node_results[nid] for nid in loop_info["nodes"]]
        self.node_results[node_id] = self._process_loop_results(node, loop_results)
        
        # 자식 노드 활성화
        for child_id in node.children:
            if child_id != loop_start_id:
                self.dag[child_id].status = NodeStatus.READY
                ready_nodes.append(child_id)
        
        # 루프 정보 제거
        del active_loops[loop_start_id]
    else:
        # 7. 루프 계속 - 시작 노드로 돌아감
        # 루프 내 노드들을 PENDING으로 재설정
        for loop_node_id in loop_info["nodes"]:
            if loop_node_id != loop_start_id and loop_node_id != node_id:
                self.dag[loop_node_id].status = NodeStatus.PENDING
        
        # 시작 노드를 READY로 설정
        self.dag[loop_start_id].status = NodeStatus.READY
        ready_nodes.append(loop_start_id)
```

**Loop 결과 처리:**

```python
def _process_loop_results(self, node: DAGNode, results: List[Any]) -> Any:
    """Loop 결과 처리"""
    process_type = node.flow_node.agent_instance.custom_parameters.get(
        "loop_result_process_type", "last"
    )
    
    if process_type == "list":
        # 모든 반복 결과를 리스트로 반환
        return [result for sublist in results for result in sublist]
    elif process_type == "last":
        # 마지막 반복 결과만 반환
        return results[-1] if results else None
    else:
        return results
```

### 6.3 병합 노드 보호

**문제:** Selector에서 스킵된 브랜치의 하위 노드가 다른 경로에서도 사용되는 경우

**해결:** `_has_active_path_to_node()`로 활성 경로 확인

```python
def _has_active_path_to_node(self, node_id: str, skipped_nodes: Set[str]) -> bool:
    """노드로 가는 활성 경로가 하나라도 있으면 True"""
    node = self.dag.get(node_id)
    for parent_id in node.parents:
        if parent_id in skipped_nodes:
            continue
        # SKIPPED만 아니면 '살아있는 경로'로 인정
        return True
    return False
```

---

## 7. Rule Engine

### 7.1 Rule Engine 개요

**역할:** 조건 평가를 위한 규칙 엔진

**사용 사례:**
- Selector 노드: 어떤 브랜치를 선택할지 결정
- Loop 종료 조건: 루프를 종료할지 결정

### 7.2 지원하는 연산자

```python
class RuleEnginOperator(str, Enum):
    EXISTS = "exists"                    # 값이 존재하는지
    NOT_EXISTS = "not_exists"            # 값이 존재하지 않는지
    EQUALS = "=="                        # 같음
    NOT_EQUALS = "!="                    # 다름
    GREATER_THAN = ">"                   # 큼
    LESS_THAN = "<"                      # 작음
    GREATER_THAN_OR_EQUALS = ">="        # 크거나 같음
    LESS_THAN_OR_EQUALS = "<="           # 작거나 같음
    IN = "in"                           # 포함됨 (리스트)
    NOT_IN = "not_in"                   # 포함되지 않음
    CONTAINS = "contains"               # 포함됨 (문자열)
    NOT_CONTAINS = "not_contains"       # 포함되지 않음
    REGEX = "regex"                     # 정규식 매칭
```

### 7.3 Rule 형식

```python
# 단일 조건
{
    "var": "prev_result.status",
    "operator": "==",
    "value": "success"
}

# AND 조건
{
    "and": [
        {"var": "prev_result.status", "operator": "==", "value": "success"},
        {"var": "prev_result.count", "operator": ">", "value": "10"}
    ]
}

# OR 조건
{
    "or": [
        {"var": "prev_result.status", "operator": "==", "value": "success"},
        {"var": "prev_result.status", "operator": "==", "value": "completed"}
    ]
}

# 중첩 조건
{
    "and": [
        {"var": "prev_result.status", "operator": "==", "value": "success"},
        {
            "or": [
                {"var": "prev_result.count", "operator": ">", "value": "10"},
                {"var": "prev_result.count", "operator": "<", "value": "5"}
            ]
        }
    ]
}
```

### 7.4 Selector 규칙 예시

```python
selector_rules = [
    {
        "rule_name": "성공 케이스",
        "when": {
            "and": [
                {"var": "prev_result.status", "operator": "==", "value": "success"},
                {"var": "prev_result.count", "operator": ">", "value": "0"}
            ]
        },
        "result": {
            "next_node_id": "node_2"
        }
    },
    {
        "rule_name": "실패 케이스",
        "when": {
            "var": "prev_result.status",
            "operator": "==",
            "value": "failed"
        },
        "result": {
            "next_node_id": "node_3"
        }
    }
]
```

### 7.5 Loop 종료 조건 예시

```python
loop_exit_conditions = [
    {
        "var": "prev_result.status",
        "operator": "==",
        "value": "completed"
    },
    {
        "var": "loop_count",
        "operator": ">=",
        "value": "5"
    }
]
```

### 7.6 Rule 평가 흐름

```
1. 템플릿 변수 치환
   "{prev_result.status}" → "success"
   ↓
2. Literal 값 파싱
   "success" → "success" (문자열)
   "10" → 10 (정수)
   "True" → True (불린)
   ↓
3. 조건 평가
   - 연산자에 따라 비교 수행
   - 타입 자동 변환 지원
   ↓
4. 논리 연산
   - AND: 모든 조건이 True
   - OR: 하나라도 True
   ↓
5. 결과 반환
   - True/False
```

---

## 8. 병렬 실행 메커니즘

### 8.1 asyncio.TaskGroup 사용

**이유:**
- 여러 노드를 동시에 실행
- 하나의 노드가 실패하면 나머지 자동 취소
- 예외 그룹으로 안전한 에러 처리

**구현:**

```python
async def execute(self, input_data: Any = None, test_mode: bool = False):
    ready_nodes = start_id_list.copy()
    running_tasks = {}
    
    try:
        async with asyncio.TaskGroup() as tg:
            while ready_nodes or running_tasks:
                # 실행 가능한 노드들을 병렬로 실행
                for node_id in list(ready_nodes):
                    ready_nodes.remove(node_id)
                    node.status = NodeStatus.RUNNING
                    
                    # TaskGroup 내에서 태스크 생성
                    task = tg.create_task(self._process_node(node.flow_node))
                    running_tasks[node_id] = task
                    
                    # 태스크 완료 모니터링
                    tg.create_task(self._monitor_task(...))
                
                # 태스크 완료 대기
                if running_tasks and not ready_nodes:
                    done, _ = await asyncio.wait(
                        running_tasks.values(),
                        return_when=asyncio.FIRST_COMPLETED
                    )
    
    except* Exception as exc_group:
        # 예외 처리
        logger.error(f"워크플로우 실행 중 오류 발생: {exc_group}")
        raise
```

### 8.2 태스크 모니터링

```python
async def _monitor_task(self, task, node_id, ready_nodes, running_tasks, active_loops):
    """node 실행 태스크 모니터링 및 완료 처리"""
    try:
        # 태스크 완료 대기
        result = await task
        
        # 결과 저장 및 상태 업데이트
        self.node_results[node_id] = result
        self.dag[node_id].status = NodeStatus.COMPLETED
        
        # 자식 노드 중 실행 가능한 노드 찾기
        for child_id in self.dag[node_id].children:
            child_node = self.dag[child_id]
            if child_node.status == NodeStatus.PENDING:
                # 모든 부모 노드가 완료되었는지 확인
                all_parents_completed = all(
                    self.dag[parent_id].status == NodeStatus.COMPLETED
                    for parent_id in child_node.parents
                    if self.dag[parent_id].status != NodeStatus.SKIPPED
                )
                
                if all_parents_completed:
                    child_node.status = NodeStatus.READY
                    ready_nodes.append(child_id)
    
    except Exception as e:
        # 태스크 실패 처리
        logger.error(f"Node {node_id} execution failed: {e}")
        self.dag[node_id].status = NodeStatus.FAILED
    
    finally:
        # 실행 중인 태스크에서 제거
        if node_id in running_tasks:
            del running_tasks[node_id]
```

### 8.3 병렬 실행 예시

```
시작 시점:
- Node 1: READY
- Node 2: PENDING (Node 1 완료 대기)
- Node 3: PENDING (Node 1 완료 대기)

Node 1 실행 완료:
- Node 2: READY → 즉시 실행 시작
- Node 3: READY → 즉시 실행 시작 (Node 2와 병렬)

Node 2, 3 모두 완료:
- Node 4: READY (모든 부모 완료) → 실행 시작
```

---

## 9. 실제 사용 예시

### 9.1 워크플로우 실행

```python
# WorkflowEngine에서 서비스 실행
workflow_engine = WorkflowEngine()

result = await workflow_engine.execute_service(
    service_id="service_123",
    execution_input=ExecutionInput(query="안녕하세요"),
    input_data=None,
    test_mode=False
)

# 결과: List[PrevResult]
# [
#     PrevResult(
#         data=...,
#         node_id="node_5",
#         agent_type="LLM",
#         agent_sub_type="llm_agent_aws_bedrock"
#     )
# ]
```

### 9.2 워크플로우 구조 예시

```
Node 1: Guardrail Agent (입력 검증)
  ↓
Node 2: Retriever Agent (RAG 검색)
  ↓
Node 3: LLM Agent (응답 생성)
  ↓
Node 4: Util Agent (결과 저장)
```

### 9.3 Selector를 사용한 분기

```
Node 1: LLM Agent (SQL 생성)
  ↓
Node 2: Selector (SQL 타입 확인)
  ├─ "SELECT" → Node 3: Executor Agent (DB 조회)
  └─ "INSERT/UPDATE/DELETE" → Node 4: Guardrail Agent (권한 확인)
                                ↓
                            Node 5: Executor Agent (DB 실행)
  ↓
Node 6: LLM Agent (결과 분석)
```

### 9.4 Loop를 사용한 반복

```
Node 1: Loop Start (최대 10회)
  ↓
Node 2: LLM Agent (작업 계획)
  ↓
Node 3: Executor Agent (작업 실행)
  ↓
Node 4: LLM Agent (결과 확인)
  ↓
Node 5: Loop End (종료 조건: status == "completed")
  ↓ (조건 불만족 시 Node 1로 돌아감)
Node 6: 최종 결과 처리
```

---

## 10. 핵심 설계 원칙

### 10.1 DAG 기반 실행

- **비순환 그래프**: 순환 참조 방지
- **의존성 관리**: 부모 노드 완료 후 자식 노드 실행
- **병렬 실행**: 의존성이 없는 노드는 동시 실행

### 10.2 상태 기반 스케줄링

- **명확한 상태 전이**: PENDING → READY → RUNNING → COMPLETED
- **상태 기반 판단**: 상태로 실행 가능 여부 결정
- **에러 처리**: FAILED 상태로 에러 추적

### 10.3 확장성

- **노드 타입 확장**: 새로운 노드 타입 추가 용이
- **Rule Engine**: 다양한 조건 평가 지원
- **중첩 워크플로우**: Service 노드로 다른 워크플로우 실행

### 10.4 안전성

- **TaskGroup**: 하나의 노드 실패 시 안전한 취소
- **병합 노드 보호**: 스킵된 브랜치의 하위 노드 보호
- **최대 반복 횟수**: Loop 무한 반복 방지

---

## 11. 요약

### Workflow 시스템의 핵심

- ✅ **DAG 기반 실행**: Directed Acyclic Graph로 워크플로우 구조 표현
- ✅ **상태 기반 스케줄링**: 노드 상태에 따라 실행 순서 결정
- ✅ **병렬 실행**: asyncio.TaskGroup으로 의존성 없는 노드 동시 실행
- ✅ **Loop/Selector 지원**: 반복 실행 및 조건부 분기

### 주요 컴포넌트

- ✅ **WorkflowEngine**: 서비스 워크플로우 실행 진입점
- ✅ **WorkflowExecutor**: DAG 기반 실행 엔진
- ✅ **RuleEngine**: 조건 평가 엔진
- ✅ **DAGNode**: 노드 상태 및 관계 관리

### 노드 타입

- ✅ **일반 노드**: Agent/Service 실행
- ✅ **Selector 노드**: 조건에 따른 분기
- ✅ **Loop 노드**: 조건에 따른 반복 실행

### 핵심 특징

- ✅ 상태 기반 스케줄링
- ✅ 병렬 실행 지원
- ✅ 안전한 에러 처리
- ✅ 확장 가능한 아키텍처

