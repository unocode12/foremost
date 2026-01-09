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

### 로그 구성 분석

#### 1. WebFlux 로깅의 핵심 과제

**문제점**:
- WebFlux는 **비동기/논블로킹** 환경
- 스레드가 계속 바뀜 (ThreadLocal 기반 MDC가 작동 안 함)
- 리액티브 체인에서 컨텍스트 전파 필요

**해결책**:
- `Reactor Context`를 통한 컨텍스트 전파
- `WebFilter`를 통한 전역 로깅
- `log4j-layout-template-json`을 통한 구조화된 로그

---

#### 2. log4j-layout-template-json 개요

**역할**: JSON 형식의 구조화된 로그 레이아웃 정의

**$resolver 키워드**: 다양한 정보를 동적으로 로그에 포함

**예시 설정** (`log4j2.xml` 또는 `log4j2-spring.xml`):

```xml
<Configuration>
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <JsonTemplateLayout eventTemplateUri="classpath:LogstashJsonEventLayoutV1.json">
                <EventTemplateAdditionalField 
                    key="application" 
                    value="${spring:spring.application.name}" 
                    format="JSON"/>
                <EventTemplateAdditionalField 
                    key="environment" 
                    value="${spring:spring.profiles.active}" 
                    format="JSON"/>
            </JsonTemplateLayout>
        </Console>
    </Appenders>
    
    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

**$resolver 사용 예시**:

```json
{
  "timestamp": "${resolver:timestamp}",
  "level": "${resolver:level}",
  "thread": "${resolver:thread}",
  "logger": "${resolver:logger}",
  "message": "${resolver:message}",
  "exception": "${resolver:exception}",
  "mdc": {
    "traceId": "${resolver:mdc:key=traceId}",
    "spanId": "${resolver:mdc:key=spanId}",
    "userId": "${resolver:mdc:key=userId}"
  },
  "context": {
    "traceId": "${resolver:context:key=traceId}",
    "requestId": "${resolver:context:key=requestId}"
  }
}
```

**주요 $resolver 키워드**:
- `${resolver:timestamp}`: 타임스탬프
- `${resolver:level}`: 로그 레벨
- `${resolver:thread}`: 스레드 이름
- `${resolver:logger}`: 로거 이름
- `${resolver:message}`: 로그 메시지
- `${resolver:exception}`: 예외 정보
- `${resolver:mdc:key=xxx}`: MDC에서 값 추출
- `${resolver:context:key=xxx}`: Reactor Context에서 값 추출

---

#### 3. WebFilter를 통한 전역 로깅

**WebFilter의 역할**:
- 모든 HTTP 요청/응답에 대한 로깅
- Reactor Context에 traceId, requestId 등 저장
- MDC와 Reactor Context 동기화

**기본 구조**:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLoggingFilter implements WebFilter {
    
    private static final String TRACE_ID_KEY = "traceId";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String START_TIME_KEY = "startTime";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String traceId = generateTraceId();
        String requestId = generateRequestId();
        
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // 1. 요청 로깅
        logRequest(request, traceId, requestId);
        
        // 2. Reactor Context에 값 저장
        return chain.filter(exchange)
            .contextWrite(Context.of(
                TRACE_ID_KEY, traceId,
                REQUEST_ID_KEY, requestId,
                START_TIME_KEY, startTime
            ))
            .doOnSuccess(v -> {
                // 3. 응답 로깅 (성공)
                logResponse(exchange, traceId, requestId, startTime, true);
            })
            .doOnError(throwable -> {
                // 4. 응답 로깅 (에러)
                logErrorResponse(exchange, traceId, requestId, startTime, throwable);
            })
            .doFinally(signalType -> {
                // 5. 리소스 정리
                MDC.clear();
            });
    }
    
    private void logRequest(ServerHttpRequest request, String traceId, String requestId) {
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(REQUEST_ID_KEY, requestId);
        
        log.info("Request: {} {} | traceId: {} | requestId: {} | headers: {}",
            request.getMethod(),
            request.getURI(),
            traceId,
            requestId,
            request.getHeaders()
        );
    }
    
    private void logResponse(ServerWebExchange exchange, String traceId, String requestId, 
                            long startTime, boolean success) {
        ServerHttpResponse response = exchange.getResponse();
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("Response: {} | traceId: {} | requestId: {} | status: {} | duration: {}ms",
            exchange.getRequest().getURI(),
            traceId,
            requestId,
            response.getStatusCode(),
            duration
        );
    }
    
    private void logErrorResponse(ServerWebExchange exchange, String traceId, String requestId,
                                 long startTime, Throwable throwable) {
        long duration = System.currentTimeMillis() - startTime;
        
        log.error("Error Response: {} | traceId: {} | requestId: {} | duration: {}ms | error: {}",
            exchange.getRequest().getURI(),
            traceId,
            requestId,
            duration,
            throwable.getMessage(),
            throwable
        );
    }
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
```

**핵심 포인트**:
1. **contextWrite()**: Reactor Context에 값 저장 (비동기 체인 전반에 전파)
2. **MDC.put()**: MDC에도 저장 (동기 로깅용)
3. **doOnSuccess/doOnError**: 성공/실패 시점에 로깅
4. **doFinally**: 리소스 정리

---

#### 4. Reactor Context와 MDC 동기화

**문제**: WebFlux는 스레드가 계속 바뀌므로 ThreadLocal 기반 MDC가 작동하지 않음

**해결**: Reactor Context를 MDC와 동기화

**동기화 필터 예시**:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ContextToMDCFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
            .contextWrite(context -> {
                // Reactor Context의 값을 MDC에 복사
                context.stream()
                    .forEach(entry -> {
                        if (entry.getKey() instanceof String) {
                            String key = (String) entry.getKey();
                            Object value = entry.getValue();
                            if (value != null) {
                                MDC.put(key, value.toString());
                            }
                        }
                    });
                return context;
            })
            .doFinally(signalType -> {
                // 정리
                MDC.clear();
            });
    }
}
```

**자동 동기화 설정** (Spring Boot 3.x):

```yaml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

또는 Micrometer Tracing 사용 시:

```java
@Configuration
public class LoggingConfig {
    
    @Bean
    public ContextSnapshotFactory contextSnapshotFactory() {
        return ContextSnapshotFactory.builder()
            .build();
    }
}
```

---

#### 5. HandlerFilterFunction (RouterFunction용 필터)

**역할**: RouterFunction 기반 라우팅에서 사용하는 필터

**특징**:
- `RouterFunction` 레벨에서 필터링
- 특정 라우트에만 적용 가능
- `ServerRequest`와 `ServerResponse` 직접 접근

**예시**:

```java
@Configuration
public class RouterConfig {
    
    @Bean
    public RouterFunction<ServerResponse> userRouter(UserHandler userHandler) {
        return RouterFunctions.route()
            .GET("/api/users/{id}", userHandler::getUser)
            .POST("/api/users", userHandler::createUser)
            .filter(requestLoggingFilter())  // 필터 적용
            .build();
    }
    
    private HandlerFilterFunction<ServerResponse, ServerResponse> requestLoggingFilter() {
        return (request, next) -> {
            long startTime = System.currentTimeMillis();
            String traceId = extractTraceId(request);
            
            log.info("Router Request: {} {} | traceId: {}",
                request.method(),
                request.path(),
                traceId
            );
            
            return next.handle(request)
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("Router Response: {} | traceId: {} | status: {} | duration: {}ms",
                        request.path(),
                        traceId,
                        response.statusCode(),
                        duration
                    );
                })
                .doOnError(throwable -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("Router Error: {} | traceId: {} | duration: {}ms | error: {}",
                        request.path(),
                        traceId,
                        duration,
                        throwable.getMessage()
                    );
                });
        };
    }
    
    private String extractTraceId(ServerRequest request) {
        return request.headers().firstHeader("X-Trace-Id")
            .orElse(UUID.randomUUID().toString());
    }
}
```

**HandlerFilterFunction 인터페이스**:

```java
@FunctionalInterface
public interface HandlerFilterFunction<T extends ServerResponse, R extends ServerResponse> {
    Mono<R> filter(ServerRequest request, HandlerFunction<T> next);
}
```

**사용 시나리오**:
- 특정 라우트에만 로깅 적용
- 라우트별 인증/인가 체크
- 라우트별 성능 모니터링

---

#### 6. ExchangeFilterFunction (WebClient용 필터)

**역할**: WebClient의 HTTP 요청/응답에 대한 필터링 및 로깅

**특징**:
- 외부 API 호출 시 로깅
- 요청/응답 본문 로깅 가능
- 재시도, 타임아웃 등과 함께 사용

**예시**:

```java
@Component
public class WebClientConfig {
    
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
            .baseUrl("https://api.example.com")
            .filter(loggingFilter())
            .filter(requestIdFilter())
            .build();
    }
    
    private ExchangeFilterFunction loggingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String requestId = UUID.randomUUID().toString();
            long startTime = System.currentTimeMillis();
            
            log.info("WebClient Request: {} {} | requestId: {} | headers: {}",
                request.method(),
                request.url(),
                requestId,
                request.headers()
            );
            
            // 요청 본문 로깅 (선택적)
            if (request.body() != null) {
                return request.body()
                    .cast(DataBuffer.class)
                    .doOnNext(dataBuffer -> {
                        // 본문 로깅 (주의: 본문은 한 번만 읽을 수 있음)
                        log.debug("Request Body: {}", 
                            dataBuffer.toString(StandardCharsets.UTF_8));
                    })
                    .then(Mono.just(request));
            }
            
            return Mono.just(request);
        })
        .andThen(ExchangeFilterFunction.ofResponseProcessor(response -> {
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("WebClient Response: {} | requestId: {} | status: {} | duration: {}ms",
                response.statusCode(),
                requestId,
                response.statusCode(),
                duration
            );
            
            return Mono.just(response);
        }));
    }
    
    private ExchangeFilterFunction requestIdFilter() {
        return (request, next) -> {
            String requestId = UUID.randomUUID().toString();
            
            // Reactor Context에서 traceId 추출
            return Mono.deferContextual(contextView -> {
                String traceId = contextView.getOrDefault("traceId", "unknown");
                
                // 요청 헤더에 추가
                ClientRequest filteredRequest = ClientRequest.from(request)
                    .header("X-Trace-Id", traceId)
                    .header("X-Request-Id", requestId)
                    .build();
                
                return next.exchange(filteredRequest)
                    .contextWrite(Context.of("requestId", requestId));
            });
        };
    }
}
```

**ExchangeFilterFunction 인터페이스**:

```java
@FunctionalInterface
public interface ExchangeFilterFunction {
    Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next);
    
    // 편의 메서드
    static ExchangeFilterFunction ofRequestProcessor(
        Function<ClientRequest, Mono<ClientRequest>> processor) { ... }
    
    static ExchangeFilterFunction ofResponseProcessor(
        Function<ClientResponse, Mono<ClientResponse>> processor) { ... }
}
```

**사용 시나리오**:
- 외부 API 호출 로깅
- 요청/응답 본문 로깅
- 헤더 추가/수정
- 재시도 로직
- 타임아웃 설정

---

#### 7. 전체 로깅 흐름

**요청 처리 시 로깅 흐름**:

