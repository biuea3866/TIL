package com.biuea.practice.ticket

import java.time.LocalDateTime

class Invitation(
    private val _when: LocalDateTime
) {
    val `when`: LocalDateTime get() = this._when
}