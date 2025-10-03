package com.biuea.practice.ticket

// as-is
//class Audience(
//    private var _bag: Bag
//) {
//    val bag: Bag get() = this._bag
//}

class Audience(
    private var _bag: Bag
) {
    val bag: Bag get() = this._bag

    fun buy(ticket: Ticket): Long {
        return this.bag.hold(ticket)
    }
}