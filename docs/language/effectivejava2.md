# Effective Java - Part 2

## ✅ 6. 불필요한 객체 생성을 피하라 (Effective Java Item 6)

### 🔑 핵심 한 문장

**똑같은 의미의 객체를 매번 새로 만들지 말고, 재사용할 수 있으면 재사용하라.**

> **면접 단골 질문**: "불필요한 객체 생성이 왜 문제인가요? 어떤 경우에 피해야 하나요?"

---

## 📌 왜 중요한가?

불필요한 객체 생성은 단순한 성능 문제가 아닙니다:

- **메모리 낭비**: 불필요한 객체가 힙 메모리 점유
- **GC 압박**: 가비지 컬렉션 빈도 증가 → 애플리케이션 일시 정지
- **성능 저하**: 객체 생성 비용 + GC 비용
- **API 설계 감각**: 객체 생명주기와 재사용에 대한 이해

---

## 1️⃣ 대표적인 나쁜 예

### ❌ String 객체 생성

```java
// 나쁜 예
String s = new String("hello");
```

**왜 나쁜가?**

- `"hello"`는 이미 String Pool에 있음
- `new`는 무조건 새 객체 생성
- 의미적으로 동일한 객체를 굳이 새로 만듦 → 메모리 + GC 낭비

**개선**:

```java
// 좋은 예
String s = "hello"; // String Pool에서 재사용
```

**비교**:

```java
String s1 = new String("hello");
String s2 = new String("hello");
System.out.println(s1 == s2); // false - 다른 객체

String s3 = "hello";
String s4 = "hello";
System.out.println(s3 == s4); // true - 같은 객체 재사용
```

---

### ❌ 정규식 Pattern 매번 생성

```java
// 나쁜 예
static boolean isValid(String s) {
    return s.matches("\\d+");
}
```

**내부에서 무슨 일이?**

```java
// String.matches() 내부 구현 (의사 코드)
public boolean matches(String regex) {
    return Pattern.compile(regex).matcher(this).matches();
    //     ^^^^^^^^^^^^^^^^^ 매번 새로 생성!
}
```

**문제점**:

- `Pattern.compile()`은 **비용이 매우 비쌈**
- 정규식을 컴파일하고 내부 상태를 생성
- 매번 호출할 때마다 새 `Pattern` 객체 생성
- GC 압박 증가

**✅ 개선**:

```java
// 좋은 예
public class PhoneNumber {
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    static boolean isValid(String s) {
        return DIGITS.matcher(s).matches();
    }
}
```

**성능 비교**:

```java
// 나쁜 예: 매번 Pattern 생성
long start = System.currentTimeMillis();
for (int i = 0; i < 1_000_000; i++) {
    "123".matches("\\d+"); // 매번 Pattern.compile() 호출
}
long end = System.currentTimeMillis();
System.out.println("나쁜 예: " + (end - start) + "ms"); // 매우 느림

// 좋은 예: Pattern 재사용
Pattern pattern = Pattern.compile("\\d+");
start = System.currentTimeMillis();
for (int i = 0; i < 1_000_000; i++) {
    pattern.matcher("123").matches();
}
end = System.currentTimeMillis();
System.out.println("좋은 예: " + (end - start) + "ms"); // 매우 빠름
```

**장점**:

- ✔ 객체 1번 생성
- ✔ GC 압박 감소
- ✔ 성능 대폭 개선 (수십 배 ~ 수백 배)

---

## 2️⃣ 박싱 객체의 함정 (자주 나오는 면접 포인트)

### ❌ 불필요한 오토박싱

```java
// 나쁜 예
Long sum = 0L;
for (long i = 0; i < 1_000_000; i++) {
    sum += i; // 매번 Long 객체 생성!
}
```

**문제 분석**:

- `sum += i`는 `sum = Long.valueOf(sum.longValue() + i)`와 동일
- 매 반복마다 `Long` 객체 생성
- 백만 개 객체 → GC 폭탄 💣

**✅ 개선**:

```java
// 좋은 예
long sum = 0L; // 기본 타입 사용
for (long i = 0; i < 1_000_000; i++) {
    sum += i; // 오토박싱 없음
}
```

**📌 핵심**: 기본 타입을 쓸 수 있으면 무조건 기본 타입

### 박싱 vs 언박싱

```java
// 오토박싱 (Auto-boxing)
Integer i = 100; // int → Integer 자동 변환

// 언박싱 (Unboxing)
int j = i; // Integer → int 자동 변환

// 루프에서의 문제
Long sum = 0L; // 래퍼 타입
for (long i = 0; i < 1_000_000; i++) {
    sum += i; // 매번 Long.valueOf(sum.longValue() + i) 호출
}
```

**성능 차이**:

- 기본 타입: 직접 연산 (매우 빠름)
- 래퍼 타입: 객체 생성 + 언박싱 + 박싱 (느림)

---

## 3️⃣ Boolean, Integer 생성자 사용 ❌

### ❌ 생성자 사용

```java
// 나쁜 예
Boolean b1 = new Boolean("true"); // 매번 새 객체
Boolean b2 = new Boolean("true");
System.out.println(b1 == b2); // false - 다른 객체

Integer i1 = new Integer(100); // 매번 새 객체
Integer i2 = new Integer(100);
System.out.println(i1 == i2); // false - 다른 객체
```

### ✅ 정석

```java
// 좋은 예 1: valueOf() 사용 (캐시 재사용)
Boolean b1 = Boolean.valueOf("true");
Boolean b2 = Boolean.valueOf("true");
System.out.println(b1 == b2); // true - 같은 객체

Integer i1 = Integer.valueOf(100);
Integer i2 = Integer.valueOf(100);
System.out.println(i1 == i2); // true (캐시 범위 내)

// 좋은 예 2: 오토박싱 (내부적으로 valueOf() 호출)
Boolean b3 = true; // Boolean.valueOf(true)와 동일
Integer i3 = 100; // Integer.valueOf(100)과 동일
```

