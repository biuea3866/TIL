package com.biuea.practice.movie_data_oriented

import com.biuea.practice.movie.Money

class ReservationAgency {
    fun reserve(
        screening: Screening,
        customer: Customer,
        audienceCount: Int,
    ): Reservation {
        val fee = screening.calculateFee(audienceCount)

        return Reservation(customer, screening, fee, audienceCount)
    }
}