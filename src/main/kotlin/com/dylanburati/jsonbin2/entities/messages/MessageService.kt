package com.dylanburati.jsonbin2.entities.messages

import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import java.time.Instant

class MessageService(container: ServiceContainer) : BaseService(container) {
  fun getConversationHistory(conversationId: String, limit: Int = 1000): List<Message> {
    require(limit > 0) { "Limit must be positive" }
    val findManyQuery = queryOf("""
      SELECT "message"."id", "sender_id", "sender"."conversation_id", "sender"."user_id",
        "sender"."nickname", "time", "target", "content"
      FROM "message" LEFT JOIN "conversation_user" "sender"
      ON "message"."sender_id" = "sender"."id"
      WHERE "sender"."conversation_id" = ? ORDER BY "time" DESC
      LIMIT ?
      """,
      conversationId,
      limit
    )
      .map { row ->
        Message(
          id = row.string("id"),
          sender = ConversationUser(
            id = row.string("sender_id"),
            conversationId = row.string("conversation_id"),
            userId = row.string("user_id"),
            nickname = row.string("nickname")
          ),
          time = row.instant("time"),
          target = row.string("target"),
          content = jacksonObjectMapper().readTree(row.string("content"))
        )
      }
      .asList
    return session.run(findManyQuery).asReversed()
  }

  fun save(message: Message) {
    val rowsAffected = session.run(
      queryOf(
        """
        INSERT INTO "message" ("id", "sender_id", "time", "target", "content")
        VALUES (?, ?, ?, ?, ? ::jsonb)
        """,
        message.id,
        message.sender.id,
        message.time,
        message.target,
        jacksonObjectMapper().writeValueAsString(message.content)
      ).asUpdate
    )

    if (rowsAffected != 1) throw Exception("Could not insert record")
  }

  fun send(sender: ConversationUser, target: String, content: JsonNode): Message {
    val message = Message(
      id = generateId(),
      sender = sender,
      time = Instant.now(),
      target = target,
      content = content
    )

    save(message)

    return message
  }
}