```
[HTTP 요청 도착]
    ↓
[RequestLoggingFilter]
    ├─ traceId 생성
    ├─ requestId 생성
    ├─ MDC에 저장
    ├─ Reactor Context에 저장 (contextWrite)
    └─ 요청 로깅
    ↓
[ContextToMDCFilter] (선택적)
    └─ Reactor Context → MDC 동기화
    ↓
[Controller / RouterFunction]
    ├─ HandlerFilterFunction (RouterFunction인 경우)
    └─ 비즈니스 로직 실행
    ↓
[Service]
    └─ 로깅 (MDC/Context 값 자동 포함)
    ↓
[WebClient 호출]
    ├─ ExchangeFilterFunction
    └─ 외부 API 요청 로깅
    ↓
[응답 생성]
    ↓
[RequestLoggingFilter.doOnSuccess]
    └─ 응답 로깅
    ↓
[HTTP Response]
```

**로그 출력 예시**:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "thread": "reactor-http-nio-2",
  "logger": "com.example.RequestLoggingFilter",
  "message": "Request: GET /api/users/123",
  "mdc": {
    "traceId": "abc123def456",
    "requestId": "req789xyz"
  },
  "context": {
    "traceId": "abc123def456",
    "requestId": "req789xyz",
    "startTime": "1705312245123"
  }
}
```

---

#### 8. 모범 사례

**1. 로깅 레벨 구분**:
```java
// 요청/응답: INFO
log.info("Request: {} {}", method, path);

// 상세 정보: DEBUG
log.debug("Request Headers: {}", headers);
log.debug("Request Body: {}", body);

// 에러: ERROR
log.error("Error occurred", throwable);
```

**2. 민감 정보 제거**:
```java
private String sanitizeHeaders(HttpHeaders headers) {
    HttpHeaders sanitized = new HttpHeaders();
    headers.forEach((key, values) -> {
        if (isSensitiveHeader(key)) {
            sanitized.add(key, "***");
        } else {
            sanitized.put(key, values);
        }
    });
    return sanitized.toString();
}

private boolean isSensitiveHeader(String key) {
    return key.equalsIgnoreCase("Authorization") ||
           key.equalsIgnoreCase("Cookie") ||
           key.equalsIgnoreCase("X-API-Key");
}
```

**3. 성능 고려**:
```java
// 본문 로깅은 DEBUG 레벨로만
if (log.isDebugEnabled()) {
    log.debug("Request Body: {}", body);
}

// 큰 본문은 일부만 로깅
private String truncateBody(String body, int maxLength) {
    if (body.length() > maxLength) {
        return body.substring(0, maxLength) + "... (truncated)";
    }
    return body;
}
```

**4. 비동기 로깅**:
```java
// 로깅이 블로킹되지 않도록
return chain.filter(exchange)
    .doOnSuccess(v -> {
        // 비동기로 로깅
        Mono.fromRunnable(() -> logResponse(exchange))
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    });
```

---

#### 9. 핵심 정리

**WebFlux 로깅의 핵심**:

1. **Reactor Context 사용**: 비동기 체인에서 컨텍스트 전파
2. **MDC 동기화**: Reactor Context → MDC로 값 복사
3. **WebFilter**: 전역 요청/응답 로깅
4. **HandlerFilterFunction**: RouterFunction 레벨 필터링
5. **ExchangeFilterFunction**: WebClient 레벨 필터링
6. **구조화된 로그**: JSON 형식으로 일관된 로그 출력

**각 필터의 역할**:

| 필터 | 적용 범위 | 주요 용도 |
|------|----------|----------|
| **WebFilter** | 모든 요청 | 전역 로깅, traceId 생성 |
| **HandlerFilterFunction** | 특정 RouterFunction | 라우트별 로깅, 인증/인가 |
| **ExchangeFilterFunction** | WebClient 호출 | 외부 API 호출 로깅 |

**권장 사항**:
- ✅ traceId는 WebFilter에서 생성
- ✅ Reactor Context로 전파
- ✅ MDC와 동기화하여 기존 로거 활용
- ✅ 구조화된 JSON 로그 사용
- ✅ 민감 정보는 마스킹 처리 

### 캐시 구성 분석

#### 1. WebFlux에서의 캐시 개요

**WebFlux 캐시의 핵심 과제**:
- **비동기/논블로킹**: 모든 캐시 작업이 리액티브 스트림으로 처리되어야 함
- **블로킹 방지**: 동기 Redis 클라이언트 사용 시 EventLoop 스레드 블로킹
- **리액티브 통합**: `Mono`/`Flux`와 자연스럽게 통합

**해결책**:
- **Spring Data Redis Reactive**: Lettuce 기반 비동기 Redis 클라이언트
- **ReactiveRedisTemplate**: 리액티브 방식의 Redis 템플릿
- **@Cacheable과 통합**: Spring Cache Abstraction과 리액티브 통합

---

#### 2. Redis Reactive 의존성 설정

**Maven 의존성**:

```xml
<dependencies>
    <!-- Spring Data Redis Reactive -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
    </dependency>
    
    <!-- Lettuce (비동기 Redis 클라이언트) -->
    <dependency>
        <groupId>io.lettuce</groupId>
        <artifactId>lettuce-core</artifactId>
    </dependency>
    
    <!-- Spring Cache Abstraction -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
</dependencies>
```

**Gradle 의존성**:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'io.lettuce:lettuce-core'
    implementation 'org.springframework.boot:spring-boot-starter-cache'
}
```

**application.yml 설정**:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
        shutdown-timeout: 100ms
  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10분 (밀리초)
      cache-null-values: false
      use-key-prefix: true
      key-prefix: "cache:"
```

---

#### 3. ReactiveRedisTemplate 설정

**기본 설정**:

```java
@Configuration
@EnableCaching
public class RedisConfig {
    
    @Bean
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory();
        factory.setHostName("localhost");
        factory.setPort(6379);
        factory.setPassword("password");
        factory.setTimeout(Duration.ofSeconds(2));
        return factory;
    }
    
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        
        RedisSerializationContext<String, Object> serializationContext = 
            RedisSerializationContext.<String, Object>newSerializationContext()
                .key(RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
                .value(RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
                .hashKey(RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()))
                .hashValue(RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
                .build();
        
        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
    
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new ReactiveStringRedisTemplate(connectionFactory);
    }
}
```

**직렬화 설정 설명**:
- **Key**: `StringRedisSerializer` (문자열 키)
- **Value**: `GenericJackson2JsonRedisSerializer` (JSON 직렬화)
- **Hash Key/Value**: 각각 적절한 직렬화 설정

---

#### 4. ReactiveRedisTemplate 사용법

**기본 CRUD 작업**:

```java
@Service
@Slf4j
public class CacheService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    public CacheService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    // 값 저장
    public Mono<Boolean> set(String key, Object value) {
        return redisTemplate.opsForValue()
            .set(key, value)
            .doOnSuccess(result -> log.debug("Cache set: {} = {}", key, value));
    }
    
    // 값 저장 (TTL 포함)
    public Mono<Boolean> set(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue()
            .set(key, value, ttl)
            .doOnSuccess(result -> log.debug("Cache set: {} = {} (TTL: {})", key, value, ttl));
    }
    
    // 값 조회
    public Mono<Object> get(String key) {
        return redisTemplate.opsForValue()
            .get(key)
            .doOnNext(value -> log.debug("Cache get: {} = {}", key, value));
    }
    
    // 타입 안전한 조회
    public <T> Mono<T> get(String key, Class<T> type) {
        return redisTemplate.opsForValue()
            .get(key)
            .cast(type)
            .switchIfEmpty(Mono.error(new CacheMissException("Cache miss: " + key)));
    }
    
    // 값 삭제
    public Mono<Long> delete(String key) {
        return redisTemplate.delete(key)
            .doOnSuccess(count -> log.debug("Cache delete: {} (count: {})", key, count));
    }
    
    // 여러 키 삭제
    public Mono<Long> delete(String... keys) {
        return redisTemplate.delete(Arrays.asList(keys))
            .doOnSuccess(count -> log.debug("Cache delete: {} keys (count: {})", keys.length, count));
    }
    
    // 키 존재 여부 확인
    public Mono<Boolean> exists(String key) {
        return redisTemplate.hasKey(key)
            .doOnNext(exists -> log.debug("Cache exists: {} = {}", key, exists));
    }
    
    // TTL 설정
    public Mono<Boolean> expire(String key, Duration ttl) {
        return redisTemplate.expire(key, ttl)
            .doOnSuccess(result -> log.debug("Cache expire: {} = {} (TTL: {})", key, result, ttl));
    }
}
```

**Hash 작업**:

```java
@Service
public class HashCacheService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    // Hash 필드 설정
    public Mono<Boolean> hSet(String key, String field, Object value) {
        return redisTemplate.opsForHash()
            .put(key, field, value)
            .doOnSuccess(result -> log.debug("Hash set: {}[{}] = {}", key, field, value));
    }
    
    // Hash 필드 조회
    public Mono<Object> hGet(String key, String field) {
        return redisTemplate.opsForHash()
            .get(key, field)
            .doOnNext(value -> log.debug("Hash get: {}[{}] = {}", key, field, value));
    }
    
    // Hash 전체 조회
    public Mono<Map<Object, Object>> hGetAll(String key) {
        return redisTemplate.opsForHash()
            .entries(key)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .doOnNext(map -> log.debug("Hash get all: {} = {}", key, map));
    }
    
    // Hash 필드 삭제
    public Mono<Long> hDelete(String key, String... fields) {
        return redisTemplate.opsForHash()
            .remove(key, (Object[]) fields)
            .doOnSuccess(count -> log.debug("Hash delete: {}[{}] (count: {})", key, fields, count));
    }
}
```

**List 작업**:

```java
@Service
public class ListCacheService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    // 리스트 왼쪽에 추가
    public Mono<Long> lPush(String key, Object... values) {
        return redisTemplate.opsForList()
            .leftPushAll(key, values)
            .doOnSuccess(count -> log.debug("List push left: {} (count: {})", key, count));
    }
    
    // 리스트 오른쪽에 추가
    public Mono<Long> rPush(String key, Object... values) {
        return redisTemplate.opsForList()
            .rightPushAll(key, values)
            .doOnSuccess(count -> log.debug("List push right: {} (count: {})", key, count));
    }
    
    // 리스트 조회 (범위)
    public Flux<Object> lRange(String key, long start, long end) {
        return redisTemplate.opsForList()
            .range(key, start, end)
            .doOnNext(value -> log.debug("List range: {}[{}-{}] = {}", key, start, end, value));
    }
    
    // 리스트 길이 조회
    public Mono<Long> lSize(String key) {
        return redisTemplate.opsForList()
            .size(key)
            .doOnNext(size -> log.debug("List size: {} = {}", key, size));
    }
}
```

---

#### 5. Spring Cache Abstraction과 통합

**리액티브 캐시 매니저 설정**:

```java
@Configuration
@EnableCaching
public class ReactiveCacheConfig {
    
    @Bean
    public ReactiveCacheManager cacheManager(ReactiveRedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();
        
        return ReactiveRedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
}
```

**@Cacheable 사용 (리액티브 메서드)**:

```java
@Service
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final ReactiveCacheManager cacheManager;
    
    public UserService(UserRepository userRepository, ReactiveCacheManager cacheManager) {
        this.userRepository = userRepository;
        this.cacheManager = cacheManager;
    }
    
    // @Cacheable은 리액티브 메서드에서 직접 작동하지 않음
    // 수동으로 캐시 처리 필요
    
    public Mono<User> getUser(String id) {
        String cacheKey = "user:" + id;
        ReactiveCache cache = cacheManager.getCache("users");
        
        return cache.get(cacheKey, User.class)
            .switchIfEmpty(
                userRepository.findById(id)
                    .flatMap(user -> cache.put(cacheKey, user).thenReturn(user))
            )
            .doOnNext(user -> log.debug("User retrieved: {}", user.getId()));
    }
    
