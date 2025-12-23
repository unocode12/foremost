# Effective Java

## ✅ 1. 생성자 대신 정적 팩터리 메서드를 고려하라 (Effective Java Item 1)

### 📌 핵심 정의

객체 생성을 생성자(`new`)가 아니라 의미 있는 이름을 가진 `static` 메서드로 제공하라는 원칙입니다.

---

## 🌟 장점 (매우 중요)

### 1️⃣ 이름을 가질 수 있다 (가독성 ↑)

```java
// 생성자 사용 - 의미 불명
new BigInteger(10, 100, random);

// 정적 팩터리 메서드 사용 - 의미 명확
BigInteger.probablePrime(100, random);
```

- ✔ "무엇을 만드는지" 바로 드러남
- ✔ 코드 가독성 향상
- ✔ 같은 시그니처의 생성자를 여러 개 만들 수 없지만, 정적 팩터리는 이름으로 구분 가능

### 2️⃣ 호출할 때마다 새로운 객체를 만들 필요가 없다

```java
public static Boolean valueOf(boolean b) {
    return b ? Boolean.TRUE : Boolean.FALSE;
}
```

- ✔ 캐싱 가능
- ✔ 불변 객체와 궁합 최고
- ✔ 성능 + 메모리 절약
- ✔ 인스턴스 통제(instance-controlled) 클래스 구현 가능

**예시**: `Boolean.valueOf()`, `Integer.valueOf()` 등은 자주 사용되는 값들을 캐싱하여 반환합니다.

#### 🔍 인스턴스 통제(Instance-Controlled) 클래스란?

**인스턴스 통제 클래스**는 클래스가 자신의 인스턴스 생성과 생명주기를 직접 제어하는 클래스입니다.

**핵심 개념**:

- 클래스가 **언제**, **어떻게**, **몇 개의** 인스턴스를 생성할지 결정
- 외부에서 `new` 연산자로 직접 생성 불가 (생성자 `private`)
- 정적 팩터리 메서드를 통해서만 인스턴스 생성 가능

**대표적인 예시**:

1. **싱글톤 (Singleton)**: 인스턴스를 하나만 생성

```java
public class Singleton {
    private static final Singleton INSTANCE = new Singleton();

    private Singleton() {} // 외부 생성 불가

    public static Singleton getInstance() {
        return INSTANCE; // 항상 같은 인스턴스 반환
    }
}
```

2. **불변 값 클래스**: 같은 값이면 같은 인스턴스 반환 (캐싱)

```java
public class Color {
    private static final Map<String, Color> CACHE = new HashMap<>();
    private final String name;

    private Color(String name) {
        this.name = name;
    }

    public static Color of(String name) {
        // 같은 이름이면 캐시에서 반환, 없으면 새로 생성 후 캐싱
        return CACHE.computeIfAbsent(name, Color::new);
    }
}
```

3. **열거형 (Enum)**: 미리 정의된 상수들만 존재

```java
public enum Planet {
    MERCURY, VENUS, EARTH; // 컴파일 타임에 인스턴스 생성됨

    // 외부에서 new Planet() 불가능
}
```

**인스턴스 통제의 장점**:

- ✔ **메모리 절약**: 같은 인스턴스를 재사용
- ✔ **객체 동일성 보장**: `==` 비교 가능 (`equals()` 대신)
- ✔ **불변성 보장**: 인스턴스 생성 후 변경 불가
- ✔ **싱글톤 패턴 구현**: 전역에서 하나의 인스턴스만 존재

**실제 Java API 예시**:

- `Boolean.valueOf()`: `Boolean.TRUE` 또는 `Boolean.FALSE`만 반환
- `Integer.valueOf(int)`: -128 ~ 127 범위는 캐싱된 인스턴스 반환
- `String.intern()`: 문자열 풀에서 같은 문자열이면 같은 인스턴스 반환

### 3️⃣ 반환 타입의 하위 타입 객체를 반환할 수 있다

```java
// ArrayList 반환
public static List<String> of() {
    return new ArrayList<>();
}

// LinkedList 반환
public static List<String> of() {
    return new LinkedList<>();
}
```

- ✔ 구현 숨김
- ✔ OCP(Open-Closed Principle) 만족
- ✔ API 유연성 ↑
- ✔ 인터페이스 기반 프레임워크의 핵심

**예시**: `Collections` 클래스의 `unmodifiableList()`, `synchronizedList()` 등은 인터페이스 타입을 반환하지만 실제로는 다른 구현체를 반환합니다.

### 4️⃣ 입력값에 따라 다른 객체를 반환할 수 있다

```java
public static <E extends Enum<E>> EnumSet<E> of(E e) {
    return isSmall(e) ? new RegularEnumSet<>() : new JumboEnumSet<>();
}
```

- ✔ 조건별 최적 구현 선택 가능
- ✔ 런타임에 최적의 구현체 선택
- ✔ 성능 최적화 가능

**예시**: `EnumSet`은 원소 개수에 따라 `RegularEnumSet` 또는 `JumboEnumSet`을 반환합니다.

### 5️⃣ 객체 생성 시점에 클래스가 없어도 된다 (Service Provider)

