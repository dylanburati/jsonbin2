package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.conversations.handlers.BroadcastHandler
import com.dylanburati.jsonbin2.entities.conversations.handlers.GuessrHandler
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ActiveConversation(val conversation: Conversation) {
  val userMap = ConcurrentHashMap<String, AtomicInteger>()
  val sessionMap = ConcurrentHashMap<String, WsContext>()

  private val handlers: ArrayList<MessageHandler> = arrayListOf(
    GuessrHandler(this),
    BroadcastHandler(this)
  )

  fun handleMessage(convUser: ConversationUser, action: String, data: JsonNode) {
    for (h in handlers) {
      if (h.onMessage(convUser, action, data)) return
    }
  }

  fun handleSessionOpen(ctx: WsContext, convUser: ConversationUser) {
    sessionMap[ctx.sessionId] = ctx
    val ct = userMap.compute(convUser.id) { _, v ->
      v?.apply { incrementAndGet() } ?: AtomicInteger(1)
    }
    if (ct!!.get() == 1) {
      for (h in handlers) h.onUserEnter(convUser)
    }
  }

  fun handleSessionClose(ctx: WsCloseContext, convUser: ConversationUser?) {
    sessionMap.remove(ctx.sessionId)
    if (convUser != null) {
      val ct = userMap.computeIfPresent(convUser.id) { _, v ->
        v.apply { decrementAndGet() }
      }
      if (ct?.get() == 0) {
        for (h in handlers) h.onUserExit(convUser)
      }
    }
  }

  fun broadcast(message: Any) {
    sessionMap
      .filter { (_, otherCtx) -> otherCtx.session.isOpen }
      .forEach { (_, otherCtx) ->
        otherCtx.send(message)
      }
  }

  fun handleNicknameChange(convUser: ConversationUser) {
    for (h in handlers) h.onUserEnter(convUser)
  }
}