    // 또는 헬퍼 메서드 사용
    public Mono<User> getUserWithCache(String id) {
        return getOrCache("user:" + id, User.class, 
            () -> userRepository.findById(id));
    }
    
    private <T> Mono<T> getOrCache(String key, Class<T> type, Supplier<Mono<T>> supplier) {
        ReactiveCache cache = cacheManager.getCache("default");
        return cache.get(key, type)
            .switchIfEmpty(
                supplier.get()
                    .flatMap(value -> cache.put(key, value).thenReturn(value))
            );
    }
}
```

**캐시 무효화**:

```java
@Service
public class UserService {
    
    private final ReactiveCacheManager cacheManager;
    
    public Mono<Void> evictUser(String id) {
        ReactiveCache cache = cacheManager.getCache("users");
        return cache.evict("user:" + id)
            .doOnSuccess(v -> log.debug("Cache evicted: user:{}", id));
    }
    
    public Mono<Void> evictAllUsers() {
        ReactiveCache cache = cacheManager.getCache("users");
        return cache.clear()
            .doOnSuccess(v -> log.debug("Cache cleared: users"));
    }
}
```

---

#### 6. 고급 캐시 패턴

**캐시 어사이드 패턴 (Cache-Aside)**:

```java
@Service
@Slf4j
public class ProductService {
    
    private final ProductRepository productRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    public Mono<Product> getProduct(String id) {
        String cacheKey = "product:" + id;
        
        // 1. 캐시에서 조회
        return redisTemplate.opsForValue()
            .get(cacheKey)
            .cast(Product.class)
            .switchIfEmpty(
                // 2. 캐시 미스 시 DB 조회
                productRepository.findById(id)
                    .flatMap(product -> {
                        // 3. DB 결과를 캐시에 저장
                        return redisTemplate.opsForValue()
                            .set(cacheKey, product, Duration.ofMinutes(30))
                            .thenReturn(product);
                    })
            )
            .doOnNext(product -> log.debug("Product retrieved: {}", product.getId()))
            .doOnError(error -> log.error("Error getting product: {}", id, error));
    }
    
    public Mono<Product> updateProduct(String id, Product product) {
        String cacheKey = "product:" + id;
        
        // 1. DB 업데이트
        return productRepository.save(product)
            .flatMap(savedProduct -> {
                // 2. 캐시 업데이트
                return redisTemplate.opsForValue()
                    .set(cacheKey, savedProduct, Duration.ofMinutes(30))
                    .thenReturn(savedProduct);
            })
            .doOnNext(p -> log.debug("Product updated: {}", p.getId()));
    }
    
    public Mono<Void> deleteProduct(String id) {
        String cacheKey = "product:" + id;
        
        // 1. DB 삭제
        return productRepository.deleteById(id)
            .then(
                // 2. 캐시 삭제
                redisTemplate.delete(cacheKey)
            )
            .then()
            .doOnSuccess(v -> log.debug("Product deleted: {}", id));
    }
}
```

**캐시 쓰기 스루 패턴 (Write-Through)**:

```java
@Service
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    public Mono<Order> createOrder(Order order) {
        String cacheKey = "order:" + order.getId();
        
        // 1. DB 저장
        return orderRepository.save(order)
            .flatMap(savedOrder -> {
                // 2. 캐시에 동시 저장
                return redisTemplate.opsForValue()
                    .set(cacheKey, savedOrder, Duration.ofHours(1))
                    .thenReturn(savedOrder);
            })
            .doOnNext(o -> log.debug("Order created: {}", o.getId()));
    }
    
    public Mono<Order> getOrder(String id) {
        String cacheKey = "order:" + id;
        
        // 캐시에서만 조회 (쓰기 스루에서는 항상 캐시에 있음)
        return redisTemplate.opsForValue()
            .get(cacheKey)
            .cast(Order.class)
            .switchIfEmpty(
                // 캐시 미스 시 DB 조회 후 캐시 저장
                orderRepository.findById(id)
                    .flatMap(order -> redisTemplate.opsForValue()
                        .set(cacheKey, order, Duration.ofHours(1))
                        .thenReturn(order))
            );
    }
}
```

**캐시 쓰기 백 패턴 (Write-Back)**:

```java
@Service
@Slf4j
public class AnalyticsService {
    
    private final AnalyticsRepository analyticsRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    public Mono<Void> recordEvent(String eventId, Event event) {
        String cacheKey = "event:" + eventId;
        
        // 1. 캐시에만 저장 (비동기로 DB 저장)
        return redisTemplate.opsForValue()
            .set(cacheKey, event, Duration.ofHours(24))
            .then(
                // 2. 백그라운드에서 DB 저장
                analyticsRepository.save(event)
                    .subscribeOn(Schedulers.boundedElastic())
                    .then()
            )
            .doOnSuccess(v -> log.debug("Event recorded: {}", eventId));
    }
}
```

---

#### 7. 캐시 무효화 전략

**TTL 기반 자동 만료**:

```java
@Service
public class CacheEvictionService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    public Mono<Boolean> setWithExpiration(String key, Object value, Duration ttl) {
        return redisTemplate.opsForValue()
            .set(key, value, ttl)
            .doOnSuccess(result -> log.debug("Cache set with TTL: {} = {} (TTL: {})", key, value, ttl));
    }
    
    public Mono<Boolean> refreshExpiration(String key, Duration ttl) {
        return redisTemplate.expire(key, ttl)
            .doOnSuccess(result -> log.debug("Cache expiration refreshed: {} (TTL: {})", key, ttl));
    }
}
```

**이벤트 기반 무효화**:

```java
@Service
@Slf4j
public class CacheInvalidationService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    // 특정 키 무효화
    public Mono<Long> invalidate(String key) {
        return redisTemplate.delete(key)
            .doOnSuccess(count -> log.info("Cache invalidated: {}", key));
    }
    
    // 패턴 기반 무효화
    public Mono<Long> invalidateByPattern(String pattern) {
        return redisTemplate.keys(pattern)
            .collectList()
            .flatMap(keys -> {
                if (keys.isEmpty()) {
                    return Mono.just(0L);
                }
                return redisTemplate.delete(keys)
                    .doOnSuccess(count -> log.info("Cache invalidated by pattern: {} (count: {})", pattern, count));
            });
    }
    
    // 사용 예시
    public Mono<Void> invalidateUserCaches(String userId) {
        return invalidateByPattern("user:" + userId + ":*")
            .then();
    }
}
```

**태그 기반 무효화**:

```java
@Service
@Slf4j
public class TagBasedCacheService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    // 태그와 함께 캐시 저장
    public Mono<Boolean> setWithTag(String key, Object value, String tag, Duration ttl) {
        String tagKey = "tag:" + tag;
        
        return redisTemplate.opsForValue()
            .set(key, value, ttl)
            .flatMap(result -> {
                // 태그에 키 추가
                return redisTemplate.opsForSet()
                    .add(tagKey, key)
                    .then(redisTemplate.expire(tagKey, ttl))
                    .thenReturn(result);
            })
            .doOnSuccess(r -> log.debug("Cache set with tag: {} = {} (tag: {})", key, value, tag));
    }
    
    // 태그로 무효화
    public Mono<Long> invalidateByTag(String tag) {
        String tagKey = "tag:" + tag;
        
        return redisTemplate.opsForSet()
            .members(tagKey)
            .cast(String.class)
            .collectList()
            .flatMap(keys -> {
                if (keys.isEmpty()) {
                    return Mono.just(0L);
                }
                // 태그된 모든 키 삭제
                return redisTemplate.delete(keys)
                    .flatMap(count -> 
                        // 태그 키도 삭제
                        redisTemplate.delete(tagKey)
                            .thenReturn(count)
                    )
                    .doOnSuccess(count -> log.info("Cache invalidated by tag: {} (count: {})", tag, count));
            });
    }
}
```

---

#### 8. 성능 최적화

**파이프라인 사용 (배치 작업)**:

```java
@Service
@Slf4j
public class BatchCacheService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    // 여러 키를 한 번에 조회
    public Flux<Object> getMultiple(List<String> keys) {
        return Flux.fromIterable(keys)
            .flatMap(key -> redisTemplate.opsForValue().get(key))
            .doOnNext(value -> log.debug("Batch get: {}", value));
    }
    
    // 여러 키를 한 번에 저장
    public Mono<Void> setMultiple(Map<String, Object> keyValues, Duration ttl) {
        return Flux.fromIterable(keyValues.entrySet())
            .flatMap(entry -> redisTemplate.opsForValue()
                .set(entry.getKey(), entry.getValue(), ttl))
            .then()
            .doOnSuccess(v -> log.debug("Batch set: {} keys", keyValues.size()));
    }
}
```

**캐시 워밍업**:

```java
@Component
@Slf4j
public class CacheWarmupService {
    
    private final UserRepository userRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        log.info("Starting cache warmup...");
        
        userRepository.findAll()
            .flatMap(user -> {
                String cacheKey = "user:" + user.getId();
                return redisTemplate.opsForValue()
                    .set(cacheKey, user, Duration.ofHours(1));
            })
            .then()
            .doOnSuccess(v -> log.info("Cache warmup completed"))
            .subscribe();
    }
}
```

**캐시 히트율 모니터링**:

```java
@Service
@Slf4j
public class CacheMetricsService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    private final Counter cacheHits = Counter.builder("cache.hits")
        .description("Cache hits")
        .register(meterRegistry);
    
    private final Counter cacheMisses = Counter.builder("cache.misses")
        .description("Cache misses")
        .register(meterRegistry);
    
    public <T> Mono<T> getWithMetrics(String key, Class<T> type, Supplier<Mono<T>> supplier) {
        return redisTemplate.opsForValue()
            .get(key)
            .cast(type)
            .doOnNext(value -> cacheHits.increment())
            .switchIfEmpty(
                supplier.get()
                    .doOnNext(value -> {
                        cacheMisses.increment();
                        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(10))
                            .subscribe();
                    })
            );
    }
}
```

---

#### 9. 에러 처리 및 폴백

**캐시 실패 시 폴백**:

```java
@Service
@Slf4j
public class ResilientCacheService {
    
    private final ProductRepository productRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    
    public Mono<Product> getProduct(String id) {
        String cacheKey = "product:" + id;
        
        return redisTemplate.opsForValue()
            .get(cacheKey)
            .cast(Product.class)
            .timeout(Duration.ofSeconds(1))  // 타임아웃 설정
            .onErrorResume(RedisException.class, e -> {
                // Redis 실패 시 DB에서 직접 조회
                log.warn("Cache error, falling back to DB: {}", e.getMessage());
                return productRepository.findById(id);
            })
            .switchIfEmpty(
                productRepository.findById(id)
                    .flatMap(product -> {
                        // 캐시 저장 시도 (실패해도 계속 진행)
                        return redisTemplate.opsForValue()
                            .set(cacheKey, product, Duration.ofMinutes(30))
                            .onErrorResume(e -> {
                                log.warn("Failed to cache product: {}", e.getMessage());
                                return Mono.empty();
                            })
                            .thenReturn(product);
                    })
            );
    }
}
```

**서킷 브레이커와 통합**:

```java
@Service
@Slf4j
public class CircuitBreakerCacheService {
    
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final CircuitBreaker circuitBreaker;
    
