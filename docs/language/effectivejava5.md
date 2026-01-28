# 이펙티브 자바 3판 — 아이템 21 ~ 30 (기술적으로 자세한 정리)

> 범위: **아이템 21~30**  
> 주제 흐름: **인터페이스 설계(21~25)** → **제네릭/타입 안정성(26~30)**

---

## 아이템 21. 인터페이스는 구현하는 쪽을 생각해 설계하라

### 1) 핵심
인터페이스는 “구현체가 지켜야 할 계약(contract)”이다.  
한 번 공개(public)되면 **바꾸기 매우 어렵기 때문에**, 설계 단계에서 구현 난이도/호환성까지 고려해야 한다.

### 2) 특히 조심할 것: 디폴트 메서드(default method)
Java 8부터 인터페이스에 기본 구현을 넣을 수 있지만, 다음 문제가 생길 수 있다.

- **기존 구현체가 새로운 디폴트 메서드의 가정을 깨뜨릴 수 있음**
- 디폴트 메서드가 내부 메서드를 호출하면서 **불변식(invariant)** 을 전제로 하면 위험
- 다중 상속 충돌(두 인터페이스가 같은 시그니처의 디폴트 메서드 제공)

예시(개념):
- 어떤 인터페이스에 `default boolean removeIf(...)` 같은 메서드를 추가했는데,
  기존 구현체가 `Iterator.remove()`를 지원하지 않거나, 동시 수정 규칙을 달리 구현하면 런타임 예외/오동작 가능

### 3) 설계 체크리스트
- 인터페이스는 **최소 메서드**로 시작(확장 여지)
- 가능한 한 “행동”이 아닌 “능력/역할” 중심으로 설계
- 반드시 필요한 경우가 아니라면 디폴트 메서드로 “상태를 가정”하지 말 것
- 인터페이스 변경은 **다수 구현체**에 대해 테스트/검증 필요

### 4) 실무 팁
- 확장 가능성이 크면, 인터페이스 + 정적 팩터리(또는 SPI)로 진화 경로 확보
- 공통 구현은 디폴트 메서드보다 **skeletal implementation(골격 구현)** 패턴도 고려(아이템 20 연계)

---

## 아이템 22. 인터페이스는 타입을 정의하는 용도로만 사용하라

### 1) 핵심
인터페이스는 “이 객체는 **이 타입이다**”를 표현해야 한다.  
단순히 상수 모음 용도로 쓰면(일명 **constant interface** 패턴) 나쁜 설계가 된다.

### 2) 상수 인터페이스가 나쁜 이유
- 구현 클래스의 공개 API에 **불필요한 상수들이 섞여 들어감**
- 네임스페이스 오염(이름 충돌 가능)
- 구현체 교체/리팩토링 시 호환성 문제
- “타입”이 아닌데 인터페이스를 구현하게 만드는 설계 오류

❌ 나쁜 예:
```java
public interface PhysicalConstants {
    double AVOGADROS_NUMBER = 6.022_140_76e23;
}
public class Chemistry implements PhysicalConstants { }
```

✅ 좋은 대안
- `public final class` 유틸리티 클래스에 `public static final` 상수로 제공
- 또는 `enum`으로 그룹화(의미 있는 타입이 된다면)

```java
public final class PhysicalConstants {
    private PhysicalConstants() {}
    public static final double AVOGADROS_NUMBER = 6.022_140_76e23;
}
```

---

## 아이템 23. 태그 달린 클래스보다는 클래스 계층구조를 활용하라

### 1) 태그 달린 클래스(tagged class)란?
하나의 클래스가 `type` 같은 태그 필드로 여러 “형태”를 표현하는 구조

❌ 예(개념):
```java
class Figure {
    enum Shape { RECTANGLE, CIRCLE }
    final Shape shape;
    // shape에 따라 필요한 필드가 달라짐 (radius vs length/width)
}
```

### 2) 왜 문제인가
- 불필요한 필드가 항상 존재(메모리 낭비)
- 생성자/메서드가 조건문으로 가득(복잡성 증가)
- 새 타입 추가 시 모든 조건문 수정(변경 취약)
- 컴파일러의 도움 감소(타입 시스템 활용 못함)

### 3) 해결: 클래스 계층구조(상속/추상화)
- 공통은 추상 클래스(또는 인터페이스)
- 변형별로 하위 클래스로 분리

