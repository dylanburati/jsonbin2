package com.dylanburati.jsonbin2.entities.users

import com.dylanburati.jsonbin2.entities.ServiceContainer
import io.javalin.http.Context
import io.javalin.http.Handler

object UserController {
  private val services = ServiceContainer
  data class LoginArgs(val username: String = "", val password: String = "")

  fun createUser(ctx: Context) {
    val args = ctx.body<LoginArgs>()
    check(args.username.length in 2..63) { "Username must be 2-63 characters" }
    check( args.password.length >= 8) { "Password must be at least 8 characters" }
    check(!args.username.contains(Regex("[^A-Za-z0-9-_.]"))) {
      "Username can not contain special characters"
    }

    val user = services.userService.createUser(args.username, args.password)
    ctx.json(object {
      val success = true
      val userId = user.id
    })
  }

  fun login(ctx: Context) {
    val args = ctx.body<LoginArgs>()

    val user = services.userService.authenticateUser(args.username, args.password)
    ctx.json(object {
      val success = true
      val userId = user.id
    })
  }
}
