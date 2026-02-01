package com.biuea.practice.inline

fun main() {
    val list = listOf("asd", 1, '1')
    println(list.filter { it.isType<String>() })
}

inline fun<reified T> Any.isType(): Boolean {
    return this is T
}