package com.biuea.practice.movie_assign_responsibility

import java.time.Duration

class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _movieType: MovieType,
    private var _discountConditions: List<DiscountCondition>,
    private var _discountAmount: Money,
    private var _discountPercent: Double
) {
    val fee get() = this._fee

    fun calculateMovieFee(screening: Screening): Money {
        if (this.isDiscountable(screening)) {
            return this._fee.minus(this.calculateDiscountAmount())
        }

        return this._fee
    }

    private fun isDiscountable(screening: Screening): Boolean {
        return _discountConditions.any { it.isSatisfiedBy(screening) }
    }

    private fun calculateDiscountAmount(): Money {
        return Money.ZERO
    }
}

enum class MovieType {
    AMOUNT_DISCOUNT,
    PERCENT_DISCOUNT,
    NONE_DISCOUNT
}