```java
DriverManager.getConnection(...)
```

- ✔ SPI(Service Provider Interface) 패턴 기반
- ✔ 플러그인 구조 가능
- ✔ 런타임에 구현체 로딩 가능

**예시**: JDBC의 `DriverManager.getConnection()`은 런타임에 드라이버를 로드합니다.

---

## ⚠️ 단점 (이것도 반드시 알아야 함)

### 1️⃣ 상속이 어렵다

```java
private Constructor(); // 정적 팩터리 사용 시 흔함
```

- ✔ 상속 막힘
- ✔ 프레임워크(JPA 등)에선 주의 필요
- ✔ 생성자가 `private`이면 상속 불가

**해결책**: 컴포지션(Composition) 사용 권장

#### 🔍 상속 vs 컴포지션

**상속(Inheritance)의 문제점**:

- 정적 팩터리 메서드를 사용하는 클래스는 보통 생성자가 `private`
- `private` 생성자는 상속 불가능
- "is-a" 관계가 아닌 경우 상속은 부적절

**컴포지션(Composition)이란?**:

- 클래스를 확장하는 대신, **필드로 다른 클래스의 인스턴스를 참조**하는 방식
- "has-a" 관계
- 상속보다 유연하고 안전함

**예시 코드**:

```java
// 정적 팩터리를 사용하는 불변 클래스
public class User {
    private final String name;
    private final String email;

    private User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public static User of(String name, String email) {
        return new User(name, email);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}

// ❌ 상속 불가능 (생성자가 private)
// public class AdminUser extends User { ... } // 컴파일 에러!

// ✅ 컴포지션 사용 - User를 필드로 가짐
public class AdminUser {
    private final User user;  // User 인스턴스를 필드로 가짐
    private final String role;

    private AdminUser(User user, String role) {
        this.user = user;
        this.role = role;
    }

    public static AdminUser of(User user, String role) {
        return new AdminUser(user, role);
    }

    // User의 메서드를 위임(Delegation)하여 제공
    public String getName() {
        return user.getName();  // User의 기능 재사용
    }

    public String getEmail() {
        return user.getEmail();
    }

    public String getRole() {
        return role;
    }
}

// 사용 예시
User user = User.of("홍길동", "hong@example.com");
AdminUser admin = AdminUser.of(user, "ADMIN");
```

**컴포지션의 장점**:

- ✔ **유연성**: 런타임에 다른 구현체로 교체 가능
- ✔ **캡슐화**: 내부 구현을 숨길 수 있음
- ✔ **다중 상속 효과**: 여러 클래스를 조합 가능
- ✔ **테스트 용이**: Mock 객체 주입 쉬움

**Effective Java 원칙**: "상속보다는 컴포지션을 사용하라" (아이템 18)

### 2️⃣ API에서 눈에 잘 띄지 않는다

```java
new User()   // 직관적 - 생성자는 API 문서에서 명확히 보임
User.of()   // 문서/관례 필요 - 정적 팩터리는 찾기 어려울 수 있음
```

- ➡️ 명명 규칙이 중요
- ➡️ API 문서화 필요
- ➡️ 개발자가 찾기 어려울 수 있음

**해결책**: 명확한 명명 규칙 사용 및 API 문서화

---

## 🏷️ 자주 쓰는 정적 팩터리 메서드 이름 (암기!)

| 이름            | 의미                     | 예시                      |
| --------------- | ------------------------ | ------------------------- |
| `of()`          | 매개변수로 인스턴스 생성 | `List.of(1, 2, 3)`        |
| `from()`        | 다른 타입 → 변환         | `Date.from(instant)`      |
| `valueOf()`     | `of`보다 자세한 의미     | `Integer.valueOf(10)`     |
| `getInstance()` | 같은 인스턴스일 수도     | `Calendar.getInstance()`  |
| `newInstance()` | 매번 새 인스턴스         | `Array.newInstance(...)`  |
| `create()`      | 인스턴스 생성            | `Files.createFile(...)`   |
| `getType()`     | 특정 타입 반환           | `Files.getFileStore(...)` |
| `type()`        | 축약형                   | `Collections.emptyList()` |

---

## 💡 생성자 vs 정적 팩터리 비교표

| 항목       | 생성자 | 정적 팩터리 |
| ---------- | ------ | ----------- |
| 이름       | ❌     | ⭕          |
| 캐싱       | ❌     | ⭕          |
| 반환 타입  | 고정   | 유연        |
| 상속       | ⭕     | ❌          |
| 가독성     | 보통   | 좋음        |
| API 가시성 | 높음   | 낮음        |

---

## ✅ 2. 생성자에 매개변수가 많다면 빌더를 고려하라 (Effective Java Item 2)

### 📌 핵심 정의

선택적 매개변수가 많을 경우 점층적 생성자(telescoping constructor)나 JavaBeans 대신 **Builder 패턴**을 사용하라.

---

## ❌ 생성자 방식의 문제점

### 1️⃣ 점층적 생성자 패턴 (안 좋은 예)

