package com.biuea.practice.message_and_interface

import java.math.BigDecimal

data class Money(val amount: BigDecimal) {
    fun plus(amount: Money): Money {
        return this.copy(this.amount.add(amount.amount))
    }

    fun minus(amount: Money): Money {
        return this.copy(this.amount.subtract(amount.amount))
    }

    fun times(percent: Double): Money {
        return this.copy(this.amount.multiply(BigDecimal.valueOf(percent)))
    }

    fun isLessThan(other: Money): Boolean {
        return this.amount < other.amount
    }

    fun isGreaterThan(other: Money): Boolean {
        return this.amount > other.amount
    }

    companion object {
        val ZERO: Money = Money.wons(0)

        fun wons(amount: Long): Money {
            return Money(BigDecimal.valueOf(amount))
        }

        fun wons(amount: Double): Money {
            return Money(BigDecimal.valueOf(amount))
        }
    }
}