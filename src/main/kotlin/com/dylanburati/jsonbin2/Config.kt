package com.dylanburati.jsonbin2

import io.github.cdimascio.dotenv.dotenv
import java.net.URI

object Config {
  private val env = dotenv {
    ignoreIfMissing = true
  }

  object Database {
    val host = env["POSTGRES_HOST"] ?: "localhost"
    val port = env["POSTGRES_PORT"] ?: "5432"
    val dbName = env["POSTGRES_DB"] ?: "jsonbin"
    val url = "jdbc:postgresql://$host:$port/$dbName"
    val user = env["POSTGRES_USER"] ?: "jsonbin"
    val password =
      env["POSTGRES_PASSWORD"] ?: throw IllegalArgumentException("DB password missing")
  }

  object JWT {
    const val headerKey = "X-Access-Token"
    const val expiresInMillis = 1000L * 60 * 60 * 24 * 10
    val secret = env["JWT_SECRET"] ?: throw IllegalArgumentException("JWT secret missing")
  }

  object GSheetsLambda {
    private val apiKey = env["GSHEETS_LAMBDA_API_KEY"]
    private val endpoint = env["GSHEETS_LAMBDA_ENDPOINT"]
    private val fileId = env["GSHEETS_FILE_ID"]

    fun getApiKey(): String {
      return apiKey ?: throw IllegalStateException("Google sheet lambda configuration missing")
    }

    fun getUri(queryParams: Map<String, String>): URI {
      check(endpoint != null && fileId != null) { "Google sheet lambda configuration missing" }
      val base = URI(endpoint)
      return URI(
        base.scheme,
        base.authority,
        "${base.path}/${fileId}",
        queryParams.map { (k, v) -> "${k}=${v}" }.joinToString("&"),
        null
      )
    }
  }
}
