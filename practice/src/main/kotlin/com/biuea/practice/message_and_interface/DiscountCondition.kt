package com.biuea.practice.message_and_interface

import java.time.DayOfWeek
import java.time.LocalDateTime

interface DiscountCondition {
    fun isSatisfiedBy(screening: Screening): Boolean
}

class SequenceCondition(private var sequence: Int): DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return screening.isSequence(this.sequence)
    }
}

class PeriodCondition(
    private var dayOfWeek: DayOfWeek,
    private var startTime: LocalDateTime,
    private val endTime: LocalDateTime
): DiscountCondition {
    override fun isSatisfiedBy(screening: Screening): Boolean {
        return screening.startTime.dayOfWeek.equals(this.dayOfWeek)
                && this.startTime <= screening.startTime
                && this.endTime >= screening.startTime
    }
}