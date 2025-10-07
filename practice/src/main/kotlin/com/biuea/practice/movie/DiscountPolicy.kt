package com.biuea.practice.movie

abstract class DiscountPolicy(
    private val conditions: MutableList<DiscountCondition> = mutableListOf()
) {
    fun calculateDiscountAmount(screening: Screening): Money {
        this.conditions.forEach {
            if (it.isSatisfiedBy(screening)) {
                return this.getDiscountAmount(screening)
            }
        }

        return Money.ZERO
    }

    protected abstract fun getDiscountAmount(screening: Screening): Money
}

class AmountDiscountPolicy(
    private var discountAmount: Money,
    private var conditions: MutableList<DiscountCondition>
): DiscountPolicy(conditions) {
    override fun getDiscountAmount(screening: Screening): Money {
        return this.discountAmount
    }
}

class PercentDiscountPolicy(
    private var percent: Double,
    private var conditions: MutableList<DiscountCondition>
): DiscountPolicy(conditions) {
    override fun getDiscountAmount(screening: Screening): Money {
        return screening.movieFee.times(percent)
    }
}

class NoneDiscountPolicy: DiscountPolicy() {
    override fun getDiscountAmount(screening: Screening): Money {
        return Money.ZERO
    }
}