package com.ding1ding.jsbridge

data class ResponseMessage(
  val responseId: String?,
  val responseData: Any?,
  val callbackId: String?,
  val handlerName: String?,
  val data: Any?,
)
