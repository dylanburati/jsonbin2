package com.dylanburati.jsonbin2

import io.github.cdimascio.dotenv.dotenv

object Config {
  private val env = dotenv()

  class Database {
    companion object {
      val host = env["POSTGRES_HOST"] ?: "localhost"
      val port = env["POSTGRES_PORT"] ?: "5432"
      val dbName = env["POSTGRES_DB"] ?: "jsonbin"
      val url = "jdbc:postgresql://$host:$port/$dbName"
      val user = env["POSTGRES_USER"] ?: "jsonbin"
      val password = env["POSTGRES_PASSWORD"] ?:
          throw IllegalArgumentException("DB password missing")
    }
  }

  class JWT {
    companion object {
      const val headerKey = "X-Access-Token"
      const val expiresInMillis = 1000L * 60 * 60 * 24 * 10
      val secret = env["JWT_SECRET"] ?: throw IllegalArgumentException("JWT secret missing")
    }
  }
}
