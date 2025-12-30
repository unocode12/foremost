### WebFlux 예외 처리 전체 구조
    Subscriber 체인
    ↓ onError
    DispatcherHandler
    ↓
    WebExceptionHandler (GlobalErrorHandler)
    ↓
    HTTP Response 작성

---

    [HTTP 요청]
    ↓
    [RequestLoggingFilter]
    ↓ contextWrite(traceId)
    [Controller]
    ↓
    [Service]
    ↓
    [WebClient]
    ↓ (에러 발생)
    [onError]
    ↓
    [GlobalErrorHandler]
    ↓
    [Error Response Write]
    ↓
    [doFinally → MDC.clear]
    ↓
    [Connection Close]

### 요청으로부터 보았을 때 핸들러 위치
    [Netty]
    ↓
    [HttpWebHandlerAdapter]
    ↓
    [ExceptionHandlingWebHandler]  ← 여기에 WebExceptionHandler 존재
    ↓
    [DispatcherHandler]
    
    ExceptionHandlingWebHandler에서 List<WebExceptionHandler>를 순서대로 실행

### 우선순위
    ```java
    @Order(-2)
    public class GlobalErrorHandler implements WebExceptionHandler
    ```
    
    Order	Handler
    -2	    Custom GlobalErrorHandler
    -1	    DefaultErrorWebExceptionHandler
    0+	    기타
    
    이므로 가장 먼저 우선순위를 갖는다.

### exception 처리 순서
    1 에러 발생
    Mono.error(new BusinessException(...))
    ⬇️
    onError(BusinessException)
    
    2 GlobalErrorHandler.handle 호출
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex)
        이 시점의 상태:
            항목	            상태
            Subscriber	    살아 있음
            Reactor Context	유지됨
            MDC	            있을 수도 / 없을 수도
            Response	    아직 안 써짐
    
    3 예외 분류
    if (ex instanceof ExternalApiException)
    ✔️ 동기 코드처럼 보이지만 실제로는 Subscriber 위에서 실행
    
    4 Response 작성
    exchange.getResponse().writeWith(...)
    이 순간:
        HTTP Response 직접 완성
        Controller로 다시 안 돌아감
        스트림 종료
    
    5 이후 체인
    onError → handled → onComplete

### doOnError vs WebExceptionHandler
구분	        doOnError	        WebExceptionHandler
위치	        Subscriber 내부	    Subscriber 외부
역할	        관찰 / 로깅	        응답 생성
에러 소비	❌ (흘려보냄)	    ✅ (종결)
실행 시점	onError 시그널 중간	최종