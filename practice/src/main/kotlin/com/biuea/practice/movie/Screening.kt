package com.biuea.practice.movie

import java.time.LocalDateTime

class Screening(
    private var _movie: Movie,
    private var _sequence: Int,
    private var _whenScreened: LocalDateTime
) {
    val startTime get() = this._whenScreened
    val sequence get() = this._sequence
    val movieFee get() = this._movie.fee

    fun reservation(customer: Customer, audienceCount: Int): Reservation {
        return Reservation(customer, this, calculateFee(audienceCount), audienceCount)
    }

    fun isSequence(sequence: Int): Boolean {
        return this._sequence == sequence
    }

    private fun calculateFee(audienceCount: Int): Money {
        return _movie.calculateMovieFee(this).times(audienceCount.toDouble())
    }
}