**📌 핵심**: `valueOf()` → 캐시 재사용

**래퍼 타입 캐시 범위**:

- `Boolean`: `true`, `false` (모두 캐시)
- `Byte`: -128 ~ 127 (모두 캐시)
- `Short`: -128 ~ 127
- `Integer`: -128 ~ 127
- `Long`: -128 ~ 127
- `Character`: 0 ~ 127

```java
Integer i1 = Integer.valueOf(127);
Integer i2 = Integer.valueOf(127);
System.out.println(i1 == i2); // true - 캐시 범위 내

Integer i3 = Integer.valueOf(128);
Integer i4 = Integer.valueOf(128);
System.out.println(i3 == i4); // false - 캐시 범위 밖
```

---

## 4️⃣ 불변 객체는 재사용하라

### 불변 객체의 특징

- **상태가 변하지 않음** → 안전한 공유 가능
- **Thread-safe**: 동시성 문제 없음
- **재사용 최적**: 여러 곳에서 안전하게 공유

### 대표적인 불변 객체

- `String`
- `Integer`, `Long`, `Double` 등 래퍼 타입
- `BigInteger`, `BigDecimal`
- `LocalDate`, `LocalTime`, `LocalDateTime`
- `Pattern`
- `Collections.unmodifiableXXX()`

### 예시

```java
// 좋은 예: 불변 객체 재사용
public class Constants {
    public static final BigInteger TEN = BigInteger.TEN;
    public static final BigInteger ZERO = BigInteger.ZERO;
    public static final String EMPTY_STRING = "";

    // 불변 컬렉션
    public static final List<String> EMPTY_LIST = Collections.emptyList();
    public static final Map<String, String> EMPTY_MAP = Collections.emptyMap();
}

// 사용
BigInteger result = Constants.TEN.multiply(BigInteger.valueOf(5));
```

**장점**:

- ✔ 동시성 안전
- ✔ 재사용 최적
- ✔ 메모리 절약

---

## 5️⃣ 객체 풀링은 신중하라 ❗

### ❌ 잘못된 가정

**"객체 생성은 무조건 비싸다"** → 틀림

### ❌ 잘못된 객체 풀

```java
// 나쁜 예: 경량 객체를 풀링
public class StringPool {
    private static final Queue<String> pool = new LinkedList<>();

    public static String getString() {
        String s = pool.poll();
        if (s == null) {
            s = new String();
        }
        return s;
    }

    public static void returnString(String s) {
        pool.offer(s);
    }
}
```

**문제점**:

- `String`, `Integer` 같은 경량 객체는 풀링할 필요 없음
- 풀 관리 오버헤드가 더 클 수 있음
- 코드 복잡도 증가

### ✅ 풀링이 의미 있는 경우

다음 조건을 만족할 때만 객체 풀을 고려하세요:

1. **생성 비용이 매우 비쌈**

   - DB Connection
   - Thread
   - Socket
   - 대규모 버퍼

2. **수명이 긴 객체**

   - 자주 생성/소멸되지 않음
   - 재사용 빈도가 높음

3. **제한된 리소스**
   - Connection Pool (DB 연결 제한)
   - Thread Pool (스레드 제한)

**예시**:

```java
// 좋은 예: Connection Pool (실제로는 HikariCP, DBCP 등 사용)
public class DatabaseConnectionPool {
    private final Queue<Connection> pool = new ConcurrentLinkedQueue<>();
    private final int maxSize;

    public DatabaseConnectionPool(int maxSize) {
        this.maxSize = maxSize;
        // 초기 연결 생성
        for (int i = 0; i < maxSize; i++) {
            pool.offer(createConnection());
        }
    }

    public Connection getConnection() {
        Connection conn = pool.poll();
        if (conn == null) {
            throw new RuntimeException("Connection pool exhausted");
        }
        return conn;
    }

    public void returnConnection(Connection conn) {
        if (pool.size() < maxSize) {
            pool.offer(conn);
        } else {
            // 풀이 가득 차면 연결 종료
            closeConnection(conn);
        }
    }
}
```

**📌 핵심**: 생성 비용이 비싸고, 수명이 긴 객체만 풀링

---

## 6️⃣ 생성 비용이 비싸면 지연 생성 (Supplier 연결됨)

### 지연 초기화 (Lazy Initialization)

비용이 비싼 객체를 필요할 때까지 생성하지 않는 기법입니다.

### 예시: Supplier를 활용한 지연 생성

```java
// 아이템 5와 연결: Supplier를 통한 지연 생성
class SpellChecker {
    private final Supplier<Dictionary> dictionarySupplier;
    private Dictionary dictionary; // 캐시

    SpellChecker(Supplier<Dictionary> dictionarySupplier) {
        this.dictionarySupplier = dictionarySupplier;
    }

    public boolean isValid(String word) {
        if (dictionary == null) {
            dictionary = dictionarySupplier.get(); // 필요할 때만 생성
        }
        return dictionary.contains(word);
    }
}

// 사용
SpellChecker checker = new SpellChecker(() -> {
    // 비용이 비싼 Dictionary 생성
    return new ExpensiveDictionary();
});

// dictionary는 isValid()가 처음 호출될 때만 생성됨
```

**장점**:

- ✔ 필요 없으면 생성 안 함
- ✔ 필요할 때 1번 생성
- ✔ 메모리 절약

**👉 아이템 5 + 아이템 6 연결 포인트**: DI와 지연 생성을 함께 사용

### 다른 지연 초기화 방법

