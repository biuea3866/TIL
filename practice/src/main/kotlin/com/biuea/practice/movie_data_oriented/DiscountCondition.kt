package com.biuea.practice.movie_data_oriented

import java.time.DayOfWeek
import java.time.LocalDateTime

class DiscountCondition(
    private var _type: DiscountConditionType,
    private var _sequence: Int,
    private var _dayOfWeek: DayOfWeek,
    private var _startTime: LocalDateTime,
    private var _endTime: LocalDateTime,
) {
    val type get() = this._type
    val sequence get() = this._sequence
    val dayOfWeek get() = this._dayOfWeek
    val startTime get() = this._startTime
    val endTime get() = this._endTime

    fun setType(type: DiscountConditionType) {
        this._type = type
    }

    fun setSequence(sequence: Int) {
        this._sequence = sequence
    }

    fun setDayOfWeek(dayOfWeek: DayOfWeek) {
        this._dayOfWeek = dayOfWeek
    }

    fun setStartTime(startTime: LocalDateTime) {
        this._startTime = startTime
    }

    fun setEndTime(endTime: LocalDateTime) {
        this._endTime = endTime
    }

    fun isDiscountable(
        dayOfWeek: DayOfWeek,
        time: LocalDateTime
    ): Boolean {
        if (this._type != DiscountConditionType.PERIOD) {
            throw IllegalArgumentException()
        }

        return dayOfWeek == this._dayOfWeek &&
                    this._startTime <= time &&
                    this._endTime >= time
    }

    fun isDiscountable(sequence: Int): Boolean {
        if (this._type == DiscountConditionType.SEQUENCE) {
            throw IllegalArgumentException()
        }

        return this._sequence == sequence
    }
}

enum class DiscountConditionType {
    SEQUENCE,
    PERIOD
}