```java
public class User {
    private final String name;
    private final int age;
    private final String email;
    private final String address;

    public User(String name) {
        this(name, 0);
    }

    public User(String name, int age) {
        this(name, age, null);
    }

    public User(String name, int age, String email) {
        this(name, age, email, null);
    }

    public User(String name, int age, String email, String address) {
        this.name = name;
        this.age = age;
        this.email = email;
        this.address = address;
    }
}
```

**문제점**:

- ❌ 가독성 최악: 매개변수 의미 파악 어려움
- ❌ 실수 유발: 순서 바뀌면 다른 의미의 객체 생성
- ❌ 확장 지옥: 매개변수 추가 시 생성자 폭발

**사용 예시**:

```java
// 어떤 매개변수가 무엇을 의미하는지 불명확
User user = new User("kim", 20, null, "seoul"); // email이 null인지 확인 어려움
```

### 2️⃣ JavaBeans 패턴 (또 다른 문제)

```java
User user = new User();
user.setName("kim");
user.setAge(20);
user.setEmail("kim@test.com");
user.setAddress("seoul");
```

**문제점**:

- ❌ **불변 객체 불가능**: setter로 언제든 변경 가능
- ❌ **객체 일관성 깨짐**: 중간 상태 존재 (일부만 설정된 상태)
- ❌ **Thread-safe 아님**: 여러 스레드에서 동시에 setter 호출 시 문제
- ❌ **생성자에서 검증 불가**: 객체 완성 전에 사용 가능

---

## ✅ Builder 패턴 해결 방식

### 💡 핵심 아이디어

- **필수 값**: Builder 생성자에 전달
- **선택 값**: 체이닝 메서드로 설정
- **마지막**: `build()` 호출로 객체 생성

### 💡 Builder 예제

```java
public class User {
    private final String name;
    private final int age;
    private final String email;
    private final String address;

    private User(Builder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.email = builder.email;
        this.address = builder.address;
    }

    public static class Builder {
        // 필수 매개변수
        private final String name;

        // 선택 매개변수 - 기본값으로 초기화
        private int age = 0;
        private String email = null;
        private String address = null;

        public Builder(String name) {
            this.name = name;
        }

        public Builder age(int val) {
            age = val;
            return this; // 체이닝을 위해 자기 자신 반환
        }

        public Builder email(String val) {
            email = val;
            return this;
        }

        public Builder address(String val) {
            address = val;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }
}
```

### 사용법

```java
// 가독성 좋고 명확함
User user = new User.Builder("kim")
        .age(20)
        .email("kim@test.com")
        .address("seoul")
        .build();

// 선택적 매개변수는 생략 가능
User user2 = new User.Builder("lee")
        .age(25)
        .build();
```

**장점**:

- ✔ 읽기 쉬움: 각 매개변수의 의미가 명확
- ✔ 불변 객체: 모든 필드가 `final`
- ✔ 순서 자유: 매개변수 순서에 상관없이 설정 가능
- ✔ 실수 감소: 명시적으로 각 값 설정

---

## 🧠 Builder의 핵심 장점

### 1️⃣ 가독성 & 명확성

```java
// ❌ 점층적 생성자 - 의미 불명확
new User("kim", 20, null, "seoul"); // email이 null인지 확인 어려움

// ✅ Builder - 의미 명확
User.builder()
    .name("kim")
    .age(20)
    .address("seoul")  // email은 설정하지 않음이 명확
    .build();
```

### 2️⃣ 불변 객체 생성 가능

```java
public class User {
    private final String name;      // final - 불변
    private final int age;          // final - 불변
    private final String email;     // final - 불변
    private final String address;   // final - 불변

    // setter 없음 - 불변 보장
}
```

- ✔ 모든 필드 `final`
- ✔ Thread-safe
- ✔ 객체 안정성 ↑

### 3️⃣ 검증 로직을 한 곳에서 처리

```java
public User build() {
    // 빌드 시점에 검증
    if (age < 0) {
        throw new IllegalStateException("나이는 0 이상이어야 합니다.");
    }
    if (name == null || name.isEmpty()) {
        throw new IllegalStateException("이름은 필수입니다.");
    }
    return new User(this);
}
```

- ✔ 객체 생성 전 모든 검증 완료
- ✔ 일관성 있는 객체만 생성
- ✔ 검증 로직 중앙화

### 4️⃣ 계층 구조에도 잘 어울림 (Abstract Builder 패턴)

```java
// 추상 클래스
public abstract class Pizza {
    public enum Topping { HAM, MUSHROOM, ONION, PEPPER, SAUSAGE }
    final Set<Topping> toppings;

    abstract static class Builder<T extends Builder<T>> {
        EnumSet<Topping> toppings = EnumSet.noneOf(Topping.class);

        public T addTopping(Topping topping) {
            toppings.add(Objects.requireNonNull(topping));
            return self();
        }

        abstract Pizza build();

        // 하위 클래스는 이 메서드를 오버라이드하여 this를 반환
        protected abstract T self();
    }

    Pizza(Builder<?> builder) {
        toppings = builder.toppings.clone();
    }
}

// 구체 클래스
public class NyPizza extends Pizza {
    public enum Size { SMALL, MEDIUM, LARGE }
    private final Size size;

    public static class Builder extends Pizza.Builder<Builder> {
        private final Size size;

        public Builder(Size size) {
            this.size = Objects.requireNonNull(size);
        }

        @Override
        public NyPizza build() {
            return new NyPizza(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    private NyPizza(Builder builder) {
        super(builder);
        size = builder.size;
    }
}

// 사용
NyPizza pizza = new NyPizza.Builder(SMALL)
        .addTopping(SAUSAGE)
        .addTopping(ONION)
        .build();
```