    public <T> Mono<T> getWithCircuitBreaker(String key, Class<T> type, Supplier<Mono<T>> fallback) {
        return circuitBreaker.executeSupplier(() -> 
            redisTemplate.opsForValue()
                .get(key)
                .cast(type)
                .block()  // CircuitBreaker는 블로킹 API 사용
        )
        .map(value -> (T) value)
        .onErrorResume(e -> {
            log.warn("Circuit breaker opened, using fallback");
            return fallback.get();
        });
    }
}
```

---

#### 10. 핵심 정리

**WebFlux Redis 캐시의 핵심 원칙**:

1. **리액티브 클라이언트 사용**: Lettuce 기반 `ReactiveRedisTemplate` 사용
2. **논블로킹 보장**: 모든 캐시 작업이 `Mono`/`Flux`로 래핑
3. **에러 처리**: 캐시 실패 시 폴백 전략 구현
4. **성능 최적화**: 배치 작업, 파이프라인 활용
5. **캐시 전략**: Cache-Aside, Write-Through, Write-Back 선택

**주요 컴포넌트**:

| 컴포넌트 | 역할 |
|---------|------|
| **ReactiveRedisTemplate** | 리액티브 Redis 작업 템플릿 |
| **ReactiveCacheManager** | Spring Cache Abstraction 통합 |
| **LettuceConnectionFactory** | 비동기 Redis 연결 팩토리 |
| **RedisSerializationContext** | 직렬화 설정 |

**권장 사항**:
- ✅ `ReactiveRedisTemplate` 사용 (블로킹 방지)
- ✅ TTL 설정으로 자동 만료
- ✅ 캐시 실패 시 폴백 전략 구현
- ✅ 캐시 히트율 모니터링
- ✅ 배치 작업으로 성능 최적화
- ❌ 동기 Redis 클라이언트 사용 금지 (EventLoop 블로킹)

---

### 예외 처리 분석

#### 1. WebFlux 예외 처리 개요

WebFlux는 **리액티브 스트림 기반**의 비동기 예외 처리 메커니즘을 제공합니다.

**핵심 차이점**:
- **Spring MVC**: `try-catch` 블록으로 예외를 위로 전파 (call stack 기반)
- **WebFlux**: `onError` 시그널로 예외를 전파 (Subscriber 체인 기반)

---

#### 2. WebExceptionHandler 인터페이스

**WebExceptionHandler 인터페이스**:
```java
public interface WebExceptionHandler {
    Mono<Void> handle(ServerWebExchange exchange, Throwable ex);
}
```

**특징**:
- `ServerWebExchange`: 요청/응답 정보를 담은 컨텍스트
- `Throwable`: 발생한 예외
- `Mono<Void>`: 비동기 처리, 응답 작성 후 완료

---

#### 3. 예외 처리 흐름과 위치

**전체 예외 처리 구조**:

```
[HTTP 요청]
    ↓
[Netty EventLoop]
    ↓
[HttpWebHandlerAdapter]
    ↓
[ExceptionHandlingWebHandler]  ← 여기서 예외 처리!
    ↓
[DispatcherHandler]
    ↓
[HandlerMapping]
    ↓
[HandlerAdapter]
    ↓
[@Controller / RouterFunction]
    ↓
[Service]
    ↓
[WebClient]
    ↓ (에러 발생)
[onError 시그널]
    ↓
[ExceptionHandlingWebHandler]
    ↓
[WebExceptionHandler 목록 순회]
    ↓
[GlobalErrorHandler.handle() 호출]
    ↓
[Error Response 작성]
    ↓
[HTTP Response]
```

**핵심**: `ExceptionHandlingWebHandler`가 모든 예외를 잡아서 처리합니다.

---

#### 3-1. RouterFunction vs @Controller 라우팅 흐름 비교

**중요**: 둘 다 `DispatcherHandler`를 거치지만, 내부적으로 사용하는 `HandlerMapping`과 `HandlerAdapter` 구현체가 다릅니다.

##### @Controller를 사용한 경우

```
[DispatcherHandler]
    ↓
[RequestMappingHandlerMapping]  ← @Controller, @GetMapping 등 매핑
    ↓ Handler 찾기
[HandlerMethod] (컨트롤러 메서드 정보)
    ↓
[RequestMappingHandlerAdapter]  ← @Controller 실행
    ↓
[Controller 메서드 실행]
    ↓
[ReturnValueHandler] (ResponseEntity, @ResponseBody 등 처리)
    ↓
[HttpMessageConverter] (JSON 변환 등)
    ↓
[Response 작성]
```

**사용되는 컴포넌트**:
- `HandlerMapping`: `RequestMappingHandlerMapping`
- `HandlerAdapter`: `RequestMappingHandlerAdapter`
- `Handler`: `HandlerMethod` (컨트롤러 메서드 메타데이터)

**특징**:
- 어노테이션 기반 매핑 (`@GetMapping`, `@PostMapping` 등)
- 리플렉션을 통한 메서드 호출
- `ReturnValueHandler`를 통한 반환값 처리

---

##### RouterFunction을 사용한 경우

```
[DispatcherHandler]
    ↓
[RouterFunctionMapping]  ← RouterFunction 빈들을 순회하며 매칭
    ↓ RouterFunction.match() 호출
[Optional<HandlerFunction>] (매칭된 HandlerFunction)
    ↓
[HandlerFunctionAdapter]  ← HandlerFunction 실행
    ↓
[HandlerFunction.handle(ServerRequest)] 직접 호출
    ↓
[Mono<ServerResponse>] 반환
    ↓
[ServerResponse.writeTo()] (내부적으로 이미 완성된 응답)
    ↓
[Response 작성]
```

**사용되는 컴포넌트**:
- `HandlerMapping`: `RouterFunctionMapping`
- `HandlerAdapter`: `HandlerFunctionAdapter`
- `Handler`: `HandlerFunction<ServerResponse>`

**특징**:
- 함수형 프로그래밍 스타일
- 직접적인 함수 호출 (리플렉션 최소화)
- `ServerResponse`가 이미 완성된 상태로 반환

---

##### 상세 비교

| 항목 | @Controller | RouterFunction |
|------|------------|----------------|
| **HandlerMapping** | `RequestMappingHandlerMapping` | `RouterFunctionMapping` |
| **HandlerAdapter** | `RequestMappingHandlerAdapter` | `HandlerFunctionAdapter` |
| **Handler 타입** | `HandlerMethod` | `HandlerFunction<ServerResponse>` |
| **매핑 방식** | 어노테이션 스캔 | RouterFunction 빈 순회 |
| **실행 방식** | 리플렉션으로 메서드 호출 | 함수 직접 호출 |
| **반환값 처리** | `ReturnValueHandler` 체인 | `ServerResponse` 직접 반환 |
| **성능** | 약간 느림 (리플렉션 오버헤드) | 약간 빠름 (직접 호출) |
| **타입 안정성** | 런타임 체크 | 컴파일 타임 체크 |

---

##### RouterFunctionMapping의 동작 방식

```java
public class RouterFunctionMapping implements HandlerMapping {
    
    private final List<RouterFunction<ServerResponse>> routerFunctions;
    
    @Override
    public Mono<Object> getHandler(ServerWebExchange exchange) {
        // 등록된 모든 RouterFunction을 순회
        for (RouterFunction<ServerResponse> routerFunction : routerFunctions) {
            // RouterFunction.match()로 요청 매칭 시도
            RequestPredicate.Result result = routerFunction.route(exchange.getRequest());
            
            if (result.isMatch()) {
                // 매칭되면 HandlerFunction 반환
                HandlerFunction<ServerResponse> handlerFunction = result.handlerFunction();
                return Mono.just(handlerFunction);
            }
        }
        
        // 매칭되는 것이 없으면 빈 Mono
        return Mono.empty();
    }
}
```

**핵심**:
1. 모든 `RouterFunction` 빈을 순회
2. 각 `RouterFunction`의 `route()` 메서드로 요청 매칭
3. 매칭되면 `HandlerFunction` 반환
4. 매칭 안 되면 다음 `RouterFunction` 확인

---

##### HandlerFunctionAdapter의 동작 방식

```java
public class HandlerFunctionAdapter implements HandlerAdapter {
    
    @Override
    public boolean supports(Object handler) {
        // HandlerFunction인지 확인
        return handler instanceof HandlerFunction;
    }
    
    @Override
    public Mono<HandlerResult> handle(
            ServerWebExchange exchange, 
            Object handler) {
        
        HandlerFunction<ServerResponse> handlerFunction = 
            (HandlerFunction<ServerResponse>) handler;
        
        // ServerRequest 생성
        ServerRequest serverRequest = ServerRequest.create(
            exchange, 
            messageReaders
        );
        
        // HandlerFunction 직접 호출
        Mono<ServerResponse> responseMono = handlerFunction.handle(serverRequest);
        
        // HandlerResult로 래핑
        return responseMono.map(response -> 
            new HandlerResult(handlerFunction, response, returnType)
        );
    }
}
```

**핵심**:
1. `HandlerFunction`인지 확인
2. `ServerRequest` 생성
3. `HandlerFunction.handle()` 직접 호출
4. `Mono<ServerResponse>` 반환

---

##### 전체 흐름 비교 다이어그램

**@Controller 흐름**:
```
DispatcherHandler
    ↓
RequestMappingHandlerMapping
    → @GetMapping("/api/users") 스캔
    → HandlerMethod 찾음
    ↓
RequestMappingHandlerAdapter
    → 리플렉션으로 메서드 호출
    → UserController.getUser() 실행
    ↓
RequestResponseBodyMethodProcessor
    → ResponseEntity 처리
    ↓
HttpMessageConverter
    → JSON 변환
    ↓
Response 작성
```

**RouterFunction 흐름**:
```
DispatcherHandler
    ↓
RouterFunctionMapping
    → RouterFunction 빈 순회
    → RouterFunction.route()로 매칭
    → HandlerFunction 찾음
    ↓
HandlerFunctionAdapter
    → HandlerFunction.handle() 직접 호출
    → UserHandler.getUser() 실행
    ↓
ServerResponse (이미 완성됨)
    → bodyValue(), body() 등으로 구성 완료
    ↓
Response 작성 (내부적으로 처리)
```

---

##### 공통점과 차이점 요약

**공통점**:
- ✅ 둘 다 `DispatcherHandler`를 거침
- ✅ 둘 다 `HandlerMapping` → `HandlerAdapter` 패턴 사용
- ✅ 둘 다 리액티브 스트림 (`Mono`/`Flux`) 반환
- ✅ 둘 다 `ExceptionHandlingWebHandler`에서 예외 처리

**차이점**:
- `@Controller`: RequestMappingHandlerMapping + RequestMappingHandlerAdapter`
- `RouterFunction: RouterFunctionMapping + HandlerFunctionAdapter`

**실제 코드에서의 차이**:

```java
// @Controller 방식
@RestController
public class UserController {
    @GetMapping("/api/users/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable String id) {
        // RequestMappingHandlerAdapter가 리플렉션으로 호출
        return userService.findById(id)
            .map(ResponseEntity::ok);
    }
}

// RouterFunction 방식
@Configuration
public class RouterConfig {
    @Bean
    public RouterFunction<ServerResponse> userRouter(UserHandler handler) {
        return RouterFunctions.route()
            .GET("/api/users/{id}", handler::getUser)  // 직접 함수 참조
            .build();
    }
}

@Component
public class UserHandler {
    public Mono<ServerResponse> getUser(ServerRequest request) {
        // HandlerFunctionAdapter가 직접 호출
        String id = request.pathVariable("id");
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok().bodyValue(user));
    }
}
```

---

#### 4. ExceptionHandlingWebHandler의 역할

