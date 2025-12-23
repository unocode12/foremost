# Agent 시스템 기술 분석

## 📋 목차

1. [Agent 시스템 개요](#1-agent-시스템-개요)
2. [아키텍처 구조](#2-아키텍처-구조)
3. [핵심 컴포넌트](#3-핵심-컴포넌트)
4. [Agent 실행 흐름](#4-agent-실행-흐름)
5. [Agent 타입별 설명](#5-agent-타입별-설명)
6. [파라미터 처리 메커니즘](#6-파라미터-처리-메커니즘)
7. [템플릿 변수 처리](#7-템플릿-변수-처리)
8. [Agent Registry](#8-agent-registry)
9. [실제 사용 예시](#9-실제-사용-예시)

---

## 1. Agent 시스템 개요

### 1.1 Agent란?

**Agent**는 AI 워크플로우에서 특정 작업을 수행하는 독립적인 실행 단위입니다.

**핵심 개념:**
- **재사용 가능한 기능 단위**: 각 Agent는 특정 작업에 특화
- **표준화된 인터페이스**: 모든 Agent는 동일한 인터페이스로 실행
- **타입 안전성**: Pydantic 모델로 입력/출력 검증
- **자동 등록**: AgentRegistry를 통한 자동 발견 및 등록

### 1.2 Agent 시스템의 역할

```
┌─────────────────────────────────────────────────────────────┐
│                    워크플로우 엔진                            │
│  └─ 워크플로우 노드 실행                                      │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                  Agent Registry                              │
│  └─ agent_sub_type으로 Agent 조회                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                    BaseAgent.invoke()                        │
│  └─ 파라미터 처리 → 실행 → 결과 반환                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│              각 Agent 구현체.execute()                       │
│  - LLM Agent: LLM 호출                                       │
│  - Retriever Agent: RAG 검색                                  │
│  - Executor Agent: 도구 실행                                 │
│  - Orchestrator Agent: 작업 계획 수립                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 아키텍처 구조

### 2.1 전체 구조

```
┌─────────────────────────────────────────────────────────────┐
│                    BaseAgent (추상 클래스)                   │
│  - agent_unique_name                                         │
│  - input_pydantic_model                                      │
│  - output_pydantic_model                                     │
│  - invoke() - 공통 실행 로직                                  │
│  - execute() - 추상 메서드                                    │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ LLM Agent    │  │Retriever     │  │Executor      │
│              │  │Agent         │  │Agent         │
└──────────────┘  └──────────────┘  └──────────────┘
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Orchestrator │  │Data Agent    │  │Vision Agent  │
│ Agent        │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

### 2.2 Agent 타입 분류

```python
# be_src/common/agent/
├── llm/              # LLM Agent (LLM 호출)
├── retriever/        # Retriever Agent (RAG 검색)
├── executor/         # Executor Agent (도구 실행)
├── orchestrator/      # Orchestrator Agent (작업 계획)
├── data/             # Data Agent (데이터 처리)
├── vision/           # Vision Agent (이미지 처리)
├── guardrail/        # Guardrail Agent (안전성 검사)
└── util/             # Util Agent (유틸리티)
```

---

## 3. 핵심 컴포넌트

### 3.1 BaseAgent

**역할:** 모든 Agent의 기본 추상 클래스

**핵심 속성:**

```python
class BaseAgent(ABC):
    # Agent 구현 Class의 고유 이름
    agent_unique_name: ClassVar[str]
    
    # Agent 입력/출력 Pydantic 모델
    input_pydantic_model: ClassVar[Type[BaseModel]]
    output_pydantic_model: ClassVar[Type[BaseModel]]
    
    # 스트리밍 상태 메시지
    stream_status_message_before_agent_run: str = ""
    stream_status_message_after_agent_run: str = ""
```

**주요 메서드:**

```python
async def invoke(
    self,
    agent_instance_info: Dict[str, Any],
    user_input: Dict[str, Any],
    prev_results: Optional[List[PrevResult]] = None,
):
    """
    Agent 공통 실행 함수
    1. 파라미터 준비 (AgentConnector 사용)
    2. 공통 전처리 (common_preprocess_input_parameters_dict)
    3. 커스텀 전처리 (custom_preprocess_input_parameters)
    4. Agent 실행 (execute)
    5. 결과 반환
    """
```

**실행 흐름:**

```
invoke()
  ↓
1. AgentConnector로 파라미터 생성
  ↓
2. common_preprocess_input_parameters_dict() - 타입별 공통 처리
  ↓
3. custom_preprocess_input_parameters() - 구현체별 커스텀 처리
  ↓
4. execute() - 실제 Agent 로직 실행
  ↓
5. 결과 반환 (output_pydantic_model 타입 검증)
```

### 3.2 AgentRegistry

**역할:** Agent 클래스의 자동 등록 및 관리

**특징:**
- **싱글톤 패턴**: 애플리케이션 전체에서 하나의 인스턴스만 존재
- **자동 발견**: `common/agent` 디렉토리를 스캔하여 Agent 클래스 자동 로드
- **스키마 관리**: 각 Agent의 input/output 스키마 저장

**구현:**

```python
class AgentRegistry:
    _instance = None
    _initialized = False
    
    def __init__(self):
        if not AgentRegistry._initialized:
            self.agent_map, self.agent_execute_input_schema, self.agent_execute_output_schema = (
                self._load_agent_classes()
            )
            AgentRegistry._initialized = True
    
    def _load_agent_classes(self):
        """
        common/agent 디렉토리를 재귀적으로 스캔하여
        BaseAgent를 상속받은 모든 클래스를 로드
        """
        agent_map = {}
        agent_execute_input_schema = {}
        agent_execute_output_schema = {}
        
        for py_file in base_path.rglob("*.py"):
            # BaseAgent를 상속받은 클래스 찾기
            if issubclass(cls, BaseAgent) and cls != BaseAgent:
                agent_name = cls.agent_unique_name
                agent_map[agent_name] = cls()
                agent_execute_input_schema[agent_name] = cls.input_pydantic_model
                agent_execute_output_schema[agent_name] = cls.output_pydantic_model
        
        return agent_map, agent_execute_input_schema, agent_execute_output_schema
    
    def get_agent(self, agent_name: str) -> BaseAgent:
        """agent_unique_name으로 Agent 인스턴스 조회"""
        return self.agent_map.get(agent_name)
```

**사용 예시:**

```python
# AgentRegistry에서 Agent 조회
agent_registry = get_agent_registry()
agent = agent_registry.get_agent("llm_agent_aws_bedrock")

# Agent 실행
result = await agent.invoke(
    agent_instance_info={
        "agent_id": 1,
        "agent_type": "LLM",
        "agent_sub_type": "llm_agent_aws_bedrock",
        "agent_instance_id": 10,
        "common_parameters": {...},
        "custom_parameters": {...}
    },
    user_input={"query": "안녕하세요"},
    prev_results=None
)
```

### 3.3 AgentConnector

**역할:** Agent 입력 파라미터 처리 및 매핑

**주요 기능:**
- **다중 소스 통합**: user_input, config, prev_result에서 값 추출
- **템플릿 변수 처리**: `{prev_result.key}` 형식의 변수 해석
- **타입 변환**: 문자열을 목표 타입으로 자동 변환

**구현:**

```python
class AgentConnector:
    def __init__(
        self,
        prev_results: Optional[List[PrevResult]] = None,
        user_input: Dict[str, Any] = None,
        config: Dict[str, Any] = None,
    ):
        self.prev_results = prev_results
        self.user_input = user_input or {}
        self.config = config or {}
        self.prev_result_dict: Dict[str, Any] = {}
        self._build_prev_result_dict()
    
    def create_agent_input_parameters_dict(
        self,
        agent_input_pydantic_model: Type,
    ) -> Dict[str, Dict[str, Any]]:
        """
        Agent Input Pydantic Model의 Field 메타데이터를 기반으로
        user_input, config, prev_result에서 값을 추출하여 매핑
        """
        # 1. Pydantic Model에서 파라미터 정의 추출
        parameters_setting_definition = extract_custom_common_parameters_to_json_schema(
            agent_input_pydantic_model
        )
        
        # 2. 각 파라미터별로 input_source에 따라 값 추출
        for param_definition in parameters_definition:
            input_source = param_definition["input_source"]
            
            if input_source == "prev_result":
                value = extract_value_from_prev_result_list(self.prev_results, param_name)
            elif input_source == "user":
                value = self.user_input.get(param_name)
            elif input_source == "config":
                value = self.config[parameter_group_name].get(param_name)
            
            # 3. 템플릿 변수 처리
            if config_value에 "{prev_result.key}" 형식이 있으면:
                value = resolve_template_variables(config_value, ...)
        
        return result
```

**파라미터 소스 우선순위:**

1. **Default 값**: Pydantic Model에 정의된 기본값
2. **명시적 값**: input_source에 명시된 소스에서 값 추출
3. **템플릿 변수**: Config 값에 템플릿 변수가 있으면 해석

### 3.4 Template Resolver

**역할:** 템플릿 변수를 실제 데이터로 변환

**템플릿 변수 형식:**

```python
# 지원하는 템플릿 변수
"{prev_result.query}"           # 이전 결과에서 query 추출
"{prev_result.data.sql_result}" # 중첩된 키 경로 지원
"{user.email}"                  # 사용자 입력에서 email 추출
"{system.current_time}"         # 시스템 정보
```

**구현:**

```python
def resolve_template_variables(
    template: str,
    prev_results: Optional[List[PrevResult]] = None,
    user_input: Dict[str, Any] = None,
) -> str:
    """
    템플릿 문자열의 변수를 실제 값으로 치환
    
    예: "SELECT * FROM {prev_result.table_name}"
        → "SELECT * FROM users"
    """
    # 정규식으로 {prefix.key} 패턴 찾기
    pattern = r"\{(\w+)\.([\w.\[\]]+)\}"
    
    def replace_var(match):
        prefix = match.group(1)  # prev_result, user, system
        key_path = match.group(2)  # query, data.sql_result 등
        
        if prefix == "prev_result":
            value = extract_value_from_prev_result_list(prev_results, key_path)
        elif prefix == "user":
            value = _get_nested_value_from_any_data(user_input, key_path)
        elif prefix == "system":
            value = _get_system_value(key_path)
        
        return str(value) if value is not None else match.group(0)
    
    return re.sub(pattern, replace_var, template)
```

### 3.5 Result Data Extractor

**역할:** 이전 결과에서 필요한 데이터 추출

**기능:**
- **중첩 키 경로 지원**: `data.sql_result` 형식 지원
- **다양한 데이터 타입**: Dict, List, CustomList 등 처리
- **자동 타입 변환**: BaseModel을 dict로 자동 변환

**구현:**

```python
def extract_value_from_prev_result_list(
    prev_results: Optional[List["PrevResult"]], 
    key_path: str
) -> Any:
    """
    List[PrevResult]에서 지정된 키 경로의 값을 순차적으로 찾아서 반환
    
    Args:
        prev_results: 이전 결과 리스트
        key_path: 찾을 키 경로 (예: "query", "data.sql_result")
    
    Returns:
        찾은 값 또는 None
    """
    for prev_result_item in prev_results:
        value = _get_nested_value_from_any_data(prev_result_item.data, key_path)
        if value is not None:
            return value
    return None
```

---

## 4. Agent 실행 흐름

### 4.1 전체 실행 흐름

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 워크플로우 노드에서 Agent 호출                             │
│    agent_instance_info, user_input, prev_results 전달        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. AgentRegistry에서 Agent 조회                              │
│    agent = agent_registry.get_agent(agent_sub_type)          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. BaseAgent.invoke() 호출                                    │
│    - 파라미터 준비 시작                                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. AgentConnector로 파라미터 생성                              │
│    agent_connector = AgentConnector(prev_results, user_input, config)│
│    combined_input_parameters = agent_connector              │
│        .create_agent_input_parameters_dict(input_pydantic_model)│
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 공통 전처리 (타입별)                                        │
│    agent_execution_parameters = await                       │
│        self.common_preprocess_input_parameters_dict(...)     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. 커스텀 전처리 (구현체별)                                    │
│    agent_parameters_pydantic_instance = await               │
│        self.custom_preprocess_input_parameters(...)          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. Agent 실행                                                 │
│    result = await self.execute(agent_parameters_pydantic_instance)│
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 8. 결과 타입 검증 및 반환                                       │
│    if not isinstance(result, output_pydantic_model):         │
│        raise TypeError(...)                                   │
│    return result                                             │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 파라미터 처리 상세 흐름

```
┌─────────────────────────────────────────────────────────────┐
│ AgentConnector.create_agent_input_parameters_dict()         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 1. Pydantic Model에서 파라미터 정의 추출                      │
│    - Field 메타데이터에서 input_source, default 등 추출      │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. 각 파라미터별 값 추출                                       │
│    for param in parameters:                                  │
│        if input_source == "prev_result":                     │
│            value = extract_from_prev_result(param_name)     │
│        elif input_source == "user":                          │
│            value = user_input.get(param_name)                │
│        elif input_source == "config":                        │
│            value = config.get(param_name)                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 템플릿 변수 처리                                            │
│    if config_value에 "{prev_result.key}" 있으면:            │
│        value = resolve_template_variables(config_value, ...) │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 타입 변환                                                  │
│    value = _convert_to_target_type(value, param_type)        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 결과 반환                                                   │
│    return {                                                  │
│        "common_parameters": {...},                           │
│        "custom_parameters": {...}                            │
│    }                                                         │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. Agent 타입별 설명

### 5.1 LLM Agent

**역할:** LLM(Large Language Model) 호출 및 응답 생성

**구현체:**
- `llm_agent_aws_bedrock`: AWS Bedrock 호출
- `llm_agent_azure_openai`: Azure OpenAI 호출
- `llm_agent_openai`: OpenAI 호출
- `llm_agent_gcp_vertex`: GCP Vertex AI 호출
- `llm_agent_aws_bedrock_text_to_sql`: Text-to-SQL 변환

**특징:**
- 프롬프트 템플릿 처리
- 스트리밍 응답 지원
- 다양한 LLM 프로바이더 지원

### 5.2 Retriever Agent

**역할:** RAG(Retrieval-Augmented Generation) 검색

**기능:**
- 벡터 스토어 검색
- 관련 문서 검색
- 컨텍스트 생성

### 5.3 Executor Agent

**역할:** 도구 실행 (MCP Tool, 내부 서비스 등)

**구현체:**
- `executor_agent_mcp_tool`: MCP 도구 실행
- `executor_agent_internal_service`: 내부 서비스 실행

**특징:**
- 이전 결과를 도구 입력으로 전달
- 스트리밍 응답 지원

### 5.4 Orchestrator Agent

**역할:** 작업 계획 수립 및 여러 도구 순차 실행

**구현체:**
- `orchestrator_agent_mcp_executor`: MCP 도구를 활용한 작업 계획
- `orchestrator_agent_ai_assistant`: AI 어시스턴트 방식

**특징:**
- LLM을 활용한 작업 계획 수립
- 여러 도구를 순차적으로 실행
- 이전 작업 결과를 다음 작업에 전달

### 5.5 Data Agent

**역할:** 데이터 처리 및 변환

**기능:**
- 데이터 추출
- 데이터 변환
- 데이터 검증

### 5.6 Vision Agent

**역할:** 이미지 처리 및 분석

**기능:**
- 이미지 분석
- OCR (Optical Character Recognition)
- 이미지 생성

### 5.7 Guardrail Agent

**역할:** 안전성 검사 및 필터링

**기능:**
- 입력 검증
- 출력 필터링
- 안전성 체크

### 5.8 Util Agent

**역할:** 유틸리티 기능 제공

**기능:**
- 데이터 저장
- 결과 전달
- 헬퍼 함수

---

## 6. 파라미터 처리 메커니즘

### 6.1 AgentField

**역할:** Pydantic Field의 확장으로 Agent 파라미터 메타데이터 정의

**사용 예시:**

```python
from common.agent.agent_base import AgentField, ParameterInputSource

class LLMAgentIn(AgentBase):
    query: str = AgentField(
        title="질의",
        description="사용자 질의",
        input_source=ParameterInputSource.USER,  # user_input에서 추출
        is_required=True
    )
    
    model_id: str = AgentField(
        title="모델 ID",
        description="사용할 LLM 모델",
        input_source=ParameterInputSource.CONFIG,  # config에서 추출
        is_required=True
    )
    
    previous_context: str = AgentField(
        title="이전 컨텍스트",
        description="이전 대화 컨텍스트",
        input_source=ParameterInputSource.PREV_RESULT,  # prev_result에서 추출
        is_required=False
    )
```

### 6.2 ParameterInputSource

**입력 소스 타입:**

```python
class ParameterInputSource(str, Enum):
    CONFIG = "config"        # 관리자가 세팅하는 값
    USER = "user"            # 사용자가 입력하는 값
    PREV_RESULT = "prev_result"  # 이전 단계에서 전달되는 값
    ETC = "etc"
```

### 6.3 파라미터 우선순위

1. **Default 값**: Pydantic Model에 정의된 기본값
2. **명시적 값**: input_source에 명시된 소스에서 값 추출
   - `prev_result` → `extract_value_from_prev_result_list()`
   - `user` → `user_input.get(key)`
   - `config` → `config[group][key]`
3. **템플릿 변수**: Config 값에 템플릿 변수가 있으면 해석

---

## 7. 템플릿 변수 처리

### 7.1 템플릿 변수 형식

```python
# 기본 형식
"{prefix.key}"

# 지원하는 prefix
- prev_result: 이전 Agent 실행 결과
- user: 사용자 입력
- system: 시스템 정보

# 중첩 키 경로 지원
"{prev_result.data.sql_result}"
"{prev_result.query.param}"
"{user.profile.email}"
```

### 7.2 템플릿 변수 해석 예시

```python
# Config에 정의된 값
instruction = "다음 SQL을 실행하세요: {prev_result.sql_query}"

# prev_result에 있는 값
prev_result = {
    "sql_query": "SELECT * FROM users WHERE age > 25"
}

# 해석 결과
resolved_instruction = "다음 SQL을 실행하세요: SELECT * FROM users WHERE age > 25"
```

### 7.3 템플릿 변수 처리 흐름

```
1. Config 값에서 "{prefix.key}" 패턴 찾기
   ↓
2. prefix에 따라 데이터 소스 선택
   - prev_result → extract_value_from_prev_result_list()
   - user → _get_nested_value_from_any_data(user_input, key)
   - system → _get_system_value(key)
   ↓
3. 키 경로를 따라 중첩된 값 추출
   - "data.sql_result" → data["sql_result"]
   - "query.param" → query["param"]
   ↓
4. 값 치환
   - "{prev_result.sql_query}" → "SELECT * FROM users"
```

---

## 8. Agent Registry

### 8.1 자동 등록 메커니즘

**등록 과정:**

```
1. 애플리케이션 시작 시 AgentRegistry 초기화
   ↓
2. common/agent 디렉토리 재귀 스캔
   ↓
3. BaseAgent를 상속받은 클래스 발견
   ↓
4. agent_unique_name으로 agent_map에 등록
   ↓
5. input_pydantic_model, output_pydantic_model 저장
```

**등록 조건:**

```python
# 등록되는 클래스 조건
1. BaseAgent를 상속받음
2. BaseAgent 자체가 아님
3. 추상 클래스가 아님
4. agent_unique_name이 정의되어 있음
5. input_pydantic_model, output_pydantic_model이 정의되어 있음
```

### 8.2 Agent 조회

```python
# AgentRegistry에서 Agent 조회
agent_registry = get_agent_registry()

# agent_unique_name으로 조회
agent = agent_registry.get_agent("llm_agent_aws_bedrock")

# Input/Output 스키마 조회
input_schema = agent_registry.get_agent_execute_input_schema("llm_agent_aws_bedrock")
output_schema = agent_registry.get_agent_execute_output_schema("llm_agent_aws_bedrock")
```

### 8.3 Agent 등록 예시

```python
# be_src/common/agent/llm/llm_agent_aws_bedrock.py

class LLMAgentAwsBedrock(LLMAgent):
    agent_unique_name = "llm_agent_aws_bedrock"  # 고유 이름
    
    input_pydantic_model = LLMAgentAwsBedrockIn  # 입력 모델
    output_pydantic_model = LLMAgentAwsBedrockOut  # 출력 모델
    
    async def execute(self, input: LLMAgentAwsBedrockIn) -> LLMAgentAwsBedrockOut:
        # Agent 실행 로직
        ...
```

---

## 9. 실제 사용 예시

### 9.1 워크플로우에서 Agent 호출

```python
# 워크플로우 노드에서 Agent 실행
agent_registry = get_agent_registry()
agent = agent_registry.get_agent(agent_sub_type)

result = await agent.invoke(
    agent_instance_info={
        "agent_id": 1,
        "agent_type": "LLM",
        "agent_sub_type": "llm_agent_aws_bedrock",
        "agent_instance_id": 10,
        "common_parameters": {
            "model_id": "anthropic.claude-3-sonnet"
        },
        "custom_parameters": {
            "temperature": 0.7,
            "max_tokens": 1000
        }
    },
    user_input={
        "query": "안녕하세요"
    },
    prev_results=[
        PrevResult(
            node_id="node_1",
            data={"context": "이전 대화 내용"}
        )
    ]
)
```

### 9.2 Agent 구현 예시

```python
from common.agent.base_agent import BaseAgent
from common.agent.llm.llm_agent import LLMAgent
from common.agent.agent_base import AgentField, ParameterInputSource

class LLMAgentAwsBedrock(LLMAgent):
    agent_unique_name = "llm_agent_aws_bedrock"
    
    input_pydantic_model = LLMAgentAwsBedrockIn
    output_pydantic_model = LLMAgentAwsBedrockOut
    
    async def common_preprocess_input_parameters_dict(
        self,
        agent_execution_parameters: Dict[str, Any],
        user_input: Any,
        settings: Any,
        prev_results: Optional[List[PrevResult]] = None,
    ) -> Dict[str, Any]:
        """LLM Agent 타입별 공통 전처리"""
        # 예: query를 instruction에 매핑
        if "query" in agent_execution_parameters["common_parameters"]:
            agent_execution_parameters["common_parameters"]["instruction"] = \
                agent_execution_parameters["common_parameters"]["query"]
        return agent_execution_parameters
    
    async def custom_preprocess_input_parameters(
        self,
        agent_execution_parameters: Dict[str, Dict[str, Any]],
        user_input: Any,
        settings: Any,
        prev_results: Optional[List[PrevResult]] = None,
    ) -> LLMAgentAwsBedrockIn:
        """AWS Bedrock 특화 전처리"""
        return LLMAgentAwsBedrockIn(**agent_execution_parameters)
    
    async def execute(self, input: LLMAgentAwsBedrockIn) -> LLMAgentAwsBedrockOut:
        """AWS Bedrock LLM 호출"""
        # AWS Bedrock 클라이언트 생성
        client = get_bedrock_client()
        
        # LLM 호출
        response = await client.invoke_model(
            modelId=input.common_parameters.model_id,
            body={
                "prompt": input.common_parameters.instruction,
                "temperature": input.custom_parameters.temperature,
                "max_tokens": input.custom_parameters.max_tokens
            }
        )
        
        return LLMAgentAwsBedrockOut(
            llm_response=response["completion"]
        )
```

### 9.3 파라미터 처리 예시

```python
# Input Pydantic Model 정의
class LLMAgentIn(AgentBase):
    query: str = AgentField(
        title="질의",
        input_source=ParameterInputSource.USER,
        is_required=True
    )
    
    model_id: str = AgentField(
        title="모델 ID",
        input_source=ParameterInputSource.CONFIG,
        is_required=True
    )
    
    context: str = AgentField(
        title="컨텍스트",
        input_source=ParameterInputSource.PREV_RESULT,
        is_required=False
    )

# 실제 호출 시
user_input = {"query": "안녕하세요"}
config = {
    "common_parameters": {"model_id": "claude-3-sonnet"},
    "custom_parameters": {}
}
prev_results = [
    PrevResult(node_id="node_1", data={"context": "이전 대화"})
]

# AgentConnector가 자동으로 매핑
# 결과:
# {
#     "common_parameters": {
#         "query": "안녕하세요",  # user_input에서
#         "model_id": "claude-3-sonnet"  # config에서
#     },
#     "custom_parameters": {
#         "context": "이전 대화"  # prev_result에서
#     }
# }
```

---

## 10. 핵심 설계 원칙

### 10.1 타입 안전성

- **Pydantic 모델**: 모든 입력/출력이 Pydantic 모델로 검증
- **타입 체크**: 실행 결과가 output_pydantic_model 타입인지 검증
- **컴파일 타임 검증**: IDE에서 타입 힌트 지원

### 10.2 확장성

- **추상 클래스**: BaseAgent로 공통 로직 제공
- **플러그인 방식**: 새로운 Agent 추가 시 자동 등록
- **타입별 공통 처리**: Agent 타입별 공통 전처리 지원

### 10.3 유연성

- **다중 소스 지원**: user_input, config, prev_result에서 값 추출
- **템플릿 변수**: 동적 값 치환 지원
- **타입 변환**: 자동 타입 변환 지원

### 10.4 추적 가능성

- **OpenTelemetry**: 모든 Agent 실행 추적
- **Span Attributes**: 파라미터, 결과를 span에 기록
- **에러 추적**: 에러 발생 시 상세 정보 기록

---

## 11. 요약

### Agent 시스템의 핵심

- ✅ **표준화된 인터페이스**: 모든 Agent가 동일한 인터페이스로 실행
- ✅ **자동 등록**: AgentRegistry를 통한 자동 발견 및 등록
- ✅ **타입 안전성**: Pydantic 모델로 입력/출력 검증
- ✅ **유연한 파라미터 처리**: 다중 소스 통합 및 템플릿 변수 지원

### 주요 컴포넌트

- ✅ **BaseAgent**: 모든 Agent의 기본 클래스
- ✅ **AgentRegistry**: Agent 자동 등록 및 관리
- ✅ **AgentConnector**: 파라미터 처리 및 매핑
- ✅ **Template Resolver**: 템플릿 변수 해석
- ✅ **Result Data Extractor**: 이전 결과에서 데이터 추출

### Agent 타입

- ✅ **LLM Agent**: LLM 호출 및 응답 생성
- ✅ **Retriever Agent**: RAG 검색
- ✅ **Executor Agent**: 도구 실행
- ✅ **Orchestrator Agent**: 작업 계획 수립
- ✅ **Data/Vision/Guardrail/Util Agent**: 특화 기능 제공

