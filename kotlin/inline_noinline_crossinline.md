# inline, noinline, crossinline, reified

## 1. inline

---
inline 키워드는 함수를 호출하는 대신 컴파일러에게 호출 함수의 본문을 호출 지점에 직접 복사하라는 키워드입니다.

### 언제 사용? 
특히 고차 함수로 람다를 인자로 받을 때 람다는 익명 클래스로 컴파일되는데, 결과적으로 매 호출마다 인스턴스를 메모리에 할당되게 됩니다. 하지만 inline은 객체를 생성하지 않고, 본문을 그대로 사용하여 객체 생성 비용을 줄일 수 있습니다. 

### 예제
```kotlin
// inline 없을 때
fun main() {
    execute { println("작업중") }
}

fun execute(action: () -> Unit) {
    println("작업 시작")
    action()
    println("작업 종료")
}

public final class InlineKt {
   public static final void main() {
      execute(null.INSTANCE); // 인스턴스 생성
   }

   public static final void execute(@NotNull Function0 action) {
      Intrinsics.checkNotNullParameter(action, "action");
      System.out.println("작업 시작");
      action.invoke();
      System.out.println("작업 종료");
   }

   // $FF: synthetic method
   public static void main(String[] args) {
      main();
   }
}

// inline 적용
fun main() {
    execute { println("작업중") }
}

inline fun execute(action: () -> Unit) {
    println("작업 시작")
    action()
    println("작업 종료")
}

public final class InlineKt {
   public static final void main() {
      int $i$f$execute = 0;
      System.out.println("작업 시작");
      int var1 = 0;
      System.out.println("작업중"); // 인스턴스를 생성하지 않고, 본문 내용을 가져 옴
      System.out.println("작업 종료");
   }

   public static final void execute(@NotNull Function0 action) {
      Intrinsics.checkNotNullParameter(action, "action");
      int $i$f$execute = 0;
      System.out.println("작업 시작");
      action.invoke();
      System.out.println("작업 종료");
   }

   // $FF: synthetic method
   public static void main(String[] args) {
      main();
   }
}
```

## 2. noinline

---
inline 함수에서 여러 람다를 인자로 받을 때 특정 람다만 인라인화하고 싶지 않은 경우에 사용됩니다.  

### 언제 사용?
여러 고차 함수를 전달 받을 수 있는데, 이 중 일부가 inline되었을 때 코드가 비대화되는 걸 방지할 때 사용합니다. 

### 예제
```kotlin
public final class InlineKt {
   public static final void main() {
      Function0 action2$iv = null.INSTANCE; // action2는 람다 객체 생성
      int $i$f$noInlineExecute = 0;
      int a$iv = 1;
      System.out.println("작업 시작");
      int var3 = 0;
      System.out.println("1번 작업중"); // action1은 inline
      action2$iv.invoke();
      System.out.println("작업 종료");
   }

   public static final int noInlineExecute(@NotNull Function0 action1, @NotNull Function0 action2) {
      Intrinsics.checkNotNullParameter(action1, "action1");
      Intrinsics.checkNotNullParameter(action2, "action2");
      int $i$f$noInlineExecute = 0;
      int a = 1;
      System.out.println("작업 시작");
      action1.invoke();
      action2.invoke();
      System.out.println("작업 종료");
      return a;
   }

   // $FF: synthetic method
   public static void main(String[] args) {
      main();
   }
}
```

## 3. crossinline

---
inline 함수 내부에서 전달받은 람다를 다른 스레드(비동기)에서 실행할 때 사용합니다.

### 언제 사용?
inline은 호출 내용이 복사되기 때문에, 호출 함수에서의 return(지역 반환)이 적용되지 않습니다. 즉, inline을 이용할 경우 inline 함수의 return이 외부 함수를 종료시킬 수 있습니다. crossinline은 return을 강제로 막아 이 문제를 해결해줍니다.

### 예제
![스크린샷 2026-01-30 18.31.52.png](../img/kotlin/%E1%84%89%E1%85%B3%E1%84%8F%E1%85%B3%E1%84%85%E1%85%B5%E1%86%AB%E1%84%89%E1%85%A3%E1%86%BA%202026-01-30%2018.31.52.png)

```kotlin
public final class InlineKt {
   public static final void main() {
      int $i$f$crossInlineExecute = 0;
      new InlineKt$main$$inlined$crossInlineExecute$1();
   }

   public static final int action() {
      return 1;
   }

   public static final void crossInlineExecute(@NotNull final Function0 action) {
      Intrinsics.checkNotNullParameter(action, "action");
      int $i$f$crossInlineExecute = 0;
      Runnable var10001 = new Runnable() {
         public final void run() {
            action.invoke();
         }
      };
   }

   // $FF: synthetic method
   public static void main(String[] args) {
      main();
   }
}

// InlineKt$main$$inlined$crossInlineExecute$1.java
package com.biuea.practice.inline;

import kotlin.Metadata;
import kotlin.jvm.internal.SourceDebugExtension;

public final class InlineKt$main$$inlined$crossInlineExecute$1 implements Runnable {
   public final void run() {
      int var1 = 0;
      InlineKt.action();
   }
}
```

