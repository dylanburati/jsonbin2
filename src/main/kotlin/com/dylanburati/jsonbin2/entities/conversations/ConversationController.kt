package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.JsonExtended
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.users.User
import io.javalin.http.Context

object ConversationController {
  data class CreateConversationArgs(
    val title: String = "",
    val nickname: String = "",
    val tags: List<String> = listOf()
  )

  data class DeleteConversationsArgs(
    val ids: List<String>
  )

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

  data class DeleteConversationsResult(
    val success: Boolean
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

  fun validateTag(tag: String) {
    check(tag.length in 2..63) { "Tag must be 2-63 characters" }
    check(!tag.contains(Regex("\\v"))) { "Tag can not contain special characters" }
    check(tag.isNotBlank()) { "Tag can not be all spaces" }
  }

  fun createConversation(ctx: Context) {
    val services = ctx.attribute<ServiceContainer>("services")!!
    val args = ctx.body<CreateConversationArgs>()
    validateTitle(args.title)
    validateNickname(args.nickname)
    args.tags.forEach { validateTag(it) }

    val user = ctx.attribute<User>("user")!!
    val conv = services.conversationService.createConversation(user, args)
    ctx.json(
      CreateConversationResult(
        success = true,
        title = conv.title,
        nickname = args.nickname,
        conversationId = conv.id
      )
    )
  }

  private fun listConversations(ctx: Context, tag: String?) {
    val services = ctx.attribute<ServiceContainer>("services")!!
    val user = ctx.attribute<User>("user")!!
    val conversations = services.conversationService.getUserConversations(user.id)
    val result = conversations.mapNotNull { convUser ->
      val conv = convUser.conversation
      checkNotNull(conv) { "Expected relation to be loaded" }
      if (tag == null || conv.tags.contains(tag)) {
        JsonExtended(conv).also { obj ->
          obj.extensions["nickname"] = convUser.nickname
        }
      } else null
    }

    ctx.json(ListConversationsResult(success = true, conversations = result))
  }

  fun listConversations(ctx: Context) {
    return listConversations(ctx, null)
  }

  fun listConversationsWithTag(ctx: Context) {
    return listConversations(ctx, ctx.pathParam("tag"))
  }

  fun deleteConversations(ctx: Context) {
    val services = ctx.attribute<ServiceContainer>("services")!!
    val user = ctx.attribute<User>("user")!!
    val args = ctx.body<DeleteConversationsArgs>()
    args.ids.forEach { id -> services.conversationService.deleteConversation(id, user) }

    ctx.json(DeleteConversationsResult(success = true))
  }
}