---

## ⚠️ 단점

| 단점                 | 설명                                    |
| -------------------- | --------------------------------------- |
| **코드 양 증가**     | Builder 클래스 필요 (보통 1.5배 정도)   |
| **단순 객체엔 과함** | 필드 1~2개면 오버엔지니어링             |
| **성능**             | 미세한 객체 1개 추가 (대부분 무시 가능) |

**하지만 대부분 무시 가능한 수준**입니다.

---

## 🧠 언제 Builder를 써야 할까?

다음 조건을 만족할 때 Builder 패턴을 고려하세요:

- ✔ **매개변수 4개 이상**
- ✔ **선택적 파라미터 존재**
- ✔ **불변 객체 필요**
- ✔ **객체 생성 시 검증 로직 필요**
- ✔ **향후 확장 가능성**

### 단순한 경우는 생성자나 정적 팩터리 사용

```java
// ❌ Builder 불필요 (과함)
public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {  // 생성자로 충분
        this.x = x;
        this.y = y;
    }
}

// ✅ Builder 적절
public class HttpRequest {
    private final String url;
    private final String method;
    private final Map<String, String> headers;
    private final String body;
    private final int timeout;
    private final boolean followRedirects;
    // ... 10개 이상의 선택적 매개변수
}
```

---

## ✅ 3. private 생성자나 열거 타입으로 싱글턴임을 보증하라 (Effective Java Item 3)

### 📌 핵심 정의

클래스의 인스턴스가 오직 하나만 생성됨을 보장하려면 `private` 생성자를 사용하거나, 더 나은 방법으로 **enum**을 사용하라.

---

### 🎯 싱글톤이란?

- **JVM 내에서 단 하나의 인스턴스만 존재**
- **전역 접근 가능**
- **상태를 가지면 매우 위험** → 보통 무상태(stateless) 객체

**사용 사례**:

- 설정 관리 클래스
- 로깅 클래스
- 캐시 관리자
- 데이터베이스 연결 풀

---

## 🧠 방법 1️⃣ public static final 필드 방식

```java
public class Settings {
    public static final Settings INSTANCE = new Settings();

    private Settings() {
        // 리플렉션 공격 방지
        if (INSTANCE != null) {
            throw new IllegalStateException("이미 인스턴스가 존재합니다.");
        }
    }
}
```

### 장점

- ✔ 구현 간단
- ✔ JVM 로딩 시 한 번만 생성 (Thread-safe)
- ✔ 명확한 싱글톤임을 API에서 바로 확인 가능

### 단점

- ❌ 리플렉션으로 생성자 접근 가능
- ❌ 직렬화 시 깨질 수 있음 (`readResolve()` 필요)

**사용 예시**:

```java
Settings settings = Settings.INSTANCE;
```

---

## 🧠 방법 2️⃣ 정적 팩터리 메서드 방식

```java
public class Settings {
    private static final Settings INSTANCE = new Settings();

    private Settings() {
        // 리플렉션 공격 방지
        if (INSTANCE != null) {
            throw new IllegalStateException("이미 인스턴스가 존재합니다.");
        }
    }

    public static Settings getInstance() {
        return INSTANCE;
    }

    // 직렬화 시 싱글톤 보장
    private Object readResolve() {
        return INSTANCE;
    }
}
```

### 장점

- ✔ API 유연성: 나중에 싱글톤이 아닌 방식으로 변경 가능
- ✔ 필요 시 싱글톤 → 멀티톤 변경 가능
- ✔ 제네릭 싱글톤 팩터리로 활용 가능

**제네릭 싱글톤 팩터리 예시**:

```java
public class SingletonFactory {
    private static final Map EMPTY_MAP = new HashMap<>();

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> emptyMap() {
        return (Map<K, V>) EMPTY_MAP;
    }
}
```

### 단점

- ❌ 역시 리플렉션 / 직렬화 취약
- ❌ 첫 번째 방법보다 약간 덜 명확함

---

## 🔥 방법 3️⃣ 열거 타입(enum) 방식 (가장 권장 ⭐)

```java
public enum Settings {
    INSTANCE;

    public void doSomething() {
        // 싱글톤 로직
    }
}
```

### 왜 최고인가?

| 항목              | enum         | 다른 방법               |
| ----------------- | ------------ | ----------------------- |
| **Thread-safe**   | ⭕ JVM 보장  | ⚠️ 주의 필요            |
| **직렬화 안전**   | ⭕ 자동      | ❌ `readResolve()` 필요 |
| **리플렉션 방어** | ⭕ 완벽      | ❌ 취약                 |
| **코드 간결성**   | ⭕ 매우 간단 | ⚠️ 복잡                 |

➡️ **Joshua Bloch가 가장 추천하는 방법**

**사용 예시**:

```java
Settings settings = Settings.INSTANCE;
settings.doSomething();
```

