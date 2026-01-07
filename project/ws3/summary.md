## 공통부 정리

### 기본 스펙
+ 자바 : 21버전
+ 스프링부트 : 3.4.3버전
+ 로그트레이싱 : Spring cloud sleuth -> Micrometer Tracing 

### 개발범위
+ API Gateway
+ 가변부 중 TV + VS auth
+ 공통 배치
  + auth-synchronizer
  + nlp-synchronizer
  + rdx-batch

### 요청헤더

+ Device -> API Gateway
  + Authorization : Access Token으로 헤더값이 존재하면 2.0 기준의 /authCheck를 통해 토큰 유효성 체크
  + 그 외 Device 정보 헤더들

+ API Gateway ->  BFF/Core Gateway
  + device header
  + X-Client-Id : 디바이스의 맥 어드레스로 X-Device-Id, X-Device-Model 값으로 복호화 한 값

+ Core Gateway -> Core services
  + device header
  + X-Client-Id : 디바이스의 맥 어드레스로 X-Device-Id, X-Device-Model 값으로 복호화 한 값

### 스프링 배치 5.x 마이그레이션으로 배치 시스템 테이블의 스키마 변경 건이 있음.

### API Gateway 연계 API 허용 처리
+ 대상 : API Gateway만 연계하는 1.0 서비스
+ 호출 Flow
  + 1.0 기준 : 1.0 External ALB -> 1.0 서비스
  + 3.0 기준 : 3.0 External ALB -> API Gateway -> 1.0 Internal ALB -> 1.0 서비스(url 호출을 하는 경우, X-Forwarded-for를 통해 API Gateway IP 대역을 체크하여 허용 범위인지 확인, /xxxx30 이 url에 포함되어야 함.)
+ 1.0 서비스(스프링부트)에도 API gateway를 통해서 들어오는 로직을 위한 filter 소스를 추가해야 함.

### 디바이스 IP의 기준으로 'X-Real-IP'를 사용

### Model Secret이란 ? 디바이스 모델별로 고유하게 부여되는 정보인데, 인증 시에도 사용.
  + auth 서비스에서 Model Secret 조회가 진행된다.
  + 서비스에서 ConcurrentHashMap을 통해 로컬 캐시를 구현하였으며, 만약 로컬캐시의 값이 존재할 경우 sdpremotehashcache 값을 참조하지 않음.

### Circuit Breaker (차단기라고 생각하면 됨)
  + 상태 종류
    + CLOSED : 정상 상태, 모든 요청이 외부 서비스로 전달됨
    + OPEN : 장애 상태, 모든 요청이 차단, Fallback로직 실행
    + HALF_OPEN : 복구 테스트 상태, 제한된 수의 요청만 외부 서비스로 전달
  + 의존성 
    + resilience4j
    + openfeign(외부 호출용)
  + 설정 정보
  ```text
  resilience4j:
  circuitbreaker:
    configs:
      default:
        slow-call-duration-threshold: 8000   # 지연 요청(slow call)이라고 판단할 시간. (ms 단위) 해당 설정 값 이상일 경우 slow call로 판단 [ms]
        wait-duration-in-open-state: 30000   # Open -> Half_Open으로 전환하기 전에 최소 대기 해야 할 시간 [ms]
        record-failure-predicate: "com.xxx.xxx.base.config.CircuitBreakerConfig"
    instances:
       GftsApi:
        base-config: default
  ```
    + configs : CB에서 공통으로 사용하는 설정 정보
    + instances : 실제 동작하는 CB 객체를 생성하고 설정을 적용, 
@CircuitBreaker(name = "인스턴스 명", fallbackMethod = "fallback 메소드 명")으로 설정
  + 그외 특징
    + 400번대 에러(client)는 실패로 기록하지 않는다. 
    + record-failure-predicate 설정에서 설정한 Config 빈에서 정의하며, true면 실패로 기록, false는 실패로 기록하지 않는다. (test method overriding)

## 가변부 정리

### WebClinet 분석 

### 로그 구성
  +  log4j-layout-template-json 사용을 통해 json 형식의 로그 레이아웃 정의, $resolver 키워드를 이용해서 다양한 정보 로그 포맷팅
  + Webflux 전용 전역 필터(WebFilter)를 통해 모든 요청에 대해서 로깅 처리
  + 그 외 HandlerFilterFunction, ExchangeFilterFunction(WebClient용) 등을 활용해 사용 필요에 따라 구분 

### 캐시 구성 분석 

### 예외 처리 분석
  + WebFlux의 WebExceptionHandler를 구현하여 전역 예외 처리
  + 비동기 예외 처리
  + 우선순위를 -2로 설정하여 높게 설정해 기본 예외 처리보다 먼저 실행되도록 함

### Webflux router와 handler, ServerRequest와 ServerResponse

### 그 외 Webflux 사용 시 사용되는 컴포넌트 및 객체 기술적 분석 및 동작 방식과 흐름 정리

## API Gateway 정리