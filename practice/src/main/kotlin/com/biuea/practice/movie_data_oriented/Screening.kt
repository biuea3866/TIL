package com.biuea.practice.movie_data_oriented

import com.biuea.practice.movie.Money
import java.time.LocalDateTime

class Screening(
    private var _movie: Movie,
    private var _sequence: Int,
    private var _whenScreened: LocalDateTime
) {
    val movie get() = this._movie
    val sequence get() = this._sequence
    val whenScreened get() = this._whenScreened

    fun setMovie(movie: Movie) {
        this._movie = movie
    }

    fun setSequence(sequence: Int) {
        this._sequence = sequence
    }

    fun setWhenScreened(whenScreened: LocalDateTime) {
        this._whenScreened = whenScreened
    }

    fun calculateFee(audienceCount: Int): Money {
        when(this._movie.movieType) {
            MovieType.AMOUNT_DISCOUNT -> {
                if (this._movie.isDiscountable(this._whenScreened, this._sequence)) {
                    return this._movie.calculateAmountDiscountedFee().times(audienceCount.toDouble())
                }
            }
            MovieType.PERCENT_DISCOUNT -> {
                if (this._movie.isDiscountable(this._whenScreened, this._sequence))  {
                    return this._movie.calculateAmountDiscountedFee().times(audienceCount.toDouble())
                }
            }
            MovieType.NONE_DISCOUNT -> {
                return this._movie.calculateNoneDiscountedFee().times(audienceCount.toDouble())
            }
        }

        return this._movie.calculateNoneDiscountedFee().times(audienceCount.toDouble())
    }
}