---

## ⚠️ 왜 private 생성자만으로는 부족한가?

### ❌ 리플렉션 공격

```java
// private 생성자 우회 가능
Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
constructor.setAccessible(true);
Settings s2 = constructor.newInstance();

System.out.println(Settings.INSTANCE == s2); // false - 다른 인스턴스!
```

➡️ **두 번째 인스턴스 생성 가능**

**방어 코드**:

```java
private Settings() {
    if (INSTANCE != null) {
        throw new IllegalStateException("이미 인스턴스가 존재합니다.");
    }
}
```

하지만 이 방법도 완벽하지 않습니다 (멀티스레드 환경에서 경쟁 조건 발생 가능).

### ❌ 직렬화 공격

```java
// 직렬화
try (ObjectOutput out = new ObjectOutputStream(new FileOutputStream("settings.obj"))) {
    out.writeObject(Settings.INSTANCE);
}

// 역직렬화
Settings s2 = null;
try (ObjectInput in = new ObjectInputStream(new FileInputStream("settings.obj"))) {
    s2 = (Settings) in.readObject();
}

System.out.println(Settings.INSTANCE == s2); // false - 다른 인스턴스!
```

➡️ **다른 인스턴스 생성**

**해결책**: `readResolve()` 메서드 추가

```java
private Object readResolve() {
    return INSTANCE; // 항상 같은 인스턴스 반환
}
```

---

## ✅ enum이 이 모든 문제를 해결하는 이유

### 1. JVM이 enum 생성자 호출을 강제 제어

- enum 상수는 JVM이 직접 생성
- 외부에서 생성자 호출 불가능

### 2. 리플렉션으로 생성자 접근 불가

```java
// enum의 경우 리플렉션으로도 생성 불가
Constructor<Settings> constructor = Settings.class.getDeclaredConstructor();
// IllegalArgumentException 발생!
```

### 3. 직렬화 시 동일 enum 상수 반환

- Java의 직렬화는 enum을 특별히 처리
- 항상 같은 enum 상수 반환 보장
- `readResolve()` 불필요

### 4. Thread-safe 보장

- enum 상수는 JVM 로딩 시점에 한 번만 생성
- 멀티스레드 환경에서도 안전

---

## 🧠 실무 예시

### enum 싱글톤 예시

```java
public enum DatabaseConnection {
    INSTANCE;

    private Connection connection;

    DatabaseConnection() {
        // 초기화 로직
        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void executeQuery(String sql) {
        // 쿼리 실행 로직
    }
}

// 사용
DatabaseConnection.INSTANCE.executeQuery("SELECT * FROM users");
```

### 상태를 가진 싱글톤 (주의 필요)

```java
public enum Counter {
    INSTANCE;

    private int count = 0; // 상태 - 위험할 수 있음!

    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
```

**주의**: 상태를 가지는 싱글톤은 멀티스레드 환경에서 동기화 필요할 수 있습니다.

---

## 🧠 언제 enum을 쓰면 안 되나?

| 상황                | 이유                       | 대안                             |
| ------------------- | -------------------------- | -------------------------------- |
| **상속 필요**       | enum 상속 불가             | 다른 싱글톤 방식 사용            |
| **프레임워크 요구** | JPA, Jackson 등 일부 제한  | `@Singleton` 어노테이션 사용     |
| **지연 초기화**     | enum은 eager (즉시 초기화) | 정적 팩터리 메서드 + 지연 초기화 |

### 지연 초기화가 필요한 경우

```java
public class LazySingleton {
    private static volatile LazySingleton INSTANCE;

    private LazySingleton() {}

    public static LazySingleton getInstance() {
        if (INSTANCE == null) {
            synchronized (LazySingleton.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LazySingleton();
                }
            }
        }
        return INSTANCE;
    }
}
```

**주의**: 지연 초기화는 복잡하고, 대부분의 경우 불필요합니다. enum의 즉시 초기화가 더 안전하고 간단합니다.

---

## 📊 싱글톤 구현 방법 비교표

| 방법                    | Thread-safe | 리플렉션 방어 | 직렬화 안전 | 코드 간결성 | 권장도 |
| ----------------------- | ----------- | ------------- | ----------- | ----------- | ------ |
| **public static final** | ⭕          | ❌            | ❌          | ⭕          | ⭐⭐   |
| **정적 팩터리**         | ⭕          | ❌            | ⚠️          | ⭕          | ⭐⭐   |
| **enum**                | ⭕          | ⭕            | ⭕          | ⭕⭕        | ⭐⭐⭐ |

---

## 🛠️ enum으로 유틸리티 클래스 만들기

유틸리티 클래스는 인스턴스화가 필요 없는 정적 메서드만 가진 클래스입니다. enum을 사용하면 인스턴스화를 완벽하게 막을 수 있습니다.

### ❌ 전통적인 유틸리티 클래스 (문제점)

```java
public class StringUtils {
    // private 생성자로 인스턴스화 방지 시도
    private StringUtils() {
        throw new AssertionError("인스턴스화 불가");
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String reverse(String str) {
        if (str == null) return null;
        return new StringBuilder(str).reverse().toString();
    }
}
```

