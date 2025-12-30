> WebFlux에서 요청 처리 흐름은 "메서드 호출 스택"이 아니라
> "Subscriber가 Subscriber를 감싼 실행 체인"이다

> Reactor에서 Subscriber는 아래와 같다.
```java
interface Subscriber<T> {
    void onSubscribe(Subscription s);
    void onNext(T t);
    void onError(Throwable t);
    void onComplete();
}
```

```
Subscriber(
  WebClientSubscriber(
    ServiceSubscriber(
      ControllerSubscriber(
        FilterSubscriber(
          ExceptionHandlingSubscriber(
            NettySubscriber
          )
        )
      )
    )
  )
)
```

### 스프링 MVC와의 비교
#### Spring MVC (동기)

    Thread
    ↓
    Filter
    ↓
    Controller
    ↓
    Service
    ↓
    return response

+ 하나의 스레드
+ call stack 기반
+ 예외는 try-catch로 위로 전파

#### Spring WebFlux (리액티브)

    Subscriber
    └─ Subscriber
    └─ Subscriber
    └─ Subscriber
+ call stack X
+ 시그널 전달 모델
+ 예외는 onError 시그널


### WebFlux 전체 흐름
1) HTTP 요청 도착 (Netty EventLoop)
2) Mono<Void> 파이프라인 생성 (아직 실행 안 됨)
3) WebFilter / Controller / Service 가
   → Subscriber를 하나씩 "포장"
4) 맨 마지막에 subscribe() 호출
5) 데이터(onNext)와 신호(onComplete / onError)가
   → 안쪽 → 바깥쪽으로 흐름
6) 예외 발생 시 onError가
   → 바깥 Subscriber들로 역전파