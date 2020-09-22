package com.dylanburati.jsonbin2.entities.users

import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.text
import me.liuwj.ktorm.schema.varchar

data class User(
  var id: String,
  var username: String,
  var authType: AuthType,
  var password: String?
) {
  enum class AuthType {
    NONE,
    BCRYPT
  }

  object TABLE : Table<Nothing>("user") {
    val id = varchar("id").primaryKey()
    val username = varchar("username")
    val authType = text("auth_type")
    val password = text("password")
  }
}