**문제점**:

- ❌ 리플렉션으로 생성자 접근 가능
- ❌ 상속 가능 (생성자가 `private`이어도 상속은 가능)
- ❌ 실수로 인스턴스 생성 가능

### ✅ enum으로 유틸리티 클래스 만들기

```java
public enum StringUtils {
    INSTANCE; // enum 상수 (실제로는 사용하지 않음)

    // 정적 메서드만 제공
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String reverse(String str) {
        if (str == null) return null;
        return new StringBuilder(str).reverse().toString();
    }

    public static String capitalize(String str) {
        if (isEmpty(str)) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
```

**사용법**:

```java
// 정적 메서드 호출 (enum 상수는 사용하지 않음)
if (StringUtils.isEmpty("")) {
    System.out.println("빈 문자열");
}

String reversed = StringUtils.reverse("hello");
String capitalized = StringUtils.capitalize("java");
```

### 💡 더 나은 방법: enum 상수 없이 사용

enum 상수를 실제로 사용하지 않는다면, 다음과 같이 명시적으로 표시할 수 있습니다:

```java
public enum MathUtils {
    ; // 빈 enum 상수 선언 (세미콜론 필수)

    public static int add(int a, int b) {
        return a + b;
    }

    public static int multiply(int a, int b) {
        return a * b;
    }

    public static double sqrt(double value) {
        return Math.sqrt(value);
    }
}
```

**사용법**:

```java
int sum = MathUtils.add(5, 3);
int product = MathUtils.multiply(4, 7);
double root = MathUtils.sqrt(16.0);
```

### 🎯 enum 유틸리티 클래스의 장점

| 항목                | enum 유틸리티 | private 생성자        |
| ------------------- | ------------- | --------------------- |
| **인스턴스화 방지** | ⭕ 완벽       | ⚠️ 리플렉션 우회 가능 |
| **상속 방지**       | ⭕ 완벽       | ⚠️ 상속 가능          |
| **리플렉션 방어**   | ⭕ 완벽       | ❌ 취약               |
| **코드 간결성**     | ⭕ 매우 간단  | ⚠️ 생성자 필요        |

### 📝 실무 예시

#### 날짜 유틸리티 클래스

```java
public enum DateUtils {
    ; // 빈 enum 상수

    private static final DateTimeFormatter DEFAULT_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static String format(LocalDate date) {
        if (date == null) return null;
        return date.format(DEFAULT_FORMATTER);
    }

    public static LocalDate parse(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        return LocalDate.parse(dateString, DEFAULT_FORMATTER);
    }

    public static boolean isWeekend(LocalDate date) {
        if (date == null) return false;
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }

    public static long daysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 0;
        return ChronoUnit.DAYS.between(start, end);
    }
}
```

#### 컬렉션 유틸리티 클래스

```java
public enum CollectionUtils {
    ; // 빈 enum 상수

    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.isEmpty();
    }

    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return !isEmpty(collection);
    }

    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) return Collections.emptyList();
        return list.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public static <T, R> List<R> map(List<T> list, Function<T, R> mapper) {
        if (isEmpty(list)) return Collections.emptyList();
        return list.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }
}
```

### ⚠️ 주의사항

1. **enum 상수는 사용하지 않음**: 유틸리티 클래스의 경우 enum 상수는 단순히 인스턴스화를 막기 위한 용도입니다.

2. **정적 메서드만 제공**: 인스턴스 메서드는 제공하지 않습니다.

3. **네이밍**: enum 상수 이름(`INSTANCE`)은 관례일 뿐, 실제로 사용하지 않습니다.

### 🔄 다른 방법과 비교

#### 방법 1: private 생성자 (전통적)

```java
public class StringUtils {
    private StringUtils() {
        throw new AssertionError("인스턴스화 불가");
    }

    public static boolean isEmpty(String str) { ... }
}
```

**단점**: 리플렉션으로 우회 가능

#### 방법 2: abstract 클래스

```java
public abstract class StringUtils {
    private StringUtils() {}

    public static boolean isEmpty(String str) { ... }
}
```

**단점**: 상속 가능, 리플렉션으로 우회 가능

#### 방법 3: enum (권장 ⭐)

```java
public enum StringUtils {
    ;

    public static boolean isEmpty(String str) { ... }
}
```

**장점**: 완벽한 인스턴스화 방지, 리플렉션 방어, 상속 불가

---

## ✅ 5. 자원을 직접 명시하지 말고 의존 객체 주입을 사용하라

### 📌 핵심 정의

클래스가 사용할 자원을 직접 생성하거나 고정하지 말고, **외부에서 주입받도록 설계**하라.

---

## ❌ 나쁜 예 (자원 직접 명시)

```java
public class SpellChecker {
    private static final Dictionary dictionary = new KoreanDictionary();

    public static boolean isValid(String word) {
        return dictionary.contains(word);
    }

    public static List<String> suggestions(String typo) {
        return dictionary.suggestions(typo);
    }
}
```

### 문제점

- ❌ **구현체에 강결합**: `KoreanDictionary`에 직접 의존
- ❌ **테스트 불가**: 실제 사전을 사용해야만 테스트 가능
- ❌ **교체 불가능**: 다른 사전(영어, 일본어 등)으로 교체 불가
- ❌ **확장성 없음**: 새로운 사전 타입 추가 시 코드 수정 필요

