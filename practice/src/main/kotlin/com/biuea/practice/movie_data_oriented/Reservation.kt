package com.biuea.practice.movie_data_oriented

import com.biuea.practice.movie.Money

class Reservation(
    private var _customer: Customer,
    private var _screening: Screening,
    private var _fee: Money,
    private var _audienceCount: Int
) {
    val customer get() = this._customer
    val screening get() = this._screening
    val fee get() = this._fee
    val audienceCount get() = this._audienceCount

    fun setCustomer(customer: Customer) {
        this._customer = customer
    }

    fun setScreening(screening: Screening) {
        this._screening = screening
    }

    fun setFee(fee: Money) {
        this._fee = fee
    }

    fun setAudienceCount(audienceCount: Int) {
        this._audienceCount = audienceCount
    }
}
