### WebFlux 전체 흐름
    [Netty EventLoop Thread]
    |
    | WebFilter 시작
    | MDC에 traceId 있음 (Ingress / Gateway)
    |
    | contextWrite (MDC → Context 복사)
    v
    [Subscriber 체인 시작]
    |
    | (Hooks 작동)
    | Thread 변경 시마다
    | Context → MDC 자동 복사
    v
    [Service / Handler / WebClient]
    |
    | log.info() → MDC 정상
    |
    | 에러 발생
    v
    [Subscriber.onError()]