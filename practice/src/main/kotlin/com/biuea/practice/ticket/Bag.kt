package com.biuea.practice.ticket

/**
 * 가방은 관람객의 이벤트 당첨 유무에 따라 소지품의 변동이 생긴다.
 * 이벤트가 당첨되어 있다면 가방엔 초대장이 있을 것이고, 그렇지 못한 관람객은 초대장이 없고 현금만 있을 것이다.
 * => 생성자를 통해 인스턴스를 어떻게 생성시킬 것인지 강제한다.
 *
 */
class Bag private constructor(
    private var _amount: Long,
    private var _invitation: Invitation?,
    private var _ticket: Ticket?
) {
    constructor(amount: Long) : this(amount, null, null)

    constructor(invitation: Invitation, amount: Long): this(amount, invitation, null)

    fun hasInvitation(): Boolean {
        return this._invitation != null
    }

    fun hasTicket(): Boolean {
        return this._ticket != null
    }

    fun setTicket(ticket: Ticket) {
        this._ticket = ticket
    }

    fun minusAmount(amount: Long) {
        this._amount -= amount
    }

    fun plusAmount(amount: Long) {
        this._amount += amount
    }

    fun hold(ticket: Ticket): Long {
        if (this.hasInvitation()) {
            this.setTicket(ticket)
            return 0L
        } else {
            this.setTicket(ticket)
            this.minusAmount(ticket.fee)
            return ticket.fee
        }
    }
}