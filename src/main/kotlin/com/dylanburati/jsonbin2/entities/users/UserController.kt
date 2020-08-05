package com.dylanburati.jsonbin2.entities.users

import com.dylanburati.jsonbin2.entities.ServiceContainer
import io.javalin.http.Context
import org.eclipse.jetty.util.log.Log

object UserController {
  private val logger = Log.getLogger(this::class.java)
  private val services = ServiceContainer

  data class CreateUserArgs(
    val isGuest: Boolean = false,
    val username: String = "",
    val password: String = ""
  )

  data class LoginArgs(val username: String = "", val password: String = "")
  data class LoginResult(
    val success: Boolean,
    val token: String,
    val userId: String,
    val username: String
  )

  data class CurrentUserResult(
    val success: Boolean,
    val userId: String,
    val username: String
  )

  fun createUser(ctx: Context) {
    val args = ctx.body<CreateUserArgs>()
    if (args.isGuest) {
      check(args.username.isEmpty() && args.password.isEmpty()) { "Guest user can not specify username or password" }
    } else {
      check(args.username.length in 2..63) { "Username must be 2-63 characters" }
      check(args.password.length >= 8) { "Password must be at least 8 characters" }
      check(!args.username.contains(Regex("[^A-Za-z0-9-_.]"))) {
        "Username can not contain special characters"
      }
    }

    val user = services.userService.run {
      if (args.isGuest) createGuest()
      else createUser(args.username, args.password)
    }
    ctx.json(
      LoginResult(
        success = true,
        token = services.userService.generateJWT(user),
        userId = user.id,
        username = user.username
      )
    )
  }

  fun login(ctx: Context) {
    val args = ctx.body<LoginArgs>()

    val user = services.userService.authenticateUser(args.username, args.password)
    ctx.json(
      LoginResult(
        success = true,
        token = services.userService.generateJWT(user),
        userId = user.id,
        username = user.username
      )
    )
  }

  fun currentUser(ctx: Context) {
    val user = ctx.attribute<User>("user")!!
    ctx.json(
      CurrentUserResult(
        success = true,
        userId = user.id,
        username = user.username
      )
    )
  }
}
