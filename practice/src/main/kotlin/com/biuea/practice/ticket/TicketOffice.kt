package com.biuea.practice.ticket

class TicketOffice(
    private var _amount: Long,
    private val _tickets: MutableList<Ticket>
) {
    fun getTicket(): Ticket {
        return this._tickets.removeFirst()
    }

    fun minusAmount(amount: Long) {
        this._amount -= amount
    }

    private fun plusAmount(amount: Long) {
        this._amount += amount
    }

    fun sellTicketTo(audience: Audience) {
        this.plusAmount(audience.buy(this.getTicket()))
    }
}