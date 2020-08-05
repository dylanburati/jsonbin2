package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.messages.Message
import com.dylanburati.jsonbin2.entities.users.User
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.UnauthorizedResponse
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import org.eclipse.jetty.util.log.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

object ConversationController {
  private val logger = Log.getLogger(this::class.java)
  private val taskScheduler = ScheduledThreadPoolExecutor(1)
  private val allSessions = ConcurrentHashMap<String, ConversationUser>()
  private val activeConversations = ConcurrentHashMap<String, ActiveConversation>()
  private val services = ServiceContainer

  data class CreateConversationArgs(val title: String = "", val nickname: String = "")
  data class CreateConversationResult(
    val success: Boolean,
    val title: String,
    val conversationId: String
  )

  data class MessageArgs(val action: String?, val data: Map<String, Any>?)
  data class GetMessagesResult(val type: String, val data: List<Message>)
  data class SendMessageResult(val type: String, val data: Message)

  fun validateTitle(title: String) {
    check(title.length in 2..63) { "Title must be 2-63 characters" }
    check(!title.contains(Regex("\\v"))) { "Title can not contain special characters" }
    check(title.isNotBlank()) { "Title can not be all spaces" }
  }

  fun validateNickname(nickname: String) {
    check(nickname.length in 2..63) { "Username must be 2-63 characters" }
    check(!nickname.contains(Regex("\\v"))) { "Username can not contain special characters" }
    check(nickname.isNotBlank()) { "Username can not be all spaces" }
  }

  fun createConversation(ctx: Context) {
    val args = ctx.body<CreateConversationArgs>()
    validateTitle(args.title)
    validateNickname(args.nickname)

    val user = ctx.attribute<User>("user")!!
    val conv = services.conversationService.createConversation(user, args.title, args.nickname)
    ctx.json(
      CreateConversationResult(
        success = true,
        title = conv.title,
        conversationId = conv.id
      )
    )
  }

  fun handleConnect(ctx: WsContext) {
    val convId = ctx.pathParam("conversation-id")

    activeConversations.computeIfAbsent(convId) {
      val conv = services.conversationService.getById(convId)
        ?: throw BadRequestResponse("Invalid conversation id: $convId")
      logger.info("Loading conversation $convId")
      ActiveConversation(conv)
    }
  }

  fun handleMessage(ctx: WsMessageContext) {
    val convId = ctx.pathParam("conversation-id")
    val inMessage = ctx.message<MessageArgs>()
    check(inMessage.action != null && inMessage.data != null) { "Action and data are required" }

    var convUser = allSessions[ctx.sessionId]
    if (convUser == null) {
      if (inMessage.action != "login") throw UnauthorizedResponse("Login required first")

      val token = inMessage.data["token"]
      val nickname = inMessage.data["nickname"]
      check(nickname is String?) { "Expected a string for the username" }
      if (nickname != null) validateNickname(nickname)
      if (token !is String) throw UnauthorizedResponse("Missing JWT")
      val user = services.userService.verifyJWT(token)

      convUser =
        services.conversationService.upsertConversationUser(convId, user, nickname)
      val active = activeConversations[convId]!!
      allSessions[ctx.sessionId] = convUser
      active.userMap[convUser] = ctx

      ctx.send(object {
        val type = "login"
        val title = active.conversation.title
        val nickname = convUser.nickname
      })
      return
    }
    when (inMessage.action) {
      "getMessages" -> {
        val history = services.messageService.getConversationHistory(convUser.conversationId)
        ctx.send(GetMessagesResult(
          type = "getMessages",
          data = history
        ))
      }
      else -> {
        val msg = services.messageService.send(convUser, inMessage.action, inMessage.data)
        activeConversations[convUser.conversationId]!!.userMap
          .filter { (_, otherCtx) -> otherCtx.session.isOpen }
          .forEach { (_, otherCtx) ->
            otherCtx.send(SendMessageResult(
              type = "message",
              data = msg
            ))
          }
      }
    }
  }

  fun handleClose(ctx: WsCloseContext) {
    val convUser = allSessions.remove(ctx.sessionId)
    val convId = ctx.pathParam("conversation-id")
    val active = activeConversations[convId] ?: return
    val empty = active.run {
      userMap.remove(convUser)
      userMap.isEmpty()
    }
    if (empty) {
      scheduleUnloadConversation(convId)
    }
  }

  fun scheduleUnloadConversation(conversationId: String) {
    taskScheduler.schedule(
      {
        if (activeConversations[conversationId]?.userMap?.isEmpty() == true) {
          activeConversations.remove(conversationId)
          logger.info("Unloaded conversation $conversationId")
        }
      },
      30L,
      TimeUnit.SECONDS
    )
  }
}
