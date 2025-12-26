# Effective Java - Part 3

## ✅ 10. equals는 일반 규약을 지켜 재정의하라 (Effective Java Item 10)

### 🔑 핵심 한 문장

**equals를 재정의할 때는 반사성, 대칭성, 추이성, 일관성, null-아님의 5가지 규약을 반드시 지켜야 한다.**

> **면접 단골 질문**: "equals를 재정의할 때 지켜야 할 규약은 무엇인가요? 각 규약을 위반하면 어떤 문제가 발생하나요?"

---

## 📌 왜 중요한가?

equals를 잘못 재정의하면 예상치 못한 버그가 발생합니다:

- **컬렉션 동작 오류**: `HashSet`, `HashMap` 등이 제대로 동작하지 않음
- **예측 불가능한 동작**: 같은 객체를 찾지 못하거나, 다른 객체를 같은 것으로 인식
- **디버깅 어려움**: 규약 위반으로 인한 버그는 찾기 매우 어려움
- **API 신뢰성 저하**: 다른 개발자들이 equals 동작을 예측할 수 없음

---

## 1️⃣ equals를 재정의하지 않아도 되는 경우

### ✅ 재정의하지 말아야 할 경우

1. **각 인스턴스가 본질적으로 고유한 경우**

   - `Thread`, `Process` 등
   - 값이 아닌 동작하는 개체를 표현하는 클래스

2. **논리적 동치성(logical equality)을 검사할 필요가 없는 경우**

   - `java.util.regex.Pattern`
   - `Random`

3. **상위 클래스의 equals가 하위 클래스에도 적절한 경우**

   - `AbstractSet`, `AbstractList`의 equals 사용
   - 대부분의 `Set`, `List` 구현체

4. **클래스가 private이거나 package-private이고 equals를 호출할 일이 없는 경우**

   - 내부적으로만 사용되는 클래스

### ✅ 재정의해야 하는 경우

**값 클래스(value class)**에서 객체의 논리적 동치성을 확인해야 할 때:

- `Integer`, `String` 같은 값 클래스
- 두 객체가 같은 값을 가지면 같은 것으로 간주해야 함
- `Map`의 키나 `Set`의 원소로 사용할 때

---

## 2️⃣ equals의 일반 규약

### 📋 Object 명세의 equals 규약

```
equals 메서드는 동치관계(equivalence relation)를 구현하며, 다음을 만족한다:

1. 반사성(reflexive): null이 아닌 모든 참조 값 x에 대해, x.equals(x)는 true다.
2. 대칭성(symmetric): null이 아닌 모든 참조 값 x, y에 대해, x.equals(y)가 true면 y.equals(x)도 true다.
3. 추이성(transitive): null이 아닌 모든 참조 값 x, y, z에 대해, x.equals(y)가 true이고 y.equals(z)가 true면 x.equals(z)도 true다.
4. 일관성(consistent): null이 아닌 모든 참조 값 x, y에 대해, x.equals(y)를 반복 호출해도 항상 같은 결과를 반환한다.
5. null-아님: null이 아닌 모든 참조 값 x에 대해, x.equals(null)은 false다.
```

---

## 3️⃣ 규약 위반 사례와 문제점

### ❌ 대칭성 위반 예시

```java
// 나쁜 예: 대칭성 위반
public final class CaseInsensitiveString {
    private final String s;

    public CaseInsensitiveString(String s) {
        this.s = Objects.requireNonNull(s);
    }

    // 대칭성 위반!
    @Override
    public boolean equals(Object o) {
        if (o instanceof CaseInsensitiveString) {
            return s.equalsIgnoreCase(((CaseInsensitiveString) o).s);
        }
        if (o instanceof String) {  // 한 방향으로만 작동!
            return s.equalsIgnoreCase((String) o);
        }
        return false;
    }
}
```

**문제점**:

```java
CaseInsensitiveString cis = new CaseInsensitiveString("Polish");
String s = "polish";

cis.equals(s);  // true
s.equals(cis);  // false - 대칭성 위반!
```

**결과**:

- `List`에 넣으면 예상치 못한 동작
- `Set`에 넣으면 중복 허용 가능

**✅ 개선**:

```java
// 좋은 예: String과의 호환성 포기
@Override
public boolean equals(Object o) {
    return o instanceof CaseInsensitiveString &&
           ((CaseInsensitiveString) o).s.equalsIgnoreCase(s);
}
```

---

### ❌ 추이성 위반 예시

```java
// 나쁜 예: 추이성 위반
public class Point {
    private final int x, y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        return p.x == x && p.y == y;
    }
}

public class ColorPoint extends Point {
    private final Color color;

    public ColorPoint(int x, int y, Color color) {
        super(x, y);
        this.color = color;
    }

    // 추이성 위반!
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        
        // o가 일반 Point면 색상 무시하고 비교
        if (!(o instanceof ColorPoint)) {
            return o.equals(this);  // Point의 equals 호출
        }
        
        // o가 ColorPoint면 색상까지 비교
        return super.equals(o) && ((ColorPoint) o).color == color;
    }
}
```

**문제점**:

```java
ColorPoint p1 = new ColorPoint(1, 2, Color.RED);
Point p2 = new Point(1, 2);
ColorPoint p3 = new ColorPoint(1, 2, Color.BLUE);

p1.equals(p2);  // true (색상 무시)
p2.equals(p3);  // true (색상 무시)
p1.equals(p3);  // false (색상 다름) - 추이성 위반!
```

**✅ 개선 방법 1: 상속 대신 컴포지션**

```java
// 좋은 예: 컴포지션 사용
public class ColorPoint {
    private final Point point;
    private final Color color;

    public ColorPoint(int x, int y, Color color) {
        this.point = new Point(x, y);
        this.color = Objects.requireNonNull(color);
    }

    public Point asPoint() {
        return point;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ColorPoint)) return false;
        ColorPoint cp = (ColorPoint) o;
        return cp.point.equals(point) && cp.color.equals(color);
    }
}
```

**✅ 개선 방법 2: getClass() 사용 (하지만 리스코프 치환 원칙 위반)**

```java
// 타협안: getClass() 사용 (하위 클래스와 호환 불가)
@Override
public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) return false;
    ColorPoint cp = (ColorPoint) o;
    return super.equals(o) && cp.color == color;
}
```

**⚠️ 주의**: `getClass()` 사용은 리스코프 치환 원칙을 위반할 수 있음

---

### ❌ 일관성 위반 예시

```java
// 나쁜 예: 일관성 위반
public class Timestamp {
    private final long time;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Timestamp)) return false;
        // 시간이 가까우면 같다고 판단 (일관성 위반!)
        long diff = Math.abs(time - ((Timestamp) o).time);
        return diff < 1000;  // 1초 이내면 같다고 판단
    }
}
```

**문제점**:

- 같은 두 객체를 비교해도 시간에 따라 결과가 달라질 수 있음
- `Set`에 넣었다가 나중에 찾지 못할 수 있음

**✅ 개선**:

```java
// 좋은 예: 일관성 보장
@Override
public boolean equals(Object o) {
    if (!(o instanceof Timestamp)) return false;
    return time == ((Timestamp) o).time;  // 정확히 같아야 함
}
```

---

## 4️⃣ 올바른 equals 구현 방법

### ✅ 단계별 구현 가이드

```java
@Override
public boolean equals(Object o) {
    // 1. == 연산자로 자기 자신과의 참조 동일성 검사
    if (o == this) return true;
    
    // 2. instanceof로 타입 확인 (null 체크 포함)
    if (!(o instanceof PhoneNumber)) return false;
    
    // 3. 입력을 올바른 타입으로 형변환
    PhoneNumber pn = (PhoneNumber) o;
    
    // 4. 핵심 필드들이 모두 일치하는지 검사
    return pn.lineNum == lineNum && pn.prefix == prefix
            && pn.areaCode == areaCode;
}
```

### ✅ 필드 비교 순서

**성능 최적화를 위해 다를 가능성이 높은 필드부터 비교**:

```java
public class PhoneNumber {
    private final short areaCode, prefix, lineNum;

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PhoneNumber)) return false;
        PhoneNumber pn = (PhoneNumber) o;
        
        // 다를 가능성이 높은 필드부터 비교
        return pn.lineNum == lineNum      // 가장 다를 가능성 높음
            && pn.prefix == prefix
            && pn.areaCode == areaCode;   // 가장 다를 가능성 낮음
    }
}
```

### ✅ float, double 비교

**부동소수점은 `Float.compare()`, `Double.compare()` 사용**:

```java
public class Point {
    private final double x, y;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point)) return false;
        Point p = (Point) o;
        
        // 나쁜 예: == 연산자 사용
        // return p.x == x && p.y == y;  // 부동소수점 오차 문제
        
        // 좋은 예: compare() 사용
        return Double.compare(p.x, x) == 0 
            && Double.compare(p.y, y) == 0;
    }
}
```

### ✅ 배열 필드 비교

**배열은 `Arrays.equals()` 사용**:

```java
public class Matrix {
    private final int[][] data;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Matrix)) return false;
        Matrix m = (Matrix) o;
        return Arrays.deepEquals(data, m.data);  // 다차원 배열
        // 또는 Arrays.equals() - 1차원 배열
    }
}
```

### ✅ null 가능 참조 필드 비교

**`Objects.equals()` 사용 (null 안전)**:

```java
public class Person {
    private final String name;
    private final String email;  // null 가능

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Person)) return false;
        Person p = (Person) o;
        
        // 나쁜 예: null 체크 필요
        // return name.equals(p.name) && email.equals(p.email);
        
        // 좋은 예: Objects.equals() 사용
        return Objects.equals(name, p.name) 
            && Objects.equals(email, p.email);
    }
}
```

---

## 5️⃣ equals와 hashCode의 관계

### ⚠️ 중요: equals를 재정의하면 hashCode도 반드시 재정의하라

**equals만 재정의하면 안 되는 이유**:

```java
// hashCode를 재정의하지 않은 경우
public class PhoneNumber {
    private final short areaCode, prefix, lineNum;

    @Override
    public boolean equals(Object o) {
        // ... equals 구현
    }
    // hashCode 재정의 안 함!
}
```

**문제점**:

```java
Map<PhoneNumber, String> m = new HashMap<>();
m.put(new PhoneNumber(707, 867, 5309), "Jenny");

// 같은 객체인데 null 반환!
m.get(new PhoneNumber(707, 867, 5309));  // null
```

**이유**:

- `HashMap`은 `hashCode()`로 버킷을 찾음
- `hashCode()`가 다르면 다른 버킷에 저장
- 같은 버킷에 없으면 `equals()`를 호출하지도 않음

**✅ 해결**: `hashCode()`도 재정의 (아이템 11 참고)

---

## 6️⃣ equals 구현 시 주의사항

### ❌ 실수하기 쉬운 부분

1. **`equals(Object o)` 시그니처 오류**

   ```java
   // 나쁜 예: 타입을 구체 클래스로 지정
   public boolean equals(PhoneNumber pn) {  // 오버라이드가 아님!
       // ...
   }
   
   // 좋은 예: Object 타입 사용
   @Override
   public boolean equals(Object o) {
       // ...
   }
   ```

2. **`instanceof` 대신 `getClass()` 사용**

   ```java
   // 나쁜 예: 하위 클래스와 호환 불가
   if (o == null || o.getClass() != getClass()) return false;
   
   // 좋은 예: instanceof 사용
   if (!(o instanceof PhoneNumber)) return false;
   ```

3. **필드 비교 시 `==` 대신 `equals()` 사용**

   ```java
   // 나쁜 예: 참조 비교
   return name == pn.name;
   
   // 좋은 예: 값 비교
   return Objects.equals(name, pn.name);
   ```

---

## 7️⃣ 완전한 equals 구현 예시