```java
// 방법 1: synchronized를 사용한 지연 초기화
public class ExpensiveObject {
    private static ExpensiveObject instance;

    public static synchronized ExpensiveObject getInstance() {
        if (instance == null) {
            instance = new ExpensiveObject();
        }
        return instance;
    }
}

// 방법 2: Double-Checked Locking
public class ExpensiveObject {
    private static volatile ExpensiveObject instance;

    public static ExpensiveObject getInstance() {
        if (instance == null) {
            synchronized (ExpensiveObject.class) {
                if (instance == null) {
                    instance = new ExpensiveObject();
                }
            }
        }
        return instance;
    }
}
```

---

## 7️⃣ 성능보다 중요한 것 ⚠️

### 가독성·명확성을 해치면서까지 최적화하지 마라

```java
// 나쁨: 가독성 희생
if (x == 1 || x == 2 || x == 3 || x == 4 || x == 5) {
    // ...
}

// 차라리 명확한 코드
Set<Integer> validValues = Set.of(1, 2, 3, 4, 5);
if (validValues.contains(x)) {
    // ...
}
```

**📌 원칙**: "명확한 코드 → 병목일 때만 최적화"

### 최적화 가이드라인

1. **먼저 명확하고 읽기 쉬운 코드 작성**
2. **성능 측정 후 병목 지점 확인**
3. **병목인 경우에만 최적화**
4. **최적화 후 다시 측정하여 개선 확인**

**도널드 크누스의 명언**:

> "Premature optimization is the root of all evil"
> (조기 최적화는 모든 악의 근원이다)

---

## 🧠 한 장 요약

### ❌ 피해야 할 것

| 나쁜 예                | 문제점             | 개선                           |
| ---------------------- | ------------------ | ------------------------------ |
| `new String("hello")`  | 불필요한 객체 생성 | `"hello"` (String Pool 재사용) |
| `new Boolean("true")`  | 매번 새 객체       | `Boolean.valueOf("true")`      |
| `Long sum = 0L` (루프) | 오토박싱 반복      | `long sum = 0L`                |
| `s.matches("\\d+")`    | 매번 Pattern 생성  | 정적 `Pattern` 재사용          |
| 경량 객체 풀링         | 오버헤드만 증가    | 풀링 불필요                    |

### ✅ 권장

| 좋은 예          | 이유                     |
| ---------------- | ------------------------ |
| 정적 상수 재사용 | 불변 객체 안전 공유      |
| `valueOf()` 사용 | 캐시 재사용              |
| 기본 타입 우선   | 박싱/언박싱 비용 없음    |
| 불변 객체 공유   | Thread-safe, 재사용 최적 |
| 비싼 객체만 풀링 | 생성 비용 절약           |

---

## 📊 객체 생성 비용 비교

| 객체 타입    | 생성 비용 | 풀링 필요성           |
| ------------ | --------- | --------------------- |
| `String`     | 매우 낮음 | ❌ 불필요             |
| `Integer`    | 매우 낮음 | ❌ 불필요             |
| `Pattern`    | 높음      | ⚠️ 정적 상수로 재사용 |
| `Connection` | 매우 높음 | ⭕ 필요               |
| `Thread`     | 매우 높음 | ⭕ 필요               |
| `BigInteger` | 중간      | ❌ 불필요             |

---

## 🎯 요약

> **불필요한 객체 생성을 피하라는 것은 동일한 의미의 객체를 반복 생성하지 말고, 불변 객체나 캐시, 정적 상수를 통해 재사용하여 메모리 사용과 GC 비용을 줄이라는 의미입니다.**

---

## ✅ 7. 다 쓴 객체 참조를 해제하라 (Effective Java Item 7)

### 📌 핵심 한 문장

**GC는 "객체가 더 이상 필요 없는지"가 아니라 "참조가 남아 있는지"만 본다.**

---

## 🔴 왜 문제가 생기나?

### GC의 동작 원리

Java에서 GC 대상 조건은 단 하나입니다:

**GC Root에서 도달 가능하면 살아 있음**

즉:

- 객체를 안 쓰고 있어도
- 어딘가에 참조가 남아 있으면
- GC는 절대 수거하지 않는다

**👉 이게 바로 메모리 누수(memory leak)**

### GC Root의 종류

- 스택의 지역 변수
- 정적 변수
- JNI 참조
- 활성화된 스레드

**핵심**: GC Root에서 도달할 수 없는 객체만 GC 대상이 됩니다.

---

## 🔥 대표적인 문제 예제 (교과서급)

### ❌ 잘못된 Stack 구현

```java
public class Stack {
    private Object[] elements;
    private int size = 0;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    public Stack(int capacity) {
        elements = new Object[capacity];
    }

    public void push(Object e) {
        ensureCapacity();
        elements[size++] = e;
    }

    public Object pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        return elements[--size]; // ⚠️ 문제!
    }

    private void ensureCapacity() {
        if (elements.length == size) {
            elements = Arrays.copyOf(elements, 2 * size + 1);
        }
    }
}
```

### ❗ 뭐가 문제냐면

```java
Stack stack = new Stack(10);
stack.push(new Object());
stack.push(new Object());
Object obj = stack.pop(); // 첫 번째 객체 반환
```

**문제점**:

- 논리적으로는 스택에서 제거됨
- 하지만 배열에 참조는 그대로 남아 있음
- `elements[1]` → Object (참조 유지 ❌)

**GC 입장**:

- "어? elements 배열에서 참조 중이네? 살아있음"
- 절대 수거하지 않음
- 메모리 누수 발생 💣

**결과**:

- 스택을 오래 사용하면 메모리 누수
- OutOfMemoryError 발생 가능

---

## 🟢 올바른 코드 (이게 핵심이다)

```java
public Object pop() {
    if (size == 0) {
        throw new EmptyStackException();
    }
    Object result = elements[--size];
    elements[size] = null;   // ⭐ 다 쓴 참조 해제
    return result;
}
```

**왜 이게 중요하냐면**:

