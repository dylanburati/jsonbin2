package com.dylanburati.jsonbin2.entities.conversations

import com.dylanburati.jsonbin2.entities.BaseService
import com.dylanburati.jsonbin2.entities.ServiceContainer
import com.dylanburati.jsonbin2.entities.messages.Message
import com.dylanburati.jsonbin2.entities.users.User
import com.dylanburati.jsonbin2.nonNull
import me.liuwj.ktorm.dsl.*

class ConversationService(container: ServiceContainer) : BaseService(container) {
  fun getById(id: String): Conversation? {
    val findOneQuery = database.from(Conversation.TABLE).select()
      .where { Conversation.TABLE.id eq id }
      .limit(0, 1)
      .map { row ->
        Conversation.TABLE.let { t ->
          Conversation(
            id = row.nonNull(t.id),
            title = row.nonNull(t.title),
            isPrivate = row.nonNull(t.isPrivate)
          )
        }
      }

    return findOneQuery.firstOrNull()
  }

  fun createConversation(
    owner: User,
    args: ConversationController.CreateConversationArgs
  ): Conversation {
    val id = generateId()
    val conversation = Conversation(id = id, title = args.title, isPrivate = args.isPrivate)

    val rowsAffected = database.insert(Conversation.TABLE) {
      set(it.id, conversation.id)
      set(it.title, conversation.title)
      set(it.isPrivate, conversation.isPrivate)
    }

    if (rowsAffected != 1) throw Exception("Could not insert record")

    database.batchInsert(ConversationTag.TABLE) {
      for (tag in args.tags) {
        item {
          set(it.conversationId, conversation.id)
          set(it.tag, tag)
        }
      }
    }

    createConversationUser(conversation.id, owner, args.nickname, isOwner = true)
    return conversation
  }

  fun deleteConversation(id: String, user: User) {
    val convUser = findConversationUser(id, user.id)
    check(convUser != null && convUser.isOwner) {
      "Can't delete conversation $id. User is not the owner"
    }

    val rowsAffected = database.delete(Conversation.TABLE) {
      it.id eq id
    }

    if (rowsAffected != 1) throw Exception("Could not delete record")
  }

  fun createConversationUser(
    conversationId: String,
    user: User,
    nickname: String?,
    isOwner: Boolean = false
  ): ConversationUser {
    val convUser = ConversationUser(
      id = generateId(),
      conversationId = conversationId,
      userId = user.id,
      nickname = nickname ?: user.username,
      isOwner = isOwner
    )

    val rowsAffected = database.insert(ConversationUser.TABLE) {
      set(it.id, convUser.id)
      set(it.conversationId, convUser.conversationId)
      set(it.userId, convUser.userId)
      set(it.nickname, convUser.nickname)
      set(it.isOwner, convUser.isOwner)
    }

    if (rowsAffected != 1) throw Exception("Could not insert record")

    return convUser
  }

  fun updateConversationUser(convUser: ConversationUser): ConversationUser {
    val rowsAffected = database.update(ConversationUser.TABLE) {
      set(it.nickname, convUser.nickname)
      where {
        it.id eq convUser.id
      }
    }

    if (rowsAffected != 1) throw Exception("Could not insert record")

    return convUser
  }

  fun findConversationUser(
    conversationId: String,
    userId: String
  ): ConversationUser? {
    val findOneQuery = database.from(ConversationUser.TABLE).select()
      .where {
        (ConversationUser.TABLE.conversationId eq conversationId) and
            (ConversationUser.TABLE.userId eq userId)
      }
      .map { row ->
        ConversationUser.TABLE.let { t ->
          ConversationUser(
            id = row.nonNull(t.id),
            conversationId = row.nonNull(t.conversationId),
            userId = row.nonNull(t.userId),
            nickname = row.nonNull(t.nickname),
            isOwner = row.nonNull(t.isOwner)
          )
        }
      }

    return findOneQuery.firstOrNull()
  }

  fun getConversationUsers(conversationId: String): List<ConversationUser> {
    val findAllQuery = database.from(ConversationUser.TABLE).select()
      .where { ConversationUser.TABLE.conversationId eq conversationId }
      .map { row ->
        ConversationUser.TABLE.let { t ->
          ConversationUser(
            id = row.nonNull(t.id),
            conversationId = row.nonNull(t.conversationId),
            userId = row.nonNull(t.userId),
            nickname = row.nonNull(t.nickname),
            isOwner = row.nonNull(t.isOwner)
          )
        }
      }

    return findAllQuery
  }

  fun getUserConversations(userId: String): List<ConversationUser> {
    val findConvsQuery = database.from(ConversationUser.TABLE)
      .leftJoin(
        Conversation.TABLE, on = ConversationUser.TABLE.conversationId eq Conversation.TABLE.id)
      .select()
      .where { ConversationUser.TABLE.userId eq userId }
      .map { row ->
        (Conversation.TABLE to ConversationUser.TABLE).let { (c, cu) ->
          val convUser = ConversationUser(
            id = row.nonNull(cu.id),
            conversationId = row.nonNull(c.id),
            userId = row.nonNull(cu.userId),
            nickname = row.nonNull(cu.nickname),
            isOwner = row.nonNull(cu.isOwner)
          )
          val conv = Conversation(
            id = row.nonNull(c.id),
            title = row.nonNull(c.title),
            isPrivate = row.nonNull(c.isPrivate)
          )
          conv to convUser
        }
      }

    val convIds = findConvsQuery.map { it.first.id }

    val updateTimeMap = database.from(Message.TABLE)
      .leftJoin(ConversationUser.TABLE, on = Message.TABLE.senderId eq ConversationUser.TABLE.id)
      .select(ConversationUser.TABLE.conversationId, max(Message.TABLE.time))
      .groupBy(ConversationUser.TABLE.conversationId)
      .where { ConversationUser.TABLE.conversationId inList convIds }
      .map { row ->
        val convId = row.getString(1) ?: error("")
        val updatedAt = row.getInstant(2)
        convId to updatedAt
      }
      .associate { it }

    val tagMap = mutableMapOf<String, MutableList<String>>()
    database.from(ConversationTag.TABLE).select()
      .where { ConversationTag.TABLE.conversationId inList convIds }
      .forEach { row ->
        ConversationTag.TABLE.let { t ->
          val convId = row.nonNull(t.conversationId)
          val tag = row.nonNull(t.tag)
          tagMap.compute(convId) { k, v ->
            v?.apply { add(tag) } ?: mutableListOf(tag)
          }
        }
      }

    return findConvsQuery.map { (conv, convUser) ->
      conv.updatedAt = updateTimeMap[conv.id]
      conv.tags = tagMap[conv.id] ?: listOf()
      convUser.conversation = conv
      convUser
    }
  }
}