✅ 예:
```java
abstract class Figure {
    abstract double area();
}

final class Circle extends Figure {
    final double radius;
    Circle(double radius) { this.radius = radius; }
    @Override double area() { return Math.PI * radius * radius; }
}

final class Rectangle extends Figure {
    final double length, width;
    Rectangle(double l, double w) { this.length = l; this.width = w; }
    @Override double area() { return length * width; }
}
```

### 4) 결과
- 조건문 제거(다형성)
- 타입 추가가 쉬움(OCP에 가까움)
- 불변식이 각 타입 내부로 캡슐화

---

## 아이템 24. 멤버 클래스는 되도록 static으로 만들라

### 1) 멤버 클래스 종류
- **정적 멤버 클래스(static nested class)**
- **비정적 멤버 클래스(inner class)**: 바깥 인스턴스 참조를 자동으로 가짐
- 지역 클래스(local class)
- 익명 클래스(anonymous class)

### 2) 왜 static이 기본인가
비정적 멤버 클래스는 컴파일러가 **바깥 인스턴스에 대한 숨은 참조**를 만든다.
- 바깥 인스턴스를 필요로 하지 않는데 inner로 만들면 **메모리 누수/GC 방해** 가능
- 직렬화/동시성/생명주기 측면에서도 불필요한 결합

✅ 바깥 인스턴스가 필요 없으면:
```java
public class Outer {
    static class Helper { /* ... */ }
}
```

✅ 바깥 인스턴스에 접근이 본질이라면 inner:
```java
public class Outer {
    private int x;
    class IteratorLike {
        int readOuter() { return x; }
    }
}
```

### 3) 실무 팁
- “바깥 객체 상태를 읽어야 하는가?”를 기준으로 결정
- 단순 DTO/헬퍼/빌더는 대부분 `static`이 적합

---

## 아이템 25. 톱레벨 클래스는 한 파일에 하나만 담으라

### 1) 핵심
한 `.java` 파일에 여러 톱레벨 클래스를 넣으면
- 코드 탐색/리뷰/리팩토링이 어려워지고
- 빌드/컴파일에서 예상치 못한 문제(특히 동일 패키지, 동일 이름 충돌 등) 가능

### 2) 권장
- public 톱레벨 클래스는 **파일명과 동일**
- 나머지는 보통 별도 파일로 분리
- “도우미”는 private static 중첩 클래스로 내부화(필요하다면)

---

## 아이템 26. 로 타입(raw type)은 사용하지 말라

### 1) 로 타입이란?
제네릭 타입에서 타입 매개변수를 생략한 형태

```java
List list = new ArrayList(); // raw type
```

### 2) 왜 위험한가: 타입 안정성 붕괴
컴파일러가 타입 체크를 못 하므로 런타임에 `ClassCastException`이 터진다.

```java
List list = new ArrayList();
list.add("hello");
Integer x = (Integer) list.get(0); // 런타임 예외
```

### 3) 올바른 대안
- 타입을 명확히: `List<String>`
- 타입을 모를 때는 **비한정 와일드카드**: `List<?>`

```java
List<?> list = new ArrayList<String>();
```

### 4) 로 타입이 “허용”되는 예외
- 클래스 리터럴: `List.class` (제네릭 정보는 런타임에 소거됨)
- `instanceof`: `if (obj instanceof List)` (매개변수화 타입은 사용 불가)

---

## 아이템 27. 비검사 경고(unchecked warning)를 제거하라

### 1) 핵심
unchecked 경고는 “컴파일러가 타입 안전성을 보장하지 못한다”는 신호다.  
가능하면 **경고를 0으로 만드는 것**이 목표.

### 2) 제거 전략
- 제네릭 타입을 정확히 명시(`List<String>`)
- 불필요한 캐스팅 제거
- API 설계 개선(반환 타입/파라미터 타입 제네릭화)

### 3) 정말 안전한데 경고가 남는다면
`@SuppressWarnings("unchecked")`를 **가장 좁은 범위**에만 적용하고,
“왜 안전한지” 주석으로 근거를 남긴다.

```java
@SuppressWarnings("unchecked") // elements는 오직 T 타입만 저장되도록 클래스 내부에서 보장됨
T result = (T) elements[i];
```

> 경고를 무시하면, 진짜 버그가 경고 사이에 묻혀서 발견이 늦어진다.

