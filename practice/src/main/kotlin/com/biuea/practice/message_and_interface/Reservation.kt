package com.biuea.practice.message_and_interface

class Reservation(
    private var _customer: Customer,
    private var _screening: Screening,
    private var fee: Money,
    private var audienceCount: Int
) {
}