그래서 crossinline이 붙는 고차함수는 새로운 객체 (`InlineKt$main$$inlined$crossInlineExecute$1`)를 만들어서 지역적으로 사용되게 컴파일되고, 비지역 반환을 방지합니다.

## 4. reified

---
일반적인 제네릭은 컴파일 시점에 타입 정보가 없기 떄문에 런타임엔 타입 추론이 불가합니다. 그래서 클래스 정보만으로 제네릭 함수 내에선 어떤 타입인지 추론이 불가합니다.    
하지만 reified와 inline의 조합은 

### 언제 사용?

### 예제
```kotlin
fun main() {
    val list = listOf("asd", 1, '1')
    println(list.filter { it.isType<String>() })
}

inline fun<reified T> Any.isType(): Boolean {
    return this is T
}

---
public final class InlineKt {
    public static final void main() {
        Object[] var1 = new Object[]{"asd", 1, '1'};
        List list = CollectionsKt.listOf(var1);
        Iterable $this$filter$iv = (Iterable)list;
        int $i$f$filter = 0;
        Collection destination$iv$iv = (Collection)(new ArrayList());
        int $i$f$filterTo = 0;

        for(Object element$iv$iv : $this$filter$iv) {
            int var9 = 0;
            int $i$f$isType = 0;
            if (element$iv$iv instanceof String) { // type is T가 인라인 함수로 타입추론(String)이 가능해짐
                destination$iv$iv.add(element$iv$iv);
            }
        }

        List var13 = (List)destination$iv$iv;
        System.out.println(var13);
    }

    // $FF: synthetic method
    public static final boolean isType(Object $this$isType) {
        Intrinsics.checkNotNullParameter($this$isType, "<this>");
        int $i$f$isType = 0;
        Intrinsics.reifiedOperationMarker(3, "T");
        return $this$isType instanceof Object;
    }

    // $FF: synthetic method
    public static void main(String[] args) {
        main();
    }
}
```

인라인 함수가 호출되는 곳에서 T가 어떤 것인지 알 수 있기 때문에 실제 reified 함수 내에선 T::class.java와 같은 형태로 클래스 정보를 이용할 수 있다.   

## 5. 요약

| 키워드    |사용 타이밍|예시|효과
|--------|--|--|--|
| inline |고차 함수(람다)를 만들 때 고려| lock { .. }, filter { .. }와 같은 곳에 고차함수를 사용할 때|람다 객체 생성 방지, 비지역 반환 허용|
|noinlie|인라인 함수에 받은 람다를 저장하거나 전달할 때 |람다를 등록해야하거나, 람다를 리스트에 담을 때|특정 람다만 객체로 유지하여 인라인 제약을 벗어남|
|crossinline|람다가 다른 스레드나 객체 내부에서 실행될 때|database.funAsync { callback() }과 같이 비동기 스레드 작업이 발상할 때 |의도치 않은 return으로 상위 함수 종료 방지|
|reified|런타임에 제네릭 타입이 무엇인지 알아야할 뗴|json.toObject<User>()와 같이 클래스 타입이 필요할 때|T::class 사용 가능, 타입 캐스팅(체크) 사용가능|

### inline
```kotlin
// action 람다 객체를 생성하지 않고, 직접 변환하여 사용
inline fun fastEach(times: Int, action: (Int) -> Unit) {
    for (i in 0 until times) action(i)
}
```

### noinline
```kotlin
val callbacks = mutableList<() -> Unit>()

inline fun register(action: () -> Unit, noinline onAdded: () -> Unit) {
    action()
    callbacks.add(onAdded) // noinline이 없으면 onAdded는 직접 변환되기 때문에 담지 못함
}
```

### crossinline
```kotlin
inline fun doAsync(crossinline callback: () -> Unit) {
    val task = Runnable {
        callback() // 인라인 함수내에서 직접 변환되면 다른 스레드가 return으로 인해 종료될 수 있으므로 crossinline으로 방지 
    }
    
    Thread(task).start()
}
```

### reified
```kotlin
inline fun <reified T> List<Any>.filterBy(): List<T> {
    return this.filterIsInstance<T>() // T가 무엇인지 추론이 가능하여 런타임에 사용 가능
}
```