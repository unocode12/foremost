# Spring WebFlux 기술 정리

## 📋 목차

1. [WebFlux 개요](#1-webflux-개요)
2. [리액티브 프로그래밍 기초](#2-리액티브-프로그래밍-기초)
3. [Reactor Core: Mono와 Flux](#3-reactor-core-mono와-flux)
4. [WebFlux 아키텍처](#4-webflux-아키텍처)
5. [RouterFunction과 HandlerFunction](#5-routerfunction과-handlerfunction)
6. [ServerRequest와 ServerResponse](#6-serverrequest와-serverresponse)
7. [WebFilter와 필터 체인](#7-webfilter와-필터-체인)
8. [WebClient](#8-webclient)
9. [예외 처리](#9-예외-처리)
10. [성능 최적화](#10-성능-최적화)
11. [Spring MVC vs WebFlux](#11-spring-mvc-vs-webflux)
12. [핵심 정리](#12-핵심-정리)

---

## 1. WebFlux 개요

### 1.1 정의

**Spring WebFlux**는 Spring 5.0에서 도입된 **논블로킹 리액티브 웹 프레임워크**입니다.

**핵심 특징**:
- **논블로킹 I/O**: 비동기/논블로킹 방식으로 요청 처리
- **리액티브 스트림**: Reactive Streams 스펙 기반
- **백프레셔 (Backpressure)**: 데이터 흐름 제어
- **높은 동시성**: 적은 스레드로 많은 요청 처리

---

### 1.2 등장 배경

**기존 Spring MVC의 한계**:
- **블로킹 I/O**: 각 요청마다 스레드가 블로킹됨
- **스레드 풀 제한**: 동시 요청 수가 스레드 수에 제한
- **리소스 낭비**: 대기 중인 스레드가 메모리 점유

**WebFlux의 해결책**:
- **이벤트 루프 기반**: Netty EventLoop 사용
- **적은 스레드**: CPU 코어 수 * 2 정도의 스레드로 수천 개의 동시 연결 처리
- **리액티브 스트림**: 데이터가 준비되면 처리 (Push 모델)

---

### 1.3 사용 시나리오

**WebFlux가 적합한 경우**:
- ✅ 높은 동시성이 필요한 경우
- ✅ 논블로킹 I/O가 중요한 경우
- ✅ 스트리밍 데이터 처리
- ✅ 마이크로서비스 간 비동기 통신

**WebFlux가 부적합한 경우**:
- ❌ 블로킹 I/O가 많은 경우 (DB, 파일 시스템)
- ❌ 간단한 CRUD 애플리케이션
- ❌ 팀이 리액티브 프로그래밍에 익숙하지 않은 경우

---

## 2. 리액티브 프로그래밍 기초

### 2.1 리액티브 프로그래밍이란?

**리액티브 프로그래밍**은 데이터 스트림과 변화 전파에 중점을 둔 프로그래밍 패러다임입니다.

**핵심 개념**:
- **Publisher**: 데이터를 발행하는 주체
- **Subscriber**: 데이터를 구독하는 주체
- **Subscription**: Publisher와 Subscriber 간의 구독 관계
- **Operator**: 데이터 변환 연산자

---

### 2.2 Reactive Streams 스펙

**Reactive Streams**는 비동기 스트림 처리의 표준 스펙입니다.

**핵심 인터페이스**:

```java
// Publisher: 데이터 발행자
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

// Subscriber: 데이터 구독자
public interface Subscriber<T> {
    void onSubscribe(Subscription subscription);
    void onNext(T item);
    void onError(Throwable throwable);
    void onComplete();
}

// Subscription: 구독 관계
public interface Subscription {
    void request(long n);  // 백프레셔: n개 요청
    void cancel();         // 구독 취소
}
```

**시그널 흐름**:
```
Publisher
    ↓ subscribe()
Subscriber.onSubscribe(Subscription)
    ↓ request(n)
Publisher.onNext(item)  (n번 반복)
    ↓
Subscriber.onComplete() 또는 onError()
```

---

### 2.3 백프레셔 (Backpressure)

**백프레셔**는 Subscriber가 Publisher에게 처리할 수 있는 데이터 양을 알려주는 메커니즘입니다.

**동작 방식**:
```java
// Subscriber가 처리 가능한 양을 요청
subscription.request(10);  // 10개만 요청

// Publisher는 최대 10개만 발행
// Subscriber가 처리 완료 후 다시 요청
```

**장점**:
- 메모리 오버플로우 방지
- Subscriber의 처리 속도에 맞춰 데이터 발행
- 시스템 안정성 향상

---

## 3. Reactor Core: Mono와 Flux

### 3.1 Mono

**Mono**는 0개 또는 1개의 요소를 발행하는 Publisher입니다.

**특징**:
- 단일 값 또는 빈 스트림
- 비동기 작업의 결과를 표현
- `Optional`의 리액티브 버전

**생성 방법**:
```java
// 값으로 생성
Mono<String> mono = Mono.just("Hello");

// 빈 Mono
Mono<String> empty = Mono.empty();

// 에러 Mono
Mono<String> error = Mono.error(new RuntimeException("Error"));

// 지연 생성
Mono<String> deferred = Mono.defer(() -> Mono.just("Deferred"));

// Callable로 생성
Mono<String> fromCallable = Mono.fromCallable(() -> "From Callable");
```

**주요 연산자**:
```java
Mono<String> mono = Mono.just("Hello")
    .map(String::toUpperCase)           // 변환
    .flatMap(s -> Mono.just(s + " World"))  // 비동기 변환
    .filter(s -> s.length() > 5)        // 필터링
    .switchIfEmpty(Mono.just("Default"))  // 빈 경우 대체
    .doOnNext(System.out::println)      // 사이드 이펙트
    .doOnError(e -> log.error("Error", e))  // 에러 처리
    .onErrorReturn("Error")             // 에러 시 기본값
    .onErrorResume(e -> Mono.just("Fallback"));  // 에러 시 대체
```

---

### 3.2 Flux

**Flux**는 0개 이상의 요소를 발행하는 Publisher입니다.

**특징**:
- 여러 값의 스트림
- 스트리밍 데이터 처리
- `Stream`의 리액티브 버전

**생성 방법**:
```java
// 값들로 생성
Flux<String> flux = Flux.just("A", "B", "C");

// 범위로 생성
Flux<Integer> range = Flux.range(1, 10);

// 배열/컬렉션으로 생성
Flux<String> fromArray = Flux.fromArray(new String[]{"A", "B"});
Flux<String> fromIterable = Flux.fromIterable(Arrays.asList("A", "B"));

// 빈 Flux
Flux<String> empty = Flux.empty();

// 에러 Flux
Flux<String> error = Flux.error(new RuntimeException("Error"));

// 간격을 두고 생성
Flux<Long> interval = Flux.interval(Duration.ofSeconds(1));
```

**주요 연산자**:
```java
Flux<String> flux = Flux.just("A", "B", "C")
    .map(String::toLowerCase)           // 변환
    .filter(s -> s.startsWith("a"))     // 필터링
    .take(2)                            // 처음 n개만
    .skip(1)                            // 처음 n개 건너뛰기
    .flatMap(s -> Mono.just(s + "!"))   // 비동기 변환
    .concatWith(Flux.just("D", "E"))    // 다른 Flux와 연결
    .mergeWith(Flux.just("F", "G"))     // 병합
    .buffer(2)                          // 버퍼링
    .window(2)                          // 윈도우
    .doOnNext(System.out::println)      // 사이드 이펙트
    .doOnComplete(() -> System.out.println("Complete"))  // 완료 시
    .collectList()                      // List로 수집
    .blockFirst();                      // 첫 번째 요소 블로킹 (테스트용)
```

---

### 3.3 주요 연산자 카테고리

**변환 연산자**:
- `map`: 동기 변환
- `flatMap`: 비동기 변환 (Mono/Flux 반환)
- `concatMap`: 순차적 비동기 변환
- `switchMap`: 최신 값만 유지

**필터링 연산자**:
- `filter`: 조건 필터링
- `take`: 처음 n개
- `skip`: 처음 n개 건너뛰기
- `distinct`: 중복 제거

**조합 연산자**:
- `concat`: 순차 연결
- `merge`: 병렬 병합
- `zip`: 여러 스트림 결합
- `combineLatest`: 최신 값 결합

**에러 처리 연산자**:
- `onErrorReturn`: 에러 시 기본값
- `onErrorResume`: 에러 시 대체 스트림
- `onErrorMap`: 에러 변환
- `retry`: 재시도

**유틸리티 연산자**:
- `doOnNext`: 사이드 이펙트
- `doOnError`: 에러 로깅
- `doOnComplete`: 완료 처리
- `log`: 로깅

---

### 3.4 스레드 제어

#### 3.4.1 subscribeOn vs publishOn

**핵심 차이**:
- **subscribeOn**: 소스(Publisher)가 실행되는 스레드를 결정
- **publishOn**: 이후 연산자들이 실행되는 스레드를 결정

**subscribeOn 예시**:
```java
// subscribeOn: 소스 실행 위치 결정
Mono<String> mono = Mono.fromCallable(() -> {
    // 이 코드는 boundedElastic 스레드에서 실행
    System.out.println("Thread: " + Thread.currentThread().getName());
    // 출력: Thread: boundedElastic-1
    return blockingOperation();
})
.subscribeOn(Schedulers.boundedElastic())
.map(s -> {
    // 여전히 boundedElastic 스레드에서 실행
    System.out.println("Thread: " + Thread.currentThread().getName());
    // 출력: Thread: boundedElastic-1
    return s.toUpperCase();
});
```

**publishOn 예시**:
```java
// publishOn: 이후 연산자 실행 위치 결정
Flux<String> flux = Flux.just("A", "B", "C")
    .map(s -> {
        // EventLoop 스레드에서 실행
        System.out.println("Before publishOn: " + Thread.currentThread().getName());
        // 출력: Before publishOn: reactor-http-nio-2
        return s.toUpperCase();
    })
    .publishOn(Schedulers.parallel())  // 이후는 parallel에서
    .map(s -> {
        // parallel 스레드에서 실행
        System.out.println("After publishOn: " + Thread.currentThread().getName());
        // 출력: After publishOn: parallel-1
        return s + "!";
    })
    .map(s -> {
        // 여전히 parallel 스레드에서 실행
        System.out.println("Still parallel: " + Thread.currentThread().getName());
        // 출력: Still parallel: parallel-1
        return s.toLowerCase();
    });
```

**subscribeOn과 publishOn 조합**:
```java
Mono<String> mono = Mono.fromCallable(() -> {
    // boundedElastic 스레드에서 실행
    return blockingDatabaseQuery();
})
.subscribeOn(Schedulers.boundedElastic())
.map(data -> {
    // 여전히 boundedElastic 스레드
    return processData(data);
})
.publishOn(Schedulers.parallel())
.map(result -> {
    // parallel 스레드로 전환
    return formatResult(result);
});
```

---

#### 3.4.2 Scheduler 종류와 특징

**Schedulers.immediate()**:
- 현재 스레드에서 즉시 실행
- 스레드 전환 없음
- 오버헤드 최소

```java
Mono.just("Hello")
    .subscribeOn(Schedulers.immediate())
    .subscribe();  // 현재 스레드에서 실행
```

**Schedulers.single()**:
- 단일 스레드에서 순차 실행
- 스레드 안전성 보장
- 순서 보장

```java
Flux.range(1, 10)
    .subscribeOn(Schedulers.single())
    .subscribe();  // 모두 같은 스레드에서 순차 실행
```

**Schedulers.parallel()**:
- CPU 코어 수만큼의 워커 스레드
- CPU 집약적 작업에 적합
- 고정된 스레드 풀

```java
Flux.range(1, 100)
    .parallel(4)  // 4개 워커로 병렬 처리
    .runOn(Schedulers.parallel())
    .map(i -> i * 2)
    .sequential();
```

**Schedulers.boundedElastic()**:
- 블로킹 I/O 전용
- 동적으로 스레드 풀 확장 (최대 10 * CPU 코어)
- 각 작업에 대해 스레드 할당

```java
Mono.fromCallable(() -> {
    // 블로킹 I/O 작업
    return Files.readString(Path.of("file.txt"));
})
.subscribeOn(Schedulers.boundedElastic());
```

---

#### 3.4.3 스레드 전환 시점

**스레드 전환이 발생하는 시점**:

```java
// 1. subscribeOn: 구독 시점에 스레드 전환
Mono<String> mono = Mono.fromCallable(() -> {
    // boundedElastic 스레드에서 실행
    return "data";
})
.subscribeOn(Schedulers.boundedElastic());

// 2. publishOn: publishOn 이후부터 스레드 전환
Flux<String> flux = Flux.just("A", "B")
    .map(s -> s)  // 원래 스레드
    .publishOn(Schedulers.parallel())
    .map(s -> s);  // parallel 스레드

// 3. flatMap: 내부 Publisher의 스레드 사용
Flux<String> flux2 = Flux.just("A", "B")
    .flatMap(s -> Mono.fromCallable(() -> {
        // boundedElastic 스레드에서 실행
        return process(s);
    }).subscribeOn(Schedulers.boundedElastic()));
```

---

#### 3.4.4 스레드 전환 비용

**스레드 전환 오버헤드**:
- 컨텍스트 스위칭 비용
- CPU 캐시 미스 가능성
- 메모리 접근 패턴 변화

**최적화 팁**:
```java
// ❌ 나쁜 예: 불필요한 스레드 전환
Flux.range(1, 100)
    .publishOn(Schedulers.parallel())
    .map(i -> i * 2)  // 간단한 연산인데 스레드 전환
    .publishOn(Schedulers.parallel())
    .map(i -> i + 1);  // 또 스레드 전환

// ✅ 좋은 예: 필요한 곳에만 스레드 전환
Flux.range(1, 100)
    .map(i -> i * 2)  // EventLoop에서 실행 (빠름)
    .publishOn(Schedulers.parallel())
    .map(i -> heavyComputation(i));  // 무거운 연산만 별도 스레드
```

---

## 4. WebFlux 아키텍처

### 4.1 전체 아키텍처

**요청 처리 흐름**:

```
[HTTP Client]
    ↓
[Netty EventLoop] (NIO Thread)
    ↓
[Reactor Netty HttpServer]
    ↓
[HttpServerOperations]
    ↓
[ReactorHttpHandlerAdapter]
    ↓
[WebHandler]
    ↓
[ExceptionHandlingWebHandler]
    ↓
[WebFilter Chain]
    ↓
[DispatcherHandler]
    ↓
[HandlerMapping] (RequestMappingHandlerMapping / RouterFunctionMapping)
    ↓
[HandlerAdapter] (RequestMappingHandlerAdapter / HandlerFunctionAdapter)
    ↓
[@Controller / RouterFunction]
    ↓
[HandlerFunction / Controller Method]
    ↓
[Service Layer]
    ↓
[Repository / WebClient]
    ↓
[Mono/Flux Response]
    ↓
[Response Write]
    ↓
[Netty Channel Flush]
```

---

### 4.2 핵심 컴포넌트

**WebHandler**:
- 모든 HTTP 요청의 진입점
- `Mono<Void> handle(ServerWebExchange exchange)`

**DispatcherHandler**:
- Spring MVC의 DispatcherServlet과 유사
- HandlerMapping과 HandlerAdapter 조정

**HandlerMapping**:
- 요청을 적절한 핸들러로 매핑
- `RequestMappingHandlerMapping`: @Controller용
- `RouterFunctionMapping`: RouterFunction용

**HandlerAdapter**:
- 핸들러 실행
- `RequestMappingHandlerAdapter`: @Controller용
- `HandlerFunctionAdapter`: RouterFunction용

---

### 4.3 Netty 기반 서버

**Netty EventLoop**:
- 논블로킹 I/O 처리
- 이벤트 기반 아키텍처
- 적은 스레드로 많은 연결 처리

**EventLoop 구조**:
```
EventLoopGroup
 ├── eventloop-1 (싱글 스레드)
 ├── eventloop-2 (싱글 스레드)
 ├── ...
 └── eventloop-N (N = CPU core * 2)
```

**특징**:
- 각 EventLoop는 싱글 스레드
- 하나의 스레드가 여러 커넥션 처리
- 논블로킹으로 대기 없이 처리

---

## 5. RouterFunction과 HandlerFunction

### 5.1 RouterFunction

**RouterFunction**은 함수형 스타일의 라우팅을 제공합니다.

**기본 사용**:
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

**RequestPredicate 사용**:
```java
RouterFunction<ServerResponse> route = RouterFunctions.route()
    .GET("/api/users", 
        RequestPredicates.accept(MediaType.APPLICATION_JSON),
        userHandler::getAllUsers)
    .POST("/api/users",
        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
        userHandler::createUser)
    .build();
```

**중첩 라우팅**:
```java
RouterFunction<ServerResponse> route = RouterFunctions.nest(
    RequestPredicates.path("/api"),
    RouterFunctions.route()
        .GET("/users", userHandler::getAllUsers)
        .GET("/products", productHandler::getAllProducts)
        .build()
);
```

---

### 5.2 HandlerFunction

**HandlerFunction**은 `ServerRequest → Mono<ServerResponse>` 변환 함수입니다.

**구현 방법**:

```java
// 방법 1: 람다
HandlerFunction<ServerResponse> handler = request -> {
    String id = request.pathVariable("id");
    return ServerResponse.ok().bodyValue("User: " + id);
};

// 방법 2: 메서드 참조
@Component
public class UserHandler {
    public Mono<ServerResponse> getUser(ServerRequest request) {
        String id = request.pathVariable("id");
        return userService.findById(id)
            .flatMap(user -> ServerResponse.ok().bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}
```

---

## 6. ServerRequest와 ServerResponse

### 6.1 ServerRequest

**ServerRequest**는 HTTP 요청 정보를 리액티브 방식으로 제공합니다.

**주요 메서드**:
```java
// HTTP 메서드
HttpMethod method();
String methodName();

// URI 정보
URI uri();
String path();

// 헤더
Headers headers();
List<String> queryParams(String name);

// 경로 변수
String pathVariable(String name);
Map<String, String> pathVariables();

// 요청 본문
<T> Mono<T> bodyToMono(Class<T> elementClass);
<T> Flux<T> bodyToFlux(Class<T> elementClass);

// Form 데이터
Mono<MultiValueMap<String, String>> formData();
```

**사용 예시**:
```java
public Mono<ServerResponse> handleRequest(ServerRequest request) {
    // 경로 변수
    String id = request.pathVariable("id");
    
    // 쿼리 파라미터
    Optional<String> page = request.queryParam("page");
    
    // 헤더
    String authToken = request.headers().firstHeader("Authorization");
    
    // 본문 읽기
    Mono<User> userMono = request.bodyToMono(User.class);
    
    return ServerResponse.ok().body(userMono, User.class);
}
```

---

### 6.2 ServerResponse

**ServerResponse**는 HTTP 응답을 리액티브 방식으로 구성합니다.

**생성 방법**:
```java
// 상태 코드별 생성
ServerResponse.ok()
ServerResponse.created(URI location)
ServerResponse.noContent()
ServerResponse.badRequest()
ServerResponse.notFound()
ServerResponse.serverError()

// 커스텀 상태 코드
ServerResponse.status(HttpStatus.CREATED)
```

**응답 구성**:
```java
// 단순 값
return ServerResponse.ok()
    .bodyValue("Hello");

// Mono로 응답
Mono<User> userMono = userService.findById(id);
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .body(userMono, User.class);

// Flux로 응답
Flux<User> usersFlux = userService.findAll();
return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .body(usersFlux, User.class);

// 헤더 설정
return ServerResponse.ok()
    .header("X-Custom-Header", "value")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(data);

// 쿠키 설정
return ServerResponse.ok()
    .cookie(ResponseCookie.from("token", "abc123")
        .maxAge(Duration.ofHours(1))
        .httpOnly(true)
        .build())
    .bodyValue("Success");
```

---

## 7. WebFilter와 필터 체인

### 7.1 WebFilter

**WebFilter**는 모든 HTTP 요청에 적용되는 필터입니다.

**기본 구조**:
```java
@Component
@Order(1)
public class LoggingFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        
        log.info("Request: {} {}", request.getMethod(), request.getURI());
        
        return chain.filter(exchange)
            .doOnSuccess(v -> {
                long duration = System.currentTimeMillis() - startTime;
                log.info("Response: {} ({}ms)", 
                    exchange.getResponse().getStatusCode(), duration);
            })
            .doOnError(error -> {
                log.error("Error: {}", error.getMessage());
            });
    }
}
```

---

### 7.2 Reactor Context 활용

**Context를 통한 데이터 전파**:
```java
@Component
public class ContextFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString();
        
        return chain.filter(exchange)
            .contextWrite(Context.of("traceId", traceId))
            .doOnEach(signal -> {
                if (signal.hasValue()) {
                    String ctxTraceId = signal.getContextView()
                        .getOrDefault("traceId", "unknown");
                    log.info("TraceId: {}", ctxTraceId);
                }
            });
    }
}
```

**Context 읽기**:
```java
public Mono<ServerResponse> handleRequest(ServerRequest request) {
    return Mono.deferContextual(contextView -> {
        String traceId = contextView.getOrDefault("traceId", "unknown");
        log.info("Processing with traceId: {}", traceId);
        
        return userService.findAll()
            .collectList()
            .flatMap(users -> ServerResponse.ok().bodyValue(users));
    });
}
```

---

## 8. WebClient

### 8.1 WebClient 개요

**WebClient**는 WebFlux의 논블로킹 HTTP 클라이언트입니다.

**설정**:
```java
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl("https://api.example.com")
            .defaultHeader("User-Agent", "MyApp")
            .codecs(configurer -> {
                configurer.defaultCodecs().maxInMemorySize(1024 * 1024);
            })
            .build();
    }
}
```

---

### 8.2 WebClient 사용

**GET 요청**:
```java
@Service
public class ApiService {
    
    private final WebClient webClient;
    
    public Mono<User> getUser(String id) {
        return webClient.get()
            .uri("/users/{id}", id)
            .retrieve()
            .bodyToMono(User.class);
    }
    
    public Flux<User> getAllUsers() {
        return webClient.get()
            .uri("/users")
            .retrieve()
            .bodyToFlux(User.class);
    }
}
```

**POST 요청**:
```java
public Mono<User> createUser(User user) {
    return webClient.post()
        .uri("/users")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(user)
        .retrieve()
        .bodyToMono(User.class);
}
```

**에러 처리**:
```java
public Mono<User> getUser(String id) {
    return webClient.get()
        .uri("/users/{id}", id)
        .retrieve()
        .onStatus(HttpStatus::is4xxClientError, response -> 
            Mono.error(new ClientException("Client error")))
        .onStatus(HttpStatus::is5xxServerError, response -> 
            Mono.error(new ServerException("Server error")))
        .bodyToMono(User.class)
        .onErrorResume(WebClientResponseException.class, e -> {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Mono.empty();
            }
            return Mono.error(e);
        });
}
```

---

### 8.3 ExchangeFilterFunction

**필터 추가**:
```java
@Bean
public WebClient webClient() {
    return WebClient.builder()
        .baseUrl("https://api.example.com")
        .filter(loggingFilter())
        .filter(authFilter())
        .build();
}

private ExchangeFilterFunction loggingFilter() {
    return ExchangeFilterFunction.ofRequestProcessor(request -> {
        log.info("Request: {} {}", request.method(), request.url());
        return Mono.just(request);
    })
    .andThen(ExchangeFilterFunction.ofResponseProcessor(response -> {
        log.info("Response: {}", response.statusCode());
        return Mono.just(response);
    }));
}

private ExchangeFilterFunction authFilter() {
    return (request, next) -> {
        ClientRequest filteredRequest = ClientRequest.from(request)
            .header("Authorization", "Bearer " + getToken())
            .build();
        return next.exchange(filteredRequest);
    };
}
```

---

## 9. 예외 처리

### 9.1 WebExceptionHandler

**전역 예외 처리**:
```java
@Order(-2)
@Component
public class GlobalErrorHandler implements WebExceptionHandler {
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof BusinessException) {
            return handleBusinessException(exchange, (BusinessException) ex);
        } else if (ex instanceof ValidationException) {
            return handleValidationException(exchange, (ValidationException) ex);
        } else {
            return handleGenericException(exchange, ex);
        }
    }
    
    private Mono<Void> handleBusinessException(
            ServerWebExchange exchange, BusinessException ex) {
        ErrorResponse error = ErrorResponse.builder()
            .code(ex.getErrorCode())
            .message(ex.getMessage())
            .build();
        
        return writeResponse(exchange, HttpStatus.BAD_REQUEST, error);
    }
    
    private Mono<Void> writeResponse(
            ServerWebExchange exchange, HttpStatus status, Object body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().add(
            HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        DataBuffer buffer = response.bufferFactory()
            .wrap(objectMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}
```

---

### 9.2 throw vs Mono.error

**❌ 나쁜 예: throw 사용**:
```java
public Mono<User> getUser(String id) {
    if (id == null) {
        throw new IllegalArgumentException("ID cannot be null");
    }
    return userRepository.findById(id);
}
```

**✅ 좋은 예: Mono.error 사용**:
```java
public Mono<User> getUser(String id) {
    if (id == null) {
        return Mono.error(new IllegalArgumentException("ID cannot be null"));
    }
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(new UserNotFoundException()));
}
```

**차이점**:
- `throw`: 즉시 예외 발생, 리액티브 체인 깨짐
- `Mono.error()`: 구독 시점에 예외 발생, 리액티브 체인 유지

---

## 10. 성능 최적화

### 10.1 스레드 관리

#### 10.1.1 블로킹 작업 처리

**블로킹 작업 처리**:
```java
// 블로킹 작업은 별도 스레드에서 실행
public Mono<String> blockingOperation() {
    return Mono.fromCallable(() -> {
        // 블로킹 I/O
        return database.query();
    })
    .subscribeOn(Schedulers.boundedElastic());
}
```

**주의사항**:
- EventLoop 스레드에서 블로킹 작업 금지
- 블로킹 작업은 `boundedElastic` 스케줄러 사용
- 가능한 한 논블로킹 API 사용

---

#### 10.1.2 WebFlux 스레드 모델 상세 분석

**EventLoop 스레드 동작 원리**:

```java
// WebFlux 요청 처리 시 스레드 흐름
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable String id) {
    // 1. 이 코드는 Netty EventLoop 스레드에서 실행
    //    스레드 이름: reactor-http-nio-1, reactor-http-nio-2, ...
    log.info("Thread: {}", Thread.currentThread().getName());
    // 출력: Thread: reactor-http-nio-2
    
    // 2. 논블로킹 작업은 같은 EventLoop 스레드에서 계속 실행
    return userRepository.findById(id)  // 논블로킹 DB 조회
        .map(user -> {
            // 여전히 같은 EventLoop 스레드
            log.info("Thread: {}", Thread.currentThread().getName());
            // 출력: Thread: reactor-http-nio-2
            return user;
        });
}
```

**스레드 전환 시나리오**:

```java
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable String id) {
    // EventLoop 스레드에서 시작
    log.info("1. EventLoop: {}", Thread.currentThread().getName());
    // 출력: 1. EventLoop: reactor-http-nio-2
    
    return userRepository.findById(id)  // 논블로킹, 같은 스레드
        .doOnNext(user -> {
            log.info("2. Still EventLoop: {}", Thread.currentThread().getName());
            // 출력: 2. Still EventLoop: reactor-http-nio-2
        })
        .flatMap(user -> {
            // 블로킹 작업이 필요한 경우
            return Mono.fromCallable(() -> {
                // boundedElastic 스레드로 전환
                log.info("3. Blocking thread: {}", Thread.currentThread().getName());
                // 출력: 3. Blocking thread: boundedElastic-1
                return heavyComputation(user);
            })
            .subscribeOn(Schedulers.boundedElastic());
        })
        .doOnNext(result -> {
            // flatMap 이후 다시 EventLoop 스레드로 돌아옴
            log.info("4. Back to EventLoop: {}", Thread.currentThread().getName());
            // 출력: 4. Back to EventLoop: reactor-http-nio-2
        });
}
```

**스레드 전환 시각화**:

```
[HTTP Request 도착]
    ↓
[Netty EventLoop Thread: reactor-http-nio-2]
    ├─ 요청 파싱
    ├─ WebFilter 체인
    ├─ DispatcherHandler
    ├─ Controller Method 실행
    │   └─ userRepository.findById()  // 논블로킹, 같은 스레드
    │       └─ DB 드라이버가 논블로킹 I/O 사용
    │           └─ 데이터 준비되면 EventLoop에 콜백
    │
    ├─ [subscribeOn(boundedElastic)]  ← 스레드 전환!
    │   └─ [boundedElastic-1 Thread]
    │       └─ heavyComputation()  // 블로킹 작업
    │
    └─ [flatMap 완료 후]
        └─ [EventLoop Thread: reactor-http-nio-2]  ← 다시 돌아옴
            └─ Response Write
                └─ Netty Channel Flush
```

---

#### 10.1.3 EventLoop 스레드 풀 구조

**EventLoopGroup 구조**:

```
EventLoopGroup (기본: CPU 코어 * 2)
├── EventLoop-1 (싱글 스레드: reactor-http-nio-1)
│   ├── Connection-1
│   │   ├── Request-1 (처리 중)
│   │   ├── Request-2 (대기 중)
│   │   └── Request-3 (대기 중)
│   ├── Connection-2
│   │   ├── Request-1 (처리 중)
│   │   └── Request-2 (대기 중)
│   ├── Connection-3
│   │   └── Request-1 (처리 중)
│   └── ...
├── EventLoop-2 (싱글 스레드: reactor-http-nio-2)
│   ├── Connection-4
│   ├── Connection-5
│   └── ...
└── EventLoop-N
```

**특징**:
- 각 EventLoop는 **싱글 스레드**
- 하나의 스레드가 **여러 커넥션** 처리
- 하나의 커넥션에서 **여러 요청** 처리 가능 (HTTP Keep-Alive, HTTP/2)
- 논블로킹 I/O로 **대기 없이** 처리
- CPU 코어당 2개 EventLoop (기본값)

**동시 처리 능력**:
```java
// 예: 4코어 시스템
// EventLoop 수: 4 * 2 = 8개
// 각 EventLoop가 수백~수천 개의 커넥션 처리 가능
// 각 커넥션에서 여러 요청 처리 가능
// 총 처리 능력: 수천~수만 개의 동시 연결
```

---

#### 10.1.3-1. 하나의 스레드가 여러 커넥션을 처리하는 메커니즘

**핵심 질문**: 하나의 스레드가 어떻게 여러 커넥션과 여러 요청을 동시에 처리할 수 있을까?

**답**: **논블로킹 I/O + 이벤트 루프 + 셀렉터(Selector) 메커니즘**

---

##### Java NIO의 Selector 메커니즘

**Selector의 역할**:
- 여러 채널(Channel)을 하나의 스레드에서 모니터링
- 데이터가 준비된 채널만 선택하여 처리
- 블로킹 없이 여러 I/O 작업 관리

**기본 동작 원리**:

```java
// Java NIO Selector 예시 (의사 코드)
Selector selector = Selector.open();

// 여러 채널 등록
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);  // 논블로킹 모드
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

// 이벤트 루프
while (true) {
    // 준비된 채널만 선택 (블로킹 없음!)
    int readyChannels = selector.select();  // 또는 selectNow() (논블로킹)
    
    if (readyChannels == 0) {
        continue;  // 준비된 채널 없음, 다른 작업 처리 가능
    }
    
    // 준비된 채널들 처리
    Set<SelectionKey> selectedKeys = selector.selectedKeys();
    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
    
    while (keyIterator.hasNext()) {
        SelectionKey key = keyIterator.next();
        
        if (key.isAcceptable()) {
            // 새 연결 수락
            SocketChannel clientChannel = serverChannel.accept();
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
        } else if (key.isReadable()) {
            // 읽을 데이터 있음
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            channel.read(buffer);  // 논블로킹 읽기
            // 데이터 처리
        } else if (key.isWritable()) {
            // 쓸 수 있음
            SocketChannel channel = (SocketChannel) key.channel();
            // 응답 쓰기
        }
        
        keyIterator.remove();
    }
}
```

**핵심 포인트**:
1. `selector.select()`: 준비된 채널만 반환 (블로킹 없음)
2. 여러 채널을 하나의 스레드에서 모니터링
3. 데이터가 준비된 채널만 처리
4. 대기 중인 채널은 무시하고 다른 채널 처리

---

##### Netty의 EventLoop 메커니즘

**Netty는 Java NIO를 기반으로 더 고수준 API 제공**:

```java
// Netty EventLoop 내부 동작 (의사 코드)
public class EventLoop {
    private final Selector selector;
    private final Queue<Runnable> taskQueue;
    
    public void run() {
        while (!isShutdown()) {
            // 1. 준비된 I/O 이벤트 처리
            int readyChannels = selector.select(1000);  // 1초 타임아웃
            
            if (readyChannels > 0) {
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                for (SelectionKey key : selectedKeys) {
                    if (key.isReadable()) {
                        // 읽기 이벤트 처리
                        processRead(key);
                    }
                    if (key.isWritable()) {
                        // 쓰기 이벤트 처리
                        processWrite(key);
                    }
                }
                selectedKeys.clear();
            }
            
            // 2. 대기 중인 작업 처리
            processTaskQueue();
        }
    }
    
    private void processRead(SelectionKey key) {
        // 논블로킹 읽기
        Channel channel = (Channel) key.channel();
        ByteBuf buffer = channel.read();  // 논블로킹, 즉시 반환
        
        if (buffer != null) {
            // 데이터가 있으면 처리
            handleData(channel, buffer);
        } else {
            // 데이터 없으면 다음 채널 처리
            // 블로킹 없음!
        }
    }
}
```

---

##### 실제 동작 시나리오

**시나리오: 3개의 커넥션이 동시에 요청을 보냄**

```
시간 → 

t0: [EventLoop Thread: reactor-http-nio-2]
    ├─ selector.select() 호출
    ├─ Connection-1: 데이터 없음 (대기)
    ├─ Connection-2: 데이터 없음 (대기)
    └─ Connection-3: 데이터 없음 (대기)
    → select()는 즉시 반환 (준비된 채널 없음)
    → 다른 작업 처리 가능

t1: [Connection-1에서 데이터 도착]
    ├─ selector.select() 호출
    ├─ Connection-1: 데이터 있음! ✅
    ├─ Connection-2: 데이터 없음
    └─ Connection-3: 데이터 없음
    → Connection-1만 처리
    → HTTP 요청 파싱
    → Controller 호출
    → DB 호출 (논블로킹, 즉시 반환)
    → selector.select() 다시 호출

t2: [Connection-2에서 데이터 도착]
    ├─ selector.select() 호출
    ├─ Connection-1: DB 응답 대기 중
    ├─ Connection-2: 데이터 있음! ✅
    └─ Connection-3: 데이터 없음
    → Connection-2 처리
    → HTTP 요청 파싱
    → Controller 호출
    → DB 호출 (논블로킹, 즉시 반환)
    → selector.select() 다시 호출

t3: [Connection-1의 DB 응답 도착]
    ├─ selector.select() 호출
    ├─ Connection-1: DB 응답 있음! ✅
    ├─ Connection-2: DB 응답 대기 중
    └─ Connection-3: 데이터 없음
    → Connection-1의 응답 처리 계속
    → Response 작성
    → selector.select() 다시 호출

t4: [Connection-3에서 데이터 도착]
    ├─ selector.select() 호출
    ├─ Connection-1: 응답 전송 완료
    ├─ Connection-2: DB 응답 대기 중
    └─ Connection-3: 데이터 있음! ✅
    → Connection-3 처리
    → ...
```

**핵심**: 
- 하나의 스레드가 **순차적으로** 각 채널을 확인
- 데이터가 **준비된 채널만** 처리
- **블로킹 없이** 다음 채널로 이동
- 매우 빠르게 순환하므로 **동시에 처리하는 것처럼** 보임

---

##### 논블로킹 I/O의 핵심

**블로킹 I/O (기존 방식)**:

```java
// 블로킹 I/O
Socket socket = serverSocket.accept();  // 연결 대기 (블로킹!)
byte[] buffer = new byte[1024];
int bytesRead = socket.getInputStream().read(buffer);  // 데이터 대기 (블로킹!)
// 위 코드 실행 중에는 스레드가 블로킹됨
// 다른 커넥션 처리 불가능
```

**논블로킹 I/O (WebFlux 방식)**:

```java
// 논블로킹 I/O
ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);  // 논블로킹 모드

// 연결 수락 (논블로킹)
SocketChannel clientChannel = serverChannel.accept();
if (clientChannel != null) {
    // 연결 있음, 처리
} else {
    // 연결 없음, 다음 작업으로 (블로킹 없음!)
}

// 데이터 읽기 (논블로킹)
ByteBuffer buffer = ByteBuffer.allocate(1024);
int bytesRead = clientChannel.read(buffer);
if (bytesRead > 0) {
    // 데이터 있음, 처리
} else if (bytesRead == 0) {
    // 데이터 없음, 다음 채널로 (블로킹 없음!)
} else {
    // 연결 종료
}
```

**차이점**:
- **블로킹**: 데이터가 올 때까지 **대기** (스레드 블로킹)
- **논블로킹**: 데이터가 없으면 **즉시 반환** (다음 작업 처리)

---

##### 이벤트 루프의 순환 구조

**이벤트 루프의 무한 순환**:

```java
// EventLoop의 핵심 루프 (의사 코드)
while (true) {
    // 1. 준비된 I/O 이벤트 확인 (논블로킹)
    int readyCount = selector.selectNow();  // 즉시 반환
    
    if (readyCount > 0) {
        // 2. 준비된 채널들 처리
        processReadyChannels();
    }
    
    // 3. 대기 중인 작업 처리
    processPendingTasks();
    
    // 4. 다시 1번으로 (매우 빠르게 순환)
    // 순환 속도: 마이크로초 단위
}
```

**순환 속도**:
- 이벤트 루프는 **매우 빠르게** 순환 (마이크로초 단위)
- 각 순환마다 **모든 채널 확인**
- 데이터가 준비된 채널만 처리
- 준비되지 않은 채널은 **즉시 건너뜀**

**비유**:
```
식당 종업원이 여러 테이블을 순회:
- 테이블 1: 주문 없음 → 다음 테이블
- 테이블 2: 주문 있음! → 처리
- 테이블 3: 주문 없음 → 다음 테이블
- 테이블 4: 주문 있음! → 처리
- 다시 테이블 1로... (매우 빠르게 순환)

각 테이블을 확인하는 시간이 매우 짧아서
모든 테이블을 동시에 관리하는 것처럼 보임
```

---

##### 실제 코드로 확인

**스레드 동작 확인 코드**:

```java
@GetMapping("/test-concurrent")
public Mono<Map<String, Object>> testConcurrent() {
    String threadName = Thread.currentThread().getName();
    long threadId = Thread.currentThread().getId();
    long startTime = System.currentTimeMillis();
    
    // 논블로킹 지연 (1초)
    return Mono.delay(Duration.ofSeconds(1))
        .map(delay -> {
            long endTime = System.currentTimeMillis();
            return Map.of(
                "thread", threadName,
                "threadId", threadId,
                "duration", endTime - startTime,
                "timestamp", Instant.now()
            );
        });
}

// 동시에 100개 요청을 보내면:
// - 모두 같은 EventLoop 스레드에서 처리될 수 있음
// - 각 요청이 1초 지연되지만, 스레드는 블로킹되지 않음
// - 다른 요청도 동시에 처리됨
```

**출력 예시**:
```
Thread: reactor-http-nio-2, Request-1 started
Thread: reactor-http-nio-2, Request-2 started  (같은 스레드!)
Thread: reactor-http-nio-2, Request-3 started  (같은 스레드!)
...
Thread: reactor-http-nio-2, Request-1 completed (1초 후)
Thread: reactor-http-nio-2, Request-2 completed (1초 후)
Thread: reactor-http-nio-2, Request-3 completed (1초 후)
```

---

##### 커널 레벨의 지원

**epoll (Linux) / kqueue (macOS) / IOCP (Windows)**:

```java
// Netty는 OS의 고성능 I/O 메커니즘 사용
// Linux: epoll
// macOS: kqueue  
// Windows: IOCP

// epoll의 동작:
// 1. 커널이 파일 디스크립터를 모니터링
// 2. 데이터 준비되면 커널이 알림
// 3. 애플리케이션은 준비된 것만 확인
// 4. O(1) 시간 복잡도로 준비된 채널 찾기
```

**epoll의 장점**:
- **O(1) 성능**: 준비된 채널을 즉시 찾음
- **커널 레벨 최적화**: OS가 효율적으로 관리
- **확장성**: 수천 개의 채널도 효율적으로 처리

**전통적인 select() vs epoll**:

```java
// select() (구식, 비효율)
// - 모든 파일 디스크립터를 순회
// - O(n) 시간 복잡도
// - 최대 1024개 제한

// epoll() (현대적, 효율적)
// - 준비된 것만 반환
// - O(1) 시간 복잡도
// - 수만 개의 파일 디스크립터 지원
```

---

##### 전체 흐름 요약

**하나의 스레드가 여러 커넥션 처리하는 전체 과정**:

```
[EventLoop Thread 시작]
    ↓
[Selector 생성 및 채널 등록]
    ↓
[이벤트 루프 시작]
    ↓
    ├─ [selector.select()]  ← 준비된 채널 확인 (논블로킹)
    │   │
    │   ├─ Connection-1: 데이터 있음? → 처리
    │   ├─ Connection-2: 데이터 있음? → 처리
    │   ├─ Connection-3: 데이터 있음? → 처리
    │   └─ ... (수백 개의 커넥션)
    │
    ├─ [준비된 채널 처리]
    │   ├─ HTTP 요청 파싱
    │   ├─ Controller 호출
    │   ├─ 논블로킹 I/O 호출 (즉시 반환)
    │   └─ 콜백 등록
    │
    ├─ [대기 중인 작업 처리]
    │   └─ 이전 요청의 콜백 실행
    │
    └─ [다시 selector.select()로]  ← 매우 빠르게 순환
```

**핵심 메커니즘**:
1. **Selector**: 여러 채널을 하나의 스레드에서 모니터링
2. **논블로킹 I/O**: 데이터 없으면 즉시 반환
3. **이벤트 루프**: 빠르게 순환하며 준비된 것만 처리
4. **콜백**: I/O 완료 시 콜백으로 처리 계속
5. **epoll/kqueue**: OS 레벨의 고성능 I/O 지원

**결과**:
- 하나의 스레드가 수백~수천 개의 커넥션 관리
- 각 커넥션에서 여러 요청 처리
- 블로킹 없이 모든 요청 처리

---

#### 10.1.4 커넥션(Connection) vs 요청(Request) 이해

**핵심 개념**:
- **커넥션 (Connection)**: TCP 레벨의 연결 (3-way handshake로 생성)
- **요청 (Request)**: HTTP 레벨의 요청 (커넥션 위에서 전송)

**관계**:
```
[TCP Connection] (커넥션)
    ├── [HTTP Request 1] (요청)
    ├── [HTTP Request 2] (요청)
    ├── [HTTP Request 3] (요청)
    └── ...
```

**HTTP/1.1 Keep-Alive**:
- 하나의 TCP 커넥션으로 여러 HTTP 요청/응답 처리
- 커넥션 재사용으로 오버헤드 감소

**예시**:
```
[Client]                    [Server]
   |                           |
   |--- TCP Connection --------→|
   |                           |
   |--- HTTP Request 1 -------→|
   |←-- HTTP Response 1 -------|
   |                           |
   |--- HTTP Request 2 -------→|  (같은 커넥션 재사용)
   |←-- HTTP Response 2 -------|
   |                           |
   |--- HTTP Request 3 -------→|  (같은 커넥션 재사용)
   |←-- HTTP Response 3 -------|
   |                           |
   |--- Connection Close -----→|
```

**HTTP/2 Multiplexing**:
- 하나의 TCP 커넥션으로 여러 HTTP 요청을 **동시에** 처리
- 요청들이 병렬로 처리됨

**예시**:
```
[Client]                    [Server]
   |                           |
   |--- TCP Connection --------→|
   |                           |
   |--- HTTP Request 1 -------→|
   |--- HTTP Request 2 -------→|  (동시에!)
   |--- HTTP Request 3 -------→|  (동시에!)
   |                           |
   |←-- HTTP Response 2 -------|  (순서 무관)
   |←-- HTTP Response 1 -------|
   |←-- HTTP Response 3 -------|
```

---

#### 10.1.5 EventLoop의 커넥션과 요청 처리

**하나의 EventLoop가 처리하는 것**:

```
EventLoop Thread: reactor-http-nio-2
│
├─ Connection-1 (Client A)
│  ├─ Request-1: GET /api/users/1
│  │  └─ 처리 중 (논블로킹, DB 대기 중)
│  ├─ Request-2: GET /api/users/2
│  │  └─ 대기 중 (Request-1 완료 대기)
│  └─ Request-3: POST /api/users
│     └─ 대기 중
│
├─ Connection-2 (Client B)
│  ├─ Request-1: GET /api/products
│  │  └─ 처리 중 (논블로킹, DB 대기 중)
│  └─ Request-2: GET /api/orders
│     └─ 대기 중
│
└─ Connection-3 (Client C)
   └─ Request-1: GET /api/categories
      └─ 처리 중 (논블로킹, DB 대기 중)
```

**논블로킹 처리 과정**:

```java
// EventLoop 스레드에서의 처리 흐름
@GetMapping("/users/{id}")
public Mono<User> getUser(@PathVariable String id) {
    // [시점 1] Connection-1의 Request-1 처리 시작
    log.info("Processing request from Connection-1");
    
    return userRepository.findById(id)  // 논블로킹 DB 호출
        .doOnSubscribe(s -> {
            // [시점 2] DB 호출 시작, EventLoop는 다른 작업 처리 가능
            log.info("DB call started, EventLoop can handle other requests");
        })
        .doOnNext(user -> {
            // [시점 3] DB 응답 도착, EventLoop가 다시 이 요청 처리
            log.info("DB response received, continuing request processing");
        });
}

// 동시에 다른 요청도 처리 가능:
// - Connection-2의 Request-1도 같은 EventLoop에서 처리
// - Connection-3의 Request-1도 같은 EventLoop에서 처리
// 모두 논블로킹이므로 대기 없이 처리
```

**시간축으로 본 처리**:

```
시간 →
EventLoop Thread: reactor-http-nio-2

t0: Connection-1 Request-1 시작
t1: Connection-1 Request-1 → DB 호출 (논블로킹, 대기)
t2: Connection-2 Request-1 시작 (같은 스레드!)
t3: Connection-2 Request-1 → DB 호출 (논블로킹, 대기)
t4: Connection-3 Request-1 시작 (같은 스레드!)
t5: Connection-3 Request-1 → DB 호출 (논블로킹, 대기)
t6: Connection-1 DB 응답 도착 → 처리 계속
t7: Connection-1 Request-1 완료
t8: Connection-2 DB 응답 도착 → 처리 계속
t9: Connection-2 Request-1 완료
t10: Connection-3 DB 응답 도착 → 처리 계속
t11: Connection-3 Request-1 완료
```

**핵심 포인트**:
- ✅ 하나의 EventLoop 스레드가 **여러 커넥션** 관리
- ✅ 하나의 커넥션에서 **여러 요청** 처리 (Keep-Alive)
- ✅ 논블로킹 I/O로 **대기 없이** 다른 요청 처리
- ✅ DB 응답 도착 시 **콜백으로** 다시 처리 계속

---

#### 10.1.6 Spring MVC vs WebFlux 스레드 모델 비교

**Spring MVC (블로킹)**:

```
Thread Pool (예: 200개 스레드)
├── Thread-1
│   └── Connection-1 Request-1 (처리 중, 블로킹)
│       └── DB 대기 중... (스레드 블로킹, 다른 작업 불가)
│
├── Thread-2
│   └── Connection-2 Request-1 (처리 중, 블로킹)
│       └── DB 대기 중... (스레드 블로킹, 다른 작업 불가)
│
└── Thread-200
    └── Connection-200 Request-1 (처리 중, 블로킹)
        └── DB 대기 중... (스레드 블로킹, 다른 작업 불가)

문제:
- 200개 스레드 모두 대기 중
- 201번째 요청은 대기해야 함
- 스레드당 1개 요청만 처리
```

**WebFlux (논블로킹)**:

```
EventLoop Group (예: 8개 스레드)
├── EventLoop-1 (reactor-http-nio-1)
│   ├── Connection-1 Request-1 (DB 대기 중, 논블로킹)
│   ├── Connection-2 Request-1 (DB 대기 중, 논블로킹)
│   ├── Connection-3 Request-1 (처리 중)
│   ├── Connection-4 Request-1 (DB 대기 중, 논블로킹)
│   └── ... (수백 개의 커넥션)
│
└── EventLoop-8 (reactor-http-nio-8)
    ├── Connection-100 Request-1 (DB 대기 중, 논블로킹)
    └── ... (수백 개의 커넥션)

장점:
- 8개 스레드로 수천 개의 요청 처리
- DB 대기 중에도 다른 요청 처리 가능
- 스레드당 수백 개의 요청 처리
```

**처리 능력 비교**:

| 항목 | Spring MVC | WebFlux |
|------|-----------|---------|
| **스레드 수** | 200개 (예시) | 8개 (4코어 * 2) |
| **스레드당 커넥션** | 1개 | 수백~수천 개 |
| **스레드당 요청** | 1개 (동시) | 수백~수천 개 (동시) |
| **총 동시 처리** | 200개 요청 | 수천~수만 개 요청 |
| **스레드 대기** | 블로킹 (대기) | 논블로킹 (다른 작업) |

---

#### 10.1.7 실제 동작 예시

**시나리오: 1000개의 동시 요청**:

```java
// Spring MVC (블로킹)
// 스레드 풀: 200개
// 처리:
// - 처음 200개 요청: 즉시 처리 시작
// - 나머지 800개 요청: 대기 큐에서 대기
// - 각 스레드가 DB 응답 대기 중 (블로킹)
// - 총 처리 시간: 오래 걸림

// WebFlux (논블로킹)
// EventLoop: 8개
// 처리:
// - 모든 1000개 요청: 즉시 처리 시작
// - 각 EventLoop가 수백 개의 요청 관리
// - DB 응답 대기 중에도 다른 요청 처리
// - 총 처리 시간: 빠름
```

**코드로 확인**:

```java
@GetMapping("/test")
public Mono<String> test() {
    String threadName = Thread.currentThread().getName();
    String connectionId = extractConnectionId();  // 실제로는 요청에서 추출
    
    log.info("Thread: {}, Connection: {}, Request: {}", 
        threadName, connectionId, UUID.randomUUID());
    
    return Mono.delay(Duration.ofSeconds(1))  // 1초 지연 (논블로킹)
        .thenReturn("Response from " + threadName);
}

// 출력 예시:
// Thread: reactor-http-nio-1, Connection: conn-1, Request: req-1
// Thread: reactor-http-nio-1, Connection: conn-2, Request: req-2
// Thread: reactor-http-nio-1, Connection: conn-3, Request: req-3
// Thread: reactor-http-nio-1, Connection: conn-1, Request: req-4  (같은 커넥션!)
// Thread: reactor-http-nio-2, Connection: conn-10, Request: req-10
// ...
// 모두 같은 EventLoop 스레드에서 처리됨
```

---

#### 10.1.8 커넥션과 요청의 관계 요약

**핵심 정리**:

1. **커넥션 (Connection)**:
   - TCP 레벨의 연결
   - 3-way handshake로 생성
   - 하나의 커넥션은 여러 요청 처리 가능

2. **요청 (Request)**:
   - HTTP 레벨의 요청
   - 커넥션 위에서 전송
   - HTTP/1.1 Keep-Alive: 순차 처리
   - HTTP/2 Multiplexing: 병렬 처리

3. **EventLoop의 역할**:
   - 여러 커넥션 관리
   - 각 커넥션의 여러 요청 처리
   - 논블로킹으로 대기 없이 처리

4. **동시 처리 능력**:
   - 8개 EventLoop 스레드
   - 각 스레드가 수백 개의 커넥션 관리
   - 각 커넥션에서 여러 요청 처리
   - 총 수천~수만 개의 동시 요청 처리 가능

**비유**:
```
EventLoop = 식당 종업원 1명
커넥션 = 테이블
요청 = 주문

종업원 1명이 여러 테이블을 관리하고,
각 테이블에서 여러 주문을 받을 수 있음.
주문 처리 중에도 다른 테이블의 주문을 받을 수 있음 (논블로킹).
```

---

#### 10.1.4 스레드 전환 메커니즘

**subscribeOn의 동작**:

```java
// subscribeOn은 구독 시점에 스레드 전환
Mono<String> mono = Mono.fromCallable(() -> {
    // 이 시점에 스레드 전환 발생
    log.info("Thread: {}", Thread.currentThread().getName());
    // 출력: Thread: boundedElastic-1
    return "data";
})
.subscribeOn(Schedulers.boundedElastic());

// 구독 전까지는 스레드 전환 없음
// mono.subscribe() 호출 시점에 스레드 전환
```

**publishOn의 동작**:

```java
// publishOn은 이후 연산자부터 스레드 전환
Flux<String> flux = Flux.just("A", "B", "C")
    .map(s -> {
        // EventLoop 스레드
        log.info("Before: {}", Thread.currentThread().getName());
        // 출력: Before: reactor-http-nio-2
        return s;
    })
    .publishOn(Schedulers.parallel())  // 여기서 스레드 전환
    .map(s -> {
        // parallel 스레드
        log.info("After: {}", Thread.currentThread().getName());
        // 출력: After: parallel-1
        return s;
    });
```

**flatMap의 스레드 동작**:

```java
// flatMap 내부의 Publisher는 자체 스레드 사용
Flux<String> flux = Flux.just("A", "B")
    .flatMap(s -> {
        // EventLoop 스레드에서 실행
        return Mono.fromCallable(() -> {
            // subscribeOn이 없으면 EventLoop 스레드에서 실행 (블로킹 위험!)
            return blockingOperation(s);
        })
        .subscribeOn(Schedulers.boundedElastic());  // 필수!
    })
    .map(result -> {
        // flatMap 완료 후 원래 스레드로 돌아옴
        // 하지만 내부 Publisher가 다른 스레드에서 실행되었으므로
        // 결과는 다른 스레드에서 온 것
        return result;
    });
```

---

#### 10.1.5 스레드 안전성 고려사항

**스레드 안전하지 않은 코드**:

```java
// ❌ 나쁜 예: 공유 변수 사용
private int counter = 0;

public Mono<Integer> increment() {
    return Mono.fromCallable(() -> {
        // 여러 스레드에서 동시 접근 가능
        counter++;  // Race condition!
        return counter;
    });
}
```

**스레드 안전한 코드**:

```java
// ✅ 좋은 예: AtomicInteger 사용
private final AtomicInteger counter = new AtomicInteger(0);

public Mono<Integer> increment() {
    return Mono.fromCallable(() -> {
        return counter.incrementAndGet();  // Thread-safe
    });
}

// ✅ 좋은 예: Reactor Context 사용 (스레드 안전)
public Mono<String> process(String data) {
    return Mono.just(data)
        .flatMap(d -> {
            // Context는 스레드 안전하게 전파됨
            return Mono.deferContextual(ctx -> {
                String traceId = ctx.getOrDefault("traceId", "unknown");
                return Mono.just(d + ":" + traceId);
            });
        });
}

// ✅ 좋은 예: ThreadLocal 대신 Reactor Context
// ThreadLocal은 스레드 전환 시 값이 전파되지 않음
// Reactor Context는 스레드 전환 시에도 유지됨
```

---

#### 10.1.6 스레드 풀 크기 최적화

**EventLoop 스레드 수 설정**:

```java
@Configuration
public class NettyConfig {
    
    @Bean
    public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.addServerCustomizers(httpServer -> 
            httpServer.runOn(
                LoopResources.create("custom", 
                    4,      // 이벤트 루프 수
                    true    // 데몬 스레드
                )
            )
        );
        return factory;
    }
}
```

**스레드 풀 크기 가이드라인**:

| 작업 유형 | Scheduler | 권장 스레드 수 |
|----------|----------|--------------|
| **논블로킹 I/O** | EventLoop | CPU 코어 * 2 |
| **블로킹 I/O** | boundedElastic | 동적 (최대 10 * CPU 코어) |
| **CPU 집약적** | parallel | CPU 코어 수 |
| **순차 처리** | single | 1 |

**성능 모니터링**:

```java
// 스레드 사용량 모니터링
@Scheduled(fixedRate = 5000)
public void monitorThreads() {
    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    int threadCount = threadBean.getThreadCount();
    int peakThreadCount = threadBean.getPeakThreadCount();
    
    log.info("Thread count: {}, Peak: {}", threadCount, peakThreadCount);
    
    // EventLoop 스레드 확인
    Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().startsWith("reactor-http-nio"))
        .forEach(t -> log.info("EventLoop: {}", t.getName()));
}
```

---

#### 10.1.7 스레드 전환 비용과 최적화

**스레드 전환 비용**:
- 컨텍스트 스위칭 오버헤드
- CPU 캐시 미스
- 메모리 접근 패턴 변화

**최적화 팁**:

```java
// ❌ 나쁜 예: 불필요한 스레드 전환
Flux.range(1, 100)
    .publishOn(Schedulers.parallel())
    .map(i -> i * 2)  // 간단한 연산인데 스레드 전환
    .publishOn(Schedulers.parallel())
    .map(i -> i + 1);  // 또 스레드 전환

// ✅ 좋은 예: 필요한 곳에만 스레드 전환
Flux.range(1, 100)
    .map(i -> i * 2)  // EventLoop에서 실행 (빠름)
    .publishOn(Schedulers.parallel())
    .map(i -> heavyComputation(i))  // 무거운 연산만 별도 스레드
    .map(i -> i.toString());  // 같은 스레드에서 계속

// ✅ 좋은 예: 스레드 전환 최소화
Mono<String> mono = Mono.fromCallable(() -> {
    // 모든 블로킹 작업을 한 번에 처리
    String data1 = blockingOperation1();
    String data2 = blockingOperation2();
    return data1 + data2;
})
.subscribeOn(Schedulers.boundedElastic());  // 한 번만 전환
```

---

#### 10.1.8 실제 스레드 흐름 예시

**완전한 예시**:

```java
@GetMapping("/users/{id}/profile")
public Mono<UserProfile> getUserProfile(@PathVariable String id) {
    // [1] EventLoop 스레드: reactor-http-nio-2
    log.info("1. Controller: {}", Thread.currentThread().getName());
    
    return userRepository.findById(id)  // 논블로킹, 같은 스레드
        .doOnNext(user -> {
            // [2] EventLoop 스레드: reactor-http-nio-2
            log.info("2. After findById: {}", Thread.currentThread().getName());
        })
        .flatMap(user -> {
            // [3] EventLoop 스레드: reactor-http-nio-2
            log.info("3. Before blocking: {}", Thread.currentThread().getName());
            
            // 블로킹 작업을 별도 스레드로
            return Mono.fromCallable(() -> {
                // [4] boundedElastic 스레드: boundedElastic-1
                log.info("4. Blocking operation: {}", Thread.currentThread().getName());
                return heavyComputation(user);
            })
            .subscribeOn(Schedulers.boundedElastic());
        })
        .flatMap(result -> {
            // [5] EventLoop 스레드: reactor-http-nio-2 (flatMap 완료 후)
            log.info("5. After blocking: {}", Thread.currentThread().getName());
            
            // 또 다른 논블로킹 작업
            return profileService.getProfile(result.getId());
        })
        .doOnNext(profile -> {
            // [6] EventLoop 스레드: reactor-http-nio-2
            log.info("6. Final: {}", Thread.currentThread().getName());
        });
}
```

**출력 결과**:
```
1. Controller: reactor-http-nio-2
2. After findById: reactor-http-nio-2
3. Before blocking: reactor-http-nio-2
4. Blocking operation: boundedElastic-1
5. After blocking: reactor-http-nio-2
6. Final: reactor-http-nio-2
```

**핵심 포인트**:
- 논블로킹 작업은 EventLoop 스레드 유지
- 블로킹 작업만 별도 스레드로 전환
- flatMap 완료 후 원래 스레드로 복귀

---

### 10.2 백프레셔 활용

**백프레셔 제어**:
```java
// 요청 속도 제한
Flux<String> flux = Flux.range(1, 1000)
    .delayElements(Duration.ofMillis(10))  // 10ms마다 하나씩
    .map(i -> "Item " + i);
```

---

### 10.3 캐싱

**리액티브 캐싱**:
```java
public Mono<User> getUser(String id) {
    String cacheKey = "user:" + id;
    
    return cacheManager.getCache("users")
        .get(cacheKey, User.class)
        .switchIfEmpty(
            userRepository.findById(id)
                .flatMap(user -> 
                    cacheManager.getCache("users")
                        .put(cacheKey, user)
                        .thenReturn(user)
                )
        );
}
```

---

## 11. Spring MVC vs WebFlux

### 11.1 비교표

| 항목 | Spring MVC | WebFlux |
|------|-----------|---------|
| **프로그래밍 모델** | 명령형 (Imperative) | 리액티브 (Reactive) |
| **I/O 모델** | 블로킹 | 논블로킹 |
| **스레드 모델** | 요청당 스레드 | 이벤트 루프 |
| **동시성** | 스레드 풀 크기에 제한 | 높은 동시성 |
| **서블릿 스택** | 필요 | 불필요 (Netty) |
| **예외 처리** | try-catch | onError 시그널 |
| **반환 타입** | Object, ResponseEntity | Mono, Flux |
| **학습 곡선** | 낮음 | 높음 |

---

### 11.2 선택 가이드

**Spring MVC 선택**:
- 간단한 CRUD 애플리케이션
- 블로킹 I/O가 많은 경우
- 팀이 리액티브에 익숙하지 않은 경우
- 기존 Spring MVC 코드베이스

**WebFlux 선택**:
- 높은 동시성이 필요한 경우
- 논블로킹 I/O가 중요한 경우
- 스트리밍 데이터 처리
- 마이크로서비스 간 비동기 통신

---

## 12. 핵심 정리

### 12.1 WebFlux의 핵심 원칙

1. **논블로킹**: 모든 I/O는 논블로킹
2. **리액티브 스트림**: Publisher-Subscriber 패턴
3. **백프레셔**: 데이터 흐름 제어
4. **이벤트 루프**: 적은 스레드로 많은 연결 처리

---

### 12.2 모범 사례

**DO**:
- ✅ `Mono.error()` 사용 (throw 금지)
- ✅ 블로킹 작업은 `boundedElastic` 스케줄러 사용
- ✅ `doOnNext`, `doOnError`로 사이드 이펙트 처리
- ✅ `onErrorResume`으로 에러 복구
- ✅ Reactor Context로 데이터 전파

**DON'T**:
- ❌ EventLoop에서 블로킹 작업
- ❌ `throw` 사용
- ❌ `block()` 사용 (테스트 제외)
- ❌ 무한 스트림 생성
- ❌ 불필요한 `subscribe()` 호출

---

### 12.3 학습 경로

1. **기초**: Reactive Streams 스펙 이해
2. **Reactor**: Mono, Flux 연산자 학습
3. **WebFlux**: RouterFunction, HandlerFunction
4. **실전**: WebClient, 예외 처리, 성능 최적화

---

## 📚 참고 자료

- [Spring WebFlux 공식 문서](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Project Reactor 참조 가이드](https://projectreactor.io/docs/core/release/reference/)
- [Reactive Streams 스펙](https://www.reactive-streams.org/)

---

**작성일**: 2024  
**버전**: 1.0