```java
public final class PhoneNumber {
    private final short areaCode, prefix, lineNum;

    public PhoneNumber(int areaCode, int prefix, int lineNum) {
        this.areaCode = rangeCheck(areaCode, 999, "지역코드");
        this.prefix   = rangeCheck(prefix,   999, "프리픽스");
        this.lineNum  = rangeCheck(lineNum, 9999, "가입자 번호");
    }

    private static short rangeCheck(int val, int max, String arg) {
        if (val < 0 || val > max)
            throw new IllegalArgumentException(arg + ": " + val);
        return (short) val;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof PhoneNumber)) return false;
        PhoneNumber pn = (PhoneNumber) o;
        return pn.lineNum == lineNum && pn.prefix == prefix
                && pn.areaCode == areaCode;
    }

    // hashCode도 반드시 재정의해야 함 (아이템 11)
    @Override
    public int hashCode() {
        return Objects.hash(areaCode, prefix, lineNum);
    }
}
```

---

## 8️⃣ 자동 생성 도구 활용

### ✅ IDE 자동 생성

**IntelliJ IDEA / Eclipse**:

- `Alt + Insert` (IntelliJ) 또는 `Source > Generate`
- `equals() and hashCode()` 선택
- 필드 선택 후 자동 생성

**주의사항**:

- 자동 생성 코드도 검토 필요
- 복잡한 경우는 수동으로 작성하는 것이 나을 수 있음

### ✅ Lombok 사용

```java
@EqualsAndHashCode
public class PhoneNumber {
    private final short areaCode, prefix, lineNum;
    // equals와 hashCode 자동 생성
}
```

**장점**:

- 보일러플레이트 코드 제거
- 필드 추가 시 자동 업데이트

**단점**:

- 라이브러리 의존성 추가
- 디버깅 시 가독성 저하

---

## 9️⃣ 요약 체크리스트

### ✅ equals 재정의 전 확인

- [ ] 값 클래스인가? (논리적 동치성 검사 필요)
- [ ] 상위 클래스의 equals가 적절한가?
- [ ] 클래스가 private이고 equals 호출이 없는가?

### ✅ equals 구현 시 확인

- [ ] `equals(Object o)` 시그니처가 올바른가?
- [ ] `@Override` 어노테이션을 사용했는가?
- [ ] 반사성: `x.equals(x)`가 항상 true인가?
- [ ] 대칭성: `x.equals(y) == y.equals(x)`인가?
- [ ] 추이성: `x.equals(y) && y.equals(z)`면 `x.equals(z)`인가?
- [ ] 일관성: 반복 호출해도 같은 결과인가?
- [ ] null-아님: `x.equals(null)`이 항상 false인가?
- [ ] `hashCode()`도 재정의했는가?

### ✅ 구현 패턴

```java
@Override
public boolean equals(Object o) {
    // 1. 자기 자신 체크
    if (o == this) return true;
    
    // 2. 타입 체크
    if (!(o instanceof MyClass)) return false;
    
    // 3. 형변환
    MyClass that = (MyClass) o;
    
    // 4. 필드 비교 (성능 고려하여 순서 결정)
    return Objects.equals(field1, that.field1)
        && Objects.equals(field2, that.field2)
        && primitive1 == that.primitive1;
}
```

---

## 🔟 핵심 정리

1. **equals를 재정의하지 않아도 되는 경우가 많다**
   - 각 인스턴스가 고유한 경우
   - 논리적 동치성 검사가 불필요한 경우
   - 상위 클래스의 equals가 적절한 경우

2. **재정의할 때는 5가지 규약을 반드시 지켜야 한다**
   - 반사성, 대칭성, 추이성, 일관성, null-아님

3. **equals를 재정의하면 hashCode도 반드시 재정의하라**
   - `HashMap`, `HashSet` 등이 제대로 동작하지 않음

4. **구현 시 주의사항**
   - `equals(Object o)` 시그니처 유지
   - `instanceof` 사용 (getClass() 대신)
   - `Objects.equals()`로 null 안전 비교
   - 부동소수점은 `compare()` 사용

5. **자동 생성 도구 활용하되 검토는 필수**
   - IDE 자동 생성
   - Lombok `@EqualsAndHashCode`
