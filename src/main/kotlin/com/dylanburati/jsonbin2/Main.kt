package com.dylanburati.jsonbin2

import com.dylanburati.jsonbin2.entities.users.UserController
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import java.lang.IllegalStateException

fun main(args: Array<String>) {
  val app = Javalin.create { config ->
    config.enableCorsForAllOrigins()
  }.start(7000)

  app.routes {
    path("u") {
      post(UserController::createUser)
    }

    path("login") {
      post(UserController::login)
    }
  }

  app.exception(IllegalStateException::class.java) { e, ctx ->
    ctx.status(400)
    ctx.json(object {
      val message = e.message ?: "Unknown error"
    })
  }
}
