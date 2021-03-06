package com.dylanburati.jsonbin2.entities

import java.security.SecureRandom
import java.util.*


abstract class BaseService(val container: ServiceContainer) {
  val database = container.database
  val secureRandom = SecureRandom()

  /**
   * Generates a url-safe 6 character identifier
   */
  fun generateId(): String {
    val bytes = ByteArray(5).also { secureRandom.nextBytes(it) }
    return Base64.getUrlEncoder().encodeToString(bytes).substring(0, 6)
  }
}
