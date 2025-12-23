# AgenticWorks Open Framework 핵심 기술 분석

## 📋 목차

1. [워크플로우 DAG 구성](#1-워크플로우-dag-구성)
2. [Core 기술](#2-core-기술)
3. [아키텍처 구성](#3-아키텍처-구성)

---

## 1. 워크플로우 DAG 구성

### 1.1 DAG (Directed Acyclic Graph) 구조

워크플로우는 **DAG 구조**를 기반으로 복잡한 AI 작업을 효율적으로 실행합니다.

#### 핵심 개념

```python
@dataclass
class DAGNode:
    id: str                          # 노드 고유 ID
    flow_node: WorkflowNode          # 워크플로우 노드 정보
    children: Set[str]               # 자식 노드 ID 집합
    parents: Set[str]                # 부모 노드 ID 집합
    status: NodeStatus               # 노드 상태
    is_loop_start: bool              # 루프 시작 노드 여부
    is_loop_end: bool                # 루프 종료 노드 여부
    loop_count: int                  # 루프 반복 횟수
    loop_results: List[Any]          # 루프 결과 목록
    execution_count: int              # 실행 횟수
```

#### 노드 상태 관리

```python
class NodeStatus(Enum):
    PENDING = "pending"      # 초기 상태, 실행 조건 미충족
    READY = "ready"          # 실행 준비 완료, 모든 부모 노드 완료
    RUNNING = "running"      # 현재 실행 중
    COMPLETED = "completed"  # 실행 완료
    SKIPPED = "skipped"      # 조건부 실행에서 제외됨 (Selector)
    FAILED = "failed"        # 실행 중 에러 발생
```

**상태 전이 흐름:**
```
PENDING → READY → RUNNING → COMPLETED
                ↓
            FAILED
```

### 1.2 DAG 구성 알고리즘

#### 1단계: 노드 생성 및 관계 설정

```python
def _build_dag(self, flow_nodes: List[WorkflowNode]):
    # 1. 모든 노드를 DAGNode로 변환
    for node in flow_nodes:
        self.dag[str(node.id)] = DAGNode(id=str(node.id), flow_node=node)
    
    # 2. 부모-자식 관계 설정 (next_flow_node_id 기반)
    for node in flow_nodes:
        if node.next_flow_node_id:
            node_id = str(node.id)
            next_ids = [nid.strip() for nid in str(node.next_flow_node_id).split(",")]
            
            for next_id in next_ids:
                self.dag[node_id].children.add(next_id)      # 자식 추가
                self.dag[next_id].parents.add(node_id)        # 부모 추가
```

#### 2단계: 시작 노드 찾기

```python
def get_start_nodes(self) -> list[str]:
    """부모가 없는 노드들을 시작 노드로 설정"""
    start_nodes = []
    for node_id, node in self.dag.items():
        # 부모가 없는 노드 = 시작 노드
        if not node.parents:
            start_nodes.append(node_id)
    return start_nodes
```

### 1.3 비동기 병렬 실행 메커니즘

#### TaskGroup을 활용한 병렬 처리

```python
async def execute(self, input_data: Any = None, test_mode: bool = False):
    ready_nodes = start_id_list.copy()      # 실행 준비된 노드 목록
    running_tasks = {}                      # 실행 중인 태스크 {node_id: task}
    active_loops = {}                       # 활성화된 루프 정보
    
    async with asyncio.TaskGroup() as tg:
        while ready_nodes or running_tasks:
            # 실행 가능한 노드들을 병렬로 실행
            for node_id in list(ready_nodes):
                ready_nodes.remove(node_id)
                node.status = NodeStatus.RUNNING
                
                # 비동기 태스크 생성
                task = tg.create_task(self._process_node(node.flow_node))
                running_tasks[node_id] = task
                
                # 태스크 완료 모니터링
                tg.create_task(self._monitor_task(task, node_id, ready_nodes, running_tasks, active_loops))
            
            # 실행 중인 태스크가 있고 실행 가능한 노드가 없으면 태스크 완료 대기
            if running_tasks and not ready_nodes:
                done, _ = await asyncio.wait(
                    running_tasks.values(), 
                    return_when=asyncio.FIRST_COMPLETED
                )
```

**핵심 특징:**
- **병렬 실행**: 의존성이 없는 노드들은 동시에 실행
- **의존성 관리**: 부모 노드 완료 후 자식 노드 자동 실행
- **비동기 처리**: `asyncio.TaskGroup`으로 안전한 병렬 처리

### 1.4 노드 타입별 처리

#### 1. Agent 노드
```python
if node.agent_instance:
    agent_instance = get_agent_registry().get_agent(agent_instance.agent.agent_sub_type)
    result = await agent_instance.invoke(
        agent_instance_info=agent_setting_info,
        user_input=self.user_input,
        prev_results=input_data,
    )
```

#### 2. Service 노드 (중첩 워크플로우)
```python
elif node.service_id:
    # 다른 서비스의 워크플로우 실행 (순환 참조 방지)
    service_result = await self.workflow_engine.execute_service(
        node.service_id, execution_input, input_data
    )
```

#### 3. Selector 노드 (조건부 분기)
```python
async def _process_selector_node(self, node_id: str, input_data: Any):
    # Selector 노드 실행하여 선택된 브랜치 조회
    selected_nodes = await self._process_node(node.flow_node)
    
    # 선택되지 않은 브랜치는 SKIPPED 상태로 설정
    for child_id in node.children:
        if child_id in selected_nodes:
            self.dag[child_id].status = NodeStatus.READY
        else:
            self.dag[child_id].status = NodeStatus.SKIPPED
            self._skip_branch(child_id)  # 하위 브랜치도 스킵
```

#### 4. Loop 노드 (반복 실행)

**Loop Start 처리:**
```python
async def _handle_loop_start_node(self, node_id: str, ...):
    # 루프 정보 설정
    active_loops[node_id] = {
        "iteration": 1,
        "nodes": set(),
        "end_node_id": end_node_id,
        "max_iterations": max_iterations,
    }
    
    # 루프 내 노드들을 실행 준비 상태로 설정
    for child_id in node.children:
        self.dag[child_id].status = NodeStatus.READY
        active_loops[node_id]["nodes"].add(child_id)
```

**Loop End 처리:**
```python
async def _handle_loop_end_node(self, node_id: str, ...):
    # 루프 종료 조건 평가
    should_exit_loop = False
    
    # 1. 최대 반복 횟수 확인
    if active_loops[start_node_id]["iteration"] >= max_iterations:
        should_exit_loop = True
    
    # 2. 조건부 종료 규칙 평가 (Rule Engine 사용)
    elif loop_exit_conditions:
        rule_result = rule_engine.evaluate_ruleset_with_literals(formatted_conditions)
        if rule_result and rule_result.get("result") == True:
            should_exit_loop = True
    
    if should_exit_loop:
        # 루프 완료, 다음 노드 실행 준비
        node.status = NodeStatus.COMPLETED
        del active_loops[start_node_id]
    else:
        # 다음 반복을 위해 시작 노드 재실행
        start_node.status = NodeStatus.READY
        ready_nodes.append(start_node_id)
```

### 1.5 데이터 흐름 관리

#### PrevResult 패턴

```python
@dataclass
class PrevResult:
    data: Any                    # 실제 데이터
    node_id: str                 # 이전 노드 ID
    agent_type: str              # 에이전트 타입
    agent_sub_type: str          # 에이전트 서브 타입
```

**데이터 전달 흐름:**
```
Node A (완료) → PrevResult 생성
    ↓
Node B (입력) → List[PrevResult] 형태로 부모 노드 결과 수집
    ↓
Node B (실행) → AgentConnector를 통해 파라미터 추출
```

#### Node Result Store 패턴

특수 노드 `util_agent_flow_node_results_store`는 실행된 모든 노드의 결과를 수집하여 전달합니다.

```python
# 'Node Result Store' agent를 부모로 갖고 있으면
# 모든 실행된 노드의 결과를 부모 노드로 설정
if self.dag[parent_id].flow_node.agent_instance.agent.agent_sub_type == "util_agent_flow_node_results_store":
    executed_node_keys = list(self.node_results.keys())
    node_parents = executed_node_keys  # 모든 실행된 노드를 부모로 설정
```

---

## 2. Core 기술

### 2.1 Transaction Context (컨텍스트 관리)

#### ContextVar 기반 비동기 컨텍스트 관리

```python
import contextvars

# Context 변수 정의
transaction_context = contextvars.ContextVar("transaction_context")

class TransactionContext(BaseModel):
    request_id: str = None
    access_token: str = None
    user: UserSession = None
    sse_queue: Any = None              # SSE 스트리밍용 Queue
    async_db_session: Any = None       # DB 세션
    service_id: str = None
    channel_id: str = None
    request_message_id: str = None
    request_time: int = 0
    trace_id: str = None
    client_ip: str = None
```

**핵심 특징:**
- **비동기 안전**: `contextvars`를 사용하여 비동기 환경에서도 컨텍스트 격리
- **요청별 격리**: 각 HTTP 요청마다 독립적인 컨텍스트 유지
- **전역 접근**: 어디서든 `transaction_context_manager`로 컨텍스트 접근 가능

**사용 예시:**
```python
# 컨텍스트에 데이터 추가
transaction_context_manager.add_to_transaction_context("user", user_info)

# 컨텍스트에서 데이터 조회
user = transaction_context_manager.get_user_session()
request_id = transaction_context_manager.get_transaction_request_id()
```

### 2.2 Stream Manager (실시간 스트리밍)

#### SSE (Server-Sent Events) 기반 스트리밍

```python
class StreamManager:
    _message_stream_count: Dict[str, int] = {}              # message_id → stream_count
    _response_data_by_request_id: Dict[str, dict] = {}       # request_id → response data
    
    @staticmethod
    def init():
        # 요청별로 독립적인 Queue 생성
        transaction_context_manager.add_to_transaction_context("sse_queue", asyncio.Queue())
```

#### 이벤트 타입

```python
class StreamEvent(Enum):
    CONNECTED = "connected"           # 연결 성공
    INIT = "init"                     # 초기화
    DATA = "data"                     # 데이터 전송
    STATUS = "status"                 # 상태 변경
    NODE_STARTED = "node_started"     # 노드 실행 시작
    NODE_ENDED = "node_ended"         # 노드 실행 종료
    ERROR = "error"                   # 에러 발생
    CLOSE = "close"                   # 연결 종료
```

#### 스트리밍 메커니즘

```python
# 1. 메시지 전송 (Queue에 추가)
async def send_stream_message(message: str, message_id: str, done: bool = False):
    queue = StreamManager._asyncio_queue()
    await queue.put({
        "event": StreamEvent.DATA.value,
        "data": {
            "message_id": message_id,
            "token": message,
            "done": done,
        }
    })

# 2. 이벤트 생성기 (Queue에서 메시지 꺼내서 전송)
async def event_generator(request: Request):
    yield {"event": StreamEvent.CONNECTED.value, "data": {...}}
    
    while True:
        message = await _get_message_from_queue(asyncio_queue, timeout=1)
        if message:
            yield message
        await asyncio.sleep(0)
```

**핵심 특징:**
- **비동기 Queue**: `asyncio.Queue`를 통한 비동기 메시지 전달
- **실시간 응답**: LLM 스트리밍 응답을 실시간으로 전송
- **메트릭 수집**: 첫 응답 시간, 최종 응답 시간 자동 측정

### 2.3 Trace Manager (분산 추적)

#### OpenTelemetry 기반 추적

```python
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.trace import TracerProvider

def initialize_trace():
    # Phoenix (OpenInference) 연동
    otlp_exporter = OTLPSpanExporter(endpoint=PHOENIX_ENDPOINT)
    trace.set_tracer_provider(TracerProvider(resource=resource))
    trace.get_tracer_provider().add_span_processor(BatchSpanProcessor(otlp_exporter))
    
    # AI 라이브러리 계측
    OpenAIInstrumentor().instrument()
    BedrockInstrumentor().instrument()
    
    return trace.get_tracer("ai-agent")
```

#### OpenInference 표준 준수

```python
def set_openinference_attributes(span, openinference_kind: str, ...):
    """Phoenix에서 인식 가능한 표준 속성 설정"""
    span.set_attribute("openinference.span.kind", openinference_kind)
    
    if openinference_kind == "LLM":
        span.set_attribute("llm.invocation_parameters", ...)
        span.set_attribute("llm.output_messages.0.message.content", ...)
    
    elif openinference_kind == "RETRIEVER":
        span.set_attribute("retrieval.query.text", ...)
        span.set_attribute("retrieval.documents.0.document.content", ...)
```

**핵심 특징:**
- **분산 추적**: 요청별 trace_id로 전체 실행 경로 추적
- **Phoenix 연동**: OpenInference 표준으로 LLM 실행 추적
- **자동 계측**: OpenAI, Bedrock 등 AI 라이브러리 자동 계측

### 2.4 Process Log (비동기 로깅)

#### Redis Stream 기반 비동기 로깅

```python
class ProcessLog:
    async def save_service_usage_data(self, serviceUsage: ServiceUsageHistoryCreateIn):
        """실시간 처리 중 Redis Stream에 로그 저장"""
        data = self._convert_service_usage_model_to_dict(serviceUsage)
        await self.redis.xadd(SERVICE_USAGE_LOG, data)  # Redis Stream에 추가
    
    def start_consumer(self):
        """별도 스레드에서 Redis Stream 소비"""
        self._consumer_thread = threading.Thread(
            target=self._run_consumer_thread, 
            daemon=True
        )
        self._consumer_thread.start()
```

#### Consumer 패턴

```python
async def _consumer_loop(self):
    """Redis Stream에서 메시지를 읽어 DB에 저장"""
    # Consumer Group 생성
    await redis.xgroup_create(SERVICE_USAGE_LOG, WORKER_GROUP_NAME, id="0", mkstream=True)
    
    while not self.STOP_EVENT.is_set():
        # 2초 블로킹하여 메시지 읽기
        messages = await redis.xreadgroup(
            groupname=WORKER_GROUP_NAME,
            consumername=consumer_name,
            streams={SERVICE_USAGE_LOG: ">"},
            count=100,
            block=2000,
        )
        
        # 벌크로 DB에 저장
        if messages:
            insert_data = []
            for stream, logs in messages:
                for message_id, data in logs:
                    insert_data.append(ServiceUsageHistory(**data))
            
            # 벌크 저장
            await common_history_repository.bulk_create_service_usage_log(insert_data)
            
            # ACK 처리
            await redis.xack(SERVICE_USAGE_LOG, WORKER_GROUP_NAME, *ack_ids)
```

**핵심 특징:**
- **비동기 처리**: 메인 스레드와 분리된 스레드에서 로그 처리
- **성능 최적화**: Redis Stream으로 버퍼링 후 벌크 저장
- **안정성**: Consumer Group으로 메시지 손실 방지

### 2.5 Rule Engine (규칙 엔진)

#### 동적 조건 평가

```python
class RuleEngine:
    def evaluate_ruleset_with_literals(
        self, 
        ruleset: List[Dict[str, Any]], 
        match_all: bool = False
    ):
        """템플릿 변수가 치환된 규칙 집합 평가"""
        for rule in ruleset:
            if self._evaluate_rule_with_literal(rule["when"]):
                return {
                    "matched_rule": rule.get("rule_name"),
                    "result": rule["result"]
                }
```

#### 지원 연산자

```python
class RuleEnginOperator(str, Enum):
    EXISTS = "exists"
    EQUALS = "=="
    NOT_EQUALS = "!="
    GREATER_THAN = ">"
    LESS_THAN = "<"
    IN = "in"
    CONTAINS = "contains"
    REGEX = "regex"
    # ... 기타 연산자
```

#### 규칙 예시

```json
[
    {
        "rule_name": "AWS Bedrock 선택",
        "when": {
            "and": [
                { "var": "model_id", "operator": "contains", "value": "claude-3" }
            ]
        },
        "result": {
            "next_node_id": "18"
        }
    }
]
```

**핵심 특징:**
- **동적 평가**: 런타임에 규칙 평가
- **템플릿 변수 지원**: `{prev_result.key}` 형식의 변수 치환
- **타입 자동 변환**: 안전한 타입 비교 및 변환

---

## 3. 아키텍처 구성

### 3.1 계층화된 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    프론트엔드 계층                        │
│              React + TypeScript (SPA)                    │
└─────────────────────────────────────────────────────────┘
                          ↓ HTTP/SSE
┌─────────────────────────────────────────────────────────┐
│                  백엔드 서비스 계층                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Management  │  │   API App    │  │ Indexing App │  │
│  │    App      │  │              │  │              │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│              Agent Engine (Common Module)                │
│  ┌──────────────────────────────────────────────────┐   │
│  │         에이전트/실행 계층 (Agent Layer)          │   │
│  │  LLM │ Retriever │ Orchestrator │ Executor │ ... │   │
│  └──────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────┐   │
│  │      플랫폼 기반 계층 (Platform Layer)            │   │
│  │  auth │ config │ database │ exception │ logging │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    외부 서비스 계층                       │
│  OpenAI │ AWS Bedrock │ Azure OpenAI │ GCP Vertex AI   │
│  OpenSearch │ Azure AI Search │ S3 │ Blob Storage     │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  데이터 저장소 계층                       │
│            PostgreSQL │ Redis (Elasticache)             │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Agent Registry 패턴

#### 자동 에이전트 발견 및 등록

```python
class AgentRegistry:
    _instance = None  # 싱글톤 패턴
    
    def _load_agent_classes(self):
        """파일 시스템을 스캔하여 Agent 클래스 자동 로드"""
        agent_map = {}
        base_path = Path(__file__).parent
        
        # agent/ 디렉토리 하위의 모든 .py 파일 스캔
        for py_file in base_path.rglob("*.py"):
            if "__" in str(py_file) or "schema" in str(py_file):
                continue
            
            module = importlib.import_module(module_path)
            
            # BaseAgent를 상속받은 클래스 찾기
            for name, cls in inspect.getmembers(module, inspect.isclass):
                if issubclass(cls, BaseAgent) and cls != BaseAgent:
                    if hasattr(cls, "agent_unique_name"):
                        agent_name = cls.agent_unique_name
                        agent_map[agent_name] = cls()  # 인스턴스 생성
                        logger.info(f"Added Agent: {agent_name}")
        
        return agent_map
```

**핵심 특징:**
- **자동 발견**: 파일 시스템 스캔으로 에이전트 자동 등록
- **싱글톤 패턴**: 애플리케이션 전체에서 단일 인스턴스 사용
- **타입 안정성**: Pydantic 모델로 입력/출력 검증

### 3.3 BaseAgent 추상화

#### 공통 실행 흐름

```python
class BaseAgent(ABC):
    agent_unique_name: ClassVar[str]                    # 고유 이름
    input_pydantic_model: ClassVar[Type[BaseModel]]     # 입력 스키마
    output_pydantic_model: ClassVar[Type[BaseModel]]    # 출력 스키마
    
    async def invoke(
        self,
        agent_instance_info: Dict[str, Any],
        user_input: Dict[str, Any],
        prev_results: Optional[List[PrevResult]] = None,
    ):
        # 1. AgentConnector로 파라미터 추출
        agent_connector = AgentConnector(prev_results, user_input, config)
        combined_input_parameters = agent_connector.create_agent_input_parameters_dict(...)
        
        # 2. 공통 전처리
        agent_execution_parameters = await self.common_preprocess_input_parameters_dict(...)
        
        # 3. 커스텀 전처리 (각 Agent별 구현)
        agent_parameters = await self.custom_preprocess_input_parameters(...)
        
        # 4. Agent 실행 (각 Agent별 구현)
        result = await self._execute_agent(agent_parameters)
        
        # 5. 출력 검증
        if not isinstance(result, self.output_pydantic_model):
            raise TypeError("Output pydantic model type error")
        
        return result
```

**핵심 특징:**
- **템플릿 변수 해결**: `{prev_result.key}` 형식의 변수 자동 치환
- **타입 검증**: Pydantic으로 입력/출력 자동 검증
- **추적 지원**: OpenTelemetry로 자동 추적

### 3.4 의존성 주입 및 데코레이터 패턴

#### 트랜잭션 데코레이터

```python
@transactional(db_type="read")
async def get_service(service_id: str):
    # 자동으로 DB 세션 관리
    # 트랜잭션 자동 커밋/롤백
    pass
```

#### 추적 데코레이터

```python
@agent_tracer("agent.{agent_instance_info[agent_sub_type]}")
async def invoke(self, agent_instance_info: Dict[str, Any], ...):
    # 자동으로 OpenTelemetry span 생성
    # Phoenix에 추적 정보 전송
    pass
```

### 3.5 데이터 흐름 아키텍처

```
사용자 요청
    ↓
Router (FastAPI)
    ↓
Service (비즈니스 로직)
    ↓
Workflow Engine
    ↓
Workflow Executor (DAG 실행)
    ↓
Agent Registry → Agent Instance
    ↓
Client (AWS/Azure/GCP/OpenAI)
    ↓
외부 서비스
    ↓
응답 수집
    ↓
Stream Manager (SSE)
    ↓
프론트엔드 (실시간 스트리밍)
```

### 3.6 확장성 설계

#### 1. 플러그인 아키텍처
- 새로운 Agent 추가 시 자동 등록 (Registry 패턴)
- MCP (Model Context Protocol) 지원으로 외부 도구 통합

#### 2. 멀티 클라우드 지원
- 클라이언트 추상화로 클라우드 벤더 독립성
- AWS, Azure, GCP, OpenAI 등 통합 지원

#### 3. 모듈화
- `common/` 모듈로 공통 기능 재사용
- `indexing_app`은 독립적인 `common/` 모듈 사용 (영향도 최소화)

---

## 🎯 핵심 기술 요약

### 워크플로우 DAG
- ✅ DAG 기반 의존성 관리
- ✅ 비동기 병렬 실행 (asyncio.TaskGroup)
- ✅ 상태 기반 스케줄링
- ✅ Loop, Selector 등 고급 노드 타입 지원

### Core 기술
- ✅ ContextVar 기반 비동기 컨텍스트 관리
- ✅ SSE 기반 실시간 스트리밍
- ✅ OpenTelemetry 분산 추적
- ✅ Redis Stream 비동기 로깅
- ✅ 동적 규칙 엔진

### 아키텍처
- ✅ 계층화된 구조 (Router → Service → Repository)
- ✅ Registry 패턴으로 자동 에이전트 등록
- ✅ BaseAgent 추상화로 일관된 인터페이스
- ✅ 데코레이터 패턴으로 횡단 관심사 처리
- ✅ 멀티 클라우드 및 확장 가능한 설계

---

## 📚 참고 문서

- 워크플로우 실행: `docs/help_pages/docs/dev_guides/agent_execution/workflow_executor.md`
- 에이전트 개발: `docs/help_pages/docs/dev_guides/agent_development.md`
- 아키텍처: `docs/architecture_flow.drawio`

