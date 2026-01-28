apigate 및 bff scale out 동작 X
apigateway에서의 CPU 기준 70에서 60으로 변경
bff에서의 scale out 동작 X(cpu가 65~75 사이에 분포, 70이상이 유지가 안되서 그런것으로 보임, 기준은 70임)
Webclient의 설정값 pendingAcquireMaxCount이 디폴트값인 1000으로 세팅되어 있어, 요청이 많을 시 webclient connection 수가 full이면서, pendingAcquireMaxCount 조차도 모두 소진하여 관련 에러 발생
retry 및 count가 너무 작으면 cpu 사용 증가, 메모리에 여유가 있다면 해당 값 조장. 다만 처리량을 늘려주진 않고 메모리 버퍼임.
Webclient 쪽 설정 최적화 필요해보임
연결 풀 설정 관련 값(asis)
MAX_CONNECTIONS = 500                    // 최대 연결 수
PENDING_ACQUIRE_TIMEOUT = 5000          // 연결 획득 대기 시간: 5초 (밀리초)
MAX_IDLE_TIME_SECONDS = 20              // 최대 유휴 시간: 20초
MAX_LIFE_TIME_MINUTES = 2               // 최대 생명주기: 2분
EVICT_BACKGROUND_SECONDS = 10            // 백그라운드 정리 주기: 10초

타임아웃 설정 관련 값(asis)
CONNECTION_TIMEOUT = 5000                // 연결 타임아웃: 5초 (밀리초)
REQUEST_TIMEOUT = 5                      // 요청 타임아웃: 5초
  - ReadTimeout: 5초
  - WriteTimeout: 5초
  - ResponseTimeout: 5초

WebclientUtil 설정 관련 값(asis)
DEFAULT_TIMEOUT = 10초                   // 기본 타임아웃: 10초
DEFAULT_MAX_RETRY = 1                    // 최대 재시도 횟수: 1회
RETRY_DELAY = 3초                        // 재시도 지연 시간: 3초

apps쪽에서 다음과 같은 에러 발생 : RejectedExecutionException: Thread limit exceeded replacing blocked worker
관련 에러는 레디스(lettuce 사용)에서 비동기 클라이언트라도 동기 api 호출 시 forkjoinpool 워커스레드 사용 (jvm 내부 공유 풀) 단순 톰캣 스레드 블로킹으로 생각했는데 그게 아니었음.
[톰캣 스레드]
    ↓
[cache.get() 또는 redisTemplate.get() 호출]
    ↓
[Lettuce 비동기 Redis 명령 실행] ← Netty Event Loop 스레드
    ↓
[CompletableFuture.get() 호출] ← 내부적으로 ForkJoinPool.commonPool() 사용 ⚠️
    ├─ 블로킹된 워커를 ForkJoinPool.commonPool()에서 새 스레드로 대체 시도
    ├─ 스레드 한계 도달 시 RejectedExecutionException 발생 가능
    └─ 톰캣 스레드는 여전히 블로킹
    ↓
[Redis 응답 대기] ← 톰캣 스레드 블로킹
EventExecutorGroup을 이용한 전용 풀을 사용해서 처리해야 한다고 함.
bff circuitbreaker 동작 시 해당 api 요청이 open되는 순간, core 서비스는 요청이 들어오지 않는다고 생각하고 오히려 scale in을 할 수 있는 위험이 있음. 오히려 요청이 너무 많아져 core 서비스 리소스를 많이 써 scale out이 되어야 하는데, 앞단에서 slow query 비중 증가 및 서버 에러 발생 서 circuit breaker open이 되면 대규모 트래픽에서는 문제 커질 수 있음.
요청이 많아지만 core 서비스가 준비되지 않으면 8초 이상의 슬로우 쿼리가 증가할 것이고 이는 차단기 open으로 이어지고 문제가 커지게 됨. 그러므로 코어쪽 서비스가 좀 더 트래픽에 민감하게 반응하거나, 미리 프로비저닝이 충분히 되어 있어야 한다.