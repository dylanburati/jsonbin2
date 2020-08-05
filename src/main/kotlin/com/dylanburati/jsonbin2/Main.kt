package com.dylanburati.jsonbin2

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.conversations.ConversationController
import com.dylanburati.jsonbin2.entities.users.UserController
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.*

fun main() {
  val app = Javalin.create { config ->
    config.enableCorsForAllOrigins()
  }.start(7000)

  val authHandler = Handler { ctx ->
    if (ctx.method() != "OPTIONS") {
      val token = ctx.header(Config.JWT.headerKey) ?: throw UnauthorizedResponse("Missing JWT")
      ctx.attribute("user", ServiceContainer.userService.verifyJWT(token))
    }
  }

  app.routes {
    path("/u") {
      post(UserController::createUser)
    }

    path("/login") {
      post(UserController::login)
    }

    before("/g", authHandler)
    path("/g") {
      post(ConversationController::createConversation)
    }
  }

  val badRequestHandler: ExceptionHandler<Exception> = ExceptionHandler { e, ctx ->
    ctx.status(400)
    ctx.json(object {
      val message = e.message ?: "Unknown error"
    })
  }
  app.exception(BadRequestResponse::class.java, badRequestHandler)
  app.exception(IllegalArgumentException::class.java, badRequestHandler)
  app.exception(IllegalStateException::class.java, badRequestHandler)

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
