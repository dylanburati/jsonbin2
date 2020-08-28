package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.JsonExtended
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.users.User
import io.javalin.http.Context

object ConversationController {
  data class CreateConversationArgs(val title: String = "", val nickname: String = "")
  data class CreateConversationResult(
    val success: Boolean,
    val title: String,
    val nickname: String,
    val conversationId: String
  )

  data class ListConversationsResult(
    val success: Boolean,
    val conversations: List<JsonExtended<Conversation>>
  )

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
    val services = ctx.attribute<ServiceContainer>("services")!!
    val args = ctx.body<CreateConversationArgs>()
    validateTitle(args.title)
    validateNickname(args.nickname)

    val user = ctx.attribute<User>("user")!!
    val conv = services.conversationService.createConversation(user, args.title, args.nickname)
    ctx.json(
      CreateConversationResult(
        success = true,
        title = conv.title,
        nickname = args.nickname,
        conversationId = conv.id
      )
    )
  }

  fun listConversations(ctx: Context) {
    val services = ctx.attribute<ServiceContainer>("services")!!
    val user = ctx.attribute<User>("user")!!
    val conversations = services.conversationService.getUserConversations(user.id)
    val result = conversations.map { convUser ->
      val conv = convUser.conversation
      checkNotNull(conv) { "Expected relation to be loaded" }
      JsonExtended(conv).also { obj ->
        obj.extensions["nickname"] = convUser.nickname
      }
    }

    ctx.json(ListConversationsResult(success = true, conversations = result))
  }
}
