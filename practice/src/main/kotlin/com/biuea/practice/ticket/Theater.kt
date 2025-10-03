package com.biuea.practice.ticket

// as-is
//class Theater(private var _ticketSeller: TicketSeller) {
//    fun enter(audience: Audience) {
//        // 초대장이 있는 경우
//        if (audience.bag.hasInvitation()) {
//            val ticket = this._ticketSeller.ticketOffice.getTicket()
//            audience.bag.setTicket(ticket)
//        } else {
//            val ticket = this._ticketSeller.ticketOffice.getTicket()
//            audience.bag.minusAmount(ticket.fee)
//            this._ticketSeller.ticketOffice.plusAmount(ticket.fee)
//            audience.bag.setTicket(ticket)
//        }
//    }
//}

// to-be
class Theater(private var _ticketSeller: TicketSeller) {
    fun enter(audience: Audience) {
        this._ticketSeller.sellTo(audience)
    }
}