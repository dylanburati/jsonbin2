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

object RealtimeController {
  private const val WS_TIMEOUT_MILLIS = 3600L * 1000

  private val logger = Log.getLogger(this::class.java)
  private val taskScheduler = ScheduledThreadPoolExecutor(1)
  private val allSessions = ConcurrentHashMap<String, ConversationUser>()
  private val activeConversations = ConcurrentHashMap<String, ActiveConversation>()
  private val services = ServiceContainer

  data class MessageArgs(val action: String?, val data: JsonNode)
  data class LoginArgs(val token: String?)
  data class LoginResult(
    val type: String,
    val title: String,
    val nickname: String,
    val isFirstLogin: Boolean,
    val users: List<JsonExtended<ConversationUser>>
  )

  data class GetMessagesResult(val type: String, val data: List<Message>)
  data class SetNicknameResult(val type: String, val data: ConversationUser)

  fun handleConnect(ctx: WsContext) {
    ctx.session.idleTimeout = WS_TIMEOUT_MILLIS
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

    if (args.token == null) throw UnauthorizedResponse("Missing JWT")
    val user = services.userService.verifyJWT(args.token)

    val userList = services.conversationService.getConversationUsers(convId).toMutableList()
    val convUser =
      services.conversationService.upsertConversationUser(convId, user, null)
    val active = activeConversations[convId]!!
    allSessions[ctx.sessionId] = convUser
    active.handleSessionOpen(ctx, convUser)

    val isFirstLogin = userList.none { it.id == convUser.id }
    if (isFirstLogin) userList.add(convUser)

    ctx.send(LoginResult(
      type = "login",
      title = active.conversation.title,
      nickname = convUser.nickname,
      isFirstLogin = isFirstLogin,
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
        .getOrElse { error("Could not get token and nickname from login data") }

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
      "setNickname" -> {
        val nick = jacksonObjectMapper()
          .runCatching { treeToValue<String>(inMessage.data)!! }
          .getOrElse { error("Could not get nickname") }
        ConversationController.validateNickname(nick)
        convUser.nickname = nick
        services.conversationService.upsertConversationUser(convUser)
        val active = activeConversations[convUser.conversationId]!!
        active.broadcast(SetNicknameResult(type = "setNickname", data = convUser))
        active.handleNicknameChange(convUser)
      }
      else -> {
        activeConversations[convUser.conversationId]!!.handleMessage(
          convUser,
          inMessage.action,
          inMessage.data
        )
      }
    }
  }

  fun handleClose(ctx: WsCloseContext) {
    val convUser = allSessions.remove(ctx.sessionId)
    val convId = ctx.pathParam("conversation-id")
    val active = activeConversations[convId] ?: return
    active.handleSessionClose(ctx, convUser)
    if (active.sessionMap.isEmpty()) {
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
