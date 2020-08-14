package com.dylanburati.jsonbin2

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.conversations.ConversationController
import com.dylanburati.jsonbin2.entities.conversations.RealtimeController
import com.dylanburati.jsonbin2.entities.remote.questions.QuestionController
import com.dylanburati.jsonbin2.entities.users.UserController
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.http.*
import io.javalin.websocket.WsExceptionHandler

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

    before("/me", authHandler)
    path("/me") {
      get(UserController::currentUser)
    }

    before("/g", authHandler)
    path("/g") {
      get(ConversationController::listConversations)
      post(ConversationController::createConversation)
    }

    before("/guessr/q", authHandler)
    path("/guessr/q") {
      get("refresh", QuestionController::refresh)
    }

    path("/ws") {
      ws(":conversation-id") {
        ws ->
        ws.onConnect(RealtimeController::handleConnect)
        ws.onMessage(RealtimeController::handleMessage)
        ws.onClose(RealtimeController::handleClose)
      }
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
    ctx.status(e.status)
    ctx.json(object {
      val message = "Unauthenticated: ${e.message ?: "Unknown error"}"
    })
  }

  app.exception(ForbiddenResponse::class.java) { e, ctx ->
    ctx.status(e.status)
    ctx.json(object {
      val message = "Forbidden: ${e.message ?: "Unknown error"}"
    })
  }

  app.exception(TooManyRequestsResponse::class.java) { e, ctx ->
    ctx.status(e.status)
    ctx.header("Retry-After", e.retryAfterSeconds.toString())
    ctx.json(object {
      val message = "Too Many Requests: ${e.message ?: "Unknown error"}"
    })
  }

  val wsBadRequestHandler: WsExceptionHandler<Exception> = WsExceptionHandler { e, ctx ->
    ctx.send(object {
      val type = "error"
      val message = e.message ?: "Unknown error"
    })
  }
  app.wsException(BadRequestResponse::class.java, wsBadRequestHandler)
  app.wsException(IllegalArgumentException::class.java, wsBadRequestHandler)
  app.wsException(IllegalStateException::class.java, wsBadRequestHandler)
  app.wsException(UnauthorizedResponse::class.java) { e, ctx ->
    ctx.send(object {
      val type = "error"
      val message = "Unauthenticated: ${e.message ?: "Unknown error"}"
    })
  }
}
