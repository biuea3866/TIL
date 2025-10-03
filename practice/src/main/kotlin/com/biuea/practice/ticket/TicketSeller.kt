package com.biuea.practice.ticket

// as-is
//class TicketSeller(
//    private var _ticketOffice: TicketOffice
//) {
//    val ticketOffice get() = this._ticketOffice
//
//    fun sellTo(audience: Audience) {
//        if (audience.bag.hasInvitation()) {
//            val ticket = this.ticketOffice.getTicket()
//            audience.bag.setTicket(ticket)
//        } else {
//            val ticket = this.ticketOffice.getTicket()
//            audience.bag.minusAmount(ticket.fee)
//            this.ticketOffice.plusAmount(ticket.fee)
//            audience.bag.setTicket(ticket)
//        }
//    }
//}

// to-be
class TicketSeller(
    private var _ticketOffice: TicketOffice
) {
    val ticketOffice get() = this._ticketOffice

    fun sellTo(audience: Audience) {
        this._ticketOffice.sellTicketTo(audience)
    }
}