package com.dylanburati.jsonbin2.entities.conversations

import io.javalin.websocket.WsContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ActiveConversation(val conversation: Conversation) {
  val userMap = ConcurrentHashMap<String, AtomicInteger>()
  val sessionMap = ConcurrentHashMap<String, WsContext>()

  fun broadcast(message: Any) {
    sessionMap
      .filter { (_, otherCtx) -> otherCtx.session.isOpen }
      .forEach { (_, otherCtx) ->
        otherCtx.send(message)
      }
  }
}