- `elements[size] = null` → GC 루트와의 연결 끊김
- 정상 수거 가능
- 메모리 누수 방지

**개선된 전체 코드**:

```java
public class Stack {
    private Object[] elements;
    private int size = 0;
    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    public Stack(int capacity) {
        elements = new Object[capacity];
    }

    public void push(Object e) {
        ensureCapacity();
        elements[size++] = e;
    }

    public Object pop() {
        if (size == 0) {
            throw new EmptyStackException();
        }
        Object result = elements[--size];
        elements[size] = null; // 다 쓴 참조 해제
        return result;
    }

    private void ensureCapacity() {
        if (elements.length == size) {
            elements = Arrays.copyOf(elements, 2 * size + 1);
        }
    }
}
```

---

## 🧠 "그럼 항상 null 처리해야 하나요?"

### ❌ 절대 아님

**✔ 특정 경우에만 필요합니다**

### ✅ 명시적으로 참조 해제가 필요한 경우

#### 1️⃣ 자기 메모리를 직접 관리하는 클래스

**예시**:

- 배열
- Map / Set
- 캐시
- 풀(pool)

```java
// 나쁜 예
public class ObjectCache {
    private Object[] cache = new Object[100];
    private int index = 0;

    public void add(Object obj) {
        cache[index++] = obj;
    }

    public Object get(int i) {
        return cache[i];
    }

    // 문제: 제거 메서드가 없음
}

// 좋은 예
public class ObjectCache {
    private Object[] cache = new Object[100];
    private int index = 0;

    public void add(Object obj) {
        cache[index++] = obj;
    }

    public Object get(int i) {
        return cache[i];
    }

    public void remove(int i) {
        cache[i] = null; // 명시적 해제
    }
}
```

**👉 이 경우 개발자가 수명 관리 책임자**

#### 2️⃣ 장기 생존 객체

**예시**:

- static 필드
- 싱글톤
- 캐시
- 리스너 등록

```java
// 나쁜 예
public class UserManager {
    private static List<User> users = new ArrayList<>();

    public void addUser(User user) {
        users.add(user);
    }

    // 문제: 제거 메서드가 없음
    // JVM 종료까지 메모리 유지 😱
}

// 좋은 예
public class UserManager {
    private static List<User> users = new ArrayList<>();

    public void addUser(User user) {
        users.add(user);
    }

    public void removeUser(User user) {
        users.remove(user);
        // 또는 명시적으로 null 처리
        user = null; // (하지만 remove()만으로도 충분)
    }
}
```

**여기서 제거 안 하면**: JVM 종료까지 메모리 유지 😱

#### 3️⃣ 리스너 / 콜백

```java
// 나쁜 예
public class EventSource {
    private List<EventListener> listeners = new ArrayList<>();

    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    // 문제: 제거 메서드가 없음
    // 리스너가 계속 쌓임
}

// 좋은 예
public class EventSource {
    private List<EventListener> listeners = new ArrayList<>();

    public void addListener(EventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(EventListener listener) {
        listeners.remove(listener);
        // 필수: 안 지우면 메모리 누수
    }
}
```

**📌 GUI / Spring / Observer 패턴에서 매우 흔함**

**Spring 예시**:

```java
@Component
public class MyComponent {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @EventListener
    public void handleEvent(MyEvent event) {
        // 이벤트 처리
    }

    // Spring이 자동으로 리스너 등록/해제 관리
    // 하지만 명시적으로 해제해야 하는 경우도 있음
}
```

---

### ❌ 굳이 안 해도 되는 경우

```java
// 지역 변수는 자동으로 해제됨
public void foo() {
    Object obj = new Object();
    // 메서드 종료 시 스택 프레임 제거
    // GC가 알아서 처리
}

// 여기서 null 넣는 건 오히려 코드 냄새
public void foo() {
    Object obj = new Object();
    // ... 사용
    obj = null; // ❌ 불필요! 오히려 가독성 해침
}
```

**이유**:

- 지역 변수는 메서드 종료 시 스택 프레임 제거
- GC가 알아서 처리
- 명시적 null 처리는 오히려 코드 냄새

---

## 🔥 실제 실무에서 자주 터지는 케이스

### ❌ 캐시

```java
// 나쁜 예
public class CacheManager {
    private Map<String, Object> cache = new HashMap<>();

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public Object get(String key) {
        return cache.get(key);
    }

    // 문제: 삭제 로직 없음
    // 점점 커짐 → OutOfMemoryError
}
```

**문제점**:

- 캐시가 계속 커짐
- 오래된 항목이 제거되지 않음
- OutOfMemoryError 발생

### ✅ 해결책

#### 방법 1: LRU (Least Recently Used) 캐시

```java
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public LRUCache(int maxSize) {
        super(16, 0.75f, true); // accessOrder = true
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize; // 크기 초과 시 가장 오래된 항목 제거
    }
}

// 사용
LRUCache<String, Object> cache = new LRUCache<>(100);
cache.put("key1", value1);
cache.put("key2", value2);
// 100개 초과 시 자동으로 오래된 항목 제거
```

#### 🔍 `super(16, 0.75f, true)` 상세 설명

**LinkedHashMap 생성자 파라미터**:

```java
public LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder)
```

1. **`initialCapacity = 16`**: 초기 용량

   - HashMap의 초기 버킷(bucket) 크기
   - 16은 기본값으로 충분한 크기

2. **`loadFactor = 0.75f`**: 로드 팩터

   - HashMap이 리사이징되기 전까지 허용되는 최대 사용률
   - 0.75 = 75% 채워지면 용량을 2배로 증가
   - 예: 16 \* 0.75 = 12개 항목이 들어가면 32로 확장

3. **`accessOrder = true`**: ⭐ **핵심 파라미터**
   - `true`: 접근 순서 유지 (LRU 동작의 핵심!)
   - `false`: 삽입 순서 유지 (기본값)

