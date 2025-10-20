package com.biuea.practice.movie_data_oriented

class Customer(
    private var _name: String,
    private var _id: String
) {
    val name get() = _name
    val id get() = _id
}