**사용 예시**:

```java
// 항상 한국어 사전만 사용 가능
boolean valid = SpellChecker.isValid("안녕");
```

---

## ✅ 좋은 예 (의존 객체 주입)

```java
public class SpellChecker {
    private final Dictionary dictionary;

    public SpellChecker(Dictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary);
    }

    public boolean isValid(String word) {
        return dictionary.contains(word);
    }

    public List<String> suggestions(String typo) {
        return dictionary.suggestions(typo);
    }
}
```

### 장점

- ✔ **느슨한 결합**: 인터페이스(`Dictionary`)에 의존
- ✔ **테스트 용이**: Mock 객체 주입 가능
- ✔ **자원 교체 가능**: 런타임에 다른 구현체 주입 가능
- ✔ **OCP 만족**: 확장에는 열려있고 수정에는 닫혀있음

**사용 예시**:

```java
// 한국어 사전 사용
Dictionary koreanDict = new KoreanDictionary();
SpellChecker koreanChecker = new SpellChecker(koreanDict);

// 영어 사전 사용
Dictionary englishDict = new EnglishDictionary();
SpellChecker englishChecker = new SpellChecker(englishDict);

// 테스트용 Mock 사전 사용
Dictionary mockDict = mock(Dictionary.class);
SpellChecker testChecker = new SpellChecker(mockDict);
```

---

## 🔄 다양한 주입 방식

### 1️⃣ 생성자 주입 (가장 권장 ⭐)

```java
public class SpellChecker {
    private final Dictionary dictionary;

    public SpellChecker(Dictionary dictionary) {
        this.dictionary = Objects.requireNonNull(dictionary);
    }
}
```

**장점**:

- ✔ **불변 객체**: `final` 필드로 불변성 보장
- ✔ **테스트 쉬움**: 생성자에서 바로 주입
- ✔ **의존성 명확**: 생성자 시그니처로 필수 의존성 명확히 표현
- ✔ **Null 안전**: `Objects.requireNonNull()`으로 방어 가능

### 2️⃣ 정적 팩터리 + 주입

```java
public class SpellChecker {
    private final Dictionary dictionary;

    private SpellChecker(Dictionary dictionary) {
        this.dictionary = dictionary;
    }

    public static SpellChecker of(Dictionary dictionary) {
        return new SpellChecker(dictionary);
    }
}
```

**사용 예시**:

```java
SpellChecker checker = SpellChecker.of(new KoreanDictionary());
```

### 3️⃣ 팩터리 주입 (Supplier)

```java
public class SpellChecker {
    private final Dictionary dictionary;

    public SpellChecker(Supplier<Dictionary> dictionaryFactory) {
        this.dictionary = dictionaryFactory.get();
    }
}
```

**장점**:

- ✔ **지연 생성**: 필요할 때만 객체 생성
- ✔ **상황별 객체 생성**: 매번 새로운 인스턴스 필요 시 유용
- ✔ **복잡한 생성 로직**: 팩터리에서 복잡한 초기화 가능

**사용 예시**:

```java
// 매번 새로운 사전 인스턴스 생성
SpellChecker checker = new SpellChecker(() -> new KoreanDictionary());

// 캐싱된 사전 사용
Supplier<Dictionary> cachedFactory = () -> {
    if (cached == null) {
        cached = new KoreanDictionary();
    }
    return cached;
};
SpellChecker checker2 = new SpellChecker(cachedFactory);
```

### 4️⃣ Setter 주입 (비권장)

```java
public class SpellChecker {
    private Dictionary dictionary;

    public void setDictionary(Dictionary dictionary) {
        this.dictionary = dictionary;
    }
}
```

**단점**:

- ❌ 불변성 보장 불가
- ❌ 필수 의존성 확인 어려움
- ❌ 런타임에 NullPointerException 가능

**사용 사례**: 선택적 의존성이나 런타임에 변경이 필요한 경우에만 사용

---

## 🧠 진짜 핵심

### "자원을 직접 명시하지 말라" = "구현에 의존하지 말라"

DI는 단순한 Spring 기술이 아니라 **객체지향 설계 원칙**입니다.

**핵심 원칙**:

- **DIP (Dependency Inversion Principle)**: 고수준 모듈은 저수준 모듈에 의존하면 안 되고, 둘 다 추상화에 의존해야 함
- **OCP (Open-Closed Principle)**: 확장에는 열려있고 수정에는 닫혀있어야 함
- **단일 책임 원칙**: 각 클래스는 하나의 책임만 가져야 함

### 의존성 역전 원칙 (DIP)

```java
// ❌ 나쁜 예: 고수준 모듈이 저수준 모듈에 직접 의존
public class SpellChecker {
    private KoreanDictionary dictionary; // 구체 클래스에 의존
}

// ✅ 좋은 예: 추상화에 의존
public class SpellChecker {
    private Dictionary dictionary; // 인터페이스에 의존
}
```

---

## 🆚 싱글톤 vs DI