**accessOrder의 동작**:

```java
// accessOrder = false (기본값) - 삽입 순서 유지
LinkedHashMap<String, String> map1 = new LinkedHashMap<>(16, 0.75f, false);
map1.put("a", "1");
map1.put("b", "2");
map1.put("c", "3");
map1.get("a"); // 접근해도 순서 변경 안 됨
// 순서: a -> b -> c (삽입 순서)

// accessOrder = true - 접근 순서 유지 (LRU)
LinkedHashMap<String, String> map2 = new LinkedHashMap<>(16, 0.75f, true);
map2.put("a", "1");
map2.put("b", "2");
map2.put("c", "3");
map2.get("a"); // 접근하면 맨 뒤로 이동!
// 순서: b -> c -> a (가장 최근 접근한 것이 뒤로)
```

**왜 `accessOrder = true`가 필요한가?**

LRU(Least Recently Used)는 **가장 오래 전에 사용된 항목을 제거**하는 알고리즘입니다.

- `accessOrder = true`로 설정하면:
  - `get()` 또는 `put()`으로 접근한 항목이 **맨 뒤로 이동**
  - 가장 앞에 있는 항목이 **가장 오래 전에 사용된 항목**
  - `removeEldestEntry()`가 호출될 때 가장 앞 항목(eldest)을 제거

**동작 예시**:

```java
LRUCache<String, String> cache = new LRUCache<>(3);

cache.put("a", "1"); // [a]
cache.put("b", "2"); // [a, b]
cache.put("c", "3"); // [a, b, c]

cache.get("a");      // [b, c, a] - a가 맨 뒤로 이동 (최근 접근)
cache.put("d", "4"); // [c, a, d] - b가 제거됨 (가장 오래됨)
```

**전체 흐름**:

1. `super(16, 0.75f, true)` 호출

   - LinkedHashMap의 생성자 호출
   - `accessOrder = true` 설정으로 접근 순서 추적 활성화

2. 항목 접근 시 (`get()` 또는 `put()`)

   - LinkedHashMap이 내부적으로 접근된 항목을 맨 뒤로 이동
   - 가장 앞 항목이 가장 오래된 항목이 됨

3. `put()` 후 `removeEldestEntry()` 호출
   - LinkedHashMap이 자동으로 호출
   - 가장 앞 항목(eldest)이 파라미터로 전달됨
   - `true` 반환 시 해당 항목 제거

**만약 `accessOrder = false`라면?**

```java
// accessOrder = false인 경우
public LRUCache(int maxSize) {
    super(16, 0.75f, false); // 삽입 순서만 유지
    this.maxSize = maxSize;
}

// 문제: get()으로 접근해도 순서가 변경되지 않음
cache.put("a", "1");
cache.put("b", "2");
cache.put("c", "3");
cache.get("a"); // 접근했지만 순서는 [a, b, c] 그대로
// LRU가 제대로 동작하지 않음!
```

**📌 핵심 정리**:

- `super()`: 부모 클래스(LinkedHashMap)의 생성자 호출
- `accessOrder = true`: 접근 순서를 추적하여 LRU 구현 가능
- `removeEldestEntry()`: 가장 오래된 항목(맨 앞)을 제거할지 결정

#### 방법 2: WeakHashMap 사용

```java
import java.util.WeakHashMap;

// WeakReference 사용
Map<Key, Value> map = new WeakHashMap<>();

// Key 참조 끊기면 GC가 자동 제거
// 캐시용으로 자주 사용
```

**동작 원리**:

- `WeakHashMap`은 키를 WeakReference로 저장
- 키에 대한 강한 참조가 없으면 GC가 자동으로 제거
- 값은 키가 제거될 때 함께 제거됨

**예시**:

```java
Map<String, Object> cache = new WeakHashMap<>();

String key = new String("key1");
cache.put(key, new Object());

// key에 대한 강한 참조가 없어지면
key = null;

// GC 실행 시 자동으로 캐시에서 제거됨
```

#### 방법 3: Caffeine / Guava Cache 사용

```java
// Caffeine 예시
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

Cache<String, Object> cache = Caffeine.newBuilder()
    .maximumSize(10_000) // 최대 크기
    .expireAfterWrite(10, TimeUnit.MINUTES) // 만료 시간
    .build();

cache.put("key", value);
Object value = cache.getIfPresent("key");
```

**장점**:

- 자동으로 오래된 항목 제거
- 만료 시간 설정 가능
- 성능 최적화

---

## 🧪 WeakReference 예시 (고급)

### WeakReference란?

객체에 대한 약한 참조로, GC가 수거할 수 있도록 허용합니다.

```java
import java.lang.ref.WeakReference;

// 강한 참조
Object obj = new Object();

// 약한 참조
WeakReference<Object> weakRef = new WeakReference<>(obj);

// 강한 참조 제거
obj = null;

// GC 실행 시 weakRef.get()은 null 반환
Object retrieved = weakRef.get(); // null 가능
```

### WeakHashMap 예시

```java
import java.util.WeakHashMap;

public class WeakHashMapExample {
    public static void main(String[] args) {
        Map<String, String> map = new WeakHashMap<>();

        String key1 = new String("key1");
        String key2 = new String("key2");

        map.put(key1, "value1");
        map.put(key2, "value2");

        System.out.println(map.size()); // 2

        // 강한 참조 제거
        key1 = null;

        // GC 강제 실행 (실제로는 권장하지 않음)
        System.gc();

        // 잠시 대기
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(map.size()); // 1 (key1이 제거됨)
    }
}
```

**사용 사례**:

- 캐시 구현
- 리스너 관리
- 메타데이터 저장

---

## 📊 메모리 누수 vs 정상 메모리 사용

