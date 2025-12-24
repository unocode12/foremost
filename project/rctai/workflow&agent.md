# 워크플로우부터 Agent 실행까지의 전체 흐름 (소스 레벨)

## 📋 목차

1. [전체 흐름 개요](#1-전체-흐름-개요)
2. [1단계: API 진입점 - ChatService](#1단계-api-진입점---chatservice)
3. [2단계: WorkflowEngine - 워크플로우 엔진](#2단계-workflowengine---워크플로우-엔진)
4. [3단계: WorkflowExecutor - DAG 실행](#3단계-workflowexecutor---dag-실행)
5. [4단계: 노드 실행 - _process_node](#4단계-노드-실행---_process_node)
6. [5단계: Agent 실행 - BaseAgent.invoke](#5단계-agent-실행---baseagentinvoke)
7. [6단계: Agent 구현체 실행 - execute](#6단계-agent-구현체-실행---execute)
8. [전체 흐름 다이어그램](#8-전체-흐름-다이어그램)

---

## 1. 전체 흐름 개요

```
사용자 요청
    ↓
ChatService.process_chat_message()
    ↓
WorkflowEngine.execute_service()
    ↓
WorkflowExecutor 생성 및 execute()
    ↓
WorkflowExecutor._process_node()
    ↓
BaseAgent.invoke()
    ↓
Agent 구현체.execute()
    ↓
실제 LLM/도구 호출
```

---

## 1단계: API 진입점 - ChatService

### 1.1 소스 코드

```104:111:be_src/apps/management_app/services/chat_service.py
        execution_input = ExecutionInput(
            query=query,
            model_id=process_chat_message_in.model_id,
            files=processed_files,
            additional_data=process_chat_message_in.additional_data,
        )

        result = await workflow_engine.execute_service(process_chat_message_in.service_id, execution_input)
        return result
```

### 1.2 설명

**역할:**
- 사용자 요청을 받아 `ExecutionInput` 객체 생성
- `WorkflowEngine.execute_service()` 호출

**주요 처리:**
1. 채널 ID 생성/확인
2. 파일 처리 (첨부 파일이 있는 경우)
3. `ExecutionInput` 생성 (query, model_id, files, additional_data)
4. 워크플로우 엔진 호출

---

## 2단계: WorkflowEngine - 워크플로우 엔진

### 2.1 소스 코드

```24:63:be_src/common/core/workflow/workflow_engine.py
    @agent_tracer("execute_service_agents.{service_id}")
    async def execute_service(
        self, service_id: str, execution_input: ExecutionInput, input_data: Any = None, test_mode: bool = False
    ) -> Any:
        service_flow_info = await common_service_repository.get_service_workflow_by_id(service_id)
        if service_flow_info is None or service_flow_info.delete_flag == "Y":
            raise CustomException(Status.NOT_FOUND, "서비스가 존재하지 않습니다.")
        elif service_flow_info.use_flag != "Y":
            raise CustomException(Status.BUSINESS_ERROR, "서비스가 비활성화 되어 있습니다.")

        try:
            # ServiceWorkflow를 WorkflowNode 변환
            service_flow_node_list = []
            for workflow_node in service_flow_info.service_workflow:
                if workflow_node.referenced_agent_instance:
                    referenced_agent_instance = workflow_node.referenced_agent_instance
                    flow_node = WorkflowNode(
                        id=str(workflow_node.node_id),
                        agent_instance_id=referenced_agent_instance.id,
                        agent_instance=referenced_agent_instance,
                        next_flow_node_id=workflow_node.next_node_ids,
                        service_id=workflow_node.service_id,
                        service=service_flow_info,
                    )
                    service_flow_node_list.append(flow_node)
                elif workflow_node.referenced_service:
                    referenced_service = workflow_node.referenced_service
                    flow_node = WorkflowNode(
                        id=workflow_node.node_id,
                        service_id=(
                            referenced_service.id if hasattr(referenced_service, "id") else workflow_node.service_id
                        ),
                        next_flow_node_id=workflow_node.next_node_ids,
                        service=service_flow_info,
                    )
                    service_flow_node_list.append(flow_node)

            workflow_executor = WorkflowExecutor(execution_input, service_flow_node_list, self)
            result = await workflow_executor.execute(input_data=input_data, test_mode=test_mode)
            return result
```

### 2.2 설명

**역할:**
- 서비스 워크플로우 조회 및 검증
- `ServiceWorkflow`를 `WorkflowNode` 리스트로 변환
- `WorkflowExecutor` 생성 및 실행

**주요 처리:**
1. **서비스 조회**: `get_service_workflow_by_id(service_id)`로 DB에서 워크플로우 정의 조회
2. **검증**: 서비스 존재 여부, 활성화 여부 확인
3. **노드 변환**: 
   - `referenced_agent_instance`가 있으면 → Agent 노드로 변환
   - `referenced_service`가 있으면 → Service 노드로 변환
4. **WorkflowExecutor 생성**: `WorkflowExecutor(execution_input, service_flow_node_list, self)`
5. **실행**: `workflow_executor.execute()`

---

## 3단계: WorkflowExecutor - DAG 실행

### 3.1 초기화

```48:62:be_src/common/core/workflow/workflow_executor.py
class WorkflowExecutor:
    def __init__(
        self,
        user_input: ExecutionInput,
        flow_nodes: List[WorkflowNode],
        workflow_engine: Optional[WorkflowEngineInterface] = None,
    ):
        self.user_input = user_input.model_dump()
        self.workflow_engine = workflow_engine
        self.dag: Dict[str, DAGNode] = {}  # {node_id: dag_node}
        self.executor_input: Any = None
        self.node_results: Dict[str, Any] = {}  # {node_id: result_of_node}
        self._build_dag(flow_nodes)
        self._setup_loop_nodes()
        self.request_time: int = 0
```

**초기화 과정:**
1. `user_input` 저장 (dict로 변환)
2. DAG 구조 생성 (`_build_dag`)
3. Loop 노드 설정 (`_setup_loop_nodes`)

### 3.2 실행 루프

```110:196:be_src/common/core/workflow/workflow_executor.py
    @agent_tracer("workflow_execute")
    async def execute(self, input_data: Any = None, test_mode: bool = False):
        """워크플로우 실행"""
        self.executor_input = input_data
        self.request_time = transaction_context_manager.get_transaction_context().request_time
        self._skipped_accum: Set[str] = set()  # 누적 스킵 세트 초기화

        # 모든 노드 상태 초기화
        for node in self.dag.values():
            if node.flow_node.service or node.flow_node.agent_instance.agent.agent_sub_type != "util_agent_flow_node_results_store":
                node.status = NodeStatus.PENDING
            node.execution_count = 0
            node.loop_count = 0
            node.loop_results = []

        # 노드 결과 초기화
        self.node_results = {}

        # 시작 노드 설정
        start_id_list = self.get_start_nodes()
        for start_id in start_id_list:
            self.dag[start_id].status = NodeStatus.READY

        # 시작 시간 로깅
        if not test_mode:
            first_response_time = int(time.time() * 1000)
            first_response_duration = first_response_time - self.request_time
            await agent_execution_logger.save_response_duration(
                str(ULID()), first_response_duration, ProcessEventType.SERVICE_START_TIME
            )

        # 실행 준비된 노드 목록
        ready_nodes = start_id_list.copy()
        # 현재 실행 중인 태스크 목록
        running_tasks = {}  # {node_id: task}
        # 현재 활성화된 루프 목록
        active_loops = {}  # {loop_start_id: {iteration: int, nodes: set(), end_node_id: str, parent_loop_id: str}}

        try:
            async with asyncio.TaskGroup() as tg:
                while ready_nodes or running_tasks:
                    # 실행 가능한 노드 처리
                    for node_id in list(ready_nodes):
                        ready_nodes.remove(node_id)
                        node = self.dag[node_id]

                        # 이미 완료되거나 스킵된 노드는 건너뛰기 (루프 내 노드는 예외 - 진행중인 루프 내 노드는 실행된적이 있어도 재실행해야함)
                        if (
                            node.status == NodeStatus.COMPLETED or node.status == NodeStatus.SKIPPED
                        ) and not self._is_in_active_loop(node_id, active_loops):
                            continue

                        # Selector 노드 처리
                        if (
                            node.flow_node.agent_instance
                            and node.flow_node.agent_instance.custom_parameters
                            and "selector_rules" in node.flow_node.agent_instance.custom_parameters
                        ):
                            # Selector 노드 실행하여, Rule기반 선택 브랜치 노드 조회 (선택되지 않은 브랜치는 제외 처리)
                            selected_nodes = await self._process_selector_node(node_id, input_data)

                            # 선택된 노드만 다음 실행 목록에 추가
                            for selected_node in selected_nodes:
                                ready_nodes.append(selected_node)
                            continue

                        # Loop 시작 노드 처리
                        if node.is_loop_start:
                            await StreamManager.send_workflow_node_started(node_id)
                            await self._handle_loop_start_node(node_id, input_data, ready_nodes, active_loops)
                            await StreamManager.send_workflow_node_ended(node_id)
                            continue

                        # Loop 종료 노드 처리
                        if node.is_loop_end:
                            await StreamManager.send_workflow_node_started(node_id)
                            await self._handle_loop_end_node(node_id, input_data, ready_nodes, active_loops)
                            await StreamManager.send_workflow_node_ended(node_id)
                            continue

                        # 일반 노드 처리
                        node.status = NodeStatus.RUNNING
                        task = tg.create_task(self._process_node(node.flow_node))
                        running_tasks[node_id] = task

                        # 태스크 완료 콜백을 위한 모니터링 태스크 생성
                        tg.create_task(self._monitor_task(task, node_id, ready_nodes, running_tasks, active_loops))
```

### 3.3 설명

**역할:**
- DAG 기반 노드 실행 스케줄링
- 상태 기반 실행 관리 (PENDING → READY → RUNNING → COMPLETED)
- 병렬 실행 지원 (asyncio.TaskGroup)

**주요 처리:**
1. **초기화**: 모든 노드 상태를 PENDING으로 설정
2. **시작 노드 찾기**: 부모가 없는 노드를 READY로 설정
3. **실행 루프**:
   - READY 상태 노드를 찾아 실행
   - Selector/Loop 노드는 특별 처리
   - 일반 노드는 `_process_node()` 호출
4. **병렬 실행**: `asyncio.TaskGroup`으로 의존성 없는 노드 동시 실행
5. **태스크 모니터링**: `_monitor_task()`로 완료 처리 및 자식 노드 활성화

---

## 4단계: 노드 실행 - _process_node

### 4.1 소스 코드

```536:574:be_src/common/core/workflow/workflow_executor.py
    @agent_tracer("process_node.{node.id}")
    async def _process_node(self, node: WorkflowNode, input_data: Any = None) -> Any:
        if not input_data:
            input_data = self._get_node_input(str(node.id))

        if not isinstance(input_data, PrevResult) and not (
            isinstance(input_data, list) and len(input_data) > 0 and isinstance(input_data[0], PrevResult)
        ):
            # input_data가 List[PrevResult] 타입이 아니면(MCP를 통한 호출 등), PrevResult type으로 변경
            # 일반적으로 _get_node_input에서 이미 List[PrevResult] 형태로 반환되므로 이 로직은 예외적인 경우에만 실행됨
            if input_data is not None:
                input_data = [PrevResult(data=input_data)]

        try:
            # Guardrail 등에 의해 사용자 입력이 수정된 경우, 수정된 입력을 사용
            request_id = transaction_context_manager.get_transaction_request_id()
            if self.workflow_engine and self.workflow_engine.revised_user_input_dict.get(request_id, None):
                self.user_input["query"] = self.workflow_engine.revised_user_input_dict[request_id]

            if node.agent_instance:
                """실제 노드 처리"""
                agent_instance = node.agent_instance
                agent_setting_info = {
                    "agent_id": agent_instance.agent.id,
                    "agent_type": agent_instance.agent.agent_type,
                    "agent_sub_type": agent_instance.agent.agent_sub_type,
                    "agent_instance_id": agent_instance.id,
                    "common_parameters": agent_instance.common_parameters,
                    "custom_parameters": agent_instance.custom_parameters,
                }
                agent_instance = get_agent_registry().get_agent(agent_instance.agent.agent_sub_type)
                await StreamManager.send_workflow_node_started(node.id)
                flow_step_result = await agent_instance.invoke(
                    agent_instance_info=agent_setting_info,
                    user_input=self.user_input,
                    prev_results=input_data,
                )
                await StreamManager.send_workflow_node_ended(node.id)
                return flow_step_result
```

### 4.2 설명

**역할:**
- 노드 입력 데이터 준비
- Agent 노드인 경우 Agent 실행
- Service 노드인 경우 중첩 워크플로우 실행

**주요 처리:**
1. **입력 데이터 준비**:
   - `_get_node_input()`: 부모 노드 결과를 `List[PrevResult]` 형태로 가져옴
   - 타입 변환: `PrevResult`가 아니면 변환

2. **사용자 입력 수정 처리**:
   - Guardrail 등에 의해 수정된 입력이 있으면 사용

3. **Agent 노드 실행**:
   - `agent_setting_info` 구성 (agent_id, agent_type, agent_sub_type, common_parameters, custom_parameters)
   - `AgentRegistry`에서 Agent 구현체 가져오기
   - `agent_instance.invoke()` 호출

4. **Service 노드 실행**:
   - 중첩 워크플로우 실행 (`workflow_engine.execute_service()`)

---

## 5단계: Agent 실행 - BaseAgent.invoke

### 5.1 소스 코드

```48:131:be_src/common/agent/base_agent.py
    @agent_tracer("agent.{agent_instance_info[agent_sub_type]}.{agent_instance_info[agent_instance_id]}")
    async def invoke(
        self,
        agent_instance_info: Dict[str, Any],
        user_input: Dict[str, Any],
        prev_results: Optional[List[PrevResult]] = None,
    ):
        """
        Agent 공통 실행 함수
        1-1. 각  Agent Input pydantic model에 작성된 Rule에 따라
           user_input, config, prev_result에서 input 값을 추출하여
           Agent 실행을 위한 agent_input_parameters 생성 (동일 key이름으로 단순 매핑)
        1-2. Template Variables({prev_result.key}) 형식으로 작성된 값은 해당 데이터를 찾아서 변환
        2. Agent Type별 공통 사전처리(preprocess_input_parameters) 함수 실행
        3. 개별 Agent 구현체 class에 정의된 실행전 처리 함수 실행 (prepare_execution_input)
        """

        with tracer.start_as_current_span("prepare_input_parameters") as span:
            agent_input_pydantic_model = self.input_pydantic_model  # Agent 구현 Class의 input pydantic model
            config = {
                "common_parameters": agent_instance_info.get("common_parameters", {}),
                "custom_parameters": agent_instance_info.get("custom_parameters", {}),
            }

            ### Instance 실행을 위한 input parameters 생성 시작

            # AgentConnector 인스턴스 생성 (parameters 처리용)
            agent_connector = AgentConnector(prev_results, user_input, config)

            # Tracing logging
            self._set_trace_span_attributes(span, config, "step0_base.")

            # 1. user_input, prev_result, db config를 기반으로 Agent 실행을 위한 input parameters 생성
            #   1-1. input_pydantic_model에 정의된 Rule에 따라 위 3가지 input source에서 동일한 key를 가진 값들을 추출하여 매핑
            #   1-2. Template Variables({prev_result.key}) 형식으로 작성된 값은 해당 데이터를 찾아서 변환
            combined_input_parameters = agent_connector.create_agent_input_parameters_dict(agent_input_pydantic_model)

            agent_execution_parameters = {
                "agent_id": agent_instance_info["agent_id"],
                "agent_type": agent_instance_info["agent_type"],
                "agent_sub_type": agent_instance_info["agent_sub_type"],
                "agent_instance_id": agent_instance_info["agent_instance_id"],
                "common_parameters": combined_input_parameters.get("common_parameters", {}),
                "custom_parameters": combined_input_parameters.get("custom_parameters", {}),
            }

            # Tracing logging
            self._set_trace_span_attributes(span, agent_execution_parameters, "step1_paremeters_prepared.")

            # 2. 각 Agent Type별 공통적으로 사전 input parameter 처리가 필요한 로직 or rule 실행
            #    (Agent Type별 공통 입력값 처리 함수 실행)
            agent_execution_parameters = await self.common_preprocess_input_parameters_dict(
                agent_execution_parameters, user_input, agent_instance_info, prev_results
            )

            # Tracing logging
            self._set_trace_span_attributes(span, agent_execution_parameters, "step2_common_preprocessed.")

            # 3. 각 Agent SubType별(구현체) 개별적으로 사전 input parameter 처리가 필요한 로직 or rule 실행
            #    (각 Agent 구현 Class별 입력값 처리 실행)
            agent_parameters_pydantic_instance = await self.custom_preprocess_input_parameters(
                agent_execution_parameters, user_input, agent_instance_info, prev_results
            )

            # Tracing logging
            self._set_trace_span_attributes(span, agent_parameters_pydantic_instance, "step3_custom_preprocessed.")

            span.set_status(Status(StatusCode.OK))
            ### Instance 실행을 위한 input parameters 생성 끝

        if self.stream_status_message_before_agent_run:
            await StreamManager.send_progress_status(self.stream_status_message_before_agent_run)

        # Agent 구현 Class의 동작 함수 실행
        result = await self._execute_agent(agent_parameters_pydantic_instance)

        # Type Check Output Model
        if not isinstance(result, self.output_pydantic_model):
            raise TypeError(f"{self.__class__.__name__} - Output pydandic model type error: {type(result)}")

        if self.stream_status_message_after_agent_run:
            await StreamManager.send_progress_status(self.stream_status_message_after_agent_run)

        return result
```

### 5.2 설명

**역할:**
- Agent 실행을 위한 입력 파라미터 준비
- 3단계 전처리 과정 수행
- Agent 구현체의 `execute()` 호출

**주요 처리:**

#### Step 1: AgentConnector를 통한 파라미터 생성

```python
agent_connector = AgentConnector(prev_results, user_input, config)
combined_input_parameters = agent_connector.create_agent_input_parameters_dict(agent_input_pydantic_model)
```

**AgentConnector의 역할:**
- `user_input`, `prev_results`, `config`에서 값을 추출
- Pydantic 모델의 필드 정의에 따라 매핑
- Template Variables (`{prev_result.key}`) 해석

#### Step 2: Agent Type별 공통 전처리

```python
agent_execution_parameters = await self.common_preprocess_input_parameters_dict(
    agent_execution_parameters, user_input, agent_instance_info, prev_results
)
```

**예시 (LLMAgent):**
- 멀티턴 대화 히스토리 로딩
- RAG 문서 처리
- 모델 ID 선택 처리

#### Step 3: Agent 구현체별 전처리

```python
agent_parameters_pydantic_instance = await self.custom_preprocess_input_parameters(
    agent_execution_parameters, user_input, agent_instance_info, prev_results
)
```

**역할:**
- 각 Agent 구현체별 특수 처리
- Pydantic 모델 인스턴스 생성

#### Step 4: Agent 실행

```python
result = await self._execute_agent(agent_parameters_pydantic_instance)
```

```137:139:be_src/common/agent/base_agent.py
    async def _execute_agent(self, agent_parameters_pydantic_instance):
        result = await self.execute(agent_parameters_pydantic_instance)
        return result
```

---

## 6단계: Agent 구현체 실행 - execute

### 6.1 LLM Agent 예시

**LLMAgentAwsBedrock의 execute 메서드:**

```python
# be_src/common/agent/llm/llm_agent_aws_bedrock.py

async def execute(self, input: LLMAgentAwsBedrockIn) -> LLMAgentAwsBedrockOut:
    """LLM Agent 실행"""
    message_id = str(ulid.ULID())
    
    # 프롬프트 생성
    prompt = self._create_prompt(
        common_parameters=input.common_parameters,
        custom_parameters=input.custom_parameters
    )
    
    # Inference 설정
    inference_config = AwsLLMInferenceConfig(
        max_tokens=input.common_parameters.max_tokens,
        temperature=input.common_parameters.temperature,
        top_p=input.common_parameters.top_p,
        stop_sequences=input.common_parameters.stop if input.common_parameters.stop else [],
    )
    
    # AWS Bedrock Client 호출
    llm_response = await aws_bedrock_client.converse(
        is_response_stream=input.common_parameters.is_response_stream,
        is_multi_turn=input.common_parameters.is_multi_turn,
        prompt=prompt,
        message_histories=input.common_parameters.message_histories,
        inference_config=inference_config,
        query=input.common_parameters.query,
        model_id=input.custom_parameters.model_id,
        guardrail_id=input.custom_parameters.guardrail_id,
        cache_prompt=input.custom_parameters.cache_prompt,
    )
    
    # 응답 처리
    if input.common_parameters.is_response_stream:
        # 스트리밍 응답 처리
        return await self._process_stream_response(llm_response, message_id)
    else:
        # 일반 응답 처리
        return LLMAgentAwsBedrockOut(
            llm_response=llm_response,
            message_id=message_id
        )
```

### 6.2 설명

**역할:**
- 실제 LLM/도구 호출
- 응답 처리 및 반환

**주요 처리:**
1. **프롬프트 생성**: Instruction, Template Variables 처리
2. **Inference 설정**: temperature, max_tokens, top_p 등
3. **LLM Client 호출**: AWS Bedrock, OpenAI, Azure OpenAI 등
4. **응답 처리**: 스트리밍/일반 응답 처리
5. **결과 반환**: Pydantic Output 모델로 반환

---

## 7. AgentConnector 상세

### 7.1 소스 코드

```python
# be_src/common/agent/agent_connector.py

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
        """Agent 입력 파라미터 생성"""
        # Pydantic 모델의 필드 정보 추출
        model_fields = agent_input_pydantic_model.model_fields
        
        result = {"common_parameters": {}, "custom_parameters": {}}
        
        for field_name, field_info in model_fields.items():
            # 필드 메타데이터에서 input_source 확인
            field_metadata = field_info.json_schema_extra or {}
            input_source = field_metadata.get("input_source", ParameterInputSource.CONFIG)
            
            # input_source에 따라 값 추출
            if input_source == ParameterInputSource.USER_INPUT:
                value = self.user_input.get(field_name)
            elif input_source == ParameterInputSource.PREV_RESULT:
                value = self._extract_from_prev_result(field_name)
            else:  # CONFIG
                value = self._extract_from_config(field_name)
            
            # Template Variables 해석
            if isinstance(value, str) and "{" in value:
                value = resolve_template_variables(
                    value, self.prev_results, self.user_input
                )
            
            # common_parameters 또는 custom_parameters에 할당
            if field_name in ["common_parameters", "custom_parameters"]:
                result[field_name] = value
            else:
                # 필드 위치에 따라 할당
                if field_name.startswith("common_"):
                    result["common_parameters"][field_name] = value
                else:
                    result["custom_parameters"][field_name] = value
        
        return result
```

### 7.2 설명

**역할:**
- `user_input`, `prev_results`, `config`에서 값 추출
- Pydantic 모델 필드 정의에 따라 매핑
- Template Variables 해석

**처리 과정:**
1. **필드 정보 추출**: Pydantic 모델의 필드 메타데이터 확인
2. **Input Source 확인**: `input_source`에 따라 값 추출 위치 결정
   - `USER_INPUT`: `user_input`에서 추출
   - `PREV_RESULT`: `prev_results`에서 추출
   - `CONFIG`: `config`에서 추출
3. **Template Variables 해석**: `{prev_result.key}` 형식 변환
4. **결과 구성**: `common_parameters`, `custom_parameters`로 분류

---

## 8. 전체 흐름 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│ 1. ChatService.process_chat_message()                        │
│    - 사용자 요청 수신                                          │
│    - ExecutionInput 생성                                      │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. WorkflowEngine.execute_service()                         │
│    - 서비스 워크플로우 조회                                    │
│    - ServiceWorkflow → WorkflowNode 변환                      │
│    - WorkflowExecutor 생성                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. WorkflowExecutor.__init__()                               │
│    - DAG 구조 생성 (_build_dag)                              │
│    - Loop 노드 설정 (_setup_loop_nodes)                      │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. WorkflowExecutor.execute()                                │
│    - 시작 노드 찾기 (get_start_nodes)                        │
│    - 실행 루프 시작 (asyncio.TaskGroup)                       │
│    - READY 노드 실행                                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. WorkflowExecutor._process_node()                           │
│    - 노드 입력 데이터 준비 (_get_node_input)                 │
│    - Agent 노드인 경우: agent_instance.invoke() 호출         │
│    - Service 노드인 경우: workflow_engine.execute_service()  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. BaseAgent.invoke()                                        │
│    - AgentConnector로 파라미터 생성                           │
│    - common_preprocess_input_parameters_dict()               │
│    - custom_preprocess_input_parameters()                    │
│    - _execute_agent() → execute()                            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. Agent 구현체.execute()                                     │
│    - LLM Agent: 프롬프트 생성, LLM 호출                      │
│    - Retriever Agent: RAG 검색                                │
│    - Executor Agent: 도구 호출                                │
│    - Orchestrator Agent: 작업 계획 및 실행                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 8. 실제 LLM/도구 호출                                        │
│    - AWS Bedrock Client                                      │
│    - OpenAI Client                                           │
│    - MCP Client                                              │
│    - 내부 Service 호출                                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 9. 핵심 포인트

### 9.1 데이터 흐름

```
ExecutionInput (query, model_id, files, additional_data)
    ↓
WorkflowExecutor.user_input (dict)
    ↓
BaseAgent.invoke(user_input, prev_results)
    ↓
AgentConnector (파라미터 추출 및 변환)
    ↓
Agent 구현체.execute(input: Pydantic Model)
    ↓
실제 LLM/도구 호출
```

### 9.2 상태 관리

```
NodeStatus.PENDING
    ↓ (모든 부모 완료)
NodeStatus.READY
    ↓ (실행 시작)
NodeStatus.RUNNING
    ↓ (실행 완료)
NodeStatus.COMPLETED
    ↓ (자식 노드 활성화)
```

### 9.3 병렬 실행

```python
async with asyncio.TaskGroup() as tg:
    # 의존성 없는 노드들을 동시 실행
    for node_id in ready_nodes:
        task = tg.create_task(self._process_node(node.flow_node))
        running_tasks[node_id] = task
```

### 9.4 파라미터 처리 3단계

1. **AgentConnector**: 기본 파라미터 추출 및 Template Variables 해석
2. **common_preprocess_input_parameters_dict**: Agent Type별 공통 처리
3. **custom_preprocess_input_parameters**: Agent 구현체별 특수 처리

---

## 10. 요약

### 전체 흐름

1. ✅ **ChatService**: 사용자 요청 수신 및 `ExecutionInput` 생성
2. ✅ **WorkflowEngine**: 서비스 워크플로우 조회 및 `WorkflowNode` 변환
3. ✅ **WorkflowExecutor**: DAG 구조 생성 및 노드 실행 스케줄링
4. ✅ **WorkflowExecutor._process_node**: 노드 입력 준비 및 Agent 호출
5. ✅ **BaseAgent.invoke**: 파라미터 준비 및 3단계 전처리
6. ✅ **Agent 구현체.execute**: 실제 LLM/도구 호출

### 핵심 컴포넌트

- ✅ **WorkflowExecutor**: DAG 기반 실행 엔진
- ✅ **BaseAgent**: Agent 공통 실행 로직
- ✅ **AgentConnector**: 파라미터 추출 및 변환
- ✅ **Agent 구현체**: 실제 LLM/도구 호출

### 특징

- ✅ **상태 기반 스케줄링**: 노드 상태에 따라 실행 순서 결정
- ✅ **병렬 실행**: 의존성 없는 노드 동시 실행
- ✅ **파라미터 자동 처리**: Template Variables, Input Source 자동 처리
- ✅ **타입 안전성**: Pydantic 모델로 타입 검증

