# String, StringBuffer, StringBuilder

String은 내부 byte 배열이 불변으로 되어있고, 새로운 값으로 변경할 때는 새로운 String 인스턴스를 만들어 이전에 사용했던 인스턴스는 GC의 대상이 되어버리게 한다.

StringBuffer, StringBuilder는 값을 담는 내부 byte 배열이 불변이 아닌 가변으로 되어있어 내부 상태를 업데이트할 수 있는 객체이다.
새로운 값으로 변경할 때는 계속해서 기존 인스턴스를 활용하여 내부의 상태를 업데이트하는 방식이다.
즉, 멀티 스레드 환경에서는 여러 스레드들이 인스턴스를 참조하여 상태를 업데이트하기 때문에 Thread Safe한 객체인지 확인할 필요가 있다.

StringBuffer는 내부적으로 synchronized를 이용하여 다른 스레드의 접근을 제어한다.
하지만 StringBuilder는 이런 Mutex가 없기 때문에 여러 스레드가 동시에 접근하여 내부 상태를 업데이트한다.
성능면에서 스레드 접근 제어가 없는 StringBuilder가 월등이 빠르겠지만, 값의 일관성과 원자성이 보장되지 못한다는 점에서 원자성이 보장되는 StringBuffer를 사용하는 것이 좋다.

---

## 비교 요약

| 항목 | String | StringBuffer | StringBuilder |
|------|--------|-------------|---------------|
| 가변성 | 불변 (Immutable) | 가변 (Mutable) | 가변 (Mutable) |
| Thread-Safe | O (불변이므로) | O (synchronized) | X |
| 성능 | 문자열 조합 시 느림 | 중간 | 빠름 |
| 메모리 | 변경 시 새 인스턴스 생성 → GC 부담 | 내부 배열 재사용 | 내부 배열 재사용 |

## 내부 구조

```java
// String - 불변
public final class String {
    private final byte[] value;  // final → 변경 불가
}

// StringBuilder - 가변
public final class StringBuilder extends AbstractStringBuilder {
    byte[] value;  // final 아님 → 변경 가능
    int count;     // 현재 문자열 길이
}

// StringBuffer - 가변 + synchronized
public final class StringBuffer extends AbstractStringBuilder {
    @Override
    public synchronized StringBuffer append(String str) {
        // 동기화 처리
        super.append(str);
        return this;
    }
}
```

## String의 + 연산

```java
// 컴파일러가 내부적으로 StringBuilder로 변환 (Java 9+에서는 invokedynamic으로 최적화)
String result = "Hello" + " " + "World";

// 하지만 반복문 내에서는 매번 새로운 StringBuilder가 생성될 수 있음
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i;  // 매 반복마다 StringBuilder 생성 → 비효율
}

// 반복문에서는 명시적으로 StringBuilder 사용
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);
}
String result = sb.toString();
```

## 사용 기준

* **String**: 문자열 변경이 거의 없는 경우, 상수 문자열
* **StringBuilder**: 단일 스레드에서 문자열을 빈번하게 조작하는 경우 (대부분의 경우)
* **StringBuffer**: 멀티 스레드 환경에서 공유되는 문자열을 조작하는 경우 (드문 케이스)

## String Pool

```java
String s1 = "Hello";        // String Pool에 저장
String s2 = "Hello";        // Pool에서 재사용 (같은 참조)
String s3 = new String("Hello");  // 힙에 새로 생성 (다른 참조)

s1 == s2;      // true  (같은 Pool 참조)
s1 == s3;      // false (다른 참조)
s1.equals(s3); // true  (값 비교)
```

* 리터럴로 생성한 String은 Heap 내 String Pool 영역에 저장되어 재사용
* `new String()`으로 생성하면 Pool을 거치지 않고 힙에 새 인스턴스 생성
* `intern()` 메서드로 힙의 String을 Pool로 등록 가능
