# 이펙티브 자바 3판 — 아이템 11 ~ 20 정리

## 아이템 11. equals를 재정의하려거든 hashCode도 재정의하라
- equals가 같으면 hashCode도 같아야 한다
- 해시 기반 컬렉션(HashMap, HashSet)의 필수 규약

```java
@Override
public int hashCode() {
    return Objects.hash(field1, field2);
}
```

## 아이템 12. toString을 항상 재정의하라
- 기본 toString은 의미 없음
- 객체의 핵심 상태를 사람이 읽을 수 있게 표현

```java
@Override
public String toString() {
    return "User{name='" + name + "', age=" + age + "}";
}
```

## 아이템 13. clone 재정의는 주의해서 진행하라
- Cloneable은 설계 결함 존재
- 복사 생성자 또는 복사 팩터리 권장

```java
public User(User other) {
    this.name = other.name;
    this.age = other.age;
}
```

## 아이템 14. Comparable을 구현할지 고려하라
- 자연스러운 정렬 기준이 있다면 구현
- compareTo는 equals와 일관성 유지

```java
@Override
public int compareTo(User o) {
    return Integer.compare(this.age, o.age);
}
```

## 아이템 15. 클래스와 멤버의 접근 권한을 최소화하라
- 정보 은닉은 객체지향의 핵심
- 가능한 가장 좁은 접근 수준 사용

## 아이템 16. public 클래스에서는 public 필드가 아닌 접근자 메서드를 사용하라
```java
private int x;

public int getX() {
    return x;
}
```

## 아이템 17. 변경 가능성을 최소화하라 (불변 클래스)
- 불변 객체는 스레드 안전
- 디버깅과 유지보수가 쉬움

```java
public final class Money {
    private final int amount;
}
```

## 아이템 18. 상속보다는 컴포지션을 사용하라
- 상속은 캡슐화 붕괴 위험
- 포함 관계(컴포지션)가 더 안전

```java
class MySet<E> {
    private final Set<E> set;
}
```

## 아이템 19. 상속을 고려해 설계하고 문서화하라
- 상속용 클래스는 내부 동작 문서화 필수
- 아니면 final로 상속 제한

## 아이템 20. 추상 클래스보다는 인터페이스를 우선하라
- 다중 구현 가능
- 유연한 기능 확장 가능

```java
public interface Flyable {
    void fly();
}
```
