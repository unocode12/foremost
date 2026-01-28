# 이펙티브 자바 3판 — 아이템 11 ~ 20 (기술적으로 자세한 정리)

> 범위: **아이템 11~20**  
> 목표: “왜 그런가(규약/동작원리)”와 “어떻게 구현하는가(패턴/주의점)”를 함께 이해

---

## 아이템 11. `equals`를 재정의하려거든 `hashCode`도 재정의하라

### 1) 왜 필요한가: 해시 기반 컬렉션의 동작 원리
`HashMap/HashSet`은 (개념적으로) 다음 순서로 원소를 찾는다.

1. `hashCode()`로 **버킷(bucket)** 을 선택
2. 같은 버킷 안에서 `equals()`로 **동등성** 확인

따라서 **`equals`가 true인 두 객체는 반드시 같은 `hashCode`를 반환**해야 컬렉션이 “같은 키/원소”로 취급한다.

### 2) 깨지면 발생하는 증상
- `HashSet`에 “같은 객체(논리적으로 동일)”가 **중복 저장**
- `HashMap`에서 put한 키로 get이 **실패**
- `contains`, `remove`가 **false를 반환**하는 이상 동작

### 3) 규약(Contracts)
- `equals(a,b) == true` 이면 `a.hashCode() == b.hashCode()` 여야 한다.
- 반대는 필수 아님(해시 충돌은 가능): `hashCode`가 같아도 `equals`가 false일 수 있다.

### 4) 구현 패턴(권장)
- 가장 쉬운 방법: `Objects.hash(...)`
- 성능이 중요하면 직접 31 배수 누적 방식으로 계산 (충돌을 줄이고 비용을 줄임)

```java
@Override
public int hashCode() {
    return Objects.hash(field1, field2, field3);
}
```

직접 구현 예시(성능/충돌 제어):
```java
@Override
public int hashCode() {
    int result = 17;
    result = 31 * result + Integer.hashCode(age);
    result = 31 * result + (name == null ? 0 : name.hashCode());
    return result;
}
```

### 5) 주의 포인트
- `equals` 비교에 사용한 필드는 **반드시** `hashCode`에도 포함해야 일관성이 유지된다.
- 가변 객체에서 해시 키로 쓰이면 위험: 필드가 바뀌면 `hashCode`가 바뀌어 컬렉션 내부에서 “잃어버린 키”가 된다.

---

## 아이템 12. `toString`을 항상 재정의하라

### 1) 왜 필요한가
기본 `toString()`은 `클래스명@해시` 형태로 **의미가 없다**.  
로그/디버깅/예외 메시지에서 객체 상태를 빠르게 파악하려면 **사람이 읽을 수 있는 표현**이 필요하다.

### 2) 좋은 `toString`의 기준
- 객체의 “핵심 상태”를 포함 (모든 필드가 아니라, 의미 있는 필드)
- 포맷을 문서화하면 안정성이 좋아짐(단, 포맷 고정은 변경 비용이 생김)
- 민감정보(비밀번호/토큰/주민번호 등)는 출력 금지

```java
@Override
public String toString() {
    return "User{name='%s', age=%d}".formatted(name, age);
}
```

### 3) 팁
- 컬렉션/배열 필드는 `Arrays.toString`, `Arrays.deepToString` 고려
- 성능 문제로 매우 자주 호출되면(초당 수만 번) 캐싱 고려(하지만 변경 가능 객체면 위험)

---

## 아이템 13. `clone` 재정의는 주의해서 진행하라

### 1) 왜 위험한가
`Cloneable`은 “clone 가능”을 표식으로만 제공하고, `Object.clone()`은
- protected
- 얕은 복사 기본
- 예외 설계가 어색(Checked 예외인 `CloneNotSupportedException`)

즉, **API 설계 관점에서 함정이 많다.**

### 2) 얕은 복사(shallow copy) 문제
참조 필드(배열/컬렉션/가변 객체)를 그대로 복사하면 원본과 클론이 내부 상태를 공유한다.

