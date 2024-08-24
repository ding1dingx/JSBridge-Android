package com.ding1ding.jsbridge

interface Callback<T> {
  fun onResult(result: T)
}
