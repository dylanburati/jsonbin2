package com.dylanburati.jsonbin2.entities.conversations

import com.fasterxml.jackson.annotation.JsonIgnore
import me.liuwj.ktorm.schema.Table
import me.liuwj.ktorm.schema.boolean
import me.liuwj.ktorm.schema.varchar

data class ConversationUser(
  @JsonIgnore var id: String,
  @JsonIgnore var conversationId: String,
  var userId: String,
  var nickname: String,
  var isOwner: Boolean
) {
  @JsonIgnore
  var conversation: Conversation? = null

  object TABLE : Table<Nothing>("conversation_user") {
    val id = varchar("id").primaryKey()
    val conversationId = varchar("conversation_id")
    val userId = varchar("user_id")
    val nickname = varchar("nickname")
    val isOwner = boolean("is_owner")
  }
}