```java
// 예: int[] scores가 참조로 복사되면 원본/복제본이 같은 배열을 공유
```

### 3) 대안(권장 순서)
1. **복사 생성자(copy constructor)**
2. **복사 팩터리(copy factory)**
3. 정말 필요하면 clone(상속/라이브러리 제약 등)

복사 생성자:
```java
public User(User other) {
    this.name = other.name;
    this.scores = other.scores.clone(); // 방어적 복사
}
```

복사 팩터리:
```java
public static User copyOf(User other) {
    return new User(other);
}
```

### 4) clone을 해야 한다면(최소 가이드)
- `super.clone()` 호출 후, **가변 필드는 깊은 복사**
- 반환 타입을 구체 타입으로 공변 반환(covariant return) 가능

---

## 아이템 14. `Comparable`을 구현할지 고려하라

### 1) 의미
`Comparable`은 객체의 **자연스러운 순서(natural ordering)** 를 정의한다.
- 정렬(`Collections.sort`, `Arrays.sort`)
- 정렬 기반 컬렉션(`TreeSet`, `TreeMap`)에서 필수적

### 2) `compareTo` 규약 핵심
- `sgn(x.compareTo(y)) == -sgn(y.compareTo(x))`
- 추이성(transitivity): x>y, y>z ⇒ x>z
- 일관성: 값이 변하지 않으면 반복 호출 결과가 동일
- **(권장)** `compareTo()==0` 이면 `equals()`도 true

> 특히 `TreeSet/TreeMap`은 **compareTo==0을 중복으로 취급**한다.  
> equals가 false여도 compareTo가 0이면 원소가 추가되지 않을 수 있다.

### 3) 구현 패턴(오버플로우 금지)
❌ 나쁜 예:
```java
return this.age - o.age; // 오버플로우 위험
```

✅ 좋은 예:
```java
return Integer.compare(this.age, o.age);
```

다중 필드 비교는 `Comparator` 체이닝이 가장 안전하고 읽기 쉽다:
```java
@Override
public int compareTo(User o) {
    return Comparator.comparing(User::getAge)
            .thenComparing(User::getName)
            .compare(this, o);
}
```

---

## 아이템 15. 클래스와 멤버의 접근 권한을 최소화하라

### 1) 목표: 정보 은닉(캡슐화)
외부에 노출되는 API를 최소화하면
- 결합도 감소
- 변경 파급 최소화
- 불변식(invariant) 유지가 쉬움

### 2) 실전 규칙
- 기본은 `private`
- 패키지 내부에서만 쓰면 package-private(접근자 생략)
- 상속 확장 지점이 아니면 `protected`도 최소화
- 공개 API(`public`)는 “영원히 지원해야 하는 계약”이라고 생각

### 3) 추가 팁
- public 클래스의 public static final 배열은 사실상 가변이므로 금지(아이템 15의 대표 함정)
  - 대신 unmodifiable view 또는 방어적 복사 제공

---

## 아이템 16. public 클래스에서는 public 필드가 아닌 접근자 메서드를 사용하라

### 1) 왜 public 필드가 문제인가
- 캡슐화 붕괴: 검증/로깅/동기화/불변식 유지 불가
- 내부 표현 변경 불가(호환성 깨짐)
- 스레드 안전 보장 어려움

❌:
```java
public int x;
public int y;
```

✅:
```java
private int x;
private int y;

public int getX() { return x; }
public int getY() { return y; }
```

### 2) 예외
- 패키지 내부 전용의 단순 DTO/레코드(Record) 등은 상황에 따라 허용 가능
- 공개 API는 되도록 접근자 사용

---

## 아이템 17. 변경 가능성을 최소화하라 (불변 클래스)

### 1) 불변(immutable)의 장점
- 스레드 안전(동기화 없이 안전)
- 공유/캐싱이 쉬움
- 방어적 복사 부담 감소
- 예측 가능성 증가(디버깅 쉬움)