**ExceptionHandlingWebHandler 구조**:
```java
public class ExceptionHandlingWebHandler implements WebHandler {
    
    private final WebHandler delegate;
    private final List<WebExceptionHandler> exceptionHandlers;
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange) {
        return delegate.handle(exchange)
            .onErrorResume(throwable -> {
                // 등록된 WebExceptionHandler들을 순서대로 실행
                for (WebExceptionHandler handler : exceptionHandlers) {
                    if (handler.canHandle(exchange, throwable)) {
                        return handler.handle(exchange, throwable);
                    }
                }
                // 처리할 수 없으면 예외를 다시 던짐
                return Mono.error(throwable);
            });
    }
}
```

**동작 방식**:
1. 내부 `delegate` (DispatcherHandler)가 요청 처리
2. 예외 발생 시 `onErrorResume`으로 잡음
3. 등록된 `WebExceptionHandler` 목록을 순회
4. 각 핸들러가 예외를 처리할 수 있는지 확인
5. 처리 가능한 핸들러가 응답 작성

---

#### 5. 우선순위 설정과 동작

**@Order 어노테이션으로 우선순위 설정**:

```java
@Order(-2)  // 낮은 숫자 = 높은 우선순위
@Component
public class GlobalErrorHandler implements WebExceptionHandler {
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 예외 처리 로직
    }
}
```

**우선순위 순서**:

| Order | Handler | 설명 |
|-------|---------|------|
| **-2** | Custom GlobalErrorHandler | 사용자 정의 전역 예외 핸들러 |
| **-1** | DefaultErrorWebExceptionHandler | Spring Boot 기본 예외 핸들러 |
| **0+** | 기타 핸들러 | 추가 예외 핸들러들 |

**동작 원리**:
- `ExceptionHandlingWebHandler`는 `@Order` 값이 낮은 순서대로 핸들러를 실행
- 첫 번째로 예외를 처리한 핸들러가 응답을 작성하면 종료
- 이후 핸들러는 실행되지 않음

**예시**:
```java
// 우선순위 -2: 가장 먼저 실행
@Order(-2)
@Component
public class GlobalErrorHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 비즈니스 예외 처리
        if (ex instanceof BusinessException) {
            return handleBusinessException(exchange, (BusinessException) ex);
        }
        // 처리 못하면 다음 핸들러로 전달 (Mono.error로 다시 던짐)
        return Mono.error(ex);
    }
}

// 우선순위 -1: GlobalErrorHandler가 처리 못한 경우 실행
@Order(-1)
@Component
public class DefaultErrorWebExceptionHandler implements WebExceptionHandler {
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 모든 예외를 기본 형식으로 처리
        return handleDefaultException(exchange, ex);
    }
}
```

---

#### 6. 비동기 예외 처리 메커니즘

**리액티브 스트림에서의 예외 전파**:

```java
// 예외 발생 예시
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
        .flatMap(user -> {
            if (user.isDeleted()) {
                return Mono.error(new UserDeletedException("User is deleted"));
            }
            return Mono.just(user);
        });
}

// Controller에서 사용
@GetMapping("/users/{id}")
public Mono<ResponseEntity<User>> getUser(@PathVariable String id) {
    return userService.getUser(id)
        .map(ResponseEntity::ok)
        // 여기서 예외가 발생하면 onError 시그널로 전파됨
        // try-catch가 아니라 onError 시그널!
}
```

**예외 전파 흐름**:

```
[Service.getUser()]
    ↓ Mono.error(new UserNotFoundException())
    ↓ onError 시그널 발생
[Controller]
    ↓ onError 시그널 전파 (try-catch 없음!)
[DispatcherHandler]
    ↓ onError 시그널 전파
[ExceptionHandlingWebHandler]
    ↓ onErrorResume으로 잡음
[GlobalErrorHandler.handle()]
    ↓ 예외 처리 및 응답 작성
[HTTP Response]
```

**핵심 차이점**:

| 항목 | Spring MVC | WebFlux |
|------|-----------|---------|
| **예외 전파** | call stack 위로 전파 | onError 시그널로 전파 |
| **예외 처리** | try-catch 블록 | onErrorResume, onErrorReturn |
| **스레드** | 같은 스레드 | 비동기, 다른 스레드 가능 |
| **응답 상태** | 예외 발생 시점에 결정 | WebExceptionHandler에서 결정 |

---

#### 7. 실제 구현 예시

**완전한 GlobalErrorHandler 구현**:

```java
@Order(-2)
@Component
@Slf4j
public class GlobalErrorHandler implements WebExceptionHandler {
    
    private final ObjectMapper objectMapper;
    
    public GlobalErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        log.error("Exception occurred: {}", ex.getMessage(), ex);
        
        // 예외 타입에 따라 분기 처리
        if (ex instanceof BusinessException) {
            return handleBusinessException(exchange, (BusinessException) ex);
        } else if (ex instanceof ValidationException) {
            return handleValidationException(exchange, (ValidationException) ex);
        } else if (ex instanceof ExternalApiException) {
            return handleExternalApiException(exchange, (ExternalApiException) ex);
        } else if (ex instanceof WebClientResponseException) {
            return handleWebClientException(exchange, (WebClientResponseException) ex);
        } else {
            return handleGenericException(exchange, ex);
        }
    }
    
    private Mono<Void> handleBusinessException(
            ServerWebExchange exchange, 
            BusinessException ex) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(exchange.getRequest().getPath().value())
            .build();
        
        return writeResponse(
            exchange, 
            HttpStatus.BAD_REQUEST, 
            errorResponse
        );
    }
    
    private Mono<Void> handleValidationException(
            ServerWebExchange exchange, 
            ValidationException ex) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("VALIDATION_ERROR")
            .message(ex.getMessage())
            .errors(ex.getFieldErrors())
            .timestamp(LocalDateTime.now())
            .path(exchange.getRequest().getPath().value())
            .build();
        
        return writeResponse(
            exchange, 
            HttpStatus.BAD_REQUEST, 
            errorResponse
        );
    }
    
    private Mono<Void> handleExternalApiException(
            ServerWebExchange exchange, 
            ExternalApiException ex) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("EXTERNAL_API_ERROR")
            .message("External API call failed")
            .details(ex.getDetails())
            .timestamp(LocalDateTime.now())
            .path(exchange.getRequest().getPath().value())
            .build();
        
        return writeResponse(
            exchange, 
            HttpStatus.SERVICE_UNAVAILABLE, 
            errorResponse
        );
    }
    
    private Mono<Void> handleWebClientException(
            ServerWebExchange exchange, 
            WebClientResponseException ex) {
        
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("EXTERNAL_SERVICE_ERROR")
            .message(ex.getMessage())
            .timestamp(LocalDateTime.now())
            .path(exchange.getRequest().getPath().value())
            .build();
        
        return writeResponse(exchange, status, errorResponse);
    }
    
    private Mono<Void> handleGenericException(
            ServerWebExchange exchange, 
            Throwable ex) {
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .code("INTERNAL_ERROR")
            .message("An unexpected error occurred")
            .timestamp(LocalDateTime.now())
            .path(exchange.getRequest().getPath().value())
            .build();
        
        // 프로덕션에서는 상세 에러 메시지 숨김
        if (log.isDebugEnabled()) {
            errorResponse.setMessage(ex.getMessage());
        }
        
        return writeResponse(
            exchange, 
            HttpStatus.INTERNAL_SERVER_ERROR, 
            errorResponse
        );
    }
    
    private Mono<Void> writeResponse(
            ServerWebExchange exchange, 
            HttpStatus status, 
            Object body) {
        
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(
            HttpHeaders.CONTENT_TYPE, 
            MediaType.APPLICATION_JSON_VALUE
        );
        
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error writing error response", e);
            return Mono.error(e);
        }
    }
}
```

**에러 응답 DTO**:

```java
@Builder
@Getter
@Setter
public class ErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private String path;
    private List<FieldError> errors;
    private Map<String, Object> details;
}
```

---

#### 8. 예외 처리 순서와 상태

**예외 처리 시점의 상태**:

| 항목 | 상태 | 설명 |
|------|------|------|
| **Subscriber** | 살아 있음 | 예외가 발생했지만 Subscriber는 아직 존재 |
| **Reactor Context** | 유지됨 | Context 정보는 그대로 유지 |
| **MDC** | 있을 수도 / 없을 수도 | 로깅 필터에 따라 다름 |
| **Response** | 아직 안 써짐 | 응답은 아직 작성되지 않음 |
| **Request** | 읽을 수 있음 | 요청 정보는 여전히 접근 가능 |

**예외 처리 순서**:

```
1. 에러 발생
   Mono.error(new BusinessException(...))
   ⬇️
   
2. onError(BusinessException) 시그널 발생
   ⬇️
   
3. GlobalErrorHandler.handle() 호출
   public Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
   ⬇️
   이 시점의 상태:
   - Subscriber: 살아 있음
   - Reactor Context: 유지됨
   - Response: 아직 안 써짐
   ⬇️
   
4. 예외 분류
   if (ex instanceof BusinessException)
   ⬇️
   (동기 코드처럼 보이지만 실제로는 Subscriber 위에서 실행)
   ⬇️
   
5. Response 작성
   exchange.getResponse().writeWith(...)
   ⬇️
   이 순간:
   - HTTP Response 직접 완성
   - Controller로 다시 안 돌아감
   - 스트림 종료
   ⬇️
   
6. 이후 체인
   onError → handled → onComplete
```

---

#### 9. doOnError vs WebExceptionHandler 비교

**doOnError**:
- **위치**: Subscriber 내부
- **역할**: 관찰 / 로깅
- **에러 소비**: ❌ (흘려보냄)
- **실행 시점**: onError 시그널 중간

```java
// doOnError 예시
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .doOnError(ex -> {
            // 로깅만 하고 예외는 그대로 전파
            log.error("Error finding user: {}", ex.getMessage());
        })
        // 예외는 여전히 전파됨
        .switchIfEmpty(Mono.error(new UserNotFoundException()));
}
```

**WebExceptionHandler**:
- **위치**: Subscriber 외부
- **역할**: 응답 생성
- **에러 소비**: ✅ (종결)
- **실행 시점**: 최종

```java
// WebExceptionHandler는 예외를 소비하고 응답을 작성
@Override
public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    // 예외를 소비하고 응답 작성
    return writeErrorResponse(exchange, ex);
}
```

**사용 시나리오**:

| 용도 | 사용 방법 |
|------|----------|
| **로깅** | `doOnError` 사용 |
| **응답 생성** | `WebExceptionHandler` 사용 |
| **예외 변환** | `onErrorMap` 사용 |
| **기본값 반환** | `onErrorReturn` 사용 |

---

#### 10. 예외 처리 모범 사례

**1. 예외 계층 구조 설계**:

```java
// 기본 예외 클래스
public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    
    public BaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

// 비즈니스 예외
public class BusinessException extends BaseException {
    public BusinessException(String errorCode, String message) {
        super(errorCode, message);
    }
}

// 검증 예외
public class ValidationException extends BaseException {
    private final List<FieldError> fieldErrors;
    
    public ValidationException(String message, List<FieldError> fieldErrors) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = fieldErrors;
    }
    
    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
```

**2. 예외 처리 체인 구성**:

```java
@Order(-2)
@Component
public class GlobalErrorHandler implements WebExceptionHandler {
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 1. 로깅
        logError(exchange, ex);
        
        // 2. 예외 분류 및 처리
        return handleException(exchange, ex)
            // 3. 에러 응답 작성 실패 시 대체 처리
            .onErrorResume(e -> {
                log.error("Failed to write error response", e);
                return writeFallbackResponse(exchange);
            });
    }
}
```

**3. Reactor Context 활용**:

```java
@Override
public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    // Reactor Context에서 traceId 추출
    String traceId = exchange.getAttributeOrDefault(
        "traceId", 
        "unknown"
    );
    
    ErrorResponse errorResponse = ErrorResponse.builder()
        .code("ERROR")
        .message(ex.getMessage())
        .traceId(traceId)  // Context 정보 포함
        .build();
    
    return writeResponse(exchange, HttpStatus.INTERNAL_SERVER_ERROR, errorResponse);
}
```

---

#### 11. throw vs Mono.error: WebFlux 예외 처리 방식 비교

**핵심 차이**: WebFlux 리액티브 스트림에서는 **예외를 던지는 것(`throw`)과 `Mono.error()`를 반환하는 것**이 완전히 다르게 동작합니다.

---

##### 11-1. throw new Exception()의 문제점

**일반적인 예외 던지기**:

```java
// ❌ 나쁜 예: throw 사용
public Mono<User> getUser(String id) {
    if (id == null || id.isEmpty()) {
        throw new IllegalArgumentException("ID cannot be null or empty");
    }
    
    return userRepository.findById(id)
        .switchIfEmpty(throw new UserNotFoundException("User not found"));
}
```

**문제점**:
1. **즉시 실행**: `throw`는 메서드가 호출되는 즉시 예외를 던짐
2. **리액티브 체인 깨짐**: 리액티브 스트림이 구독되기 전에 예외가 발생
3. **예외 처리 불가**: `onError` 시그널로 전파되지 않음
4. **WebExceptionHandler 미작동**: 예외가 리액티브 체인 밖에서 발생하여 잡히지 않음

**실제 동작**:
```java
// 호출 시점에 즉시 예외 발생
Mono<User> userMono = userService.getUser(null);  // 여기서 예외 발생!
// 위 코드 실행 시점에 IllegalArgumentException이 던져짐
// 리액티브 체인은 생성되지도 않음
```

---

##### 11-2. Mono.error()의 올바른 사용

**리액티브 예외 처리**:

```java
// ✅ 좋은 예: Mono.error() 사용
public Mono<User> getUser(String id) {
    if (id == null || id.isEmpty()) {
        return Mono.error(new IllegalArgumentException("ID cannot be null or empty"));
    }
    
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")));
}
```

**장점**:
1. **지연 실행**: `Mono.error()`는 구독 시점에 예외를 발생시킴
2. **리액티브 체인 유지**: 리액티브 스트림의 일부로 동작
3. **onError 시그널**: 예외가 `onError` 시그널로 전파됨
4. **WebExceptionHandler 작동**: 예외가 리액티브 체인 내에서 발생하여 잡힘

**실제 동작**:
```java
// Mono.error()는 Mono를 반환 (아직 예외 발생 안 함)
Mono<User> userMono = userService.getUser(null);  // 여기서는 예외 발생 안 함

// 구독 시점에 예외 발생
userMono.subscribe(
    user -> System.out.println(user),
    error -> System.out.println("Error: " + error)  // 여기서 예외 처리
);
```

---

##### 11-3. 상세 비교

| 항목 | `throw new Exception()` | `Mono.error(new Exception())` |
|------|------------------------|-------------------------------|
| **실행 시점** | 메서드 호출 즉시 | 구독 시점 |
| **리액티브 체인** | 깨짐 | 유지됨 |
| **예외 전파** | call stack 위로 | onError 시그널로 |
| **WebExceptionHandler** | 작동 안 함 | 작동함 |
| **에러 핸들링** | try-catch 필요 | onErrorResume, onErrorReturn 사용 |
| **비동기 처리** | 불가능 | 가능 |

---

##### 11-4. 실제 사용 예시

**예시 1: 검증 로직**

```java
// ❌ 나쁜 예
public Mono<User> createUser(User user) {
    if (user == null) {
        throw new IllegalArgumentException("User cannot be null");
    }
    if (user.getEmail() == null) {
        throw new IllegalArgumentException("Email cannot be null");
    }
    return userRepository.save(user);
}

// ✅ 좋은 예
public Mono<User> createUser(User user) {
    return Mono.just(user)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("User cannot be null")))
        .filter(u -> u.getEmail() != null)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Email cannot be null")))
        .flatMap(userRepository::save);
}
```

**예시 2: 비즈니스 로직**

```java
// ❌ 나쁜 예
public Mono<Order> processOrder(Order order) {
    if (order.getItems().isEmpty()) {
        throw new BusinessException("Order must have at least one item");
    }
    
    return inventoryService.checkStock(order)
        .flatMap(available -> {
            if (!available) {
                throw new BusinessException("Insufficient stock");
            }
            return orderService.save(order);
        });
}

// ✅ 좋은 예
public Mono<Order> processOrder(Order order) {
    return Mono.just(order)
        .filter(o -> !o.getItems().isEmpty())
        .switchIfEmpty(Mono.error(new BusinessException("Order must have at least one item")))
        .flatMap(inventoryService::checkStock)
        .filter(Boolean::booleanValue)
        .switchIfEmpty(Mono.error(new BusinessException("Insufficient stock")))
        .thenReturn(order)
        .flatMap(orderService::save);
}
```

**예시 3: 중첩된 예외 처리**

```java
// ✅ 좋은 예: 중첩된 예외 처리
public Mono<User> getUserWithProfile(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found: " + id)))
        .flatMap(user -> 
            profileService.getProfile(user.getId())
                .map(profile -> {
                    user.setProfile(profile);
                    return user;
                })
                .onErrorResume(ProfileNotFoundException.class, e -> {
                    // 프로필이 없어도 사용자는 반환
                    log.warn("Profile not found for user: {}", user.getId());
                    return Mono.just(user);
                })
        )
        .onErrorMap(DataAccessException.class, e -> 
            new ServiceException("Database error", e)
        );
}
```

---

##### 11-5. 예외 변환 패턴

**onErrorMap**: 예외 타입 변환

```java
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
        .onErrorMap(DataAccessException.class, e -> 
            new ServiceException("Database access failed", e)
        )
        .onErrorMap(IllegalArgumentException.class, e -> 
            new BusinessException("Invalid user ID", e)
        );
}
```

**onErrorResume**: 예외를 다른 Mono로 대체

```java
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
        .onErrorResume(UserNotFoundException.class, e -> {
            // 예외 발생 시 기본 사용자 반환
            return Mono.just(User.defaultUser());
        })
        .onErrorResume(DataAccessException.class, e -> {
            // DB 에러 시 캐시에서 조회
            return cacheService.getUser(id);
        });
}
```

**onErrorReturn**: 예외 발생 시 기본값 반환

```java
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
        .onErrorReturn(User.defaultUser())  // 모든 예외에 대해 기본값 반환
        .onErrorReturn(UserNotFoundException.class, User.anonymousUser());  // 특정 예외만
}
```

---

##### 11-6. Controller/Handler에서의 예외 처리

**@Controller에서의 예외 처리**:

```java
// ❌ 나쁜 예
@GetMapping("/users/{id}")
public Mono<ResponseEntity<User>> getUser(@PathVariable String id) {
    if (id == null) {
        throw new IllegalArgumentException("ID cannot be null");
    }
    return userService.findById(id)
        .map(ResponseEntity::ok);
}

// ✅ 좋은 예
@GetMapping("/users/{id}")
public Mono<ResponseEntity<User>> getUser(@PathVariable String id) {
    return Mono.just(id)
        .filter(i -> i != null && !i.isEmpty())
        .switchIfEmpty(Mono.error(new IllegalArgumentException("ID cannot be null")))
        .flatMap(userService::findById)
        .map(ResponseEntity::ok)
        .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
}
```

**RouterFunction/HandlerFunction에서의 예외 처리**:

```java
// ✅ 좋은 예
public Mono<ServerResponse> getUser(ServerRequest request) {
    String id = request.pathVariable("id");
    
    return Mono.just(id)
        .filter(i -> i != null && !i.isEmpty())
        .switchIfEmpty(Mono.error(new IllegalArgumentException("ID cannot be null")))
        .flatMap(userService::findById)
        .flatMap(user -> ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(user))
        .switchIfEmpty(ServerResponse.notFound().build())
        .onErrorResume(IllegalArgumentException.class, e -> 
            ServerResponse.badRequest()
                .bodyValue(Map.of("error", e.getMessage()))
        );
}
```

---

##### 11-7. 블로킹 코드에서의 예외 처리

**블로킹 코드를 리액티브로 래핑**:

```java
// 블로킹 코드가 있는 경우
public Mono<String> processBlocking(String input) {
    // ❌ 나쁜 예: 블로킹 코드에서 throw 사용
    try {
        String result = blockingService.process(input);  // 블로킹 호출
        return Mono.just(result);
    } catch (Exception e) {
        throw e;  // 리액티브 체인 깨짐
    }
    
    // ✅ 좋은 예: Mono.fromCallable 사용
    return Mono.fromCallable(() -> blockingService.process(input))
        .onErrorMap(Exception.class, e -> new ServiceException("Processing failed", e));
}

// 또는 subscribeOn으로 별도 스레드에서 실행
public Mono<String> processBlocking(String input) {
    return Mono.fromCallable(() -> blockingService.process(input))
        .subscribeOn(Schedulers.boundedElastic())  // 블로킹 전용 스레드 풀
        .onErrorMap(Exception.class, e -> new ServiceException("Processing failed", e));
}
```

---

##### 11-8. 예외 체인 구성 패턴

**다단계 예외 처리**:

```java
public Mono<Order> processOrder(Order order) {
    return Mono.just(order)
        // 1단계: 입력 검증
        .filter(o -> o != null)
        .switchIfEmpty(Mono.error(new ValidationException("Order cannot be null")))
        .filter(o -> !o.getItems().isEmpty())
        .switchIfEmpty(Mono.error(new ValidationException("Order must have items")))
        
        // 2단계: 재고 확인
        .flatMap(inventoryService::checkStock)
        .filter(Boolean::booleanValue)
        .switchIfEmpty(Mono.error(new InsufficientStockException("Stock unavailable")))
        
        // 3단계: 주문 저장
        .thenReturn(order)
        .flatMap(orderService::save)
        
        // 예외 변환
        .onErrorMap(ValidationException.class, e -> 
            new BusinessException("Validation failed", e))
        .onErrorMap(DataAccessException.class, e -> 
            new ServiceException("Database error", e))
        
        // 최종 예외 처리
        .onErrorResume(ServiceException.class, e -> {
            log.error("Service error", e);
            return Mono.error(e);
        });
}
```

---

##### 11-9. 디버깅 팁

**예외 추적**:

```java
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
        .doOnError(error -> {
            // 예외 발생 시 로깅
            log.error("Error getting user: {}", id, error);
        })
        .doOnSuccess(user -> {
            // 성공 시 로깅
            log.debug("User found: {}", user.getId());
        })
        .onErrorMap(original -> {
            // 예외 래핑하여 컨텍스트 추가
            return new ServiceException("Failed to get user: " + id, original);
        });
}
```

**예외 체인 확인**:

```java
public Mono<User> getUser(String id) {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User not found")))
        .onErrorMap(e -> {
            // 예외 체인 확인
            log.error("Exception chain:", e);
            Throwable cause = e.getCause();
            while (cause != null) {
                log.error("Caused by:", cause);
                cause = cause.getCause();
            }
            return e;
        });
}
```

---

##### 11-10. 핵심 정리

**WebFlux에서 예외 처리 원칙**:

1. **항상 `Mono.error()` 사용**: 리액티브 체인 내에서 예외 발생
2. **`throw` 사용 금지**: 리액티브 체인 밖에서 예외 발생하여 처리 불가
3. **예외 변환 활용**: `onErrorMap`으로 예외 타입 변환
4. **예외 복구**: `onErrorResume`으로 예외 발생 시 대체 로직 실행
5. **기본값 제공**: `onErrorReturn`으로 예외 발생 시 기본값 반환

**예외 처리 체인 구성**:
```
Mono.just(data)
    .filter(...)
    .switchIfEmpty(Mono.error(...))  // 검증 실패
    .flatMap(...)
    .onErrorMap(...)                 // 예외 변환
    .onErrorResume(...)              // 예외 복구
    .onErrorReturn(...)              // 기본값 반환
    .doOnError(...)                  // 로깅
```

**실무 권장 사항**:
- ✅ 모든 예외는 `Mono.error()`로 반환
- ✅ 검증 실패는 `switchIfEmpty(Mono.error(...))` 사용
- ✅ 예외 타입별로 `onErrorMap`으로 변환
- ✅ 복구 가능한 예외는 `onErrorResume` 사용
- ✅ 로깅은 `doOnError` 사용
- ❌ 절대 `throw` 사용 금지 (리액티브 체인 내에서)

---

#### 12. 핵심 정리

**WebFlux 예외 처리의 특징**:

1. **비동기 처리**: 모든 예외 처리는 `Mono`로 래핑되어 비동기로 실행
2. **시그널 기반**: `onError` 시그널로 예외 전파 (call stack 아님)
3. **우선순위 기반**: `@Order`로 핸들러 실행 순서 제어
4. **응답 직접 작성**: `WebExceptionHandler`에서 HTTP 응답을 직접 작성
5. **컨텍스트 유지**: Reactor Context는 예외 발생 후에도 유지됨

**예외 처리 흐름**:
```
예외 발생 → onError 시그널 → ExceptionHandlingWebHandler 
→ WebExceptionHandler 목록 순회 → 첫 번째 처리 핸들러가 응답 작성
```

**권장 사항**:
- ✅ `@Order(-2)`로 사용자 정의 핸들러를 최우선으로 설정
- ✅ 예외 타입별로 분기 처리
- ✅ 일관된 에러 응답 형식 사용
- ✅ 로깅은 `doOnError`, 응답 생성은 `WebExceptionHandler`로 분리

### Webflux router와 handler, ServerRequest와 ServerResponse

#### 1. RouterFunction과 HandlerFunction 개요

WebFlux는 **함수형 프로그래밍 스타일**의 라우팅을 제공합니다.
**함수형 프로그래밍의 특징**
  + 순수 함수
  + 불변성
  + 부작용 피하기
**자바에서 보여지는 기능들**
  + 람다식
  + 함수형 인터페이스 @FunctionalInterface ***복습하기*** vs 익명클래스
  + 스트림 API
  + 메소드 참조

**핵심 인터페이스**:
- `RouterFunction<T>`: 라우팅 규칙 정의 (어떤 요청을 어떤 핸들러로 보낼지)
- `HandlerFunction<T>`: 실제 요청 처리 로직 (ServerRequest → Mono<ServerResponse>)

**기본 구조**:
```java
RouterFunction<ServerResponse> route = RouterFunctions.route()
    .GET("/api/users", handlerFunction)
    .POST("/api/users", handlerFunction)
    .build();
```

---

#### 2. RouterFunction 상세

**RouterFunction의 역할**:
1. HTTP 요청과 HandlerFunction을 매핑
2. 요청 조건 체크 (경로, 메서드, 헤더, 쿼리 파라미터 등)
3. 조건에 맞는 HandlerFunction 반환

**RouterFunction 생성 방법**:

```java
// 방법 1: RouterFunctions.route() 사용
RouterFunction<ServerResponse> route = RouterFunctions.route()
    .GET("/api/users/{id}", request -> {
        String id = request.pathVariable("id");
        return ServerResponse.ok()
            .bodyValue(new User(id, "John"));
    })
    .POST("/api/users", request -> {
        return request.bodyToMono(User.class)
            .flatMap(user -> {
                // 저장 로직
                return ServerResponse.ok().build();
            });
    })
    .build();

// 방법 2: RouterFunctions.route() + RequestPredicate
RouterFunction<ServerResponse> route = RouterFunctions.route()
    .GET("/api/users", 
        RequestPredicates.accept(MediaType.APPLICATION_JSON),
        handlerFunction)
    .build();

// 방법 3: RouterFunctions.nest() - 경로 그룹핑
RouterFunction<ServerResponse> route = RouterFunctions.nest(
    RequestPredicates.path("/api"),
    RouterFunctions.route()
        .GET("/users", userHandler)
        .GET("/products", productHandler)
        .build()
);
```

**RouterFunction 빈 등록**:
```java
@Configuration
public class RouterConfig {
    
    @Bean
    public RouterFunction<ServerResponse> userRouter(UserHandler userHandler) {
        return RouterFunctions.route()
            .GET("/api/users/{id}", userHandler::getUser)
            .POST("/api/users", userHandler::createUser)
            .PUT("/api/users/{id}", userHandler::updateUser)
            .DELETE("/api/users/{id}", userHandler::deleteUser)
            .build();
    }
}
```

---

#### 3. HandlerFunction 상세

**HandlerFunction 인터페이스**:
```java
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);
}
```

**핵심**: `HandlerFunction`은 함수형 인터페이스이므로 람다나 메서드 참조로 구현할 수 있습니다.

---

**HandlerFunction 구현 방법**:

##### 방법 1: 람다 표현식으로 직접 생성

람다 표현식을 사용하여 `HandlerFunction`을 직접 생성합니다.

```java
// HandlerFunction을 람다로 직접 생성
HandlerFunction<ServerResponse> getUserHandler = request -> {
    String id = request.pathVariable("id");
    User user = userService.findById(id);
    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(user);
};

// RouterFunction에서 사용
@Configuration
public class RouterConfig {
    
    private final UserService userService;
    
    @Bean
    public RouterFunction<ServerResponse> userRouter() {
        return RouterFunctions.route()
            // 람다로 직접 HandlerFunction 생성
            .GET("/api/users/{id}", request -> {
                String id = request.pathVariable("id");
                return userService.findById(id)
                    .flatMap(user -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(user))
                    .switchIfEmpty(ServerResponse.notFound().build());
            })
            .POST("/api/users", request -> {
                return request.bodyToMono(User.class)
                    .flatMap(userService::save)
                    .flatMap(savedUser -> ServerResponse
                        .status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(savedUser));
            })
            .build();
    }
}
```

**특징**:
- ✅ 간단한 로직에 적합
- ✅ 별도 클래스 불필요
- ⚠️ 복잡한 로직은 가독성 저하

---

##### 방법 2: 별도 클래스로 분리 (2가지 방식)

###### 방법 2-1: HandlerFunction 인터페이스를 직접 구현

`HandlerFunction` 인터페이스를 직접 구현하는 클래스를 만듭니다.

```java
// HandlerFunction 인터페이스를 직접 구현
@Component
public class GetUserHandler implements HandlerFunction<ServerResponse> {
    
    private final UserService userService;
    
    public GetUserHandler(UserService userService) {
        this.userService = userService;
    }
    
    @Override
    public Mono<ServerResponse> handle(ServerRequest request) {
        String id = request.pathVariable("id");
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}

// RouterFunction에서 사용
@Configuration
public class RouterConfig {
    
    @Bean
    public RouterFunction<ServerResponse> userRouter(GetUserHandler getUserHandler) {
        return RouterFunctions.route()
            // HandlerFunction 구현체를 직접 사용
            .GET("/api/users/{id}", getUserHandler)
            .build();
    }
}
```

**특징**:
- ✅ 명시적으로 `HandlerFunction` 구현
- ✅ 하나의 핸들러 = 하나의 엔드포인트
- ⚠️ 엔드포인트마다 클래스 생성 필요

---

###### 방법 2-2: 일반 클래스의 메서드를 메서드 참조로 사용 (가장 일반적)

일반 클래스의 메서드를 정의하고, 메서드 참조를 통해 `HandlerFunction`으로 변환합니다.

```java
// 일반 클래스 (HandlerFunction 인터페이스 구현 안 함)
@Component
public class UserHandler {
    
    private final UserService userService;
    
    public UserHandler(UserService userService) {
        this.userService = userService;
    }
    
    // 메서드 시그니처: ServerRequest → Mono<ServerResponse>
    // 이 시그니처가 HandlerFunction.handle()과 동일하므로 메서드 참조 가능
    public Mono<ServerResponse> getUser(ServerRequest request) {
        String id = request.pathVariable("id");
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
    
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(User.class)
            .flatMap(userService::save)
            .flatMap(savedUser -> ServerResponse
                .status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(savedUser))
            .onErrorResume(ValidationException.class, e -> 
                ServerResponse.badRequest()
                    .bodyValue(Map.of("error", e.getMessage())));
    }
    
    public Mono<ServerResponse> getAllUsers(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(userService.findAll(), User.class);
    }
    
    public Mono<ServerResponse> updateUser(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(User.class)
            .flatMap(user -> userService.update(id, user))
            .flatMap(updatedUser -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updatedUser))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
    
    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        String id = request.pathVariable("id");
        return userService.delete(id)
            .then(ServerResponse.noContent().build())
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}
```

**RouterFunction에서 메서드 참조 사용**:

```java
@Configuration
public class RouterConfig {
    
    @Bean
    public RouterFunction<ServerResponse> userRouter(UserHandler userHandler) {
        return RouterFunctions.route()
            // 메서드 참조: userHandler::getUser
            // 이는 자동으로 HandlerFunction<ServerResponse>로 변환됨
            // 왜? getUser 메서드의 시그니처가 HandlerFunction.handle()과 동일하기 때문
            .GET("/api/users/{id}", userHandler::getUser)
            .GET("/api/users", userHandler::getAllUsers)
            .POST("/api/users", userHandler::createUser)
            .PUT("/api/users/{id}", userHandler::updateUser)
            .DELETE("/api/users/{id}", userHandler::deleteUser)
            .build();
    }
}
```

**메서드 참조가 HandlerFunction으로 변환되는 이유**:

```java
// HandlerFunction 인터페이스
@FunctionalInterface
public interface HandlerFunction<T extends ServerResponse> {
    Mono<T> handle(ServerRequest request);  // 시그니처
}

// UserHandler의 메서드
public Mono<ServerResponse> getUser(ServerRequest request) {
    // 시그니처가 동일: ServerRequest → Mono<ServerResponse>
}

// 따라서 메서드 참조 userHandler::getUser는
// HandlerFunction<ServerResponse>로 자동 변환됨
HandlerFunction<ServerResponse> handler = userHandler::getUser;
// 위 코드는 아래와 동일:
HandlerFunction<ServerResponse> handler = request -> userHandler.getUser(request);
```

**특징**:
- ✅ 하나의 클래스에 여러 핸들러 메서드 정의 가능
- ✅ 코드 재사용 및 관리 용이
- ✅ 가장 일반적으로 사용되는 방식
- ✅ 메서드 시그니처만 맞으면 자동으로 `HandlerFunction`으로 변환

---

**방법 비교 요약**:

| 방법 | 사용 시나리오 | 장점 | 단점 |
|------|-------------|------|------|
| **방법 1: 람다** | 간단한 로직, 빠른 프로토타이핑 | 간결함, 별도 클래스 불필요 | 복잡한 로직은 가독성 저하 |
| **방법 2-1: HandlerFunction 구현** | 명시적 타입이 필요한 경우 | 타입 안정성, 명확함 | 엔드포인트마다 클래스 필요 |
| **방법 2-2: 메서드 참조** | 실무에서 가장 일반적 | 유지보수 용이, 코드 재사용 | - |

