package com.dylanburati.jsonbin2

import com.dylanburati.jsonbin2.entities.users.UserController
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.ForbiddenResponse
import io.javalin.http.UnauthorizedResponse
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

  app.exception(UnauthorizedResponse::class.java) { e, ctx ->
    ctx.status(401)
    ctx.json(object {
      val message = "Unauthenticated: ${e.message ?: "Unknown error"}"
    })
  }

  app.exception(ForbiddenResponse::class.java) { e, ctx ->
    ctx.status(403)
    ctx.json(object {
      val message = "Forbidden: ${e.message ?: "Unknown error"}"
    })
  }
}
