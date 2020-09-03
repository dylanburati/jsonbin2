package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.users.User
import kotliquery.queryOf

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

  fun createConversation(
    owner: User,
    args: ConversationController.CreateConversationArgs
  ): Conversation {
    val exists = getByTitle(args.title)
    check(exists == null) { "Conversation with the same title exists" }

    val id = generateId()
    val conversation = Conversation(id = id, title = args.title)

    val rowsAffected = session.run(
      queryOf(
        """INSERT INTO "conversation" ("id", "title") VALUES (?, ?)""",
        conversation.id,
        conversation.title
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")

    args.tags.forEach { tag ->
      val tagRowsAffected = session.run(
        queryOf(
          """INSERT INTO "conversation_tag" ("conversation_id", "tag") VALUES (?, ?)""",
          conversation.id,
          tag
        ).asUpdate
      )

      if (tagRowsAffected != 1) throw Exception("Could not insert record")
    }

    upsertConversationUser(conversation.id, owner, args.nickname, isOwner = true)
    return conversation
  }

  fun deleteConversation(id: String, user: User) {
    val convUser = findConversationUser(id, user.id)
    check(convUser != null && convUser.isOwner) {
      "Can't delete conversation $id. User is not the owner"
    }

    val rowsAffected = session.run(
      queryOf(
        """DELETE FROM "conversation" WHERE "id" = ?""",
        id
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not delete record")
  }

  fun upsertConversationUser(
    conversationId: String,
    user: User,
    nickname: String?,
    isOwner: Boolean = false
  ): ConversationUser {
    val convUser = findConversationUser(conversationId, user.id) ?: ConversationUser(
      id = generateId(),
      conversationId = conversationId,
      userId = user.id,
      nickname = nickname ?: user.username,
      isOwner = isOwner
    )

    if (nickname != null) convUser.nickname = nickname
    return upsertConversationUser(convUser)
  }

  fun upsertConversationUser(convUser: ConversationUser): ConversationUser {
    val rowsAffected = session.run(
      queryOf(
        """
        INSERT INTO "conversation_user" ("id", "conversation_id", "user_id", "nickname", "is_owner")
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT ("conversation_id", "user_id") DO UPDATE SET "nickname" = ?
        """,
        convUser.id,
        convUser.conversationId,
        convUser.userId,
        convUser.nickname,
        convUser.isOwner,
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
          nickname = row.string("nickname"),
          isOwner = row.boolean("is_owner")
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
          nickname = row.string("nickname"),
          isOwner = row.boolean("is_owner")
        )
      }
      .asList

    return session.run(findAllQuery)
  }

  fun getUserConversations(userId: String): List<ConversationUser> {
    val findAllQuery = queryOf(
      """SELECT "cu".*, "conv"."title", "lastm"."time"
        FROM "conversation_user" "cu"
        LEFT JOIN "conversation" "conv" ON "conv"."id" = "conversation_id"
        LEFT JOIN (
            SELECT "cu"."conversation_id", MAX("m"."time") "time" FROM "message" "m"
            LEFT JOIN "conversation_user" "cu" ON "m"."sender_id" = "cu"."id"
            GROUP BY "cu"."conversation_id"
        ) "lastm" ON "cu"."conversation_id" = "lastm"."conversation_id"
        WHERE "user_id" = ?
        ORDER BY "time" DESC""",
      userId
    )
      .map { row ->
        val convId = row.string("conversation_id")
        val convUser = ConversationUser(
          id = row.string("id"),
          conversationId = convId,
          userId = row.string("user_id"),
          nickname = row.string("nickname"),
          isOwner = row.boolean("is_owner")
        )
        convUser.conversation = Conversation(id = convId, title = row.string("title")).apply {
          this.updatedAt = row.instantOrNull("time")
        }
        convUser
      }
      .asList

    val convUsers = session.run(findAllQuery)
    convUsers.forEach { cu ->
      val conv = cu.conversation ?: error("")
      conv.tags = session.run(
        queryOf("""SELECT "tag" FROM "conversation_tag" WHERE "conversation_id" = ?""", conv.id)
          .map { row -> row.string("tag") }
          .asList
      )
    }

    return convUsers
  }
}
