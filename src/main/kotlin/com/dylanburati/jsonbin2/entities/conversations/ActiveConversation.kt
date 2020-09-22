package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.conversations.handlers.BroadcastHandler
import com.dylanburati.jsonbin2.entities.conversations.handlers.GuessrHandler
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ActiveConversation(val services: ServiceContainer, val conversation: Conversation) {

  private val userInactivityMap = ConcurrentHashMap<String, Instant>()
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
      userInactivityMap.remove(convUser.id)
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
        userInactivityMap[convUser.id] = Instant.now()
        for (h in handlers) h.onUserExit(convUser)
      }
    }
  }

  fun getActiveConvUserIds(leeway: Duration = Duration.ZERO): Set<String> {
    val now = Instant.now()
    val entries = userMap.asSequence().filter { (k, v) ->
      when {
        v.get() > 0 -> true
        leeway <= Duration.ZERO -> false
        else -> userInactivityMap[k].let { leftAt ->
          leftAt != null && Duration.between(leftAt, now) < leeway
        }
      }
    }
    return entries.map { it.key }.toSet()
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