| 항목       | 싱글톤                | DI                  |
| ---------- | --------------------- | ------------------- |
| **테스트** | ❌ 어려움 (전역 상태) | ⭕ 쉬움 (Mock 주입) |
| **교체**   | ❌ 불가능             | ⭕ 가능             |
| **확장성** | ❌ 낮음               | ⭕ 높음             |
| **결합도** | ❌ 높음               | ⭕ 낮음             |
| **유연성** | ❌ 낮음               | ⭕ 높음             |

### 싱글톤의 문제점

```java
// ❌ 싱글톤 사용
public class SpellChecker {
    private static final Dictionary INSTANCE = new KoreanDictionary();

    public static boolean isValid(String word) {
        return INSTANCE.contains(word);
    }
}

// 문제: 테스트 시 실제 사전을 사용해야 함
// 문제: 다른 사전으로 교체 불가능
```

### DI의 장점

```java
// ✅ DI 사용
public class SpellChecker {
    private final Dictionary dictionary;

    public SpellChecker(Dictionary dictionary) {
        this.dictionary = dictionary;
    }
}

// 장점: 테스트 시 Mock 주입 가능
// 장점: 런타임에 다른 사전으로 교체 가능
```

---

## 🧠 Spring과의 연결

Spring Framework는 Item 5를 프레임워크 차원에서 구현해주는 것입니다.

### Spring 없이 DI 구현

```java
// 수동으로 의존성 주입
Dictionary dictionary = new KoreanDictionary();
SpellChecker checker = new SpellChecker(dictionary);
```

### Spring으로 DI 구현

```java
@Component
public class SpellChecker {
    private final Dictionary dictionary;

    // Spring이 자동으로 Dictionary 구현체를 주입
    public SpellChecker(Dictionary dictionary) {
        this.dictionary = dictionary;
    }
}

// Spring 설정
@Configuration
public class AppConfig {
    @Bean
    public Dictionary dictionary() {
        return new KoreanDictionary();
    }
}
```

**Spring의 역할**:

- ✔ 의존성 자동 주입
- ✔ 생명주기 관리
- ✔ 스코프 관리 (싱글톤, 프로토타입 등)
- ✔ AOP 지원

**핵심**: Spring은 DI를 편리하게 사용할 수 있게 해주는 도구일 뿐, DI의 본질은 객체지향 설계 원칙입니다.

---

## 📝 실무 예시

### 예시 1: 데이터베이스 연결

```java
// ❌ 나쁜 예
public class UserRepository {
    private static final Connection connection = DriverManager.getConnection("jdbc:mysql://...");
}

// ✅ 좋은 예
public class UserRepository {
    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
```

### 예시 2: 로깅

```java
// ❌ 나쁜 예
public class OrderService {
    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
}

// ✅ 좋은 예 (하지만 로깅은 예외적으로 정적 사용 가능)
public class OrderService {
    private final Logger logger;

    public OrderService(Logger logger) {
        this.logger = logger;
    }
}
```

**참고**: 로깅은 보통 정적으로 사용해도 괜찮지만, 테스트를 위해 주입받는 것도 좋은 방법입니다.

### 예시 3: HTTP 클라이언트

```java
// ❌ 나쁜 예
public class ApiClient {
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public String get(String url) {
        // ...
    }
}

// ✅ 좋은 예
public class ApiClient {
    private final HttpClient httpClient;

    public ApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String get(String url) {
        // ...
    }
}

// 테스트 시 Mock 주입 가능
HttpClient mockClient = mock(HttpClient.class);
ApiClient client = new ApiClient(mockClient);
```

---

## ⚠️ 주의할 점

### 무조건 DI가 답은 아님

다음 경우는 예외적으로 직접 생성이 나을 수 있습니다:

1. **유틸리티 클래스**: 정적 메서드만 가진 클래스

```java
public class MathUtils {
    public static int add(int a, int b) {
        return a + b;
    }
}
```

2. **Stateless Helper**: 상태가 없는 헬퍼 클래스

```java
public class StringHelper {
    public static String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
```

3. **값 객체 (Value Object)**: 불변 값 객체

```java
public class Money {
    private final int amount;
    private final String currency;

    public Money(int amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }
}
```

**원칙**: 클래스가 **외부 자원**에 의존하는 경우에만 DI를 사용하세요.

---

## 📊 의존성 주입 방식 비교표

| 방식            | 불변성 | 테스트 용이성 | 명확성 | 권장도 |
| --------------- | ------ | ------------- | ------ | ------ |
| **생성자 주입** | ⭕     | ⭕            | ⭕     | ⭐⭐⭐ |
| **정적 팩터리** | ⭕     | ⭕            | ⭕     | ⭐⭐⭐ |
| **팩터리 주입** | ⭕     | ⭕            | ⚠️     | ⭐⭐   |
| **Setter 주입** | ❌     | ⚠️            | ❌     | ⭐     |

---

## 📝 한 줄 요약

> **자원을 직접 생성하지 않고 외부에서 주입받도록 설계하면 결합도를 낮추고 테스트와 확장이 쉬운 클래스를 만들 수 있다. DI는 Spring의 기술이 아니라 객체지향 설계 원칙(DIP)의 구현이다.**

---
