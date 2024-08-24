package com.ding1ding.jsbridge

interface Handler<in T, out R> {
  fun handle(parameter: T): R
}
