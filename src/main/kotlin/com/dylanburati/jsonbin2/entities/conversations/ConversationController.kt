package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.users.User
import io.javalin.http.Context
import org.eclipse.jetty.util.log.Log

object ConversationController {
  private val logger = Log.getLogger(this::class.java)
  private val services = ServiceContainer

  data class CreateConversationArgs(val title: String = "", val nickname: String = "")
  data class CreateConversationResult(
    val success: Boolean,
    val title: String,
    val conversationId: String
  )

  fun createConversation(ctx: Context) {
    val args = ctx.body<CreateConversationArgs>()
    check(args.title.length in 2..63) { "Title must be 2-63 characters" }
    check(!args.title.contains(Regex("\\v"))) { "Title can not contain special characters" }
    check(args.title.isNotBlank()) { "Title can not be all spaces" }
    check(args.nickname.length in 2..63) { "Username must be 2-63 characters" }
    check(!args.nickname.contains(Regex("\\v"))) { "Username can not contain special characters" }
    check(args.nickname.isNotBlank()) { "Username can not be all spaces" }

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
