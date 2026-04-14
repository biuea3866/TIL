# Facade 패턴

퍼사드 패턴은 종류가 다른 여러 클래스들에 대한 의존을 한 곳으로 모으고, 이 클래스들의 로직들을 이용하여 비즈니스 흐름을 만들고 간단한 인터페이스로 제공하여 호출자에게 낮은 의존성을 제공한다.

여러 클래스들이 협력하는 복잡한 로직은 내부로 감추어 클라이언트는 호출만 하면 되므로 변경에 있어서 클라이언트의 의존도를 낮출 수 있다는 장점이 있다.

다만, 표현 계층에게 제공할 인터페이스를 제공하므로 facade는 다소 표현 계층에 의존적이라고 볼 수 있다.
이런 의존성은 표현 계층의 역할이 변경되면, 이에 맞추어 내부 구현이 변경될 가능성이 있다.

# 사례

## 간단한 인터페이스 제공 사례

### AS-IS

```kotlin
@RestController
class OrderController (
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val cartService: CartService
) {
    @PostMapping("/order")
    fun order(@RequestBody body: OrderBody) {
        val cart = cartService.fetchCartBy(body.userId)
        val order = orderService.order(body.userId, cart.productIds)
        paymentService.pay(order, body.userId)
    }
}

class OrderService(
    private val orderRepository: OrderJpaRepository
) {
    fun order(
        userId: Long,
        productIds: List<Long>
    ): Order {
        val order = Order.create(userId, productIds)
        return order
    }
}

class PaymentService(
    private val paymentRepository: PaymentJpaRepository
) {
    fun pay(
        order: Order,
        userId: Int
    ) {
        val payment = payment.findByUserId(userId)
            ?: throw BadRequestException()
        payment.pay(order)
    }
}

class CartService(
    private val cartRepository: CartJpaRepository
) {
    fun fetchCartBy(userId: Long): Cart {
        return cartRepository.findByUserId(cart)
            ?: throw BadRequestException()
    }
}
```

예시에서의 코드는 표현 계층 (Controller)에서는 주문을 수행하기 위해 3개의 서비스에 대한 의존하고, 그에 대한 비즈니스의 흐름도 책임지게 된다.

즉, 컨트롤러는 주문을 위해 어떤 인터페이스를 호출해야하는지를 알아야 한다.
또한 주문에 추가적인 요구사항이 생긴다면 표현 계층에도 변경이 생기고, 다른 표현 계층에서 동일한 주문 비즈니스 흐름을 가진다면(가령 컨슈머) 마찬가지로 변경이 되어야 하는 문제점이 생긴다. (재활용이 떨어짐)

### TO-BE

```kotlin
@RestController
class OrderController (
    private val orderFacade: OrderFacade
) {
    @PostMapping("/order")
    fun order(@RequestBody body: OrderBody) {
        orderFacade.order(body.userId)
    }
}

// 신규 표현 계층 추가
class OrderConsumer(
    private val orderFacade: OrderFacade
) {
    @KafkaListener(
        topics = ["retry.order"]
    )
    fun retryOrder(orderPayload: OrderPayload) {
        orderFacade.order(orderPayload.userId)
    }
}

class OrderFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val cartService: CartService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun order(userId: Long) {
        val cart = cartService.fetchCartBy(body.userId)
        val order = orderService.order(body.userId, cart.productIds)
        paymentService.pay(order, body.userId)
        // 요구사항: 주문 로그를 쌓아주세요
        applicationEventPublisher.publishEvent(OrderEvent(order))
    }
}

class OrderService(
    private val orderRepository: OrderJpaRepository
) {
    fun order(
        userId: Long,
        productIds: List<Long>
    ): Order {
        val order = Order.create(userId, productIds)
        return order
    }
}

class PaymentService(
    private val paymentRepository: PaymentJpaRepository
) {
    fun pay(
        order: Order,
        userId: Int
    ) {
        val payment = payment.findByUserId(userId)
            ?: throw BadRequestException()
        payment.pay(order)
    }
}

class CartService(
    private val cartRepository: CartJpaRepository
) {
    fun fetchCartBy(userId: Long): Cart {
        return cartRepository.findByUserId(cart)
            ?: throw BadRequestException()
    }
}
```

Controller의 비즈니스 흐름을 OrderFacade로 옮긴 사례이다.
단순 OrderFacade로 옮겼을 뿐이고, Controller에서는 OrderFacade::order만 호출하도록 변경하였다.

1. 컨트롤러는 OrderFacade의 order 인터페이스만 의존함으로써 의존성이 낮아지게 되었다.
2. 신규 요구사항이 생기더라도 OrderFacade만 변경할 뿐이고, 컨트롤러에 영향을 주지 않는다.
3. 신규 표현 계층이 주문 비즈니스를 처리하기 위해 OrderFacade를 호출하여 재활용이 증가하게 되었다.

## 표현 계층 의존 사례

### AS-IS