---

## 아이템 28. 배열보다는 리스트를 사용하라

### 1) 핵심 차이: 배열은 공변(covariant), 제네릭은 불공변(invariant)
배열:
- `Sub[]`는 `Super[]`의 하위 타입(공변)
- 하지만 런타임 타입 체크로 `ArrayStoreException`이 발생할 수 있음

제네릭(List):
- `List<Sub>`는 `List<Super>`의 하위 타입이 아님(불공변)
- 대신 컴파일 타임에 타입 안정성 보장

### 2) 배열의 함정 예시(개념)
```java
Object[] objs = new Long[1]; // 컴파일 OK (공변)
objs[0] = "x";               // 런타임 ArrayStoreException
```

### 3) 제네릭에서 배열이 특히 곤란한 이유
- 제네릭은 타입 소거로 런타임에 `T`를 알 수 없음
- `new T[]` 불가
- `List<T>[]` 같은 “제네릭 배열”은 타입 안전하지 않아 금지/경고 유발

### 4) 결론
- API/구현에서 타입 안전성까지 원하면 **List 중심**으로 설계
- 배열은 성능/상호운용 목적일 때만 제한적으로 사용

---

## 아이템 29. 이왕이면 제네릭 타입으로 만들라

### 1) 핵심
기존에 `Object`로 구현한 타입(컬렉션/컨테이너)은 제네릭으로 바꾸면
- 호출부 캐스팅 제거
- 컴파일 타임 타입 체크
- API 사용성이 크게 좋아진다

### 2) 예: Object 기반 Stack → 제네릭 Stack
```java
public class Stack<E> {
    private E[] elements;
    private int size = 0;

    @SuppressWarnings("unchecked")
    public Stack() {
        elements = (E[]) new Object[16]; // 제네릭 배열 생성 불가 → Object[]로 만들고 캐스팅
    }

    public void push(E e) { elements[size++] = e; }

    public E pop() {
        E result = elements[--size];
        elements[size] = null; // 메모리 누수 방지
        return result;
    }
}
```

### 3) 포인트
- 내부적으로는 `Object[]`를 쓰더라도, 외부 API는 타입 안전하게 제공 가능
- 캐스팅은 클래스 내부 “단일 지점”에 고립시키고, 근거가 명확할 때만 suppress

---

## 아이템 30. 이왕이면 제네릭 메서드로 만들라

### 1) 핵심
메서드가 타입에 독립적으로 동작하면, 제네릭 메서드로 만들어
- 재사용성 증가
- 호출부 캐스팅 제거
- 타입 추론(type inference) 활용 가능

### 2) 대표 예: 두 집합의 합집합
```java
public static <E> Set<E> union(Set<? extends E> s1, Set<? extends E> s2) {
    Set<E> result = new HashSet<>(s1);
    result.addAll(s2);
    return result;
}
```

- `? extends E`를 사용해 “E의 하위 타입 원소를 담는 Set”도 받을 수 있게 한다(유연성 향상)
- 반환은 `Set<E>`로 통일(타입 추론으로 호출이 쉬워짐)

### 3) 정적 유틸리티 + 제네릭 메서드 조합
- `Collections`, `Objects` 같은 유틸 클래스 패턴과 궁합이 좋다.

---

## 빠른 체크리스트
- (21) 인터페이스는 공개 API → 구현 난이도/진화까지 고려, 디폴트 메서드는 특히 조심
- (22) 인터페이스는 “타입”을 표현, 상수 모음 용도 금지(유틸 클래스/enum 사용)
- (23) 태그 필드 + 조건문 지옥 → 계층 구조로 분리(다형성)
- (24) 바깥 인스턴스 참조가 필요 없으면 `static` 중첩 클래스로
- (25) 톱레벨 클래스 1파일 1클래스 원칙(가독성/유지보수/빌드 안정성)
- (26) raw type 금지, 모르면 `<?>`
- (27) unchecked 경고는 제거, 불가피하면 최소 범위 suppress + 근거 주석
- (28) 배열 공변/런타임 체크 vs 제네릭 불공변/컴파일 타임 체크 → 기본은 List
- (29) 컨테이너는 제네릭 타입으로(캐스팅 제거, 타입 안정성)
- (30) 타입 독립 로직은 제네릭 메서드로(타입 추론 + 재사용성)