| 상황              | 메모리 누수 | 정상 사용        |
| ----------------- | ----------- | ---------------- |
| **배열에서 제거** | 참조 유지   | `null` 처리      |
| **캐시**          | 무한 증가   | 크기 제한 / 만료 |
| **리스너**        | 등록만 함   | 등록/해제 쌍     |
| **지역 변수**     | -           | 자동 해제        |

---

## 🎯 요약

> **Java는 GC가 있지만, 객체 참조가 남아 있으면 GC 대상이 되지 않기 때문에 배열, 캐시, 컬렉션처럼 메모리를 직접 관리하는 경우엔 다 쓴 객체 참조를 명시적으로 해제해야 합니다.**

---

## ✅ 9. try-with-resources를 사용하라 (Effective Java Item 9)

### 📌 핵심 한 문장

**반드시 닫아야 하는 자원은 try-with-resources로 관리하라. finally보다 안전하고, 코드도 더 간결하다.**

---

## 1️⃣ 반드시 닫아야 하는 자원이란?

다음 인터페이스를 구현한 객체들:

- **`AutoCloseable`**: Java 7에서 도입
- **`Closeable`**: `AutoCloseable`을 상속 (Java 5부터 존재)

### 대표 예시

- `InputStream` / `OutputStream`
- `Reader` / `Writer`
- `Socket`
- JDBC `Connection` / `Statement` / `ResultSet`
- `FileChannel`
- `ZipFile`

**👉 닫지 않으면 OS 자원 누수**

**인터페이스 구조**:

```java
public interface AutoCloseable {
    void close() throws Exception;
}

public interface Closeable extends AutoCloseable {
    void close() throws IOException; // 더 구체적인 예외
}
```

---

## 2️⃣ ❌ try-finally의 문제점

### 단일 자원도 위험

```java
// 나쁜 예
InputStream in = new FileInputStream("data.txt");
try {
    // 파일 읽기 작업
    int data = in.read();
} finally {
    in.close(); // 예외 발생 가능!
}
```

**문제점**:

- `close()`에서 예외 발생 시?
- 원래 예외가 덮어씌워짐
- 예외 처리 복잡

**예외 덮어쓰기 예시**:

```java
InputStream in = new FileInputStream("data.txt");
try {
    int data = in.read(); // IOException 발생
    if (data == -1) {
        throw new IOException("파일 끝");
    }
} finally {
    in.close(); // 여기서도 IOException 발생
    // 결과: close()의 예외만 보이고, read()의 예외는 사라짐!
}
```

### 🔥 다중 자원 → 지옥

```java
// 나쁜 예: 다중 자원 관리
InputStream in = new FileInputStream("a.txt");
OutputStream out = new FileOutputStream("b.txt");
try {
    // 파일 복사 작업
    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
    }
} finally {
    // 복잡한 예외 처리
    try {
        out.close();
    } catch (IOException e) {
        // 로깅
    }
    try {
        in.close();
    } catch (IOException e) {
        // 로깅
    }
}
```

**문제점**:

- 📌 가독성 ❌
- 📌 예외 처리 ❌
- 📌 실수 가능성 💥
- 📌 자원 해제 순서 주의 필요

---

## 3️⃣ ✅ try-with-resources (정답)

### 기본 사용법

```java
// 좋은 예: 단일 자원
try (InputStream in = new FileInputStream("data.txt")) {
    int data = in.read();
    // 자동으로 close() 호출
}

// 좋은 예: 다중 자원
try (InputStream in = new FileInputStream("a.txt");
     OutputStream out = new FileOutputStream("b.txt")) {
    // 파일 복사 작업
    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = in.read(buffer)) != -1) {
        out.write(buffer, 0, bytesRead);
    }
    // 자동으로 역순으로 close() 호출 (out -> in)
}
```

### 장점

- ✔ **자동 close**: 블록 종료 시 자동으로 `close()` 호출
- ✔ **선언 순서의 역순으로 close**: 나중에 선언한 것부터 닫힘
- ✔ **예외 안전**: suppressed exception으로 예외 보존
- ✔ **코드 간결**: 보일러플레이트 코드 제거

### 실제 예시

```java
// 파일 복사
public void copyFile(String source, String dest) throws IOException {
    try (InputStream in = new FileInputStream(source);
         OutputStream out = new FileOutputStream(dest)) {

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
    }
    // 자동으로 close() 호출됨
}

// JDBC 사용
public List<User> getUsers() throws SQLException {
    String sql = "SELECT * FROM users";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql);
         ResultSet rs = stmt.executeQuery()) {

        List<User> users = new ArrayList<>();
        while (rs.next()) {
            users.add(mapRow(rs));
        }
        return users;
    }
    // 자동으로 rs -> stmt -> conn 순서로 close()
}
```

---

## 4️⃣ Suppressed Exception (핵심 포인트)

### ❌ try-finally의 문제

```java
// 나쁜 예
try {
    throw new RuntimeException("main exception");
} finally {
    throw new IOException("close exception");
}
// 결과: IOException만 보임 (RuntimeException 사라짐!)
```

**문제**: `close()` 예외 → 기존 예외 덮어씀

### ✅ try-with-resources의 해결

```java
// 좋은 예
try (Resource r = new Resource()) {
    throw new RuntimeException("main exception");
}
// 결과:
// - RuntimeException("main exception") - 주 예외
// - IOException("close exception") - suppressed 예외
```

**장점**:

- 주 예외 유지
- `close()` 예외는 suppressed로 보존

### Suppressed Exception 확인 방법

```java
try (Resource r = new Resource()) {
    throw new RuntimeException("main");
} catch (RuntimeException e) {
    // 주 예외
    System.out.println("Main exception: " + e.getMessage());

    // Suppressed 예외 확인
    Throwable[] suppressed = e.getSuppressed();
    for (Throwable t : suppressed) {
        System.out.println("Suppressed: " + t.getMessage());
    }
}
```

