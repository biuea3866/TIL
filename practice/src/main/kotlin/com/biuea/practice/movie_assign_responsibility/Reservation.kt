package com.biuea.practice.movie_assign_responsibility

class Reservation(
    private var _customer: Customer,
    private var _screening: Screening,
    private var fee: Money,
    private var audienceCount: Int
) {
}