**권장**: 실무에서는 **방법 2-2 (메서드 참조)**를 가장 많이 사용합니다.

---

#### 4. ServerRequest 상세

**ServerRequest의 역할**:
- HTTP 요청 정보를 리액티브 방식으로 제공
- 요청 본문, 헤더, 쿼리 파라미터, 경로 변수 등 접근

**주요 메서드**:

```java
public interface ServerRequest {
    // HTTP 메서드
    HttpMethod method();
    String methodName();
    
    // URI 정보
    URI uri();
    String path();
    PathContainer pathContainer();
    
    // 헤더
    Headers headers();
    HttpHeaders headers();
    List<String> queryParams(String name);
    MultiValueMap<String, String> queryParams();
    
    // 경로 변수
    Map<String, String> pathVariables();
    String pathVariable(String name);
    
    // 요청 본문 (리액티브)
    <T> Mono<T> bodyToMono(Class<T> elementClass);
    <T> Mono<T> bodyToMono(ParameterizedTypeReference<T> typeReference);
    <T> Flux<T> bodyToFlux(Class<T> elementClass);
    
    // 속성 (요청 범위 데이터 저장)
    Map<String, Object> attributes();
    <T> Optional<T> attribute(String name);
    
    // Principal (인증 정보)
    Mono<? extends Principal> principal();
    
    // Form 데이터
    Mono<MultiValueMap<String, String>> formData();
    Mono<MultiValueMap<String, Part>> multipartData();
}
```

**ServerRequest 사용 예시**:

```java
public Mono<ServerResponse> handleRequest(ServerRequest request) {
    // 1. 경로 변수 추출
    String userId = request.pathVariable("id");
    
    // 2. 쿼리 파라미터
    Optional<String> page = request.queryParam("page");
    String size = request.queryParam("size")
        .orElse("10");
    
    // 3. 헤더 읽기
    String authToken = request.headers()
        .firstHeader("Authorization");
    List<String> acceptHeaders = request.headers()
        .get("Accept");
    
    // 4. 요청 본문 읽기 (Mono)
    Mono<User> userMono = request.bodyToMono(User.class);
    
    // 5. 요청 본문 읽기 (Flux)
    Flux<Order> ordersFlux = request.bodyToFlux(Order.class);
    
    // 6. Form 데이터
    Mono<MultiValueMap<String, String>> formData = request.formData();
    
    // 7. Multipart 데이터
    Mono<MultiValueMap<String, Part>> multipart = request.multipartData();
    
    // 8. 속성 저장/조회
    request.attributes().put("traceId", "12345");
    String traceId = request.attribute("traceId")
        .orElse("unknown");
    
    // 9. Principal (인증된 사용자)
    Mono<String> username = request.principal()
        .cast(Authentication.class)
        .map(Authentication::getName);
    
    return ServerResponse.ok().build();
}
```

**ServerRequest의 리액티브 특성**:
- 모든 본문 읽기 메서드는 `Mono` 또는 `Flux` 반환
- 실제 데이터는 `subscribe()` 시점에 읽힘
- 스트림 방식으로 처리되어 메모리 효율적

---

#### 5. ServerResponse 상세

**ServerResponse의 역할**:
- HTTP 응답을 리액티브 방식으로 구성
- 상태 코드, 헤더, 본문 설정

**주요 메서드**:

```java
public interface ServerResponse {
    // 상태 코드
    HttpStatus statusCode();
    int rawStatusCode();
    
    // 헤더
    HttpHeaders headers();
    
    // 응답 본문
    <T> T body();
    
    // 정적 팩토리 메서드
    static BodyBuilder status(HttpStatus status);
    static BodyBuilder status(int status);
    static BodyBuilder ok();
    static BodyBuilder created(URI location);
    static BodyBuilder noContent();
    static BodyBuilder badRequest();
    static BodyBuilder notFound();
    static BodyBuilder serverError();
    
    // BodyBuilder 인터페이스
    interface BodyBuilder {
        BodyBuilder header(String name, String... values);
        BodyBuilder headers(HttpHeaders headers);
        BodyBuilder contentType(MediaType contentType);
        BodyBuilder allow(HttpMethod... methods);
        BodyBuilder allow(Set<HttpMethod> methods);
        BodyBuilder eTag(String etag);
        BodyBuilder lastModified(ZonedDateTime lastModified);
        BodyBuilder location(URI location);
        BodyBuilder cacheControl(CacheControl cacheControl);
        
        // 본문 설정
        <T> Mono<ServerResponse> bodyValue(T body);
        <T> Mono<ServerResponse> body(Mono<T> body, Class<T> elementClass);
        <T> Mono<ServerResponse> body(Flux<? extends T> body, Class<T> elementClass);
        <T> Mono<ServerResponse> body(Mono<T> body, ParameterizedTypeReference<T> typeReference);
        Mono<ServerResponse> render(String name, Object... modelAttributes);
        Mono<ServerResponse> render(String name, Map<String, ?> model);
    }
}
```

**ServerResponse 사용 예시**:

```java
// 1. 단순 값 반환
return ServerResponse.ok()
    .bodyValue("Hello World");

// 2. 객체 반환 (Mono)
Mono<User> userMono = userService.findById(id);
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .body(userMono, User.class);

// 3. 리스트 반환 (Flux)
Flux<User> usersFlux = userService.findAll();
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .body(usersFlux, User.class);

// 4. 상태 코드 설정
return ServerResponse.status(HttpStatus.CREATED)
    .location(URI.create("/api/users/123"))
    .bodyValue(savedUser);

// 5. 헤더 설정
return ServerResponse.ok()
    .header("X-Custom-Header", "value")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(data);

// 6. 쿠키 설정
return ServerResponse.ok()
    .cookie(ResponseCookie.from("token", "abc123")
        .maxAge(Duration.ofHours(1))
        .httpOnly(true)
        .build())
    .bodyValue("Success");

// 7. 에러 응답
return ServerResponse.badRequest()
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(Map.of("error", "Invalid input"));

// 8. 빈 응답
return ServerResponse.noContent().build();

// 9. 리다이렉트
return ServerResponse.status(HttpStatus.FOUND)
    .location(URI.create("/new-location"))
    .build();

// 10. 커스텀 응답
return ServerResponse.status(299)
    .header("X-Custom-Status", "Custom")
    .bodyValue("Custom response");
```

**ServerResponse의 리액티브 특성**:
- `body()` 메서드는 `Mono<ServerResponse>` 반환
- 실제 응답 쓰기는 `subscribe()` 시점에 수행
- 스트림 방식으로 처리되어 대용량 데이터도 효율적

---

#### 6. 전체 흐름 예시

**완전한 예시 코드**:

```java
@Configuration
public class UserRouterConfig {
    
    @Bean
    public RouterFunction<ServerResponse> userRouter(UserHandler userHandler) {
        return RouterFunctions.route()
            // GET /api/users/{id}
            .GET("/api/users/{id}", 
                RequestPredicates.accept(MediaType.APPLICATION_JSON),
                userHandler::getUser)
            
            // GET /api/users?page=1&size=10
            .GET("/api/users", 
                RequestPredicates.queryParam("page", p -> true),
                userHandler::getAllUsers)
            
            // POST /api/users
            .POST("/api/users", 
                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                userHandler::createUser)
            
            // PUT /api/users/{id}
            .PUT("/api/users/{id}", 
                RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                userHandler::updateUser)
            
            // DELETE /api/users/{id}
            .DELETE("/api/users/{id}", userHandler::deleteUser)
            
            // 필터 적용
            .filter((request, next) -> {
                // 전처리
                log.info("Request: {} {}", request.method(), request.path());
                return next.handle(request)
                    .doOnSuccess(response -> {
                        // 후처리
                        log.info("Response: {}", response.statusCode());
                    });
            })
            .build();
    }
}

@Component
public class UserHandler {
    
    private final UserService userService;
    
    public Mono<ServerResponse> getUser(ServerRequest request) {
        String id = request.pathVariable("id");
        
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build())
            .onErrorResume(Exception.class, e -> 
                ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyValue(Map.of("error", e.getMessage())));
    }
    
    public Mono<ServerResponse> getAllUsers(ServerRequest request) {
        String page = request.queryParam("page").orElse("0");
        String size = request.queryParam("size").orElse("10");
        
        Flux<User> users = userService.findAll(
            Integer.parseInt(page), 
            Integer.parseInt(size));
        
        return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(users, User.class);
    }
    
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(User.class)
            .flatMap(userService::save)
            .flatMap(savedUser -> ServerResponse
                .status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Location", "/api/users/" + savedUser.getId())
                .bodyValue(savedUser))
            .onErrorResume(ValidationException.class, e -> 
                ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("error", e.getMessage())));
    }
    
    public Mono<ServerResponse> updateUser(ServerRequest request) {
        String id = request.pathVariable("id");
        
        return request.bodyToMono(User.class)
            .flatMap(user -> userService.update(id, user))
            .flatMap(updatedUser -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updatedUser))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
    
    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        String id = request.pathVariable("id");
        
        return userService.delete(id)
            .then(ServerResponse.noContent().build())
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}
```

---

#### 7. RouterFunction vs @Controller 비교

| 항목 | RouterFunction | @Controller |
|------|---------------|-------------|
| **스타일** | 함수형 프로그래밍 | 어노테이션 기반 |
| **타입 안정성** | 컴파일 타임 체크 | 런타임 체크 |
| **테스트** | 함수 단위 테스트 용이 | MockMvc 필요 |
| **조건부 라우팅** | RequestPredicate로 유연 | 어노테이션 조합 |
| **성능** | 약간 빠름 (오버헤드 적음) | 약간 느림 |
| **가독성** | 함수형 스타일 선호 시 | 전통적 스타일 선호 시 |

**언제 RouterFunction을 사용할까?**:
- 함수형 스타일 선호
- 타입 안정성이 중요한 경우
- 복잡한 조건부 라우팅이 필요한 경우
- 성능 최적화가 중요한 경우

**언제 @Controller를 사용할까?**:
- 기존 Spring MVC 코드와의 일관성
- 팀이 어노테이션 스타일에 익숙한 경우
- 간단한 REST API

---

#### 8. 핵심 정리

**RouterFunction**:
- ✅ 라우팅 규칙을 함수형으로 정의
- ✅ RequestPredicate로 조건 체크
- ✅ 빈으로 등록하여 사용

**HandlerFunction**:
- ✅ `ServerRequest → Mono<ServerResponse>` 변환
- ✅ 비즈니스 로직 처리
- ✅ 리액티브 스트림 반환

**ServerRequest**:
- ✅ HTTP 요청 정보 접근
- ✅ 리액티브 방식으로 본문 읽기 (`bodyToMono`, `bodyToFlux`)
- ✅ 경로 변수, 쿼리 파라미터, 헤더 등 접근

**ServerResponse**:
- ✅ HTTP 응답 구성
- ✅ 리액티브 방식으로 본문 쓰기 (`body`, `bodyValue`)
- ✅ 상태 코드, 헤더, 쿠키 설정

**전체 흐름**:
```
HTTP Request
    ↓
RouterFunction (라우팅 매칭)
    ↓
HandlerFunction (요청 처리)
    ↓
ServerRequest (요청 정보 읽기)
    ↓
비즈니스 로직 (Mono/Flux)
    ↓
ServerResponse (응답 생성)
    ↓
HTTP Response
```

### 그 외 Webflux 사용 시 사용되는 컴포넌트 및 객체 기술적 분석 및 동작 방식과 흐름 정리

## API Gateway 정리