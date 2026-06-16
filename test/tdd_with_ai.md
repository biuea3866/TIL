# [TDD] TDD와 AI

## 테스트

---

소프트웨어에서 테스트는 코드가 의도한 대로 동작하는지를 확인하는 작업이다. 메서드에 입력값을 전달하면 예상되는 출력값이 반환되는지 검증한다. 내부 구현이 잘못되면 의도하지 않은 결과를 받게 되고, 개발자는 이를 수정하여 올바른 결과를 도출한다.

코드의 올바른 동작 검증은 소프트웨어의 품질을 향상시킨다. 균일한 품질의 소프트웨어는 신뢰성이 높아지고, 결국 시장에서 사용자가 선택하는 요소가 된다.

### TDD가 풀려는 문제

테스트 없이 개발하면 당장은 작동하지만 버그와 고객 문의가 발생하여 지속적으로 핫픽스 코드를 생산하게 된다. 이는 고객 신뢰도를 떨어뜨린다.

TDD는 이러한 불안정한 흐름을 해결하는 방법론이다. 요구사항 명세를 먼저 정의한 후 구현한다. 테스트 코드라는 변하지 않는 인터페이스가 있으므로 개발자는 리팩토링을 통해 내부 구현을 쉽게 변경할 수 있으며, 안정적인 소프트웨어를 만들 수 있다.

## Red-Green-Refactor

---

TDD는 Red, Green, Refactor 세 단계를 반복하며 진행한다. 실패하는 테스트로 행동을 먼저 정의하고, 그 테스트를 통과시킨 뒤, 테스트가 보호하는 상태에서 내부 구현을 완성하는 흐름이다.

### Red

테스트가 반드시 실패해야 한다. 컴파일 에러도 실패의 한 형태이며, 미구현된 객체를 사용하여 컴파일 에러를 발생시킨다. Red 단계에서는 어떤 행동들이 있는지를 정의하며, 구현하지 않고 행동만 기술한다.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CalculatorTest : FunSpec({
    test("1 + 2 = 3") {
        Calculator().add(1, 2) shouldBe 3
    }
})
```

### Green

테스트를 빠르게 통과시키는 방법을 선택한다. 구현 품질에 초점을 맞추지 않고, 테스트 통과가 주목표이다.

```kotlin
class Calculator {
    fun add(a: Int, b: Int): Int = 3
}
```

### Refactor

모든 테스트가 통과되었고 테스트 명세가 완성되었다. 이제 테스트는 변하지 않는 인터페이스가 되며, 개발자는 내부 구현을 완성시킬 수 있다. 기존 동작을 보호하면서 내부 구조를 개선할 수 있다.

```kotlin
class Calculator {
    fun add(a: Int, b: Int): Int = a + b
}
```

## TDD와 AI

---

AI는 항상 균일한 품질을 제공하지 않는다. 같은 프롬프트에도 다른 출력이 나올 수 있으며, 모델 업데이트 후 코드 생성 패턴이 변할 수 있다.

| 문제점 | 의미 | 사례 |
|--------|------|------|
| 확률적 출력 | 같은 입력에도 출력이 미세하게 달라짐 | 메서드 이름이 `findById`에서 `getById`로 변경 |
| 환각(Hallucination) | 존재하지 않는 API/라이브러리를 생성 | `Optional.orElseThrowIfEmpty()` 같은 가짜 메서드 |
| 컨텍스트 손실 | 시스템 전체를 모르고 일반적 구현으로 채움 | 도메인 규칙을 무시한 구현 |
| 모델 회귀 | 업데이트 후 다른 결과 생성 | 모델 버전 변경 시 코드 스타일 변화 |

이 문제들의 공통점은 AI 코드가 의도대로 동작하는지 사람이 확인해야 한다는 점이다. 자동화된 테스트가 이를 해결할 수 있다. 테스트는 결정적이므로 균일한 코드를 검증할 수 있다.

### AI가 놓치는 도메인 불변식

다음 코드는 컴파일을 통과하지만 이미 사용된 쿠폰이나 만료된 쿠폰도 허용한다. AI는 도메인의 불변식을 알지 못했기 때문이다.

```kotlin
class CouponService(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository,
) {
    fun useCoupon(userId: Long, couponId: Long) {
        val coupon = couponRepository.findById(couponId).get()
        val user = userRepository.findById(userId).get()

        coupon.status = CouponStatus.USED
        user.point += coupon.discountAmount
    }
}
```

### TDD로 AI 통제

AI에게 코드를 작성시킬 때 가장 강력한 통제 수단은 테스트를 먼저 정의하는 것이다. 사람이 도메인 규칙을 테스트로 기술하고, AI는 그 테스트를 통과시키는 구현만 만들면 된다. 단계별로 사람과 AI의 책임을 나누면 다음과 같다.

| 단계 | 담당 | 무엇을 |
|------|------|--------|
| Red | 사람 | 도메인 규칙·불변식·엣지 케이스를 테스트 코드로 표현 |
| Green | AI | 그 테스트만 통과시키는 최소 구현 생성 |
| Refactor | 사람과 AI | 테스트가 보호하는 상태에서 내부 구조 개선 |

먼저 사람이 도메인 규칙을 테스트로 표현한다.

```kotlin
class CouponServiceTest : FunSpec({
    test("사용된 쿠폰을 다시 사용하면 예외가 발생한다") {
        val coupon = Coupon(id = 1L, status = CouponStatus.USED, discountAmount = 1000)
        val service = CouponService(FakeCouponRepository(coupon), FakeUserRepository())

        shouldThrow<AlreadyUsedCouponException> {
            service.useCoupon(userId = 1L, couponId = 1L)
        }
    }

    test("만료된 쿠폰은 사용할 수 없다") {
        val expired = Coupon(
            id = 1L,
            status = CouponStatus.ACTIVE,
            discountAmount = 1000,
            expiresAt = ZonedDateTime.now().minusDays(1),
        )
        val service = CouponService(FakeCouponRepository(expired), FakeUserRepository())

        shouldThrow<ExpiredCouponException> {
            service.useCoupon(userId = 1L, couponId = 1L)
        }
    }

    test("정상 쿠폰을 사용하면 상태가 USED 로 바뀌고 유저에게 포인트가 적립된다") {
        val active = Coupon(id = 1L, status = CouponStatus.ACTIVE, discountAmount = 1000)
        val user = User(id = 1L, point = 0)
        val service = CouponService(FakeCouponRepository(active), FakeUserRepository(user))

        service.useCoupon(userId = 1L, couponId = 1L)

        active.status shouldBe CouponStatus.USED
        user.point shouldBe 1000
    }
})
```

이 테스트를 기준으로 AI는 도메인 규칙이 모두 반영된 구현을 생성한다.

```kotlin
class CouponService(
    private val couponRepository: CouponRepository,
    private val userRepository: UserRepository,
) {
    fun useCoupon(userId: Long, couponId: Long) {
        val coupon = couponRepository.findById(couponId)
            ?: throw CouponNotFoundException(couponId)

        if (coupon.status == CouponStatus.USED) {
            throw AlreadyUsedCouponException(couponId)
        }
        if (coupon.isExpired()) {
            throw ExpiredCouponException(couponId)
        }

        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        coupon.status = CouponStatus.USED
        user.point += coupon.discountAmount
    }
}
```

테스트가 명세 역할을 하면서 도메인 규칙들이 모두 반영된 구현이 생성된다. 결국 테스트는 AI의 확률적 출력을 가두는 결정적인 안전망이 된다.
