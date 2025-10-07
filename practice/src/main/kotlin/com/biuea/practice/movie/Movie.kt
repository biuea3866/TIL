package com.biuea.practice.movie

import java.time.Duration

class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _discountPolicy: DiscountPolicy
) {
    val fee get() = this._fee

    fun calculateMovieFee(screening: Screening): Money {
        return this._fee.minus(_discountPolicy.calculateDiscountAmount(screening))
    }
}