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