### 2) 불변 클래스 작성 규칙
1. 객체 상태를 변경하는 메서드 제공 금지(setter 금지)
2. 클래스를 확장할 수 없게(`final` 또는 생성자 private + 정적 팩터리)
3. 모든 필드를 `final`
4. 모든 필드를 `private`
5. 가변 구성요소는 방어적 복사로 감싸기(생성자/접근자 모두)

```java
public final class Money {
    private final long amount;
    private final String currency;

    public Money(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new IllegalArgumentException();
        return new Money(this.amount + other.amount, this.currency);
    }
}
```

### 3) 주의
- 불변이라도 내부에 가변 객체 참조를 “그대로 노출”하면 불변이 깨진다.

---

## 아이템 18. 상속보다는 컴포지션을 사용하라

### 1) 상속의 핵심 문제: 캡슐화 위반
상속은 상위 클래스의 내부 구현에 강하게 결합된다.
- 상위 클래스가 업데이트되면 하위 클래스가 깨질 수 있음
- 재정의 가능한 메서드를 상위가 내부에서 호출하면 예기치 않은 동작(훅 메서드 문제)

### 2) 컴포지션(위임) 패턴
“필요한 기능”을 가진 객체를 필드로 두고, 메서드를 위임한다.

```java
public class ForwardingSet<E> implements Set<E> {
    private final Set<E> s;
    public ForwardingSet(Set<E> s) { this.s = s; }
    public int size() { return s.size(); }
    public boolean add(E e) { return s.add(e); }
    // ... 나머지 위임
}
```

### 3) 효과
- 기능을 확장하면서도 구현 결합을 낮춤
- 데코레이터(Decorator)처럼 조합 가능

---

## 아이템 19. 상속을 고려해 설계하고 문서화하라. 그러지 않았다면 상속을 금지하라

### 1) 핵심
상속용 클래스는 “재정의 포인트”를 안전하게 제공해야 한다.
- 어떤 메서드를 재정의해도 불변식이 깨지지 않아야 함
- 내부 동작(특히 재정의 메서드 호출 타이밍)을 문서화해야 함

### 2) 위험 패턴
- 생성자에서 재정의 가능한 메서드를 호출
  - 하위 클래스 필드 초기화 전 호출될 수 있어 NPE/논리 오류 발생

### 3) 상속을 허용하지 않을 거면
- 클래스를 `final`로 선언하거나
- 모든 생성자를 `private`/`package-private`로 제한하고 정적 팩터리 제공

---

## 아이템 20. 추상 클래스보다는 인터페이스를 우선하라

### 1) 왜 인터페이스가 유리한가
- 다중 구현 가능(단일 상속 제한 회피)
- 타입 역할(“~할 수 있다”)을 표현하기 좋음
- 구현을 강제하지 않으면서 확장 가능

### 2) Java 8+ 디폴트 메서드
- 인터페이스도 기본 구현 제공 가능
- 단, 상태(필드)는 가질 수 없으므로 복잡한 공통 구현은 여전히 추상 클래스/헬퍼 클래스가 필요할 수 있다.

```java
public interface Flyable {
    void fly();

    default void takeOff() {
        // 기본 동작 제공
    }
}
```

### 3) 실무 패턴
- 인터페이스 + 정적 유틸/정적 팩터리(또는 skeletal implementation: AbstractXxx 형태) 조합이 흔함

---

## 빠른 체크리스트
- (11) equals 재정의 ⇒ hashCode도 같이
- (12) 로그/디버깅을 위해 toString은 “의미 있게”
- (13) clone 대신 복사 생성자/팩터리
- (14) 자연 순서가 있으면 Comparable, 구현은 Comparator 체이닝으로 안전하게
- (15~16) public API는 노출 최소화 + 필드 직접 공개 금지
- (17) 가능하면 불변, 가변 필드는 방어적 복사
- (18~19) 상속은 위험, 허용 시 문서화/설계 필수
- (20) 타입 역할은 인터페이스 우선
