package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.entities.users.UserService
import kotliquery.*
import org.flywaydb.core.Flyway

object ServiceContainer {
  private fun initSession(): Session {
    val host = System.getenv("POSTGRES_HOST") ?: "localhost"
    val port = System.getenv("POSTGRES_PORT") ?: "5432"
    val dbName = System.getenv("POSTGRES_DB") ?: "jsonbin"
    val url = "jdbc:postgresql://$host:$port/$dbName"
    val dbUser = System.getenv("POSTGRES_USER")
    val dbPass = System.getenv("POSTGRES_PASSWORD")

    Flyway.configure().dataSource(url, dbUser, dbPass).load().migrate()
    return sessionOf(url, dbUser, dbPass)
  }

  val session = this.initSession()
  val userService = UserService(this)
}
