package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.JsonExtended
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.messages.Message
import com.dylanburati.jsonbin2.entities.users.User
import io.javalin.http.Context
import org.eclipse.jetty.util.log.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor

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
  data class LoginResult(
    val type: String,
    val title: String,
    val nickname: String,
    val users: List<JsonExtended<ConversationUser>>
  )

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
}
