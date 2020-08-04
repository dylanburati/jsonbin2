package com.dylanburati.jsonbin2.entities

import com.dylanburati.jsonbin2.Config
import com.dylanburati.jsonbin2.entities.users.UserService
import kotliquery.*
import org.flywaydb.core.Flyway

object ServiceContainer {
  private fun initSession(): Session {
    return Config.Database.run {
      Flyway.configure().dataSource(url, user, password).load().migrate()
      sessionOf(url, user, password)
    }
  }

  val session = this.initSession()
  val userService = UserService(this)
}
