### 파이썬은 GIL이 있다고 해서 "진짜 싱글 스레드"는 아님
    항목	        가능          여부
    OS          스레드 생성	 ✅
    I/O         병렬성	     ✅
    CPU         병렬성	     ❌
    멀티코어      활용	     ❌

### GIL 핵심은 Reference Counting 기반의 메모리 관리!
### GIL 때문에 한 시점에 하나의 바이트코드만 실행 => 한 시점에는 하나의 스레드만 실행

### 코루틴은 “중간에 멈췄다가 다시 이어서 실행할 수 있는 함수”
+ 실행 → 중단(yield/await) → 다른 작업 수행 → 다시 이어서 실행
+ 핵심은 제어권을 호출자에게 “양보(yield)”할 수 있다는 것

### 코루틴이 필요한 이유
+ 스레드 기반 동시성
  + 컨텍스트 스위칭 비용 큼
  + GIL 때문에 CPU 병렬성 제한 
+ 블로킹 I/O
  + 네트워크 / 파일 I/O 동안 CPU 놀고 있음
---
+ I/O 대기 시간 동안 다른 작업 수행
+ 단일 스레드에서 수천 개 작업 처리
+ 명시적이고 예측 가능한 동시성

### 코루틴 정의
```python
async def coro():
    await asyncio.sleep(1)
    print("done")
```
+ async def
  + 코루틴 객체를 반환
  + 호출해도 즉시 실행 X
+ await
  + “이 작업 끝날 때까지 여기서 멈춰도 됨”
  + 이벤트 루프에 제어권 반환

### 코루틴 전체 구조
```text
Coroutine (async def)
        ↓ (wrap)
      Task  ──────▶ Event Loop
        ↓
     Future (결과 컨테이너)
```
+ Coroutine: 실행 로직
+ Task: 코루틴을 이벤트 루프에 올린 실행 단위
+ Future: “아직 오지 않은 결과”를 담는 상자

### 이벤트 루프와의 관계
> 코루틴은 혼자 실행되지 않는다.
+ [Coroutine] ↔ [Event Loop] ↔ [Future/Task]
+ 실행 흐름
  + 이벤트 루프가 코루틴 실행
  + await에서 중단
  + 다른 코루틴 실행
  + I/O 완료 시 다시 재개

### FASTAPI에서의 코루틴
> Uvicorn이 이벤트 루프를 띄우고 → HTTP 요청마다 FastAPI 엔드포인트 코루틴을 Task로 감싸 실행 → await 지점에서 다른 요청 처리

#### Uvicorn
+ ASGI(Asynchronous Server Gateway Interface) 서버
+ 이벤트 루프 소유자
+ 기본적으로 uvloop 사용 (C 기반 고성능 루프)
```text
Uvicorn
 └── Event Loop (uvloop / asyncio)
```

#### FastAPI
+ ASGI 애플리케이션
+ 코루틴 팩토리
+ 라우팅 / DI / validation 담당

#### Starlette
+ FastAPI 내부 프레임워크
+ 미들웨어, ASGI 호출 규약 처리

```python
@app.get("/users")
async def users():
    data = await fetch_from_db()
    return data
```
+ 내부에서
```text
Task(users)
   ↓ await
Task(fetch_from_db) → PENDING
```

```python
@app.get("/sync")
def sync_api():
    return heavy_cpu_work()
```
+ 동기 함수의 경우 내부에서 ThreadPoolExecutor로 오프로드
```text
Event Loop
   └── await run_in_executor(sync_api)
           └── Worker Thread
```