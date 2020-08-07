package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.JsonExtended
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.messages.Message
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.javalin.http.BadRequestResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import org.eclipse.jetty.util.log.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

object RealtimeController {
  private val logger = Log.getLogger(this::class.java)
  private val taskScheduler = ScheduledThreadPoolExecutor(1)
  private val allSessions = ConcurrentHashMap<String, ConversationUser>()
  private val activeConversations = ConcurrentHashMap<String, ActiveConversation>()
  private val services = ServiceContainer

  data class MessageArgs(val action: String?, val data: JsonNode)
  data class LoginArgs(val token: String?, val nickname: String?)
  data class LoginResult(
    val type: String,
    val title: String,
    val nickname: String,
    val users: List<JsonExtended<ConversationUser>>
  )

  data class GetMessagesResult(val type: String, val data: List<Message>)
  data class SendMessageResult(val type: String, val data: Message)
  data class StatusResult(val type: String, val data: String)

  fun handleConnect(ctx: WsContext) {
    val convId = ctx.pathParam("conversation-id")

    activeConversations.computeIfAbsent(convId) {
      val conv = services.conversationService.getById(convId)
        ?: throw BadRequestResponse("Invalid conversation id: $convId")
      logger.info("Loading conversation $convId")
      ActiveConversation(conv)
    }
  }

  private fun handleLoginMessage(ctx: WsMessageContext, args: LoginArgs) {
    val convId = ctx.pathParam("conversation-id")

    if (args.nickname != null) ConversationController.validateNickname(args.nickname)
    if (args.token == null) throw UnauthorizedResponse("Missing JWT")
    val user = services.userService.verifyJWT(args.token)

    val convUser =
      services.conversationService.upsertConversationUser(convId, user, args.nickname)
    val active = activeConversations[convId]!!
    allSessions[ctx.sessionId] = convUser
    active.run {
      sessionMap[ctx.sessionId] = ctx
      val ct = active.userMap.compute(convUser.id) { k, v ->
        if (v == null) AtomicInteger(1)
        else v.apply { incrementAndGet() }
      }
      if (ct!!.get() == 1)
        broadcast(StatusResult(type = "status", data = "${convUser.nickname} joined"))
    }


    val userList = services.conversationService.getConversationUsers(convId)

    ctx.send(LoginResult(
      type = "login",
      title = active.conversation.title,
      nickname = convUser.nickname,
      users = userList.map {
        JsonExtended(it).also { obj ->
          obj.extensions["isActive"] = active.userMap[it.id].let { ct ->
            ct != null && ct.get() > 0
          }
        }
      }
    ))
  }

  fun handleMessage(ctx: WsMessageContext) {
    val inMessage = ctx.message<MessageArgs>()
    check(inMessage.action != null) { "Action is required" }

    val convUser = allSessions[ctx.sessionId]
    if (convUser == null) {
      if (inMessage.action != "login") throw UnauthorizedResponse("Login required first")

      val args = jacksonObjectMapper()
        .runCatching { treeToValue<LoginArgs>(inMessage.data)!! }
        .getOrElse { throw IllegalStateException("Could not get token and nickname from login data") }

      return handleLoginMessage(ctx, args)
    }
    when (inMessage.action) {
      "getMessages" -> {
        val history = services.messageService.getConversationHistory(convUser.conversationId)
        ctx.send(
          GetMessagesResult(
            type = "getMessages",
            data = history
          )
        )
      }
      else -> {
        val msg = services.messageService.send(convUser, inMessage.action, inMessage.data)
        activeConversations[convUser.conversationId]!!.broadcast(SendMessageResult(
          type = "message",
          data = msg
        ))
      }
    }
  }

  fun handleClose(ctx: WsCloseContext) {
    val convUser = allSessions.remove(ctx.sessionId)
    val convId = ctx.pathParam("conversation-id")
    val active = activeConversations[convId] ?: return
    val empty = active.run {
      sessionMap.remove(ctx.sessionId)
      sessionMap.isEmpty()
    }
    if (convUser != null) {
      active.run {
        val ct = userMap.computeIfPresent(convUser.id) { k, v ->
          v.apply { decrementAndGet() }
        }
        if (ct?.get() == 0)
          broadcast(StatusResult(type = "status", data = "${convUser.nickname} left"))
      }
    }
    if (empty) {
      scheduleUnloadConversation(convId)
    }
  }

  private fun scheduleUnloadConversation(conversationId: String) {
    taskScheduler.schedule(
      {
        if (activeConversations[conversationId]?.sessionMap?.isEmpty() == true) {
          activeConversations.remove(conversationId)
          logger.info("Unloaded conversation $conversationId")
        }
      },
      30L,
      TimeUnit.SECONDS
    )
  }
}
