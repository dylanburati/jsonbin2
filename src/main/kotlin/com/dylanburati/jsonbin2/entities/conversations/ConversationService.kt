package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.users.User
import kotliquery.queryOf
import java.lang.Exception

class ConversationService(container: ServiceContainer) : BaseService(container) {
  fun createConversation(owner: User, title: String, nickname: String?): Conversation {
    val existsQuery = queryOf("""SELECT * FROM "conversation" WHERE "title" = ? LIMIT 1""", title)
      .map { row -> row.string("id") }
      .asSingle
    val exists = session.run(existsQuery)

    check(exists == null) { "Conversation with the same title exists" }

    val id = generateId()
    val conversation = Conversation(id = id, title = title, users = ArrayList())

    val rowsAffected = session.run(
      queryOf(
        """INSERT INTO "conversation" ("id", "title") VALUES (?, ?)""",
        conversation.id,
        conversation.title
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")

    return addConversationUsers(conversation, listOf(owner to nickname))
  }

  fun addConversationUsers(
    conversation: Conversation,
    users: List<Pair<User, String?>>
  ): Conversation {
    val conversationUsers = users.map { (user, nickname) ->
      ConversationUser(
        id = generateId(),
        conversationId = conversation.id,
        userId = user.id,
        nickname = nickname ?: user.username
      )
    }

    for(row in conversationUsers) {
      val rowsAffected = session.run(
        queryOf(
          """
          INSERT INTO "conversation_user" ("id", "conversation_id", "user_id", "nickname")
          VALUES (?, ?, ?, ?)
          """,
          row.id,
          row.conversationId,
          row.userId,
          row.nickname
        ).asUpdate
      )

      if (rowsAffected != 1) throw Exception("Could not insert record")
    }

    conversation.users.addAll(conversationUsers)
    return conversation
  }
}
