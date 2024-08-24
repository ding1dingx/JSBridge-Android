package com.ding1ding.jsbridge

interface MessageHandler<in InputType, out OutputType> {
  fun handle(parameter: InputType): OutputType
}
