# RDB Connection 및 @transactional 데코레이터 기술 설명

## 📋 목차

1. [RDB Connection 구조](#1-rdb-connection-구조)
2. [@transactional 데코레이터](#2-transactional-데코레이터)
3. [Transaction Context 연동](#3-transaction-context-연동)
4. [실제 사용 예시](#4-실제-사용-예시)
5. [핵심 설계 원칙](#5-핵심-설계-원칙)

---

## 1. RDB Connection 구조

### 1.1 동기/비동기 엔진 분리

```python
# be_src/common/database/rdb_connection.py

""" Sync DB Connection (동기) """
SQLALCHEMY_DATABASE_URL = settings.RDB_DATABASE_URL

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    pool_size=10,                    # 기본 연결 풀 크기
    max_overflow=20,                 # 최대 오버플로우 연결 수
    isolation_level="READ COMMITTED", # 트랜잭션 격리 수준
    connect_args={"options": "-csearch_path={}".format(SCHEMA_NAME)},
)

SessionLocal = sessionmaker(
    autocommit=False, 
    autoflush=False, 
    bind=engine
)

""" Async DB Connection (비동기) """
SQLALCHEMY_DATABASE_URL = settings.RDB_DATABASE_URL_ASYNC
SQLALCHEMY_READ_DATABASE_URL = settings.RDB_DATABASE_URL_ASYNC

# Write용 엔진 (쓰기 전용)
write_async_engine = create_async_engine(
    SQLALCHEMY_DATABASE_URL,
    pool_size=10,
    max_overflow=20,
    isolation_level="READ COMMITTED",
    connect_args={"server_settings": {"search_path": SCHEMA_NAME}},
)

# Read용 엔진 (읽기 전용 - 읽기 전용 복제본 사용 가능)
read_async_engine = create_async_engine(
    SQLALCHEMY_READ_DATABASE_URL,
    pool_size=10,
    max_overflow=20,
    isolation_level="READ COMMITTED",
    connect_args={"server_settings": {"search_path": SCHEMA_NAME}},
)
```

### 1.2 세션 팩토리 생성

```python
# Write 세션 팩토리
AsyncSessionLocal = sessionmaker(
    bind=write_async_engine,
    expire_on_commit=False,    # 커밋 후 객체 만료 방지
    class_=AsyncSession,        # 비동기 세션 클래스
    autocommit=False,           # 자동 커밋 비활성화
    autoflush=False,            # 자동 플러시 비활성화
)

# Read 세션 팩토리
AsyncReadSessionLocal = sessionmaker(
    bind=read_async_engine,
    expire_on_commit=False,
    class_=AsyncSession,
    autocommit=False,
    autoflush=False,
)
```

**핵심 특징:**
- **Read/Write 분리**: 읽기와 쓰기를 별도 엔진으로 분리 (읽기 전용 복제본 활용 가능)
- **Connection Pooling**: 연결 풀링으로 성능 최적화
- **스키마 설정**: PostgreSQL 스키마 자동 설정 (`search_path`)

---

## 2. @transactional 데코레이터

### 2.1 데코레이터 구조

```python
def transactional(db_type: str = "write"):
    """
    트랜잭션 관리 데코레이터
    
    Args:
        db_type: "write" 또는 "read"
            - "write": 쓰기 트랜잭션 (커밋 수행)
            - "read": 읽기 전용 트랜잭션 (커밋 없음)
    """
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            # 1. 기존 세션 확인 (중첩 트랜잭션 지원)
            db_session = transaction_context_manager.get_async_db_session()
            if db_session:
                # 이미 세션이 있으면 재사용 (중첩 트랜잭션)
                return await func(*args, **kwargs)
            
            # 2. 세션 팩토리 선택
            session_factory = (
                AsyncSessionLocal if db_type == "write" 
                else AsyncReadSessionLocal
            )
            
            # 3. 세션 생성 및 컨텍스트에 저장
            async with session_factory() as session:
                transaction_context_manager.add_to_transaction_context(
                    "async_db_session", session
                )
                
                try:
                    # 4. 원래 함수 실행
                    result = await func(*args, **kwargs)
                    
                    # 5. Write 트랜잭션은 커밋
                    if db_type == "write":
                        await session.commit()
                    
                    return result
                
                except Exception as e:
                    # 6. 예외 발생 시 롤백
                    await session.rollback()
                    logger.error(f"예외 확인 - DB Rollback: {e}")
                    raise
                
                finally:
                    # 7. 컨텍스트에서 세션 제거 및 세션 닫기
                    transaction_context_manager.remove_from_transaction_context(
                        "async_db_session"
                    )
                    await session.close()
        
        return wrapper
    return decorator
```

### 2.2 핵심 동작 원리

#### 1. 중첩 트랜잭션 지원

```python
# 기존 세션이 있으면 재사용
db_session = transaction_context_manager.get_async_db_session()
if db_session:
    # 이미 상위 함수에서 세션을 생성했으면 재사용
    return await func(*args, **kwargs)
```

**시나리오:**
```python
@transactional("write")
async def service_function():
    # 이 함수에서 세션 생성
    await repository_function()  # 하위 함수는 같은 세션 재사용

@transactional("write")
async def repository_function():
    # 상위 함수의 세션을 재사용 (중첩 트랜잭션)
    # 별도의 세션을 생성하지 않음
```

#### 2. Read/Write 분리

```python
# Write 트랜잭션
@transactional("write")
async def create_user():
    # write_async_engine 사용
    # 커밋 수행
    pass

# Read 트랜잭션
@transactional("read")
async def get_user():
    # read_async_engine 사용 (읽기 전용 복제본 가능)
    # 커밋 없음
    pass
```

#### 3. 자동 트랜잭션 관리

```python
try:
    result = await func(*args, **kwargs)
    if db_type == "write":
        await session.commit()  # 성공 시 커밋
    return result
except Exception as e:
    await session.rollback()     # 실패 시 롤백
    raise
finally:
    await session.close()        # 항상 세션 닫기
```

---

## 3. Transaction Context 연동

### 3.1 ContextVar 기반 세션 관리

```python
# be_src/common/core/transaction_context.py

import contextvars

# Context 변수 정의
transaction_context = contextvars.ContextVar("transaction_context")

class TransactionContext(BaseModel):
    async_db_session: Any = None  # DB 세션 저장

class TransactionContextManager:
    def add_to_transaction_context(self, key, value):
        """컨텍스트에 데이터 추가"""
        current_data = transaction_context.get(None) or {}
        current_data[key] = value
        transaction_context.set(current_data)
    
    def get_async_db_session(self) -> AsyncSession:
        """컨텍스트에서 DB 세션 조회"""
        return self.get_transaction_context().async_db_session
```

### 3.2 세션 전파 메커니즘

```
┌─────────────────────────────────────────┐
│  Service Layer (@transactional)         │
│  └─ 세션 생성 → Context에 저장            │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  Repository Layer                       │
│  └─ Context에서 세션 조회                 │
│     (별도 세션 생성 없음)                  │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│  Database                               │
│  └─ 같은 트랜잭션으로 실행                  │
└─────────────────────────────────────────┘
```

**예시:**
```python
# Service
@transactional("write")
async def create_service(service_data):
    # 세션 생성 및 Context에 저장
    await repository.create(service_data)  # 같은 세션 사용
    await repository.create_history(service_data)  # 같은 세션 사용
    # 모든 작업이 같은 트랜잭션으로 실행됨

# Repository
class ServiceRepository:
    async def create(self, data):
        # Context에서 세션 가져오기
        session = transaction_context_manager.get_async_db_session()
        session.add(Service(**data))
        # 커밋은 Service 레이어에서 수행
```

---

## 4. 실제 사용 예시

### 4.1 Service 레이어 사용

```python
# be_src/apps/management_app/services/user_service.py

from common.decorator.db_session_decorator import transactional

class UserService:
    @transactional("read")  # 읽기 전용
    async def get_users_with_paging(self, get_users_with_paging_in):
        # read_async_engine 사용
        # 커밋 없음
        users = await user_repository.get_users(...)
        return users
    
    @transactional("write")  # 쓰기 트랜잭션
    async def update_user(self, update_user_in):
        # write_async_engine 사용
        # 자동 커밋
        await user_repository.update_user(...)
        # 성공 시 자동 커밋, 실패 시 자동 롤백
```

### 4.2 Repository 레이어 사용

```python
# be_src/common/repositories/rdb/user_repository.py

from common.core.transaction_context import transaction_context_manager

class UserRepository(BaseRepository):
    async def get_users(self, condition):
        # Context에서 세션 가져오기
        session = transaction_context_manager.get_async_db_session()
        
        query = select(User).where(...)
        result = await session.execute(query)
        return result.scalars().all()
    
    async def update_user(self, user_data):
        session = transaction_context_manager.get_async_db_session()
        
        user = await session.get(User, user_id)
        user.name = user_data.name
        # 변경사항은 세션에 저장됨
        # 커밋은 Service 레이어에서 수행
```

### 4.3 중첩 트랜잭션 예시

```python
@transactional("write")
async def complex_operation():
    # 세션 생성 (트랜잭션 시작)
    
    await repository1.create(data1)  # 같은 세션 사용
    
    @transactional("write")
    async def nested_operation():
        # 기존 세션 재사용 (새 세션 생성 안 함)
        await repository2.create(data2)  # 같은 세션 사용
    
    await nested_operation()
    
    # 모든 작업이 하나의 트랜잭션으로 처리
    # 성공 시 한 번에 커밋, 실패 시 전체 롤백
```

---

## 5. 핵심 설계 원칙

### 5.1 단일 책임 원칙

- **데코레이터**: 트랜잭션 생명주기 관리만 담당
- **Repository**: DB 쿼리만 담당 (트랜잭션 관리 안 함)
- **Service**: 비즈니스 로직 및 트랜잭션 경계 설정

### 5.2 트랜잭션 경계 명확화

```python
# ✅ 올바른 사용
@transactional("write")
async def service_function():
    await repo1.create()  # 같은 트랜잭션
    await repo2.update()  # 같은 트랜잭션
    # 모두 성공하면 커밋, 하나라도 실패하면 롤백

# ❌ 잘못된 사용
async def service_function():
    @transactional("write")
    async def inner():
        await repo1.create()  # 별도 트랜잭션
    await inner()
    
    @transactional("write")
    async def inner2():
        await repo2.update()  # 별도 트랜잭션
    await inner2()
    # 두 개의 독립적인 트랜잭션으로 실행됨
```

### 5.3 Read/Write 분리

**장점:**
- 읽기 전용 복제본 활용 가능
- 읽기 부하 분산
- 쓰기 성능 향상

```python
# 읽기 작업은 읽기 전용 엔진 사용
@transactional("read")
async def get_data():
    # read_async_engine 사용
    # 읽기 전용 복제본으로 부하 분산
    pass

# 쓰기 작업은 쓰기 전용 엔진 사용
@transactional("write")
async def update_data():
    # write_async_engine 사용
    # 마스터 DB에 직접 쓰기
    pass
```

### 5.4 ContextVar의 비동기 안전성

**왜 ContextVar를 사용하는가?**

```python
# ❌ 전역 변수 사용 (비동기 환경에서 문제 발생)
global_session = None

async def function1():
    global global_session
    global_session = session  # 다른 코루틴과 공유됨 (위험!)

async def function2():
    global global_session
    session = global_session  # function1의 세션을 사용할 수 있음 (버그!)

# ✅ ContextVar 사용 (비동기 안전)
transaction_context = contextvars.ContextVar("transaction_context")

async def function1():
    transaction_context.set({"session": session})  # 현재 컨텍스트에만 저장

async def function2():
    session = transaction_context.get()["session"]  # 자신의 컨텍스트에서만 조회
```

**핵심 특징:**
- **요청별 격리**: 각 HTTP 요청마다 독립적인 컨텍스트
- **비동기 안전**: `asyncio` 환경에서도 안전하게 동작
- **자동 전파**: 하위 함수에서 자동으로 같은 컨텍스트 접근

### 5.5 자동 리소스 관리

```python
async with session_factory() as session:
    # 세션 생성
    try:
        # 작업 수행
        pass
    except Exception:
        # 자동 롤백
        await session.rollback()
        raise
    finally:
        # 자동 세션 닫기
        await session.close()
```

**장점:**
- 메모리 누수 방지
- 연결 풀 자동 반환
- 예외 상황에서도 안전한 정리

---

## 6. 성능 최적화

### 6.1 Connection Pooling

```python
write_async_engine = create_async_engine(
    SQLALCHEMY_DATABASE_URL,
    pool_size=10,        # 기본 10개 연결 유지
    max_overflow=20,     # 최대 30개까지 확장 가능
)
```

**효과:**
- 연결 생성 비용 절감
- 동시 요청 처리 능력 향상
- 리소스 효율적 사용

### 6.2 세션 재사용

```python
# 중첩 함수 호출 시 세션 재사용
@transactional("write")
async def service():
    await repo1()  # 세션 생성
    await repo2()  # 같은 세션 재사용 (새로 생성 안 함)
```

**효과:**
- 불필요한 세션 생성 방지
- 트랜잭션 일관성 보장
- 성능 향상

---

## 7. 에러 처리 및 안정성

### 7.1 자동 롤백

```python
try:
    result = await func(*args, **kwargs)
    if db_type == "write":
        await session.commit()
    return result
except Exception as e:
    await session.rollback()  # 자동 롤백
    raise  # 예외 전파
```

**보장 사항:**
- 예외 발생 시 자동 롤백
- 데이터 일관성 유지
- 부분 커밋 방지

### 7.2 리소스 정리

```python
finally:
    transaction_context_manager.remove_from_transaction_context("async_db_session")
    await session.close()  # 항상 세션 닫기
```

**보장 사항:**
- 예외 발생 여부와 관계없이 세션 닫기
- 연결 풀에 연결 반환
- 메모리 누수 방지

---

## 8. 사용 가이드라인

### 8.1 언제 @transactional을 사용하는가?

**✅ Service 레이어에서 사용:**
```python
@transactional("write")
async def create_service(service_data):
    # 비즈니스 로직의 트랜잭션 경계
    await repo1.create(...)
    await repo2.create(...)
```

**✅ Repository 레이어에서는 사용하지 않음:**
```python
# Repository는 세션을 Context에서 가져와서 사용
class ServiceRepository:
    async def create(self, data):
        session = transaction_context_manager.get_async_db_session()
        # 세션 사용
```

### 8.2 Read vs Write 선택

```python
# 읽기 작업
@transactional("read")
async def get_data():
    # SELECT 쿼리만 수행
    pass

# 쓰기 작업 (INSERT, UPDATE, DELETE)
@transactional("write")
async def modify_data():
    # 데이터 변경 작업
    pass
```

### 8.3 주의사항

**❌ 데코레이터 중첩 사용 금지:**
```python
# 같은 함수에 여러 데코레이터 사용 시 주의
@transactional("write")
@transactional("read")  # 이렇게 하면 안 됨
async def function():
    pass
```

**✅ 명확한 트랜잭션 경계:**
```python
# Service 레이어에서만 트랜잭션 경계 설정
@transactional("write")
async def service_function():
    # 모든 하위 작업이 같은 트랜잭션
    pass
```

---

## 📚 요약

### RDB Connection
- ✅ 동기/비동기 엔진 분리
- ✅ Read/Write 엔진 분리 (읽기 전용 복제본 지원)
- ✅ Connection Pooling으로 성능 최적화
- ✅ PostgreSQL 스키마 자동 설정

### @transactional 데코레이터
- ✅ 자동 트랜잭션 관리 (커밋/롤백)
- ✅ 중첩 트랜잭션 지원 (세션 재사용)
- ✅ Read/Write 분리
- ✅ ContextVar 기반 세션 전파
- ✅ 자동 리소스 정리

### 핵심 설계 원칙
- ✅ 단일 책임 원칙 (각 계층의 역할 명확)
- ✅ 트랜잭션 경계 명확화 (Service 레이어에서 설정)
- ✅ 비동기 안전성 (ContextVar 사용)
- ✅ 자동 에러 처리 및 리소스 정리