**예시**:

```java
public class Resource implements AutoCloseable {
    @Override
    public void close() throws IOException {
        throw new IOException("close failed");
    }
}

try (Resource r = new Resource()) {
    throw new RuntimeException("work failed");
} catch (RuntimeException e) {
    System.out.println("Caught: " + e.getMessage()); // "work failed"

    Throwable[] suppressed = e.getSuppressed();
    for (Throwable t : suppressed) {
        System.out.println("Suppressed: " + t.getMessage()); // "close failed"
    }
}
```

---

## 5️⃣ 내부 동작 (컴파일 결과 개념)

### 원본 코드

```java
try (Resource r = new Resource()) {
    work();
}
```

### 컴파일 후 개념적으로 (의사 코드)

```java
Resource r = new Resource();
Throwable t = null;
try {
    work();
} catch (Throwable e) {
    t = e;
    throw e;
} finally {
    if (r != null) {
        if (t != null) {
            try {
                r.close();
            } catch (Throwable closeEx) {
                t.addSuppressed(closeEx); // suppressed로 추가
            }
        } else {
            r.close();
        }
    }
}
```

**핵심**: 이걸 사람이 쓰지 않아도 된다는 게 핵심!

### 다중 자원의 경우

```java
try (Resource1 r1 = new Resource1();
     Resource2 r2 = new Resource2()) {
    work();
}
```

**컴파일 후 개념적으로**:

```java
Resource1 r1 = new Resource1();
Throwable t = null;
try {
    Resource2 r2 = new Resource2();
    try {
        work();
    } catch (Throwable e) {
        t = e;
        throw e;
    } finally {
        if (r2 != null) {
            if (t != null) {
                try { r2.close(); }
                catch (Throwable e) { t.addSuppressed(e); }
            } else {
                r2.close();
            }
        }
    }
} catch (Throwable e) {
    t = e;
    throw e;
} finally {
    if (r1 != null) {
        if (t != null) {
            try { r1.close(); }
            catch (Throwable e) { t.addSuppressed(e); }
        } else {
            r1.close();
        }
    }
}
```

**👉 역순으로 close() 호출 보장**

---

## 6️⃣ AutoCloseable 구현 시 주의점

### 올바른 구현

```java
public class Resource implements AutoCloseable {
    private boolean closed = false;

    @Override
    public void close() throws Exception {
        if (!closed) {
            // 자원 해제 로직
            closed = true;
        }
    }
}
```

### 권장 사항

1. **`close()`는 idempotent (멱등성)**

   - 여러 번 호출해도 안전하게
   - 이미 닫힌 자원에 대해 예외 던지지 않음

2. **예외 최소화**

   - 가능하면 예외를 던지지 않음
   - 예외가 발생해도 로깅 후 무시

3. **상태 확인**
   - 이미 닫혔는지 확인 후 처리

**예시**:

```java
public class DatabaseConnection implements AutoCloseable {
    private Connection conn;
    private boolean closed = false;

    public DatabaseConnection(String url) throws SQLException {
        this.conn = DriverManager.getConnection(url);
    }

    @Override
    public void close() throws SQLException {
        if (!closed && conn != null) {
            conn.close();
            closed = true;
        }
        // 이미 닫혔으면 아무것도 하지 않음 (idempotent)
    }
}
```

---

## 7️⃣ 언제 써야 하나?

| 상황              | try-with-resources |
| ----------------- | ------------------ |
| **파일**          | ✅                 |
| **DB 커넥션**     | ✅                 |
| **소켓**          | ✅                 |
| **스트림**        | ✅                 |
| **네이티브 자원** | ✅                 |

**👉 판단 기준**: "닫아야 하는가?" → Yes면 무조건 사용

### 사용 예시

```java
// 파일 읽기
try (BufferedReader reader = Files.newBufferedReader(path)) {
    reader.lines().forEach(System.out::println);
}

// 파일 쓰기
try (BufferedWriter writer = Files.newBufferedWriter(path)) {
    writer.write("Hello, World!");
}

// 소켓
try (Socket socket = new Socket("localhost", 8080);
     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
     BufferedReader in = new BufferedReader(
         new InputStreamReader(socket.getInputStream()))) {
    // 통신 작업
}

// ZIP 파일
try (ZipFile zipFile = new ZipFile("archive.zip")) {
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    // 작업
}
```

---

## 8️⃣ Spring에서는?

### Spring이 관리해주는 경우

```java
@Service
public class UserService {
    @Autowired
    private DataSource dataSource; // Spring이 관리

    @Transactional
    public void saveUser(User user) {
        // Spring이 트랜잭션 관리
        // 개발자가 직접 close() ❌
    }
}
```

**Spring이 관리하는 자원**:

- `DataSource`
- `TransactionManager`
- `EntityManager` (JPA)

**👉 개발자가 직접 `close()` ❌**

### 하지만 여전히 필요한 경우

```java
@Service
public class FileService {
    public void processFile(String path) throws IOException {
        // 직접 만든 Stream은 여전히 try-with-resources 필요
        try (InputStream in = new FileInputStream(path);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(in))) {
            // 파일 처리
        }
    }

    public void sendData(String host, int port) throws IOException {
        // 직접 연 소켓도 try-with-resources 필요
        try (Socket socket = new Socket(host, port);
             OutputStream out = socket.getOutputStream()) {
            // 데이터 전송
        }
    }
}
```

**👉 직접 만든 자원은 여전히 try-with-resources 필수**

---

## 📊 try-finally vs try-with-resources 비교

| 항목            | try-finally | try-with-resources |
| --------------- | ----------- | ------------------ |
| **코드 간결성** | ❌ 복잡     | ⭕ 간결            |
| **예외 보존**   | ❌ 덮어씀   | ⭕ Suppressed      |
| **다중 자원**   | ❌ 복잡     | ⭕ 간단            |
| **자동 close**  | ❌ 수동     | ⭕ 자동            |
| **역순 close**  | ❌ 수동     | ⭕ 자동            |

