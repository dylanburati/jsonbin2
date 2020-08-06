package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.users.User
import kotliquery.queryOf
import java.lang.Exception

class ConversationService(container: ServiceContainer) : BaseService(container) {
  fun getById(id: String): Conversation? {
    val findOneQuery = queryOf("""SELECT * FROM "conversation" WHERE "id" = ? LIMIT 1""", id)
      .map { row ->
        Conversation(
          id = row.string("id"),
          title = row.string("title")
        )
      }
      .asSingle
    return session.run(findOneQuery)
  }

  fun getByTitle(title: String): Conversation? {
    val findOneQuery = queryOf("""SELECT * FROM "conversation" WHERE "title" = ? LIMIT 1""", title)
      .map { row ->
        Conversation(
          id = row.string("id"),
          title = row.string("title")
        )
      }
      .asSingle
    return session.run(findOneQuery)
  }

  fun createConversation(owner: User, title: String, nickname: String?): Conversation {
    val exists = getByTitle(title)
    check(exists == null) { "Conversation with the same title exists" }

    val id = generateId()
    val conversation = Conversation(id = id, title = title)

    val rowsAffected = session.run(
      queryOf(
        """INSERT INTO "conversation" ("id", "title") VALUES (?, ?)""",
        conversation.id,
        conversation.title
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")

    upsertConversationUser(conversation.id, owner, nickname ?: owner.username)
    return conversation
  }

  fun upsertConversationUser(
    conversationId: String,
    user: User,
    nickname: String?
  ): ConversationUser {
    val convUser = findConversationUser(conversationId, user.id) ?: ConversationUser(
      id = generateId(),
      conversationId = conversationId,
      userId = user.id,
      nickname = nickname ?: user.username
    )

    if (nickname != null) convUser.nickname = nickname
    val rowsAffected = session.run(
      queryOf(
        """
        INSERT INTO "conversation_user" ("id", "conversation_id", "user_id", "nickname")
        VALUES (?, ?, ?, ?)
        ON CONFLICT ("conversation_id", "user_id") DO UPDATE SET "nickname" = ?
        """,
        convUser.id,
        convUser.conversationId,
        convUser.userId,
        convUser.nickname,
        convUser.nickname
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")

    return convUser
  }

  fun findConversationUser(
    conversationId: String,
    userId: String
  ): ConversationUser? {
    val findOneQuery = queryOf(
      """
      SELECT * FROM "conversation_user"
      WHERE "conversation_id" = ? AND "user_id" = ? LIMIT 1
      """,
      conversationId,
      userId
    )
      .map { row ->
        ConversationUser(
          id = row.string("id"),
          conversationId = row.string("conversation_id"),
          userId = row.string("user_id"),
          nickname = row.string("nickname")
        )
      }
      .asSingle

    return session.run(findOneQuery)
  }

  fun getConversationUsers(conversationId: String): List<ConversationUser> {
    val findAllQuery = queryOf(
      """SELECT * FROM "conversation_user" WHERE "conversation_id" = ?""",
      conversationId
    )
      .map { row ->
        ConversationUser(
          id = row.string("id"),
          conversationId = row.string("conversation_id"),
          userId = row.string("user_id"),
          nickname = row.string("nickname")
        )
      }
      .asList

    return session.run(findAllQuery)
  }
}
