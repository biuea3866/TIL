package com.biuea.practice.movie_data_oriented

import com.biuea.practice.movie.Money
import java.time.Duration
import java.time.LocalDateTime

class Movie(
    private var _title: String,
    private var _runningTime: Duration,
    private var _fee: Money,
    private var _discountConditions: List<DiscountCondition>,
    private var _movieType: MovieType,
    private var _discountPercent: Double,
    private var _discountAmount: Money
) {
    val title: String get() = this._title
    val runningTime: Duration get() = this._runningTime
    val fee: Money get() = this._fee
    val discountConditions: List<DiscountCondition> get() = this._discountConditions
    val movieType: MovieType get() = this._movieType
    val discountPercent: Double get() = this._discountPercent
    val discountAmount: Money get() = this._discountAmount

    fun setTitle(title: String) {
        this._title = title
    }

    fun setRunningTime(runningTime: Duration) {
        this._runningTime = runningTime
    }

    fun setFee(fee: Money) {
        this._fee = fee
    }

    fun setDiscountConditions(discountConditions: List<DiscountCondition>) {
        this._discountConditions = discountConditions
    }

    fun setMovieType(movieType: MovieType) {
        this._movieType = movieType
    }

    fun setDiscountPercent(discountPercent: Double) {
        this._discountPercent = discountPercent
    }

    fun setDiscountAmount(discountAmount: Money) {
        this._discountAmount = discountAmount
    }

    fun calculateAmountDiscountedFee(): Money {
        if (this._movieType != MovieType.AMOUNT_DISCOUNT) throw IllegalArgumentException()

        return this._fee.minus(this._discountAmount)
    }

    fun calculatePercentDiscountedFee(): Money {
        if (this._movieType != MovieType.PERCENT_DISCOUNT) throw IllegalArgumentException()

        return this._fee.minus(this._fee.times(this._discountPercent))
    }

    fun calculateNoneDiscountedFee(): Money {
        if (this._movieType != MovieType.NONE_DISCOUNT) throw IllegalArgumentException()

        return this._fee
    }

    fun isDiscountable(whenScreened: LocalDateTime, sequence: Int): Boolean {
        for(condition: DiscountCondition in discountConditions) {
            if (condition.type == DiscountConditionType.PERIOD) {
                if (condition.isDiscountable(whenScreened.dayOfWeek, whenScreened)) {
                    return true
                }
            } else {
                if (condition.isDiscountable(sequence)) {
                    return true
                }
            }
        }

        return false
    }
}

enum class MovieType {
    AMOUNT_DISCOUNT,
    PERCENT_DISCOUNT,
    NONE_DISCOUNT
}