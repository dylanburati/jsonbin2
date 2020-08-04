package com.dylanburati.jsonbin2.entities

import java.security.SecureRandom
import java.util.Base64
import kotlin.random.Random

abstract class BaseService {
  val secureRandom = SecureRandom()

  /**
   * Generates a url-safe 6 character identifier
   */
  fun generateId(): String {
    val bytes = ByteArray(5).also { secureRandom.nextBytes(it) }
    return Base64.getUrlEncoder().encodeToString(bytes).substring(0, 6)
  }
}