```kotlin
@RestController
class OrderController (
    private val orderFacade: OrderFacade
) {
    @GetMapping("/order")
    fun ordersView(@RequestHeader("X-User-Id") userId: Long): List<OrderedView> {
        return orderFacade.getOrders(body.userId)
    }
}

class OrderFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {
    fun ordered(userId: Long): List<OrderedView> {
        val orders = orderService.getOrders(userId)
        val payment = paymentService.getPayment(userId)

        return orders.map {
            OrderedView(
                orderId = it.id,
                orderName = it.name,
                productNames = it.productNames,
                fee = payment.fetchOrderFeeBy(it.id)
            )
        }
    }
}

class OrderService(
    private val orderRepository: OrderJpaRepository
) {
    fun ordered(userId: Long): List<Order> {
        return orderRepository.findAllByUserId(userId)
    }
}

class PaymentService(
    private val paymentRepository: PaymentJpaRepository
) {
    fun getPayment(userId: Int): Payment {
        return payment.findByUserId(userId)
            ?: throw BadRequestException()
    }
}

data class OrderedView(
    val orderId: Long,
    val orderName: String,
    val productNames: List<String>,
    val fee: Long
)
```

표현계층에서 주문 조회에 대한 책임을 가진 OrderFacade가 존재하고, 이를 의존하여 인터페이스를 호출함에 따라 표현계층은 주문 조회를 위해 어떤 서비스를 참조해야하는지 등에 대한 필요성이 사라지게 되었다.

하지만 OrderFacade의 입장에서 보았을 때 OrderedView는 표현 계층을 위한 인터페이스라고 볼 수 있다.
어떤 주문 내역이 있는지를 클라이언트에게 제공하기 위해 전용으로 구성되었으므로 이는 표현 계층에 논리적으로 의존적이다라고 볼 수 있다.

이렇게 되면 해당 인터페이스는 한 곳에서 밖에 사용할 수 없으며, 재활용성이 저하되고 비슷한 요구사항 (가령 orderName은 앞으로 제품에서 사용하지 않을 것)이 들어온다면 수많은 변경사항이 발생할 우려가 생긴다.

### TO-BE

```kotlin
@RestController
class OrderController (
    private val orderFacade: OrderFacade
) {
    @GetMapping("/order")
    fun ordersView(@RequestHeader("X-User-Id") userId: Long): List<OrderedView> {
        return orderFacade.getOrders(userId)
    }

    @GetMapping("/order/{orderId}")
    fun orderView(@RequestHeader("X-User-Id") userId: Long): OrderView {
        return orderFacade.getOrder(userId)
    }
}

class OrderFacade(
    private val orderService: OrderService,
    private val paymentService: PaymentService,
) {
    fun getOrders(userId: Long): List<OrderedView> {
        val orders = orderService.getOrders(userId)
        return orders.map {
            OrderedView(
                orderId = it.id,
                orderName = it.name,
                productNames = it.productNames,
                fee = payment.fetchOrderFeeBy(it.id)
            )
        }
    }

    fun getOrder(userId): OrderView {
        val order = orderService.getOrder(userId)
        val payment = paymentService.getPayment(userId)

        return OrderedView(
            orderId = it.id,
            orderName = it.name,
            productNames = it.productNames,
            fee = payment.fetchOrderFeeBy(it.id),
            paidAt = payment.fetchOrderPaidAt(it.id)
        )
    }
}

class OrderService(
    private val orderRepository: OrderJpaRepository
) {
    fun getOrders(userId: Long): List<Order> {
        return orderRepository.findAllByUserId(userId)
    }

    fun getOrder(userId: Long): Order {
        return orderRepository.findAllByUserId(userId).first()
    }
}

class PaymentService(
    private val paymentRepository: PaymentJpaRepository
) {
    fun getPayment(userId: Int): Payment {
        return payment.findByUserId(userId)
            ?: throw BadRequestException()
    }
}

data class OrderedView(
    val orderId: Long,
    val orderName: String,
    val productNames: List<String>,
    val fee: Long
)

data class OrderView(
    val orderId: Long,
    val orderName: String,
    val productNames: List<String>,
    val fee: Long,
    val paidAt: ZonedDateTime
)
```

Facade가 표현 계층에 의존적일 경우 비슷한 응답값을 반환하지만 뷰에 따라 미묘하게 다를 수 있다.
핏하게 맞춰야 하는 경우라면 위와 같이 재활용이 불가능한 형태로 뷰에 맞게 응답 인터페이스를 작성할 필요가 생긴다.

또한 orderName은 제품에서 빼기로 했다는 요구사항이 생기게 되면 orderName을 참조하는 모든 인터페이스에 대한 변경이 생기게 된다.
이런 변경은 누락 사항을 유발할 수 있으며, 버그를 발생시킬 수도 있다.

---

# 정리

## Facade 패턴 적용 기준

| 상황 | Facade 사용 | 직접 호출 |
|------|:-----------:|:--------:|
| 여러 서비스가 협력하는 비즈니스 흐름 | O | |
| 여러 표현 계층에서 동일한 흐름 재사용 | O | |
| 단일 서비스만 호출하는 단순 조회 | | O |
| 표현 계층에 특화된 응답 변환만 필요 | | O |

## 관련 패턴

* **Mediator 패턴**: Facade가 단방향 단순화라면, Mediator는 객체 간 양방향 통신을 중재
* **Adapter 패턴**: 인터페이스를 변환하는 것이 목적. Facade는 복잡한 것을 단순화하는 것이 목적
* **Application Service (DDD)**: DDD에서의 Application Service가 사실상 Facade 역할을 수행
