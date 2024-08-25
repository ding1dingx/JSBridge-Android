package com.ding1ding.jsbridge

interface MessageHandler<in Input, out Output> {
  fun handle(parameter: Input): Output
}
