package com.dylanburati.jsonbin2.entities.users

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
}
