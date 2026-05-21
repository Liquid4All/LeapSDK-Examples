package com.leap.shareai.webscraping

sealed class WebPageState {
  data object Idle : WebPageState()

  data object Loading : WebPageState()

  data class Success(val content: WebPageContent) : WebPageState()

  data class Error(val message: String) : WebPageState()
}
