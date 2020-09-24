package com.dylanburati.jsonbin2.entities.messages

import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.conversations.ConversationUser
import com.dylanburati.jsonbin2.nonNull
import com.fasterxml.jackson.databind.JsonNode
import me.liuwj.ktorm.dsl.*
import java.time.Instant

class MessageService(container: ServiceContainer) : BaseService(container) {
  fun getConversationHistory(conversationId: String, limit: Int = 1000): List<Message> {
    require(limit > 0) { "Limit must be positive" }
    val findManyQuery = database.from(Message.TABLE)
      .leftJoin(ConversationUser.TABLE, on = Message.TABLE.senderId eq ConversationUser.TABLE.id)
      .select()
      .where { ConversationUser.TABLE.conversationId eq conversationId }
      .limit(0, limit)
      .orderBy(Message.TABLE.time.desc())
      .map { row ->
        (Message.TABLE to ConversationUser.TABLE).let { (m, cu) ->
          Message(
            id = row.nonNull(m.id),
            sender = ConversationUser(
              id = row.nonNull(cu.id),
              conversationId = row.nonNull(cu.conversationId),
              userId = row.nonNull(cu.userId),
              nickname = row.nonNull(cu.nickname),
              isOwner = row.nonNull(cu.isOwner)
            ),
            time = row.nonNull(m.time),
            target = row.nonNull(m.target),
            content = row.nonNull(m.content)
          )
        }
      }

    return findManyQuery.asReversed()
  }

  fun save(message: Message) {
    val rowsAffected = database.insert(Message.TABLE) {
      set(it.id, message.id)
      set(it.senderId, message.sender.id)
      set(it.time, message.time)
      set(it.target, message.target)
      set(it.content, message.content)
    }

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