---

## 🎯 요약

> **try-finally는 다중 자원과 예외 처리에 취약하지만, try-with-resources는 역순 close와 예외 보존을 보장해 실무에서 가장 안전한 자원 관리 방법입니다.**

---

## 🧠 핵심 요약

- **GC는 메모리만 관리**: OS 자원은 명시적 해제 필요
- **finalizer / cleaner ❌**: 사용하지 말 것
- **try-with-resources ✅**: 항상 사용할 것

### 🔍 OS 자원이란?

**OS 자원(Operating System Resources)**은 운영체제가 관리하는 시스템 레벨의 제한된 자원입니다.

#### 왜 GC가 관리하지 못하나?

**GC의 역할**:

- JVM 힙 메모리 내의 Java 객체만 관리
- 객체의 메모리 할당/해제만 담당

**OS 자원의 특성**:

- JVM 밖의 운영체제 레벨 자원
- 파일 디스크립터, 네트워크 소켓, 프로세스 등
- GC가 접근할 수 없음

#### 대표적인 OS 자원

| OS 자원             | 설명                              | Java 객체                             |
| ------------------- | --------------------------------- | ------------------------------------- |
| **파일 디스크립터** | 열린 파일에 대한 OS 레벨 참조     | `FileInputStream`, `FileOutputStream` |
| **소켓 디스크립터** | 네트워크 연결에 대한 OS 레벨 참조 | `Socket`, `ServerSocket`              |
| **프로세스**        | 외부 프로세스 실행                | `Process`                             |
| **메모리 맵 파일**  | OS가 관리하는 메모리 매핑         | `FileChannel`                         |
| **네이티브 메모리** | JVM 힙 밖의 메모리                | `DirectByteBuffer`                    |
| **DB 연결**         | 데이터베이스 서버 연결            | `Connection`                          |

#### 왜 명시적 해제가 필요한가?

**1. 제한된 자원**

```java
// 파일 디스크립터는 제한적 (보통 프로세스당 수천 개)
// 닫지 않으면 디스크립터 고갈
for (int i = 0; i < 10000; i++) {
    FileInputStream in = new FileInputStream("file.txt");
    // close() 없으면 디스크립터 누수
    // 10000번 반복 시 "Too many open files" 에러 발생
}
```

**2. OS 레벨 자원**

```java
// Java 객체는 GC가 수거하지만
FileInputStream in = new FileInputStream("file.txt");
in = null; // Java 객체는 GC 대상

// 하지만 OS의 파일 디스크립터는 여전히 열려있음!
// GC는 OS 자원을 해제할 수 없음
```

**3. 자원 누수의 심각성**

- **파일 디스크립터**: "Too many open files" 에러
- **소켓**: 포트 고갈, 연결 제한 초과
- **DB 연결**: Connection Pool 고갈, 서버 부하
- **메모리**: OutOfMemoryError (네이티브 메모리)

#### GC vs OS 자원 해제

```java
// 메모리 (GC가 관리)
Object obj = new Object();
obj = null;
// GC가 나중에 메모리 해제 ✅

// OS 자원 (명시적 해제 필요)
FileInputStream in = new FileInputStream("file.txt");
in = null;
// GC는 Java 객체만 수거
// OS의 파일 디스크립터는 여전히 열려있음 ❌
// 명시적으로 close() 필요!
```

#### 실제 예시

**파일 디스크립터 누수**:

```java
// 나쁜 예: 파일 디스크립터 누수
public void processFiles(List<String> files) {
    for (String file : files) {
        FileInputStream in = new FileInputStream(file);
        // 작업
        // close() 없음 → 디스크립터 누수
    }
    // 수천 개 파일 처리 시 "Too many open files" 에러
}

// 좋은 예: try-with-resources로 자동 해제
public void processFiles(List<String> files) throws IOException {
    for (String file : files) {
        try (FileInputStream in = new FileInputStream(file)) {
            // 작업
        } // 자동으로 close() 호출 → 디스크립터 해제
    }
}
```

**소켓 누수**:

```java
// 나쁜 예: 소켓 누수
public void connect(String host, int port) throws IOException {
    Socket socket = new Socket(host, port);
    // 작업
    // close() 없음 → 소켓 디스크립터 누수
    // 포트 고갈 가능
}

// 좋은 예: try-with-resources로 자동 해제
public void connect(String host, int port) throws IOException {
    try (Socket socket = new Socket(host, port)) {
        // 작업
    } // 자동으로 close() 호출 → 소켓 디스크립터 해제
}
```

**DB 연결 누수**:

```java
// 나쁜 예: DB 연결 누수
public List<User> getUsers() throws SQLException {
    Connection conn = dataSource.getConnection();
    PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users");
    ResultSet rs = stmt.executeQuery();
    // 작업
    // close() 없음 → 연결 누수
    // Connection Pool 고갈 가능
}

// 좋은 예: try-with-resources로 자동 해제
public List<User> getUsers() throws SQLException {
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users");
         ResultSet rs = stmt.executeQuery()) {
        // 작업
    } // 자동으로 close() 호출 → 연결 해제
}
```

#### 📌 핵심 정리

- **GC**: JVM 힙 메모리만 관리 (Java 객체)
- **OS 자원**: 운영체제가 관리하는 시스템 자원
- **해결책**: `close()` 메서드로 명시적 해제 (try-with-resources 사용)
- **결과**: 자원 누수 방지, 시스템 안정성 확보

**원칙**:

1. 닫아야 하는 자원은 무조건 try-with-resources
2. `AutoCloseable` 구현 시 idempotent하게
3. Spring이 관리하는 자원도 직접 만든 자원은 try-with-resources

---
